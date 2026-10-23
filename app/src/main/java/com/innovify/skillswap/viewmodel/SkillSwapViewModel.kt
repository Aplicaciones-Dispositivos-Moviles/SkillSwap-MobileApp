package com.innovify.skillswap.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.innovify.skillswap.model.beans.ApiError
import com.innovify.skillswap.model.beans.AssessmentAttempt
import com.innovify.skillswap.model.beans.AssessmentBlueprint
import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.CreateVerifierProfileRequest
import com.innovify.skillswap.model.beans.Certificate
import com.innovify.skillswap.model.beans.CertificateLink
import com.innovify.skillswap.model.beans.CertificateMismatch
import com.innovify.skillswap.model.beans.CreditTransaction
import com.innovify.skillswap.model.beans.DeclareGoalRequest
import com.innovify.skillswap.model.beans.DeviceTokenRequest
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LocalCertificate
import com.innovify.skillswap.model.beans.MiniProject
import com.innovify.skillswap.model.beans.MiniProjectSubmission
import com.innovify.skillswap.model.beans.LinkCertificateRequest
import com.innovify.skillswap.model.beans.PathNode
import com.innovify.skillswap.model.beans.QuizProgress
import com.innovify.skillswap.model.beans.SignInRequest
import com.innovify.skillswap.model.beans.SignUpRequest
import com.innovify.skillswap.model.beans.SkillOption
import com.innovify.skillswap.model.beans.SkillOptionsRequest
import com.innovify.skillswap.model.beans.SubmitAttemptRequest
import com.innovify.skillswap.model.beans.SubmitMiniProjectRequest
import com.innovify.skillswap.model.beans.VerificationCaseDetail
import com.innovify.skillswap.model.beans.Wallet
import com.innovify.skillswap.model.client.RetrofitClient
import com.innovify.skillswap.model.db.SkillSwapDbHelper
import okhttp3.MediaType
import okhttp3.MultipartBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.RequestBody
import retrofit2.Response
import java.io.File

class SkillSwapViewModel : ViewModel() {

    // ------------------------------------------------------------ OFFLINE (T49)
    // Estado de la conexión (se arranca en MainActivity) y base SQLite local.
    val connectivity = ConnectivityObserver()

    // true cuando la ruta que se ve viene de SQLite y no del servidor
    var showingCache by mutableStateOf(false)

    private var dbHelper: SkillSwapDbHelper? = null

    private fun db(context: Context): SkillSwapDbHelper {
        return dbHelper ?: SkillSwapDbHelper(context.applicationContext).also { dbHelper = it }
    }

    override fun onCleared() {
        connectivity.stop()
    }

    // ------------------------------------------------------------------ SESIÓN
    var user: AuthUser? by mutableStateOf(null)

    // Último mensaje de error de red. OJO: aquí NO se puede mostrar Toast (hilo IO);
    // la vista lo muestra con withContext(Dispatchers.Main).
    var errorMessage: String = ""

    // Sesión guardada en el teléfono (SharedPreferences) para no pedir el login al reabrir la app.
    private fun sessionPrefs(context: Context) =
        context.applicationContext.getSharedPreferences("skillswap_session", Context.MODE_PRIVATE)

    // Se llama al abrir la app (MainActivity): recupera el usuario y el token guardados, si hay.
    fun restoreSession(context: Context) {
        if (user != null) return // la app ya estaba abierta (por ejemplo, tras girar la pantalla)
        val raw = sessionPrefs(context).getString("user", null) ?: return
        try {
            val saved = Gson().fromJson(raw, AuthUser::class.java)
            if (saved != null && saved.token.isNotEmpty()) {
                user = saved
                RetrofitClient.token = saved.token
            }
        } catch (e: Exception) {
            sessionPrefs(context).edit().remove("user").apply()
        }
    }

