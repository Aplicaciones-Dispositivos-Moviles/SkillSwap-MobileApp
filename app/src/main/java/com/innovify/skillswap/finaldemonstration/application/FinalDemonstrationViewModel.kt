package com.innovify.skillswap.finaldemonstration.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.finaldemonstration.domain.FinalDemonstration
import com.innovify.skillswap.finaldemonstration.domain.MAX_VIDEO_BYTES
import com.innovify.skillswap.finaldemonstration.domain.RescheduleDemonstrationRequest
import com.innovify.skillswap.finaldemonstration.domain.ScheduleDemonstrationRequest
import com.innovify.skillswap.finaldemonstration.infrastructure.finalDemonstrationApi
import com.innovify.skillswap.iam.infrastructure.iamApi
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import java.io.File
import java.time.Instant

// finaldemonstration: the final demonstration of the current path and its video (US28 and US29). It belongs to
// the path on screen, so it receives the LearningPathViewModel.
class FinalDemonstrationViewModel(
    session: SessionStore,
    private val learningPath: LearningPathViewModel
) : SessionAwareViewModel(session) {

    // ------------------------------------------------ DEMOSTRACIÓN FINAL (US28 y US29)
    // La demostración vigente de la ruta actual (null si no hay ninguna activa)
    var demonstration: FinalDemonstration? by mutableStateOf(null)

    // id que llega en el push (FinalDemonstrationApproved o Rejected); la pantalla lo usa una vez
    var selectedDemonstrationId: Int? = null

    // Public username of the assigned Verificador (null until it loads or if it cannot be read)
    var verifierUsername: String? by mutableStateOf(null)

    // Reads the public profile of the Verificador; a failure only leaves the generic "Asignado" label
    suspend fun loadVerifierUsername(verifierUserId: Int) {
        verifierUsername = try {
            iamApi.getPublicUser(verifierUserId).body()?.username
        } catch (e: Exception) {
            null
        }
    }

    // Carga las demostraciones del Estudiante y se queda con la del push o, si no, la de la ruta actual
    suspend fun loadDemonstration(): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver tu demostración final"
            return false
        }
        return try {
            val response = finalDemonstrationApi.getMyDemonstrations()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val wanted = selectedDemonstrationId
                selectedDemonstrationId = null
                demonstration = body.firstOrNull { it.id == wanted }
                    ?: body.firstOrNull { it.learningPathId == learningPath.path?.id && it.status != "Cancelled" }
                true
            } else {
                errorMessage = messageFrom(response, "No se pudo cargar tu demostración (${response.code()})")
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    suspend fun scheduleDemonstration(modality: String, scheduledAt: Instant): Boolean {
        val pathId = learningPath.path?.id
        if (pathId == null) {
            errorMessage = "Necesitas una ruta completada para programar la demostración"
            return false
        }
        return demonstrationCall {
            finalDemonstrationApi.scheduleDemonstration(
                ScheduleDemonstrationRequest(pathId, modality, scheduledAt.toString())
            )
        }
    }

    suspend fun rescheduleDemonstration(scheduledAt: Instant): Boolean {
        val id = demonstration?.id ?: return false
        return demonstrationCall {
            finalDemonstrationApi.rescheduleDemonstration(id, RescheduleDemonstrationRequest(scheduledAt.toString()))
        }
    }

    suspend fun cancelDemonstration(): Boolean {
        val id = demonstration?.id ?: return false
        val ok = demonstrationCall { finalDemonstrationApi.cancelDemonstration(id) }
        if (ok) demonstration = null // cancelada: la ruta queda libre para programar otra
        return ok
    }

    // Sube (o reemplaza) el video grabado. true si el backend lo recibió (estado VideoSubmitted).
    suspend fun uploadDemonstrationVideo(file: File): Boolean {
        val id = demonstration?.id
        if (id == null) {
            errorMessage = "Primero programa tu demostración"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para subir el video"
            return false
        }
        if (!file.exists() || file.length() == 0L) {
            errorMessage = "No se encontró el video grabado"
            return false
        }
        if (file.length() > MAX_VIDEO_BYTES) {
            errorMessage = "El video pesa más de 25 MB. Graba uno más corto"
            return false
        }
        return try {
            val body = RequestBody.create(MediaType.parse("video/mp4"), file)
            val part = MultipartBody.Part.createFormData("file", file.name, body)
            val response = finalDemonstrationApi.uploadDemonstrationVideo(id, part)
            val result = response.body()
            if (response.isSuccessful && result != null) {
                demonstration = result
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Esta demostración no es tuya"
                    409 -> messageFrom(response, "La demostración ya fue revisada o cancelada")
                    413 -> "El video pesa más de 25 MB. Graba uno más corto"
                    415 -> "El video debe ser MP4, WebM o QuickTime"
                    502 -> "No se pudo guardar el video. Intenta de nuevo"
                    else -> messageFrom(response, "No se pudo subir el video (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Ejecuta una acción sobre la demostración y deja en demonstration la respuesta del backend
    private suspend fun demonstrationCall(call: suspend () -> Response<FinalDemonstration>): Boolean {
        return try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                demonstration = body
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> messageFrom(response, "Revisa la modalidad y la fecha")
                    403 -> "Esta demostración no es tuya"
                    404 -> messageFrom(response, "No encontramos la demostración")
                    else -> messageFrom(response, "No se pudo completar la acción (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }
}
