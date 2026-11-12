package com.innovify.skillswap.credentialverification.infrastructure

import com.innovify.skillswap.credentialverification.domain.Certificate
import com.innovify.skillswap.credentialverification.domain.CertificateLink
import com.innovify.skillswap.credentialverification.domain.LinkCertificateRequest
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface CertificateApi {

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

val certificateApi: CertificateApi by lazy { RetrofitClient.create(CertificateApi::class.java) }
