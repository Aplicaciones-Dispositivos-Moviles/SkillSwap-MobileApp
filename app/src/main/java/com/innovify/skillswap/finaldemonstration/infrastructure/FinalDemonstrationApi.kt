package com.innovify.skillswap.finaldemonstration.infrastructure

import com.innovify.skillswap.finaldemonstration.domain.FinalDemonstration
import com.innovify.skillswap.finaldemonstration.domain.RescheduleDemonstrationRequest
import com.innovify.skillswap.finaldemonstration.domain.ScheduleDemonstrationRequest
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface FinalDemonstrationApi {

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

val finalDemonstrationApi: FinalDemonstrationApi by lazy { RetrofitClient.create(FinalDemonstrationApi::class.java) }
