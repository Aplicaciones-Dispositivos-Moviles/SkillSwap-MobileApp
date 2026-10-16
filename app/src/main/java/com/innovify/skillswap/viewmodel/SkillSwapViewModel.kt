package com.innovify.skillswap.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.innovify.skillswap.model.beans.ApiError
import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.Certificate
import com.innovify.skillswap.model.beans.CertificateLink
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LinkCertificateRequest
import com.innovify.skillswap.model.beans.PathNode
import com.innovify.skillswap.model.beans.SignInRequest
import com.innovify.skillswap.model.client.RetrofitClient
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

class SkillSwapViewModel : ViewModel() {

    // ------------------------------------------------------------------ SESIÓN
    var user: AuthUser? by mutableStateOf(null)

    // Último mensaje de error de red. OJO: aquí NO se puede mostrar Toast (hilo IO);
    // la vista lo muestra con withContext(Dispatchers.Main).
    var errorMessage: String = ""

    // suspend: la VISTA lo llama dentro de viewModelScope.launch(Dispatchers.IO).
    // Devuelve true si el login fue correcto.
    suspend fun signIn(username: String, password: String): Boolean {
        return try {
            val response = RetrofitClient.webService.signIn(SignInRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                user = body
                RetrofitClient.token = body.token
                true
            } else {
                errorMessage = when (response.code()) {
                    401 -> "Usuario o contraseña incorrectos"
                    403 -> "Verifica tu correo institucional antes de ingresar"
                    else -> "No se pudo iniciar sesión (${response.code()})"
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------------------- RUTA
    // Ruta que se muestra (la más reciente del Estudiante) y el nodo elegido.
    var path: LearningPath? by mutableStateOf(null)
    var selectedNode: PathNode? by mutableStateOf(null)

    // Devuelve true si la consulta fue correcta (aunque el Estudiante aún no tenga ruta: path = null).
    suspend fun loadPath(): Boolean {
        val studentId = user?.id ?: return false
        return try {
            val response = RetrofitClient.webService.getLearningPaths(studentId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                path = body.firstOrNull()
                true
            } else {
                errorMessage = "No se pudo cargar la ruta (${response.code()})"
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------------- CERTIFICADO
    // Último certificado subido y su vínculo con el nodo (null mientras no se hayan enviado).
    var certificate: Certificate? by mutableStateOf(null)
    var certificateLink: CertificateLink? by mutableStateOf(null)

    // Texto del error del backend (ProblemDetail.detail) o uno por defecto.
    private fun messageFrom(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string()
            val error = Gson().fromJson(raw, ApiError::class.java)
            error?.detail ?: error?.title ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    // Parte multipart de texto (no pasa por Gson, así no se le agregan comillas).
    private fun textPart(name: String, value: String): MultipartBody.Part {
        return MultipartBody.Part.createFormData(name, value)
    }

    // Parte multipart del archivo. El backend exige que el Content-Type declarado coincida con los bytes
    // (image/jpeg, image/png o application/pdf).
    private fun filePart(file: UploadFile): MultipartBody.Part {
        val body = RequestBody.create(MediaType.parse(file.mime), file.bytes)
        return MultipartBody.Part.createFormData("file", file.fileName, body)
    }

    // POST api/v1/certificates. suspend: la vista lo llama en Dispatchers.IO. true si el backend respondió 201.
    suspend fun uploadCertificate(
        context: Context,
        uri: Uri, // foto de la cámara, imagen o PDF elegido
        institution: String,
        course: String,
        issueDate: String,
        ocrText: String
    ): Boolean {
        return try {
            val file = CertificateOcr.prepareUpload(context, uri)
            val response = RetrofitClient.webService.uploadCertificate(
                filePart(file),
                textPart("institutionName", institution),
                textPart("courseName", course),
                textPart("issueDate", issueDate),
                textPart("ocrText", ocrText)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                certificate = body
                certificateLink = null
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> messageFrom(response, "Este certificado ya fue registrado")
                    413 -> "La foto es demasiado grande (máximo 10 MB)"
                    415 -> "El archivo debe ser JPG, PNG o PDF"
                    else -> messageFrom(response, "No se pudo subir el certificado (${response.code()})")
                }
                false
            }
        } catch (e: IllegalStateException) {
            // Archivo inválido o demasiado grande (mensaje de CertificateOcr.prepareUpload)
            errorMessage = e.message ?: "No se pudo preparar el archivo"
            false
        } catch (e: Exception) {
            errorMessage = "No se pudo subir el certificado. Revisa tu conexión"
            false
        }
    }

    // POST api/v1/path-nodes/{nodeId}/certificate con el certificado recién subido.
    // true si el certificado cubre la habilidad del nodo; si no, errorMessage explica el motivo.
    suspend fun linkCertificate(nodeId: Int): Boolean {
        val certificateId = certificate?.id ?: return false
        return try {
            val response = RetrofitClient.webService.linkCertificate(
                nodeId, LinkCertificateRequest(certificateId)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                certificateLink = body
                body.node?.let { selectedNode = it }
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> "El certificado aún no está verificado. Quedó en revisión"
                    422 -> messageFrom(response, "El certificado no cubre la habilidad de este nodo")
                    else -> messageFrom(response, "No se pudo vincular el certificado (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo vincular el certificado. Revisa tu conexión"
            false
        }
    }

}
