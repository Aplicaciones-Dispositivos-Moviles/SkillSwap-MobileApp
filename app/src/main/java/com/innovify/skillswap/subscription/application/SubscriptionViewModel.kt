package com.innovify.skillswap.subscription.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import com.innovify.skillswap.subscription.domain.StudentPlan
import com.innovify.skillswap.subscription.infrastructure.subscriptionApi

// subscription: the Student's plan, its limits and the Premium subscription (US05).
class SubscriptionViewModel(session: SessionStore) : SessionAwareViewModel(session) {

    // ------------------------------------------------------------- MI PLAN (US05)
    var plan: StudentPlan? by mutableStateOf(null)

    // Mensaje del 409 PlanLimitReached al declarar una meta (null si no ocurrió)
    var planLimitMessage: String? by mutableStateOf(null)

    init {
        session.onSignOut {
            plan = null
            planLimitMessage = null
        }
    }

    // Lee el plan y sus límites. true si llegaron; si no, errorMessage dice por qué.
    suspend fun loadPlan(): Boolean {
        val userId = session.user?.id
        if (userId == null) {
            errorMessage = "Inicia sesión para ver tu plan"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver tu plan"
            return false
        }
        return try {
            val response = subscriptionApi.getPlan(userId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                plan = body
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Este plan no es tuyo"
                    else -> messageFrom(response, "No se pudo cargar tu plan (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Activa Premium (cobro simulado en el servidor) y vuelve a leer el plan. true si quedó activo.
    suspend fun subscribe(): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para activar Premium"
            return false
        }
        return try {
            val response = subscriptionApi.subscribe()
            if (response.isSuccessful) {
                loadPlan()
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> "El producto de pago no es válido"
                    422 -> "No encontramos una compra activa. Intenta de nuevo"
                    503 -> "El servicio de pagos no responde. Intenta en unos minutos"
                    else -> messageFrom(response, "No se pudo activar Premium (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Cancela la renovación: el plan sigue hasta el fin del periodo pagado. true si se canceló.
    suspend fun cancelSubscription(): Boolean {
        val subscription = plan?.subscription
        if (subscription == null) {
            errorMessage = "No tienes una suscripción activa"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para cancelar"
            return false
        }
        return try {
            val response = subscriptionApi.cancelSubscription(subscription.id)
            if (response.isSuccessful) {
                loadPlan()
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Esta suscripción no es tuya"
                    404 -> "No encontramos la suscripción"
                    409 -> "Tu suscripción ya venció"
                    503 -> "El servicio de pagos no responde. No se cambió nada, intenta en unos minutos"
                    else -> messageFrom(response, "No se pudo cancelar (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }
}
