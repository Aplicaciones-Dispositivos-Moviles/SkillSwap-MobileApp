package com.innovify.skillswap.credentialverification.application

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.innovify.skillswap.credentialverification.domain.Certificate
import com.innovify.skillswap.credentialverification.domain.CertificateLink
import com.innovify.skillswap.credentialverification.domain.CertificateMismatch
import com.innovify.skillswap.credentialverification.domain.LinkCertificateRequest
import com.innovify.skillswap.credentialverification.domain.LocalCertificate
import com.innovify.skillswap.credentialverification.infrastructure.CertificateOcr
import com.innovify.skillswap.credentialverification.infrastructure.LocalCertificateDao
import com.innovify.skillswap.credentialverification.infrastructure.UploadFile
import com.innovify.skillswap.credentialverification.infrastructure.certificateApi
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import com.innovify.skillswap.shared.domain.ApiError
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File

// credentialverification: the certificate saved on the phone, its upload and its link to the node. A linked
// certificate updates the selected node, so it receives the LearningPathViewModel.
class CertificateViewModel(
    session: SessionStore,
    private val learningPath: LearningPathViewModel
) : SessionAwareViewModel(session) {

    // ------------------------------------------------------------- CERTIFICADO
    // Último certificado subido y su vínculo con el nodo (null mientras no se hayan enviado).
    var certificate: Certificate? by mutableStateOf(null)
    var certificateLink: CertificateLink? by mutableStateOf(null)
    // 422 del vínculo: nodos de la ruta que el certificado sí cubre (null si no hubo ese error)
    var certificateMismatch: CertificateMismatch? by mutableStateOf(null)

    // T48: certificado guardado en el teléfono para el nodo abierto (null si no hay)
    var localCertificate: LocalCertificate? by mutableStateOf(null)

    init {
        session.onSignOut {
            certificate = null
            certificateLink = null
            localCertificate = null
        }
    }

    fun loadLocalCertificate(context: Context, nodeId: Int) {
        localCertificate = try {
            LocalCertificateDao(db(context)).findCertificate(nodeId)
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
            LocalCertificateDao(db).findCertificate(nodeId)?.let { old ->
                if (old.status == "Pending") {
                    File(old.filePath).delete()
                    LocalCertificateDao(db).deleteCertificate(old.id)
                }
            }
            val id = LocalCertificateDao(db).saveCertificate(
                LocalCertificate(
                    id = 0, nodeId = nodeId, filePath = file.absolutePath, mime = mime,
                    institution = institution, course = course, issueDate = issueDate,
                    ocrText = ocrText, status = "Pending", createdAt = System.currentTimeMillis()
                )
            )
            localCertificate = LocalCertificateDao(db).findCertificate(nodeId)
            id
        } catch (e: IllegalStateException) {
            errorMessage = e.message ?: "No se pudo guardar el certificado en el teléfono"
            -1
        } catch (e: Exception) {
            errorMessage = "No se pudo guardar el certificado en el teléfono"
            -1
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
            val response = certificateApi.uploadCertificate(
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
                    LocalCertificateDao(db(context)).markCertificateSent(localId)
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
        certificateMismatch = null
        return try {
            val response = certificateApi.linkCertificate(
                nodeId, LinkCertificateRequest(certificateId)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                certificateLink = body
                certificateMismatch = null
                body.node?.let { learningPath.selectedNode = it }
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> "El certificado aún no está verificado. Quedó en revisión"
                    422 -> {
                        // El cuerpo solo se puede leer una vez: de él salen el mensaje y los nodos sugeridos
                        val raw = try {
                            response.errorBody()?.string()
                        } catch (e: Exception) {
                            null
                        }
                        certificateMismatch = CertificateMismatch.fromErrorBody(raw)
                        try {
                            Gson().fromJson(raw, ApiError::class.java)?.detail
                        } catch (e: Exception) {
                            null
                        } ?: "El certificado no cubre la habilidad de este nodo"
                    }
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
