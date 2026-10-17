package com.innovify.skillswap.model.response

import com.innovify.skillswap.model.beans.AssessmentAttempt
import com.innovify.skillswap.model.beans.AssessmentBlueprint
import com.innovify.skillswap.model.beans.CreateVerifierProfileRequest
import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.Certificate
import com.innovify.skillswap.model.beans.CertificateLink
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LinkCertificateRequest
import com.innovify.skillswap.model.beans.MiniProject
import com.innovify.skillswap.model.beans.MiniProjectSubmission
import com.innovify.skillswap.model.beans.SignInRequest
import com.innovify.skillswap.model.beans.SignUpRequest
import com.innovify.skillswap.model.beans.SubmitAttemptRequest
import com.innovify.skillswap.model.beans.SubmitMiniProjectRequest
import com.innovify.skillswap.model.beans.UserResource
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
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

    // Entrega el enlace del repositorio. 201 MiniProjectSubmission | 400 enlace inválido | 403 | 404 |
    // 409 plazo vencido, ya entregado, hay uno más nuevo o el nodo tiene un caso en curso
    @POST("api/v1/mini-projects/{miniProjectId}/submissions")
    suspend fun submitMiniProject(
        @Path("miniProjectId") miniProjectId: Int,
        @Body body: SubmitMiniProjectRequest
    ): Response<MiniProjectSubmission>

}
