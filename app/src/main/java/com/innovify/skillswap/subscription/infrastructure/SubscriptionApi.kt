package com.innovify.skillswap.subscription.infrastructure

import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import com.innovify.skillswap.subscription.domain.PlanSubscription
import com.innovify.skillswap.subscription.domain.StudentPlan
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface SubscriptionApi {

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

}

val subscriptionApi: SubscriptionApi by lazy { RetrofitClient.create(SubscriptionApi::class.java) }
