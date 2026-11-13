package com.innovify.skillswap.assessment.application

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.innovify.skillswap.assessment.domain.AssessmentAttempt
import com.innovify.skillswap.assessment.domain.AssessmentBlueprint
import com.innovify.skillswap.assessment.domain.MiniProjectSubmission
import com.innovify.skillswap.assessment.domain.QuizProgress
import com.innovify.skillswap.assessment.domain.SubmitAttemptRequest
import com.innovify.skillswap.assessment.infrastructure.MiniProjectDao
import com.innovify.skillswap.assessment.infrastructure.QuizDao
import com.innovify.skillswap.assessment.infrastructure.SavedQuizAttempt
import com.innovify.skillswap.assessment.infrastructure.assessmentApi
import com.innovify.skillswap.learningpath.domain.PathNode
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore

// ------------------------------------------------------------ EVALUACIONES TAB
// There is no list of assessments for the Student in the backend, so the tab is built from what the phone
// knows: the nodes of the Student's paths, the quizzes saved in SQLite and the saved mini-project submissions.

// Why an item is in "Requieren tu atención", in the order they are shown.
enum class AttentionReason { UnsentQuiz, CaseInReview, QuizInProgress, ProjectEvaluating, ReadyForQuiz }

data class AttentionItem(
    val reason: AttentionReason,
    val node: PathNode,
    val skillName: String,
    val caseId: Int? = null,
    val answered: Int = 0,
    val total: Int = 0
)

enum class AssessmentKind { Quiz, MiniProject }

// A graded assessment. passed = null when the outcome is not known on the phone (reviewed by a Verifier).
data class HistoryItem(
    val kind: AssessmentKind,
    val node: PathNode,
    val skillName: String,
    val passed: Boolean?,
    val score: String?,
    val dateMillis: Long?
)

data class AssessmentOverview(val attention: List<AttentionItem>, val history: List<HistoryItem>)

/**
 * Classifies the saved assessments. Only attempts of [nodes] (the Student's own paths) count, because the
 * phone may keep another account's quizzes. A node is ready for its quiz when it is available, has its
 * certificate linked, belongs to [activeNodeIds] (the path on screen, empty when it is paused) and nothing
 * else about it is already waiting.
 */
fun assessmentOverview(
    nodes: List<PathNode>,
    activeNodeIds: Set<Int>,
    quizzes: List<SavedQuizAttempt>,
    projects: List<MiniProjectSubmission>
): AssessmentOverview {
    val byId = nodes.associateBy { it.id }
    val attention = mutableListOf<AttentionItem>()
    val history = mutableListOf<HistoryItem>()

    for (saved in quizzes) {
        val node = byId[saved.progress.nodeId] ?: continue
        val skill = saved.skillName?.ifBlank { null } ?: node.skillName
        val answers = saved.progress.answers
        when (saved.progress.status) {
            "Pending" -> attention += AttentionItem(
                AttentionReason.UnsentQuiz, node, skill, answered = answers.count { it >= 0 }, total = answers.size
            )
            "Draft" -> if (answers.any { it >= 0 }) {
                attention += AttentionItem(
                    AttentionReason.QuizInProgress, node, skill, answered = answers.count { it >= 0 }, total = answers.size
                )
            }
            "Sent" -> {
                val result = attemptOf(saved.progress.resultJson) ?: continue
                history += HistoryItem(
                    AssessmentKind.Quiz, node, skill, result.passed,
                    "${result.score}/${result.totalQuestions}", saved.updatedAt
                )
                val caseId = result.verificationCaseId
                if (caseId != null && node.status != "Completed") {
                    attention += AttentionItem(AttentionReason.CaseInReview, node, skill, caseId = caseId)
                }
            }
        }
    }

    for (submission in projects) {
        val node = byId[submission.pathNodeId] ?: continue
        val date = epochMillisOf(submission.evaluatedAt ?: submission.submittedAt)
        val caseId = submission.verificationCaseId
        when {
            submission.isEvaluating() ->
                attention += AttentionItem(AttentionReason.ProjectEvaluating, node, node.skillName)
            submission.evaluationStatus == "Approved" ->
                history += HistoryItem(AssessmentKind.MiniProject, node, node.skillName, true, null, date)
            submission.evaluationStatus == "EscalationLimitReached" ->
                history += HistoryItem(AssessmentKind.MiniProject, node, node.skillName, false, null, date)
            submission.evaluationStatus == "Escalated" && caseId != null && node.status != "Completed" ->
                attention += AttentionItem(AttentionReason.CaseInReview, node, node.skillName, caseId = caseId)
            else ->
                history += HistoryItem(AssessmentKind.MiniProject, node, node.skillName, null, null, date)
        }
    }

    val waiting = attention.map { it.node.id }.toSet()
    nodes.filter {
        it.id in activeNodeIds && it.status == "Available" && it.linkedCertificateId != null && it.id !in waiting
    }.sortedBy { it.order }.forEach {
        attention += AttentionItem(AttentionReason.ReadyForQuiz, it, it.skillName)
    }

    return AssessmentOverview(
        attention = attention.distinctBy { it.caseId?.let { id -> "case-$id" } ?: "${it.reason}-${it.node.id}" }
            .sortedBy { it.reason.ordinal },
        history = history.sortedByDescending { it.dateMillis ?: Long.MIN_VALUE }
    )
}

// Backend instant ("2026-09-18T10:00:00Z", with or without the "Z") in epoch millis, or null.
private fun epochMillisOf(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (e: Exception) {
        try {
            java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}

private fun attemptOf(json: String?): AssessmentAttempt? = try {
    json?.let { Gson().fromJson(it, AssessmentAttempt::class.java) }
} catch (e: Exception) {
    null
}

// assessment: the node quiz, saved on the phone while it is answered and resent when internet returns.
class QuizViewModel(session: SessionStore) : SessionAwareViewModel(session) {

    // Saved quiz attempts and mini-project submissions read for the Evaluaciones tab
    var savedAttempts: List<SavedQuizAttempt> by mutableStateOf(emptyList())
    var savedProjects: List<MiniProjectSubmission> by mutableStateOf(emptyList())

    // Reads what the phone has saved for these nodes (SQLite, no network). Call it on Dispatchers.IO.
    fun loadSavedAssessments(context: Context, nodeIds: Collection<Int>) {
        val db = db(context)
        savedAttempts = try {
            QuizDao(db).savedAttempts()
        } catch (e: Exception) {
            emptyList()
        }
        savedProjects = nodeIds.mapNotNull {
            try {
                MiniProjectDao(db).findSubmission(it)
            } catch (e: Exception) {
                null
            }
        }
    }

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
            savedAttempts = emptyList()
            savedProjects = emptyList()
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
