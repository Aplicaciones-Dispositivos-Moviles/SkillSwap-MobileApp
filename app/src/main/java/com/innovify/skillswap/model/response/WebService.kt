package com.innovify.skillswap.model.response

import com.innovify.skillswap.model.beans.AssessmentAttempt
import com.innovify.skillswap.model.beans.AssessmentBlueprint
import com.innovify.skillswap.model.beans.AttachEvidenceRequest
import com.innovify.skillswap.model.beans.PlanSubscription
import com.innovify.skillswap.model.beans.StudentPlan
import com.innovify.skillswap.model.beans.UpdateInterestsRequest
import com.innovify.skillswap.model.beans.CreateVerifierProfileRequest
import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.Certificate
import com.innovify.skillswap.model.beans.CertificateLink
import com.innovify.skillswap.model.beans.CreditTransaction
import com.innovify.skillswap.model.beans.DeclareGoalRequest
import com.innovify.skillswap.model.beans.DeviceTokenRequest
import com.innovify.skillswap.model.beans.FinalDemonstration
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LinkCertificateRequest
import com.innovify.skillswap.model.beans.MiniProject
import com.innovify.skillswap.model.beans.MiniProjectSubmission
import com.innovify.skillswap.model.beans.RescheduleDemonstrationRequest
import com.innovify.skillswap.model.beans.ScheduleDemonstrationRequest
import com.innovify.skillswap.model.beans.SignInRequest
import com.innovify.skillswap.model.beans.SignUpRequest
import com.innovify.skillswap.model.beans.SkillOption
import com.innovify.skillswap.model.beans.SkillOptionsRequest
import com.innovify.skillswap.model.beans.SubmitAttemptRequest
import com.innovify.skillswap.model.beans.SubmitMiniProjectRequest
import com.innovify.skillswap.model.beans.VerificationCase
import com.innovify.skillswap.model.beans.VerificationCaseDetail
import com.innovify.skillswap.model.beans.UserResource
import com.innovify.skillswap.model.beans.Wallet
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface WebService {

    // 200 AuthUser | 401 credenciales inválidas | 403 correo sin verificar
    @POST("api/v1/authentication/sign-in")
    suspend fun signIn(@Body body: SignInRequest): Response<AuthUser>

    // 201 UserResource | 400 dominio no institucional o datos inválidos | 409 usuario o correo ya existe
    @POST("api/v1/authentication/sign-up")
    suspend fun signUp(@Body body: SignUpRequest): Response<UserResource>

    // Habilita al Estudiante como Verificador de la habilidad de un nodo que completó.
    // 201 perfil creado | 200 habilidad agregada | 400 | 403 nodo no completado | 409 ya habilitado
    @POST("api/v1/verifier-profiles")
    suspend fun createVerifierProfile(@Body body: CreateVerifierProfileRequest): Response<ResponseBody>

    // Todas las rutas del Estudiante, la más reciente primero. Solo el dueño puede leerlas.
    @GET("api/v1/learning-paths")
    suspend fun getLearningPaths(@Query("studentId") studentId: Int): Response<List<LearningPath>>

    // T46/T47: habilidades candidatas del catálogo para la meta, de mayor a menor afinidad.
    // 200 lista | 400 meta vacía o muy larga | 422 la meta no coincide con ninguna habilidad
    @POST("api/v1/learning-paths/skill-options")
    suspend fun getSkillOptions(@Body body: SkillOptionsRequest): Response<List<SkillOption>>

    // Declara la meta y construye la ruta con la habilidad confirmada.
    // 201 LearningPath | 400 | 409 límite del plan o ya demostró todas las habilidades | 422 meta sin habilidades
    @POST("api/v1/learning-paths")
    suspend fun declareGoal(@Body body: DeclareGoalRequest): Response<LearningPath>

    // Sube la foto del certificado + los campos leídos por el OCR (multipart).
    // 201 Certificate | 400 | 409 archivo duplicado | 413 muy grande | 415 tipo inválido.
    // Todas las partes son MultipartBody.Part: así el texto no pasa por Gson (que le pondría comillas).
    @Multipart
    @POST("api/v1/certificates")
    suspend fun uploadCertificate(
        @Part file: MultipartBody.Part,
        @Part institutionName: MultipartBody.Part,
        @Part courseName: MultipartBody.Part,
        @Part issueDate: MultipartBody.Part,
        @Part ocrText: MultipartBody.Part
    ): Response<Certificate>

    // Asocia el certificado al nodo si cubre su habilidad.
    // 200 CertificateLink | 409 certificado no verificado | 422 no cubre la habilidad
    @POST("api/v1/path-nodes/{nodeId}/certificate")
    suspend fun linkCertificate(
        @Path("nodeId") nodeId: Int,
        @Body body: LinkCertificateRequest
    ): Response<CertificateLink>

    // Genera con IA el quiz del nodo (sin respuestas correctas). Cada llamada crea uno nuevo.
    // 201 AssessmentBlueprint | 403 | 404 | 409 nodo bloqueado o completado | 503 falló la IA
    @POST("api/v1/path-nodes/{nodeId}/assessment-blueprint")
    suspend fun generateBlueprint(@Path("nodeId") nodeId: Int): Response<AssessmentBlueprint>

    // Envía las respuestas y recibe la calificación.
    // 201 AssessmentAttempt | 400 | 403 | 404 | 409
    @POST("api/v1/assessment-attempts")
    suspend fun submitAttempt(@Body body: SubmitAttemptRequest): Response<AssessmentAttempt>

    // Último proyecto del nodo. 200 MiniProject | 404 si todavía no tiene
    @GET("api/v1/path-nodes/{nodeId}/mini-project")
    suspend fun getMiniProject(@Path("nodeId") nodeId: Int): Response<MiniProject>

    // Genera con IA el proyecto del nodo; si el último aún se puede entregar, lo devuelve (200) sin generar otro.
    // 201 nuevo | 200 el vigente | 403 | 404 | 409 | 503 falló la IA
    @POST("api/v1/path-nodes/{nodeId}/mini-project")
    suspend fun generateMiniProject(
        @Path("nodeId") nodeId: Int,
        @Query("regenerate") regenerate: Boolean
    ): Response<MiniProject>

    // Entrega el enlace del repositorio. 201 MiniProjectSubmission con evaluationStatus "Pending" (la IA evalúa
    // en segundo plano) | 400 enlace inválido | 403 | 404 |
    // 409 plazo vencido, ya entregado, hay uno más nuevo o el nodo tiene un caso en curso
    @POST("api/v1/mini-projects/{miniProjectId}/submissions")
    suspend fun submitMiniProject(
        @Path("miniProjectId") miniProjectId: Int,
        @Body body: SubmitMiniProjectRequest
    ): Response<MiniProjectSubmission>

    // La entrega con su evaluación preliminar; se consulta hasta que evaluationStatus deje de ser "Pending".
    // 200 MiniProjectSubmission | 403 no es del Estudiante | 404
    @GET("api/v1/mini-projects/{miniProjectId}/submissions/{submissionId}")
    suspend fun getMiniProjectSubmission(
        @Path("miniProjectId") miniProjectId: Int,
        @Path("submissionId") submissionId: Int
    ): Response<MiniProjectSubmission>

    // Detalle de un caso de verificación: estado, decisión, notas y preguntas falladas (quiz).
    // 200 VerificationCaseDetail | 403 no es del Estudiante | 404
    @GET("api/v1/verification-cases/{id}")
    suspend fun getVerificationCase(@Path("id") id: Int): Response<VerificationCaseDetail>

    // Apela un caso rechazado (una sola vez); el caso se reabre con otro Verificador.
    // 200 VerificationCase | 403 | 404 | 409 no está rechazado, ya se apeló o el nodo tiene otro caso abierto
    @POST("api/v1/verification-cases/{id}/appeal")
    suspend fun appealVerificationCase(@Path("id") id: Int): Response<VerificationCase>

    // US26: adjunta (o reemplaza) el enlace de evidencia mientras el caso no esté resuelto.
    // 200 VerificationCase | 400 no es http/https | 403 | 404 | 409 ya resuelto
    @PUT("api/v1/verification-cases/{id}/evidence")
    suspend fun attachEvidence(@Path("id") id: Int, @Body body: AttachEvidenceRequest): Response<VerificationCase>

    // US05: plan vigente (Free o Premium), sus límites y la suscripción. 200 | 403
    @GET("api/v1/subscriptions/{studentId}")
    suspend fun getPlan(@Path("studentId") studentId: Int): Response<StudentPlan>

    // Compra Premium sin productId: el cobro simulado usa el producto mensual por defecto.
    // 201 | 400 producto inválido | 422 sin compra activa | 503 el cobro no respondió
    @POST("api/v1/subscriptions")
    suspend fun subscribe(): Response<PlanSubscription>

    // Cancela la renovación; el plan sigue hasta el fin del periodo pagado. 200 | 403 | 404 | 409 ya venció | 503
    @PATCH("api/v1/subscriptions/{id}/cancel")
    suspend fun cancelSubscription(@Path("id") id: Int): Response<PlanSubscription>

    // US04: usuario autenticado (trae sus intereses)
    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<UserResource>

    // US04: reemplaza los intereses. 200 UserResource | 400 | 403
    @PUT("api/v1/users/{id}/interests")
    suspend fun updateInterests(@Path("id") id: Int, @Body body: UpdateInterestsRequest): Response<UserResource>

    // Saldo de SkillCredits del usuario (solo el dueño).
    // 200 Wallet | 403 no es tu billetera | 404 cuentas anteriores a las billeteras
    @GET("api/v1/wallets/{userId}")
    suspend fun getWallet(@Path("userId") userId: Int): Response<Wallet>

    // Movimientos de la billetera, del más reciente al más antiguo. 200 | 403 | 404
    @GET("api/v1/wallets/{userId}/transactions")
    suspend fun getWalletTransactions(@Path("userId") userId: Int): Response<List<CreditTransaction>>

    // Registra (o reemplaza) el token de notificaciones push de este teléfono. 204 | 400 InvalidDeviceToken
    @PUT("api/v1/users/me/device-token")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): Response<ResponseBody>

    // Olvida el token (permiso negado o cierre de sesión). 204, también si no había. Lleva su propio
    // Authorization porque al cerrar sesión el token global ya se limpió.
    @DELETE("api/v1/users/me/device-token")
    suspend fun removeDeviceToken(@Header("Authorization") authorization: String): Response<ResponseBody>

    // Programa la demostración final de una ruta completada (al menos 24 h adelante); el backend asigna un Verificador.
    // 201 | 400 | 403 | 404 | 409 (ruta sin completar, falta menos de 24 h, sin Verificador libre o ya hay una)
    @POST("api/v1/final-demonstrations")
    suspend fun scheduleDemonstration(@Body body: ScheduleDemonstrationRequest): Response<FinalDemonstration>

    // Las demostraciones del Estudiante, la más reciente primero
    @GET("api/v1/final-demonstrations/me")
    suspend fun getMyDemonstrations(): Response<List<FinalDemonstration>>

    // Mueve una demostración programada a otro momento (al menos 24 h adelante). 200 | 400 | 403 | 404 | 409
    @PATCH("api/v1/final-demonstrations/{id}/reschedule")
    suspend fun rescheduleDemonstration(
        @Path("id") id: Int,
        @Body body: RescheduleDemonstrationRequest
    ): Response<FinalDemonstration>

    // Cancela una demostración programada. 200 | 403 | 404 | 409
    @PATCH("api/v1/final-demonstrations/{id}/cancel")
    suspend fun cancelDemonstration(@Path("id") id: Int): Response<FinalDemonstration>

    // Sube (o reemplaza) el video en la parte "file": MP4, WebM o QuickTime, hasta 25 MB.
    // 200 estado VideoSubmitted | 400 | 403 | 404 | 409 | 413 | 415 | 502
    @Multipart
    @POST("api/v1/final-demonstrations/{id}/video")
    suspend fun uploadDemonstrationVideo(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part
    ): Response<FinalDemonstration>

}
