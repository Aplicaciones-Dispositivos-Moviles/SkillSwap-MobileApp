package com.innovify.skillswap.model.response

import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.Certificate
import com.innovify.skillswap.model.beans.CertificateLink
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LinkCertificateRequest
import com.innovify.skillswap.model.beans.SignInRequest
import okhttp3.MultipartBody
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

}
