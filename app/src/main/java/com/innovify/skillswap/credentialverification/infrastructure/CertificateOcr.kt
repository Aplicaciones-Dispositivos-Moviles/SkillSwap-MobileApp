package com.innovify.skillswap.credentialverification.infrastructure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream

// ----------------------------------------------------------------------------------
// Archivo listo para subir al backend: bytes + tipo real + nombre.
// ----------------------------------------------------------------------------------
class UploadFile(val bytes: ByteArray, val mime: String, val fileName: String)

// ----------------------------------------------------------------------------------
// OCR on-device con ML Kit (servicio de terceros) + utilidades de la foto o del PDF.
// El backend acepta JPG, PNG y PDF de hasta 10 MB.
// ----------------------------------------------------------------------------------
object CertificateOcr {

    private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
    private const val SAFE_IMAGE_BYTES = 9 * 1024 * 1024

    // Tipo real del archivo según sus primeros bytes (el backend también lo valida así).
    fun detectMime(context: Context, uri: Uri): String? {
        val header = ByteArray(8)
        val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: return null
        if (read < 4) return null
        fun starts(vararg signature: Int) = signature.indices.all { header[it] == signature[it].toByte() }
        return when {
            starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            starts(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            starts(0x25, 0x50, 0x44, 0x46) -> "application/pdf" // %PDF
            else -> null
        }
    }

    // Lee el texto de la foto o de la primera página del PDF. onResult(null) si ML Kit falla.
    // El callback llega en el hilo principal.
    fun recognize(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        try {
            val image = if (detectMime(context, uri) == "application/pdf") {
                val page = renderPdfFirstPage(context, uri, 1600) ?: run { onResult(null); return }
                InputImage.fromBitmap(page, 0)
            } else {
                InputImage.fromFilePath(context, uri)
            }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result -> onResult(result.text) }
                .addOnFailureListener { onResult(null) }
        } catch (e: Exception) {
            onResult(null)
        }
    }

    // Primera página del PDF como imagen (PdfRenderer viene en Android, sin dependencias).
    private fun renderPdfFirstPage(context: Context, uri: Uri, width: Int): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val height = (width.toFloat() * page.height / page.width).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE) // el PDF se dibuja sobre transparente
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Vista previa: la foto reducida (girada según el EXIF) o la primera página del PDF.
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        if (detectMime(context, uri) == "application/pdf") {
            return renderPdfFirstPage(context, uri, maxSide)
        }
        return loadImage(context, uri, maxSide)
    }

    // Carga la foto reducida (lado mayor <= maxSide) y la gira según el EXIF de la cámara.
    private fun loadImage(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
            val matrix = Matrix().apply {
                postRotate(degrees)
                postScale(scale, scale)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            null
        }
    }

    // Prepara el archivo para subir. Lanza IllegalStateException con un mensaje listo para mostrar.
    // - PDF y PNG/JPG que caben: se suben tal cual.
    // - Imagen de más de 9 MB: se reduce a 2048 px (ya girada) y se sube como JPEG.
    // - PDF de más de 10 MB: no se puede reducir, se rechaza.
    fun prepareUpload(context: Context, uri: Uri): UploadFile {
        val mime = detectMime(context, uri)
            ?: throw IllegalStateException("Formato no soportado. Usa una foto JPG/PNG o un PDF")
        val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer el archivo")

        if (mime == "application/pdf") {
            if (original.size > MAX_UPLOAD_BYTES) {
                throw IllegalStateException("El PDF supera los 10 MB permitidos")
            }
            return UploadFile(original, mime, "certificate.pdf")
        }
        if (original.size <= SAFE_IMAGE_BYTES) {
            val name = if (mime == "image/png") "certificate.png" else "certificate.jpg"
            return UploadFile(original, mime, name)
        }

        val bitmap = loadImage(context, uri, 2048)
            ?: throw IllegalStateException("No se pudo reducir la imagen")
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return UploadFile(out.toByteArray(), "image/jpeg", "certificate.jpg")
    }
}
