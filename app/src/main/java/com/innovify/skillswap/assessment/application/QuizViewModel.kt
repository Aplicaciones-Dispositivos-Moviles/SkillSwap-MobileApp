package com.innovify.skillswap.assessment.application

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.innovify.skillswap.assessment.domain.AssessmentAttempt
import com.innovify.skillswap.assessment.domain.AssessmentBlueprint
import com.innovify.skillswap.assessment.domain.QuizProgress
import com.innovify.skillswap.assessment.domain.SubmitAttemptRequest
import com.innovify.skillswap.assessment.infrastructure.QuizDao
import com.innovify.skillswap.assessment.infrastructure.assessmentApi
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore

// assessment: the node quiz, saved on the phone while it is answered and resent when internet returns.
class QuizViewModel(session: SessionStore) : SessionAwareViewModel(session) {

    // ------------------------------------------------------------ QUIZ (T50 + T51)
    // Quiz abierto, respuestas elegidas (-1 = sin responder) y su estado: Draft | Pending | Sent
    var blueprint: AssessmentBlueprint? by mutableStateOf(null)
    var answers: List<Int> by mutableStateOf(emptyList())
    var quizStatus: String by mutableStateOf("Draft")
    var attemptResult: AssessmentAttempt? by mutableStateOf(null)
    var quizMessage: String by mutableStateOf("")
    // Código HTTP del último envío de quiz que falló (0 = sin respuesta del servidor, p. ej. sin internet)
    var lastSendCode: Int by mutableStateOf(0)

    init {
        session.onSignOut {
            blueprint = null
            attemptResult = null
            quizMessage = ""
        }
    }

    private fun showQuiz(quiz: AssessmentBlueprint, progress: QuizProgress?) {
        val saved = progress?.answers
        blueprint = quiz
        answers = if (saved != null && saved.size == quiz.questions.size) saved
        else List(quiz.questions.size) { -1 }
        quizStatus = progress?.status ?: "Draft"
        attemptResult = progress?.resultJson?.let {
            try {
                Gson().fromJson(it, AssessmentAttempt::class.java)
            } catch (e: Exception) {
                null
            }
        }
        quizMessage = ""
    }

