package com.innovify.skillswap.iam.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.iam.domain.UpdateInterestsRequest
import com.innovify.skillswap.iam.infrastructure.iamApi
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore

// iam: the interests of the signed-in Student (US04).
class InterestsViewModel(session: SessionStore) : SessionAwareViewModel(session) {

    // ------------------------------------------------------- INTERESES (US04)
    var interests: List<String> by mutableStateOf(emptyList())

    init {
        session.onSignOut {
            interests = emptyList()
        }
    }

    // Lee los intereses guardados del usuario. true si llegaron.
    suspend fun loadInterests(): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver tus intereses"
            return false
        }
        return try {
            val response = iamApi.getCurrentUser()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                interests = body.interests ?: emptyList()
                true
            } else {
                errorMessage = messageFrom(response, "No se pudieron cargar tus intereses (${response.code()})")
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Guarda los intereses (reemplaza los anteriores). true si se guardaron.
    suspend fun saveInterests(topics: List<String>, description: String): Boolean {
        val userId = session.user?.id
        if (userId == null) {
            errorMessage = "Inicia sesión para guardar tus intereses"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para guardar tus intereses"
            return false
        }
        return try {
            val response = iamApi.updateInterests(
                userId, UpdateInterestsRequest(topics, description.trim().ifEmpty { null })
            )
            if (response.isSuccessful) {
                interests = response.body()?.interests ?: topics
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "No puedes cambiar los intereses de otra cuenta"
                    else -> messageFrom(response, "No se pudieron guardar tus intereses (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }
}
