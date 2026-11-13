package com.innovify.skillswap.iam.infrastructure

import com.innovify.skillswap.iam.domain.AuthUser
import com.innovify.skillswap.iam.domain.DeviceTokenRequest
import com.innovify.skillswap.iam.domain.PublicUser
import com.innovify.skillswap.iam.domain.SignInRequest
import com.innovify.skillswap.iam.domain.SignUpRequest
import com.innovify.skillswap.iam.domain.UpdateInterestsRequest
import com.innovify.skillswap.iam.domain.UserResource
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface IamApi {

    // 200 AuthUser | 401 credenciales inválidas | 403 correo sin verificar
    @POST("api/v1/authentication/sign-in")
    suspend fun signIn(@Body body: SignInRequest): Response<AuthUser>

    // 201 UserResource | 400 dominio no institucional o datos inválidos | 409 usuario o correo ya existe
    @POST("api/v1/authentication/sign-up")
    suspend fun signUp(@Body body: SignUpRequest): Response<UserResource>

    // US04: usuario autenticado (trae sus intereses)
    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<UserResource>

    // Public profile of another user (for example the Verificador of a final demonstration)
    @GET("api/v1/users/{id}")
    suspend fun getPublicUser(@Path("id") id: Int): Response<PublicUser>

    // US04: reemplaza los intereses. 200 UserResource | 400 | 403
    @PUT("api/v1/users/{id}/interests")
    suspend fun updateInterests(@Path("id") id: Int, @Body body: UpdateInterestsRequest): Response<UserResource>

    // Registra (o reemplaza) el token de notificaciones push de este teléfono. 204 | 400 InvalidDeviceToken
    @PUT("api/v1/users/me/device-token")
    suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): Response<ResponseBody>

    // Olvida el token (permiso negado o cierre de sesión). 204, también si no había. Lleva su propio
    // Authorization porque al cerrar sesión el token global ya se limpió.
    @DELETE("api/v1/users/me/device-token")
    suspend fun removeDeviceToken(@Header("Authorization") authorization: String): Response<ResponseBody>

}

val iamApi: IamApi by lazy { RetrofitClient.create(IamApi::class.java) }
