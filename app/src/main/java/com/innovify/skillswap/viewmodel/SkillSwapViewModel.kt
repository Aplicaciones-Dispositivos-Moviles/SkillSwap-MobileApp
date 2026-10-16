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
import com.innovify.skillswap.model.beans.LocalCertificate
import com.innovify.skillswap.model.beans.LinkCertificateRequest
import com.innovify.skillswap.model.beans.PathNode
import com.innovify.skillswap.model.beans.SignInRequest
import com.innovify.skillswap.model.client.RetrofitClient
import com.innovify.skillswap.model.db.SkillSwapDbHelper
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import java.io.File

class SkillSwapViewModel : ViewModel() {

    // ------------------------------------------------------------ OFFLINE (T49)
    // Estado de la conexión (se arranca en MainActivity) y base SQLite local.
    val connectivity = ConnectivityObserver()

    // true cuando la ruta que se ve viene de SQLite y no del servidor
    var showingCache by mutableStateOf(false)

    private var dbHelper: SkillSwapDbHelper? = null

    private fun db(context: Context): SkillSwapDbHelper {
        return dbHelper ?: SkillSwapDbHelper(context.applicationContext).also { dbHelper = it }
    }

    override fun onCleared() {
        connectivity.stop()
    }

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

    // Primero la red; si no hay conexión o el servidor falla (5xx), se muestra la última ruta guardada en SQLite.
    // Devuelve true si hay algo que mostrar (aunque el Estudiante aún no tenga ruta: path = null).
    suspend fun loadPath(context: Context): Boolean {
        val studentId = user?.id ?: return false
        if (!connectivity.isOnline) {
            return loadPathFromCache(context, studentId, "Sin conexión y no hay una ruta guardada en el teléfono")
        }
        return try {
            val response = RetrofitClient.webService.getLearningPaths(studentId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val latest = body.firstOrNull()
                path = latest
                showingCache = false
                // Guardar en SQLite es opcional: si falla, la ruta igual se muestra
                try {
                    if (latest != null) db(context).savePath(latest) else db(context).clearPath(studentId)
                } catch (e: Exception) {
                }
                true
            } else if (response.code() >= 500) {
                loadPathFromCache(context, studentId, "No se pudo cargar la ruta (${response.code()})")
            } else {
                errorMessage = "No se pudo cargar la ruta (${response.code()})"
                false
            }
        } catch (e: Exception) {
            loadPathFromCache(context, studentId, "No se pudo conectar con el servidor")
        }
    }

    // Lee la última ruta guardada. Si no hay ninguna, errorMessage explica por qué no se puede mostrar nada.
    private fun loadPathFromCache(context: Context, studentId: Int, error: String): Boolean {
        val cached = try {
            db(context).getPath(studentId)
        } catch (e: Exception) {
            null
        }
        return if (cached != null) {
            path = cached
            showingCache = true
            true
        } else {
            errorMessage = error
            false
        }
    }

    // ------------------------------------------------------------- CERTIFICADO
    // Último certificado subido y su vínculo con el nodo (null mientras no se hayan enviado).
    var certificate: Certificate? by mutableStateOf(null)
    var certificateLink: CertificateLink? by mutableStateOf(null)

    // T48: certificado guardado en el teléfono para el nodo abierto (null si no hay)
    var localCertificate: LocalCertificate? by mutableStateOf(null)

    fun loadLocalCertificate(context: Context, nodeId: Int) {
        localCertificate = try {
            db(context).findCertificate(nodeId)
        } catch (e: Exception) {
            null
        }
    }

    // T48: copia el archivo a filesDir/certificates (la caché la puede borrar Android) y guarda su ruta
    // en SQLite como "Pending". Devuelve el id de la fila, o -1 si falló (errorMessage dice por qué).
    fun saveEvidence(
        context: Context,
        uri: Uri,
        nodeId: Int,
        institution: String,
        course: String,
        issueDate: String,
        ocrText: String
    ): Long {
        return try {
            val mime = CertificateOcr.detectMime(context, uri)
                ?: throw IllegalStateException("El archivo debe ser JPG, PNG o PDF")
            val extension = when (mime) {
                "image/png" -> "png"
                "application/pdf" -> "pdf"
                else -> "jpg"
            }
            val folder = File(context.filesDir, "certificates").apply { mkdirs() }
            val file = File(folder, "cert_${System.currentTimeMillis()}.$extension")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No se pudo leer el archivo")
            input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }

            val db = db(context)
            // Un solo certificado pendiente por nodo: el nuevo reemplaza al anterior
            db.findCertificate(nodeId)?.let { old ->
                if (old.status == "Pending") {
                    File(old.filePath).delete()
                    db.deleteCertificate(old.id)
                }
            }
            val id = db.saveCertificate(
                LocalCertificate(
                    id = 0, nodeId = nodeId, filePath = file.absolutePath, mime = mime,
                    institution = institution, course = course, issueDate = issueDate,
                    ocrText = ocrText, status = "Pending", createdAt = System.currentTimeMillis()
                )
            )
            localCertificate = db.findCertificate(nodeId)
            id
        } catch (e: IllegalStateException) {
            errorMessage = e.message ?: "No se pudo guardar el certificado en el teléfono"
            -1
        } catch (e: Exception) {
            errorMessage = "No se pudo guardar el certificado en el teléfono"
            -1
        }
    }

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
        ocrText: String,
        localId: Long // fila de local_certificate; se marca "Sent" cuando el backend responde 201
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
                try {
                    db(context).markCertificateSent(localId)
                    localCertificate = localCertificate?.let { it.copy(status = "Sent") }
                } catch (e: Exception) {
                }
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