    // Abre el quiz del nodo. Reutiliza el guardado mientras no se haya calificado; si no hay ninguno
    // (o el anterior ya se calificó) pide uno nuevo al backend, lo cual requiere internet.
    suspend fun openQuiz(context: Context, nodeId: Int): Boolean {
        val db = db(context)
        val local = try {
            QuizDao(db).findBlueprint(nodeId)
        } catch (e: Exception) {
            null
        }
        val progress = local?.let {
            try {
                QuizDao(db).findProgress(it.id)
            } catch (e: Exception) {
                null
            }
        }
        if (local != null && progress?.status != "Sent") {
            showQuiz(local, progress)
            return true
        }
        if (!connectivity.isOnline) {
            if (local != null && progress != null) {
                showQuiz(local, progress) // sin internet se muestra el último resultado guardado
                return true
            }
            errorMessage = "Necesitas internet para descargar el quiz la primera vez"
            return false
        }
        return try {
            val response = assessmentApi.generateBlueprint(nodeId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                QuizDao(db).saveBlueprint(body, nodeId)
                showQuiz(body, QuizDao(db).findProgress(body.id))
                true
            } else {
                errorMessage = when (response.code()) {
                    503 -> "El servicio de IA no pudo generar el quiz. Intenta de nuevo en un momento"
                    else -> messageFrom(response, "No se pudo generar el quiz (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo generar el quiz. Revisa tu conexión"
            false
        }
    }

    // T50: cada opción elegida se guarda al instante en SQLite (sobrevive si se cierra la app).
    fun selectAnswer(context: Context, position: Int, option: Int) {
        val quiz = blueprint ?: return
        if (quizStatus != "Draft" || position !in answers.indices) return
        answers = answers.toMutableList().also { it[position] = option }
        try {
            QuizDao(db(context)).saveProgress(quiz.id, quiz.pathNodeId, answers, "Draft")
        } catch (e: Exception) {
            // la respuesta sigue en pantalla; se guardará con la siguiente
        }
    }

    // Envía el quiz. Sin internet queda "Pending" en SQLite y resendPending lo manda al reconectar (T51).
    suspend fun submitQuiz(context: Context) {
        val quiz = blueprint ?: return
        if (quizStatus != "Draft" || answers.any { it < 0 }) return
        try {
            QuizDao(db(context)).saveProgress(quiz.id, quiz.pathNodeId, answers, "Pending")
        } catch (e: Exception) {
            quizMessage = "No se pudieron guardar tus respuestas en el teléfono"
            return
        }
        quizStatus = "Pending"
        if (!connectivity.isOnline) {
            quizMessage = "Sin conexión.\nTus respuestas quedaron guardadas y se enviarán cuando vuelva internet."
            return
        }
        val sent = sendAttempt(context, QuizProgress(quiz.id, quiz.pathNodeId, answers, "Pending", null))
        quizMessage = if (sent != null) ""
        else errorMessage + "\nTus respuestas siguen guardadas y se reenviarán al reconectar."
    }

    // When internet returns, the open quiz screen and the navigation's resend can both try to send the same
    // attempt; the gate lets only one POST through and the other reuses the stored result.
    private val sendGate = SendOnceGate()

    // Devuelve el resultado, o null si no se pudo (queda "Pending").
    private suspend fun sendAttempt(context: Context, progress: QuizProgress): AssessmentAttempt? =
        sendGate.send(alreadyDone = { storedResult(context, progress.blueprintId) }) { postAttempt(context, progress) }

    // Result already saved for this attempt (it was sent by another caller), shown as if this call had sent it.
    private fun storedResult(context: Context, blueprintId: Int): AssessmentAttempt? {
        val saved = try {
            QuizDao(db(context)).findProgress(blueprintId)
        } catch (e: Exception) {
            null
        }
        if (saved?.status != "Sent") return null
        val result = saved.resultJson?.let {
            try {
                Gson().fromJson(it, AssessmentAttempt::class.java)
            } catch (e: Exception) {
                null
            }
        } ?: return null
        if (blueprint?.id == blueprintId) {
            attemptResult = result
            quizStatus = "Sent"
            quizMessage = ""
        }
        return result
    }

    // POST api/v1/assessment-attempts.
    private suspend fun postAttempt(context: Context, progress: QuizProgress): AssessmentAttempt? {
        lastSendCode = 0
        return try {
            val response = assessmentApi.submitAttempt(
                SubmitAttemptRequest(progress.blueprintId, progress.answers)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    QuizDao(db(context)).markAttemptSent(progress.blueprintId, Gson().toJson(body))
                } catch (e: Exception) {
                }
                if (blueprint?.id == progress.blueprintId) {
                    attemptResult = body
                    quizStatus = "Sent"
                    quizMessage = ""
                }
                body
            } else {
                lastSendCode = response.code()
                errorMessage = messageFrom(response, "No se pudo enviar el quiz (${response.code()})")
                null
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo enviar el quiz. Revisa tu conexión"
            null
        }
    }

    // T51: reenvía los quiz que quedaron "Pending". Devuelve cuántos se calificaron.
    suspend fun resendPending(context: Context): Int {
        val pending = try {
            QuizDao(db(context)).pendingAttempts()
        } catch (e: Exception) {
            emptyList()
        }
        var sent = 0
        for (progress in pending) {
            if (!connectivity.isOnline) break
            if (sendAttempt(context, progress) != null) sent++
        }
        return sent
    }

    // Reintenta a mano el quiz abierto que quedó "Pending". true si el backend lo calificó; si no, quizMessage
    // explica el motivo real (antes solo se veía "se enviarán cuando haya internet" aunque ya hubiera).
    suspend fun retryPendingQuiz(context: Context): Boolean {
        val quiz = blueprint ?: return false
        if (quizStatus != "Pending") return false
        if (!connectivity.isOnline) {
            quizMessage = "Sin conexión.\nTus respuestas siguen guardadas y se enviarán cuando vuelva internet."
            return false
        }
        val sent = sendAttempt(context, QuizProgress(quiz.id, quiz.pathNodeId, answers, "Pending", null))
        quizMessage = when {
            sent != null -> ""
            quizWasRejected() -> errorMessage + "\nEl servidor rechazó este intento. Puedes descartarlo y pedir un quiz nuevo."
            else -> errorMessage + "\nTus respuestas siguen guardadas."
        }
        return sent != null
    }

    // true si el servidor respondió con un error 4xx que reintentar no arregla (el 401 es sesión vencida)
    fun quizWasRejected(): Boolean = lastSendCode in 400..499 && lastSendCode != 401

    // Descarta el quiz guardado del nodo y pide uno nuevo al backend. true si el nuevo quedó abierto.
    suspend fun discardPendingQuiz(context: Context, nodeId: Int): Boolean {
        try {
            QuizDao(db(context)).deleteQuiz(nodeId)
        } catch (e: Exception) {
            errorMessage = "No se pudo descartar el intento guardado"
            return false
        }
        blueprint = null
        answers = emptyList()
        quizStatus = "Draft"
        attemptResult = null
        quizMessage = ""
        lastSendCode = 0
        return openQuiz(context, nodeId)
    }

    // true si el nodo tiene un quiz ya calificado cuyo resultado se puede volver a ver
    fun hasQuizResult(context: Context, nodeId: Int): Boolean {
        return try {
            val db = db(context)
            val local = QuizDao(db).findBlueprint(nodeId)
            val progress = local?.let { QuizDao(db).findProgress(it.id) }
            progress != null && progress.status == "Sent" && progress.resultJson != null
        } catch (e: Exception) {
            false
        }
    }

    // Deja cargado el último resultado del nodo (sin pedir un quiz nuevo). true si había uno.
    fun openLastResult(context: Context, nodeId: Int): Boolean {
        val db = db(context)
        val local = try {
            QuizDao(db).findBlueprint(nodeId)
        } catch (e: Exception) {
            null
        }
        val progress = local?.let {
            try {
                QuizDao(db).findProgress(it.id)
            } catch (e: Exception) {
                null
            }
        }
        if (local == null || progress == null || progress.status != "Sent" || progress.resultJson == null) {
            errorMessage = "Todavía no tienes un resultado de quiz para este nodo"
            return false
        }
        showQuiz(local, progress)
        return true
    }
}