    // Cierra la sesión: borra lo guardado y limpia lo que se ve en pantalla.
    // La copia local de la ruta (SQLite) se conserva para cuando vuelva a entrar.
    fun signOut(context: Context) {
        forgetDeviceToken() // antes de limpiar el JWT: el siguiente usuario de este teléfono no debe recibir sus avisos
        sessionPrefs(context).edit().remove("user").apply()
        RetrofitClient.token = null
        user = null
        wallet = null
        transactions = emptyList()
        path = null
        selectedNode = null
        certificate = null
        certificateLink = null
        localCertificate = null
        blueprint = null
        attemptResult = null
        project = null
        projectSubmission = null
        quizMessage = ""
    }

    // suspend: la VISTA lo llama dentro de viewModelScope.launch(Dispatchers.IO).
    // Devuelve true si el login fue correcto.
    // Usuario recién registrado: el Login lo muestra ya escrito al volver del registro
    var registeredUsername: String by mutableStateOf("")

    // WF-10: habilitarse como Verificador de la habilidad de un nodo completado.
    // Devuelve el texto que se le muestra al Estudiante (éxito o motivo del rechazo).
    suspend fun enableAsVerifier(skillTag: String): String {
        return try {
            val response = RetrofitClient.webService.createVerifierProfile(
                CreateVerifierProfileRequest(skillTag)
            )
            when {
                response.isSuccessful -> "Listo: ya puedes recibir casos de $skillTag como Verificador"
                response.code() == 409 -> "Ya estás habilitado como Verificador de $skillTag"
                response.code() == 403 -> messageFrom(response, "Completa este nodo antes de habilitarte como Verificador")
                else -> messageFrom(response, "No se pudo habilitar como Verificador (${response.code()})")
            }
        } catch (e: Exception) {
            "No se pudo conectar con el servidor"
        }
    }

