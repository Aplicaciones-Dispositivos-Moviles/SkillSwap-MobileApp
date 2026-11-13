package com.innovify.skillswap.assessment.infrastructure

import com.innovify.skillswap.assessment.domain.AssessmentAttempt
import com.innovify.skillswap.assessment.domain.AssessmentBlueprint
import com.innovify.skillswap.assessment.domain.AttachEvidenceRequest
import com.innovify.skillswap.assessment.domain.CreateVerifierProfileRequest
import com.innovify.skillswap.assessment.domain.MiniProject
import com.innovify.skillswap.assessment.domain.MiniProjectSubmission
import com.innovify.skillswap.assessment.domain.StudentReevaluation
import com.innovify.skillswap.assessment.domain.SubmitAttemptRequest
import com.innovify.skillswap.assessment.domain.SubmitMiniProjectRequest
import com.innovify.skillswap.assessment.domain.VerificationCase
import com.innovify.skillswap.assessment.domain.VerificationCaseDetail
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AssessmentApi {

    // Habilita al Estudiante como Verificador de la habilidad de un nodo que completó.
    // 201 perfil creado | 200 habilidad agregada | 400 | 403 nodo no completado | 409 ya habilitado
    @POST("api/v1/verifier-profiles")
    suspend fun createVerifierProfile(@Body body: CreateVerifierProfileRequest): Response<ResponseBody>

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

    // Reevaluaciones que un Verificador senior ordenó sobre resultados del Estudiante, la más nueva primero.
    // 200 List<StudentReevaluation> (puede no existir aún en producción: cualquier error se ignora)
    @GET("api/v1/reevaluations/me")
    suspend fun getMyReevaluations(): Response<List<StudentReevaluation>>

}

val assessmentApi: AssessmentApi by lazy { RetrofitClient.create(AssessmentApi::class.java) }
