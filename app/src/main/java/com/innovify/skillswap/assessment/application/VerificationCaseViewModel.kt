package com.innovify.skillswap.assessment.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.assessment.domain.AttachEvidenceRequest
import com.innovify.skillswap.assessment.domain.CreateVerifierProfileRequest
import com.innovify.skillswap.assessment.domain.VerificationCaseDetail
import com.innovify.skillswap.assessment.infrastructure.assessmentApi
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore

// assessment: the human verification case (status, evidence, appeal) and enabling the Student as a Verifier.
class VerificationCaseViewModel(session: SessionStore) : SessionAwareViewModel(session) {

    // ------------------------------------------------- CASO DE VERIFICACIÓN (WF-08)
    // Caso que se está viendo y su detalle (null mientras no se haya cargado)
    var selectedCaseId: Int? by mutableStateOf(null)
    var caseDetail: VerificationCaseDetail? by mutableStateOf(null)

    // Estado, decisión, notas del Verificador y, en un quiz, las preguntas falladas. true si lo leyó.
    suspend fun loadCase(caseId: Int): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver el estado del caso"
            return false
        }
        return try {
            val response = assessmentApi.getVerificationCase(caseId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                caseDetail = body
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Este caso no es tuyo"
                    404 -> "No encontramos ese caso"
                    else -> messageFrom(response, "No se pudo cargar el caso (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // US26: adjunta (o reemplaza) el enlace de evidencia de un caso abierto. true si se guardó.
    suspend fun attachEvidence(caseId: Int, url: String): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para adjuntar la evidencia"
            return false
        }
        return try {
            val response = assessmentApi.attachEvidence(caseId, AttachEvidenceRequest(url.trim()))
            val body = response.body()
            if (response.isSuccessful) {
                if (body != null) caseDetail = caseDetail?.copy(verificationCase = body)
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> "El enlace debe empezar con http:// o https://"
                    403 -> "Este caso no es tuyo"
                    404 -> "No encontramos ese caso"
                    409 -> "El caso ya fue resuelto: no se puede adjuntar más evidencia"
                    else -> messageFrom(response, "No se pudo adjuntar la evidencia (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Apela un caso rechazado (una sola vez). El backend lo reabre con otro Verificador. true si se envió.
    suspend fun appealCase(caseId: Int): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para apelar"
            return false
        }
        return try {
            val response = assessmentApi.appealVerificationCase(caseId)
            val body = response.body()
            if (response.isSuccessful) {
                if (body != null) caseDetail = caseDetail?.copy(verificationCase = body)
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Este caso no es tuyo"
                    404 -> "No encontramos ese caso"
                    else -> messageFrom(response, "No se pudo enviar la apelación (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // WF-10: habilitarse como Verificador de la habilidad de un nodo completado.
    // Devuelve el texto que se le muestra al Estudiante (éxito o motivo del rechazo).
    suspend fun enableAsVerifier(skillTag: String): String {
        return try {
            val response = assessmentApi.createVerifierProfile(
                CreateVerifierProfileRequest(skillTag)
            )
            when {
                response.isSuccessful -> "Listo: ya puedes recibir casos de $skillTag como Verificador"
                response.code() == 409 -> "Ya estás habilitado como Verificador de $skillTag"
                response.code() == 403 -> messageFrom(response, "Completa este nodo antes de habilitarte como Verificador")
                else -> messageFrom(response, "No se pudo habilitar como Verificador (${response.code()})")
            }
        } catch (e: Exception) {
            "No se pudo conectar con el servidor"
        }
    }
}