    // Registro de un Estudiante nuevo. No inicia sesión: primero debe verificar su correo.
    suspend fun signUp(username: String, email: String, password: String): Boolean {
        return try {
            val response = RetrofitClient.webService.signUp(SignUpRequest(username, email, password))
            if (response.isSuccessful) {
                registeredUsername = username
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> messageFrom(response, "Revisa los datos. El correo debe ser institucional (.edu.pe)")
                    409 -> messageFrom(response, "Ese usuario o correo ya está registrado")
                    else -> "No se pudo crear la cuenta (${response.code()})"
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    suspend fun signIn(username: String, password: String, context: Context): Boolean {
        return try {
            val response = RetrofitClient.webService.signIn(SignInRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                user = body
                RetrofitClient.token = body.token
                sessionPrefs(context).edit().putString("user", Gson().toJson(body)).apply()
                true
            } else {
                errorMessage = when (response.code()) {
                    401 -> "Usuario o contraseña incorrectos"
                    403 -> "Verifica tu correo institucional antes de ingresar"
                    else -> "No se pudo iniciar sesión (${response.code()})"
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------------------- RUTA
    // Ruta que se muestra (la más reciente del Estudiante) y el nodo elegido.
    var path: LearningPath? by mutableStateOf(null)
    var selectedNode: PathNode? by mutableStateOf(null)

    // Primero la red; si no hay conexión o el servidor falla (5xx), se muestra la última ruta guardada en SQLite.
    // Devuelve true si hay algo que mostrar (aunque el Estudiante aún no tenga ruta: path = null).
    suspend fun loadPath(context: Context): Boolean {
        val studentId = user?.id ?: return false
        if (!connectivity.isOnline) {
            return loadPathFromCache(context, studentId, "Sin conexión y no hay una ruta guardada en el teléfono")
        }
        return try {
            val response = RetrofitClient.webService.getLearningPaths(studentId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val latest = body.firstOrNull()
                path = latest
                showingCache = false
                // Guardar en SQLite es opcional: si falla, la ruta igual se muestra
                try {
                    if (latest != null) db(context).savePath(latest) else db(context).clearPath(studentId)
                } catch (e: Exception) {
                }
                true
            } else if (response.code() == 401) {
                // El token venció: se cierra la sesión y la app vuelve al Login
                signOut(context)
                errorMessage = "Tu sesión venció. Inicia sesión de nuevo"
                false
            } else if (response.code() >= 500) {
                loadPathFromCache(context, studentId, "No se pudo cargar la ruta (${response.code()})")
            } else {
                errorMessage = "No se pudo cargar la ruta (${response.code()})"
                false
            }
        } catch (e: Exception) {
            loadPathFromCache(context, studentId, "No se pudo conectar con el servidor")
        }
    }

    // Lee la última ruta guardada. Si no hay ninguna, errorMessage explica por qué no se puede mostrar nada.
    private fun loadPathFromCache(context: Context, studentId: Int, error: String): Boolean {
        val cached = try {
            db(context).getPath(studentId)
        } catch (e: Exception) {
            null
        }
        return if (cached != null) {
            path = cached
            showingCache = true
            true
        } else {
            errorMessage = error
            false
        }
    }

    // ---------------------------------------------------- META Y HABILIDAD (T47)
    // Meta escrita por el Estudiante y las habilidades candidatas que propone el backend (T46).
    var goal: String by mutableStateOf("")
    var skillOptions: List<SkillOption> by mutableStateOf(emptyList())

    // suspend: la vista lo llama en Dispatchers.IO. true si hay opciones para elegir.
    // Si el backend aún no tiene el endpoint (404), skillOptions queda vacío y la vista construye la ruta directo.
    suspend fun loadSkillOptions(goalText: String): Boolean {
        goal = goalText
        skillOptions = emptyList()
        return try {
            val response = RetrofitClient.webService.getSkillOptions(SkillOptionsRequest(goalText))
            val body = response.body()
            if (response.isSuccessful && body != null && body.isNotEmpty()) {
                skillOptions = body.sortedByDescending { it.affinity }
                true
            } else if (response.isSuccessful) {
                errorMessage = "No encontramos habilidades para esta meta. Cuéntanos qué quieres construir o para qué trabajo"
                false
            } else {
                errorMessage = when (response.code()) {
                    400 -> "Escribe tu meta (máximo 500 caracteres)"
                    409 -> messageFrom(response, "Ya demostraste las habilidades de esta meta. Prueba con una meta más avanzada")
                    422 -> messageFrom(response, "No encontramos habilidades para esta meta. Cuéntanos qué quieres construir o para qué trabajo")
                    else -> messageFrom(response, "No se pudo interpretar tu meta (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Construye la ruta con la habilidad confirmada (null = el backend interpreta la meta).
    // true si se creó; la nueva ruta queda en path y en SQLite.
    suspend fun declareGoal(context: Context, skillTag: String?): Boolean {
        return try {
            val response = RetrofitClient.webService.declareGoal(DeclareGoalRequest(goal, skillTag))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                path = body
                selectedNode = null
                showingCache = false
                try {
                    db(context).savePath(body)
                } catch (e: Exception) {
                }
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> messageFrom(response, "No se pudo crear la ruta con tu plan actual")
                    422 -> messageFrom(response, "No encontramos habilidades para esta meta")
                    else -> messageFrom(response, "No se pudo crear la ruta (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------ NOTIFICACIONES PUSH
    // Ruta a la que hay que ir porque el Estudiante tocó una notificación (NavSkillSwap la consume y la limpia)
    var pendingRoute: String? by mutableStateOf(null)

    // El permiso se pide una sola vez; si lo niegan, la app sigue funcionando sin avisos.
    fun shouldAskNotificationPermission(context: Context): Boolean =
        !sessionPrefs(context).getBoolean("notification_permission_asked", false)

    fun markNotificationPermissionAsked(context: Context) {
        sessionPrefs(context).edit().putBoolean("notification_permission_asked", true).apply()
    }

    // Con el permiso concedido envía el token de este teléfono al backend; sin permiso, lo borra.
    // Se llama tras iniciar sesión, al reabrir la app y al recuperar internet (el PUT se puede repetir).
    fun syncDeviceToken(context: Context) {
        if (user == null || !connectivity.isOnline) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            forgetDeviceToken()
            return
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    RetrofitClient.webService.registerDeviceToken(DeviceTokenRequest(token))
                } catch (e: Exception) {
                    // sin red: se reintenta cuando vuelva internet
                }
            }
        }
    }

    private fun forgetDeviceToken() {
        val jwt = RetrofitClient.token ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.webService.removeDeviceToken("Bearer $jwt")
            } catch (e: Exception) {
                // si falla, el backend olvida el token solo cuando FCM lo reporta inválido
            }
        }
    }

    // data.type de la notificación tocada -> pantalla que se abre
    fun openFromNotification(type: String, caseId: Int?) {
        pendingRoute = when (type) {
            "MiniProjectEscalatedByAi", "VerificationCaseResolved" ->
                if (caseId != null) {
                    selectedCaseId = caseId
                    "CaseStatus"
                } else "Path"
            "MiniProjectApprovedByAi" -> "Path"
            else -> null
        }
    }

    // ------------------------------------------------------- BILLETERA (solo lectura)
    // Saldo y movimientos de SkillCredits del usuario; siempre vienen del servidor (no se guardan en SQLite).
    var wallet: Wallet? by mutableStateOf(null)
    var transactions: List<CreditTransaction> by mutableStateOf(emptyList())

    // Carga el saldo y los movimientos. true si ambos llegaron; si no, errorMessage dice por qué.
    suspend fun loadWallet(): Boolean {
        val userId = user?.id
        if (userId == null) {
            errorMessage = "Inicia sesión para ver tu billetera"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver tu billetera"
            return false
        }
        return try {
            val walletResponse = RetrofitClient.webService.getWallet(userId)
            val walletBody = walletResponse.body()
            if (!walletResponse.isSuccessful || walletBody == null) {
                errorMessage = walletError(walletResponse)
                return false
            }
            val transactionsResponse = RetrofitClient.webService.getWalletTransactions(userId)
            val transactionsBody = transactionsResponse.body()
            if (!transactionsResponse.isSuccessful || transactionsBody == null) {
                errorMessage = walletError(transactionsResponse)
                return false
            }
            wallet = walletBody
            transactions = transactionsBody
            true
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    private fun walletError(response: Response<*>): String {
        return when (response.code()) {
            403 -> "Esta billetera no es tuya"
            404 -> messageFrom(response, "No encontramos tu billetera")
            else -> messageFrom(response, "No se pudo cargar tu billetera (${response.code()})")
        }
    }

    // ------------------------------------------------------------- CERTIFICADO
    // Último certificado subido y su vínculo con el nodo (null mientras no se hayan enviado).
    var certificate: Certificate? by mutableStateOf(null)
    var certificateLink: CertificateLink? by mutableStateOf(null)
    // 422 del vínculo: nodos de la ruta que el certificado sí cubre (null si no hubo ese error)
    var certificateMismatch: CertificateMismatch? by mutableStateOf(null)

    // T48: certificado guardado en el teléfono para el nodo abierto (null si no hay)
    var localCertificate: LocalCertificate? by mutableStateOf(null)

    fun loadLocalCertificate(context: Context, nodeId: Int) {
        localCertificate = try {
            db(context).findCertificate(nodeId)
        } catch (e: Exception) {
            null
        }
    }

    // T48: copia el archivo a filesDir/certificates (la caché la puede borrar Android) y guarda su ruta
    // en SQLite como "Pending". Devuelve el id de la fila, o -1 si falló (errorMessage dice por qué).
    fun saveEvidence(
        context: Context,
        uri: Uri,
        nodeId: Int,
        institution: String,
        course: String,
        issueDate: String,
        ocrText: String
    ): Long {
        return try {
            val mime = CertificateOcr.detectMime(context, uri)
                ?: throw IllegalStateException("El archivo debe ser JPG, PNG o PDF")
            val extension = when (mime) {
                "image/png" -> "png"
                "application/pdf" -> "pdf"
                else -> "jpg"
            }
            val folder = File(context.filesDir, "certificates").apply { mkdirs() }
            val file = File(folder, "cert_${System.currentTimeMillis()}.$extension")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No se pudo leer el archivo")
            input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }

            val db = db(context)
            // Un solo certificado pendiente por nodo: el nuevo reemplaza al anterior
            db.findCertificate(nodeId)?.let { old ->
                if (old.status == "Pending") {
                    File(old.filePath).delete()
                    db.deleteCertificate(old.id)
                }
            }
            val id = db.saveCertificate(
                LocalCertificate(
                    id = 0, nodeId = nodeId, filePath = file.absolutePath, mime = mime,
                    institution = institution, course = course, issueDate = issueDate,
                    ocrText = ocrText, status = "Pending", createdAt = System.currentTimeMillis()
                )
            )
            localCertificate = db.findCertificate(nodeId)
            id
        } catch (e: IllegalStateException) {
            errorMessage = e.message ?: "No se pudo guardar el certificado en el teléfono"
            -1
        } catch (e: Exception) {
            errorMessage = "No se pudo guardar el certificado en el teléfono"
            -1
        }
    }

    // ------------------------------------------------------------ QUIZ (T50 + T51)
    // Quiz abierto, respuestas elegidas (-1 = sin responder) y su estado: Draft | Pending | Sent
    var blueprint: AssessmentBlueprint? by mutableStateOf(null)
    var answers: List<Int> by mutableStateOf(emptyList())
    var quizStatus: String by mutableStateOf("Draft")
    var attemptResult: AssessmentAttempt? by mutableStateOf(null)
    var quizMessage: String by mutableStateOf("")

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
            db.findBlueprint(nodeId)
        } catch (e: Exception) {
            null
        }
        val progress = local?.let {
            try {
                db.findProgress(it.id)
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
            val response = RetrofitClient.webService.generateBlueprint(nodeId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                db.saveBlueprint(body, nodeId)
                showQuiz(body, db.findProgress(body.id))
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
            db(context).saveProgress(quiz.id, quiz.pathNodeId, answers, "Draft")
        } catch (e: Exception) {
            // la respuesta sigue en pantalla; se guardará con la siguiente
        }
    }

    // Envía el quiz. Sin internet queda "Pending" en SQLite y resendPending lo manda al reconectar (T51).
    suspend fun submitQuiz(context: Context) {
        val quiz = blueprint ?: return
        if (quizStatus != "Draft" || answers.any { it < 0 }) return
        try {
            db(context).saveProgress(quiz.id, quiz.pathNodeId, answers, "Pending")
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

    // POST api/v1/assessment-attempts. Devuelve el resultado, o null si no se pudo (queda "Pending").
    private suspend fun sendAttempt(context: Context, progress: QuizProgress): AssessmentAttempt? {
        return try {
            val response = RetrofitClient.webService.submitAttempt(
                SubmitAttemptRequest(progress.blueprintId, progress.answers)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    db(context).markAttemptSent(progress.blueprintId, Gson().toJson(body))
                } catch (e: Exception) {
                }
                if (blueprint?.id == progress.blueprintId) {
                    attemptResult = body
                    quizStatus = "Sent"
                    quizMessage = ""
                }
                body
            } else {
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
            db(context).pendingAttempts()
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

    // ------------------------------------------------------------ PROYECTO (T55)
    // Proyecto abierto del nodo y su entrega (null mientras no se haya entregado)
    var project: MiniProject? by mutableStateOf(null)
    var projectSubmission: MiniProjectSubmission? by mutableStateOf(null)

    private fun showProject(value: MiniProject, submission: MiniProjectSubmission?) {
        project = value
        projectSubmission = submission
    }

    // Abre el proyecto del nodo. Con internet pregunta al backend (y genera uno con IA si todavía no hay, o
    // si forceNew=true porque el anterior venció); sin internet muestra el último guardado en SQLite.
    suspend fun openProject(context: Context, nodeId: Int, forceNew: Boolean = false): Boolean {
        val db = db(context)
        if (!connectivity.isOnline) {
            val cached = try {
                db.findMiniProject(nodeId)
            } catch (e: Exception) {
                null
            }
            return if (cached != null) {
                showProject(cached, try { db.findSubmission(nodeId) } catch (e: Exception) { null })
                true
            } else {
                errorMessage = "Necesitas internet para ver el proyecto la primera vez"
                false
            }
        }
        return try {
            val latest = if (forceNew) null else RetrofitClient.webService.getMiniProject(nodeId)
            val response = if (latest == null || latest.code() == 404) {
                RetrofitClient.webService.generateMiniProject(nodeId, false)
            } else {
                latest
            }
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    db.saveMiniProject(body)
                } catch (e: Exception) {
                }
                showProject(body, try { db.findSubmission(nodeId) } catch (e: Exception) { null })
                true
            } else {
                errorMessage = when (response.code()) {
                    503 -> "El servicio de IA no pudo generar el proyecto. Intenta de nuevo en un momento"
                    else -> messageFrom(response, "No se pudo abrir el proyecto (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo abrir el proyecto. Revisa tu conexión"
            false
        }
    }

    // Entrega el enlace del repositorio. Solo se entrega una vez y requiere internet. true si el backend lo recibió.
    suspend fun submitProject(context: Context, repositoryUrl: String): Boolean {
        val current = project ?: return false
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para entregar el proyecto"
            return false
        }
        return try {
            val response = RetrofitClient.webService.submitMiniProject(
                current.id, SubmitMiniProjectRequest(repositoryUrl)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    db(context).saveSubmission(current.pathNodeId, body)
                } catch (e: Exception) {
                }
                projectSubmission = body
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> messageFrom(response, "El enlace del repositorio no es válido")
                    else -> messageFrom(response, "No se pudo entregar el proyecto (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo entregar el proyecto. Revisa tu conexión"
            false
        }
    }

    // ------------------------------------------------ EVALUACIÓN DE LA ENTREGA (T56)
    // La IA evalúa la entrega en segundo plano: se consulta cada 3 s, máximo 30 veces (≈ 90 s).
    private val evaluationPollAttempts = 30
    private val evaluationPollMillis = 3000L

    // Vuelve a pedir la entrega al backend (con su evaluación preliminar) y la guarda. true si la leyó.
    suspend fun refreshSubmission(context: Context): Boolean {
        val current = projectSubmission ?: return false
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver el resultado de la evaluación"
            return false
        }
        return try {
            val response = RetrofitClient.webService.getMiniProjectSubmission(current.miniProjectId, current.id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    db(context).saveSubmission(current.pathNodeId, body)
                } catch (e: Exception) {
                }
                projectSubmission = body
                true
            } else {
                errorMessage = messageFrom(response, "No se pudo leer el resultado de la evaluación (${response.code()})")
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Espera el resultado de la evaluación. true si ya lo hay; false si se acabó el tiempo (el Estudiante
    // puede volver a pedirlo). Si la IA aprobó, el nodo quedó completado en el backend: se recarga la ruta.
    suspend fun waitForEvaluation(context: Context): Boolean {
        for (attempt in 1..evaluationPollAttempts) {
            if (refreshSubmission(context) && projectSubmission?.isEvaluating() == false) {
                if (projectSubmission?.evaluationStatus == "Approved") {
                    val nodeId = selectedNode?.id
                    if (loadPath(context)) {
                        selectedNode = path?.nodes?.firstOrNull { it.id == nodeId } ?: selectedNode
                    }
                }
                return true
            }
            if (attempt < evaluationPollAttempts) delay(evaluationPollMillis)
        }
        return false
    }

    // ------------------------------------------------- CASO DE VERIFICACIÓN (WF-08)
    // Caso que se está viendo y su detalle (null mientras no se haya cargado)
    var selectedCaseId: Int? by mutableStateOf(null)
    var caseDetail: VerificationCaseDetail? by mutableStateOf(null)

    // Estado, decisión, notas del Verificador y, en un quiz, las preguntas falladas. true si lo leyó.
    suspend fun loadCase(caseId: Int): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver el estado del caso"
            return false
        }
        return try {
            val response = RetrofitClient.webService.getVerificationCase(caseId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                caseDetail = body
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Este caso no es tuyo"
                    404 -> "No encontramos ese caso"
                    else -> messageFrom(response, "No se pudo cargar el caso (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Apela un caso rechazado (una sola vez). El backend lo reabre con otro Verificador. true si se envió.
    suspend fun appealCase(caseId: Int): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para apelar"
            return false
        }
        return try {
            val response = RetrofitClient.webService.appealVerificationCase(caseId)
            val body = response.body()
            if (response.isSuccessful) {
                if (body != null) caseDetail = caseDetail?.copy(verificationCase = body)
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Este caso no es tuyo"
                    404 -> "No encontramos ese caso"
                    else -> messageFrom(response, "No se pudo enviar la apelación (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Texto del error del backend (ProblemDetail.detail) o uno por defecto.
    private fun messageFrom(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string()
            val error = Gson().fromJson(raw, ApiError::class.java)
            error?.detail ?: error?.title ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    // Parte multipart de texto (no pasa por Gson, así no se le agregan comillas).
    private fun textPart(name: String, value: String): MultipartBody.Part {
        return MultipartBody.Part.createFormData(name, value)
    }

    // Parte multipart del archivo. El backend exige que el Content-Type declarado coincida con los bytes
    // (image/jpeg, image/png o application/pdf).
    private fun filePart(file: UploadFile): MultipartBody.Part {
        val body = RequestBody.create(MediaType.parse(file.mime), file.bytes)
        return MultipartBody.Part.createFormData("file", file.fileName, body)
    }

    // POST api/v1/certificates. suspend: la vista lo llama en Dispatchers.IO. true si el backend respondió 201.
    suspend fun uploadCertificate(
        context: Context,
        uri: Uri, // foto de la cámara, imagen o PDF elegido
        institution: String,
        course: String,
        issueDate: String,
        ocrText: String,
        localId: Long // fila de local_certificate; se marca "Sent" cuando el backend responde 201
    ): Boolean {
        return try {
            val file = CertificateOcr.prepareUpload(context, uri)
            val response = RetrofitClient.webService.uploadCertificate(
                filePart(file),
                textPart("institutionName", institution),
                textPart("courseName", course),
                textPart("issueDate", issueDate),
                textPart("ocrText", ocrText)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                certificate = body
                certificateLink = null
                try {
                    db(context).markCertificateSent(localId)
                    localCertificate = localCertificate?.let { it.copy(status = "Sent") }
                } catch (e: Exception) {
                }
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> messageFrom(response, "Este certificado ya fue registrado")
                    413 -> "La foto es demasiado grande (máximo 10 MB)"
                    415 -> "El archivo debe ser JPG, PNG o PDF"
                    else -> messageFrom(response, "No se pudo subir el certificado (${response.code()})")
                }
                false
            }
        } catch (e: IllegalStateException) {
            // Archivo inválido o demasiado grande (mensaje de CertificateOcr.prepareUpload)
            errorMessage = e.message ?: "No se pudo preparar el archivo"
            false
        } catch (e: Exception) {
            errorMessage = "No se pudo subir el certificado. Revisa tu conexión"
            false
        }
    }

    // POST api/v1/path-nodes/{nodeId}/certificate con el certificado recién subido.
    // true si el certificado cubre la habilidad del nodo; si no, errorMessage explica el motivo.
    suspend fun linkCertificate(nodeId: Int): Boolean {
        val certificateId = certificate?.id ?: return false
        certificateMismatch = null
        return try {
            val response = RetrofitClient.webService.linkCertificate(
                nodeId, LinkCertificateRequest(certificateId)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                certificateLink = body
                certificateMismatch = null
                body.node?.let { selectedNode = it }
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> "El certificado aún no está verificado. Quedó en revisión"
                    422 -> {
                        // El cuerpo solo se puede leer una vez: de él salen el mensaje y los nodos sugeridos
                        val raw = try {
                            response.errorBody()?.string()
                        } catch (e: Exception) {
                            null
                        }
                        certificateMismatch = CertificateMismatch.fromErrorBody(raw)
                        try {
                            Gson().fromJson(raw, ApiError::class.java)?.detail
                        } catch (e: Exception) {
                            null
                        } ?: "El certificado no cubre la habilidad de este nodo"
                    }
                    else -> messageFrom(response, "No se pudo vincular el certificado (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo vincular el certificado. Revisa tu conexión"
            false
        }
    }

}
