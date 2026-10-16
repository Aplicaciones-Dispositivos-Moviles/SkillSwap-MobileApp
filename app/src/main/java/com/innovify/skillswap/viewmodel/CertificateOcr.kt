package com.innovify.skillswap.viewmodel

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

// Campos que se intentan sacar del texto del certificado. Vacío = no se encontró.
data class ParsedCertificate(
    val institution: String,
    val course: String,
    val issueDate: String // AAAA-MM-DD
)

// ----------------------------------------------------------------------------------
// PARSER (Kotlin puro, sin Android): convierte el texto del OCR en institución, curso y fecha.
// Son reglas simples: el resultado SIEMPRE se muestra editable antes de enviar.
// ----------------------------------------------------------------------------------
object CertificateParser {

    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "enero" to 1, "ene" to 1,
        "february" to 2, "feb" to 2, "febrero" to 2,
        "march" to 3, "mar" to 3, "marzo" to 3,
        "april" to 4, "apr" to 4, "abril" to 4, "abr" to 4,
        "may" to 5, "mayo" to 5,
        "june" to 6, "jun" to 6, "junio" to 6,
        "july" to 7, "jul" to 7, "julio" to 7,
        "august" to 8, "aug" to 8, "agosto" to 8, "ago" to 8,
        "september" to 9, "sep" to 9, "sept" to 9, "septiembre" to 9, "setiembre" to 9, "set" to 9,
        "october" to 10, "oct" to 10, "octubre" to 10,
        "november" to 11, "nov" to 11, "noviembre" to 11,
        "december" to 12, "dec" to 12, "diciembre" to 12, "dic" to 12
    )

    private val ORG_WORDS = listOf(
        "university", "universidad", "universidade", "institute", "instituto", "college",
        "school", "academy", "academia", "coursera", "udemy", "edx", "platzi", "linkedin",
        "google", "ibm", "microsoft", "oracle", "amazon", "cisco"
    )

    private val DONE_WORDS = listOf(
        "successfully completed", "has completed", "ha completado", "haber completado",
        "ha aprobado", "haber aprobado", "ha culminado", "haber culminado",
        "completó", "aprobó", "culminó"
    )

    private val BY_REGEX = Regex(
        "(?i)\\b(?:offered (?:through|by)|issued by|emitido por|otorgado por|dictado por|impartido por)\\s+(.+)"
    )

    // Palabras de relleno que van entre "completó" y el nombre del curso
    private val FILLER_REGEX = Regex(
        "(?i)^(?:(?:the|el|la|los|las|online|non-credit|no credit)\\b|[,\\s])*" +
            "(?:course|curso|specialization|especialización|program|programa)?[:\\s]*"
    )

    private val COURSE_FALLBACK_REGEX = Regex(
        "(?i)\\b(?:course|curso|specialization|especialización|programa|program)\\s*[:\\-]\\s*(.{4,})"
    )

    private const val MAX_LEN = 120

    fun parse(text: String): ParsedCertificate {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        return ParsedCertificate(
            institution = findInstitution(lines),
            course = findCourse(lines),
            issueDate = findDate(text)
        )
    }

    // ---------------------------------------------------------------- INSTITUCIÓN
    private fun findInstitution(lines: List<String>): String {
        for (line in lines) {
            val match = BY_REGEX.find(line)
            if (match != null) return clean(match.groupValues[1])
        }
        val orgLine = lines.firstOrNull { line ->
            val lower = line.lowercase()
            DONE_WORDS.none { lower.contains(it) } && ORG_WORDS.any { lower.contains(it) }
        }
        return orgLine?.let { clean(it) } ?: ""
    }

    // ---------------------------------------------------------------------- CURSO
    private fun findCourse(lines: List<String>): String {
        for ((index, line) in lines.withIndex()) {
            val lower = line.lowercase()
            val word = DONE_WORDS.firstOrNull { lower.contains(it) } ?: continue
            val rest = line.substring(lower.indexOf(word) + word.length)
            val course = rest.replaceFirst(FILLER_REGEX, "")
            if (course.length >= 4) return clean(course)
            return lines.getOrNull(index + 1)?.let { clean(it) } ?: ""
        }
        for (line in lines) {
            val match = COURSE_FALLBACK_REGEX.find(line)
            if (match != null) return clean(match.groupValues[1])
        }
        return ""
    }

    // ---------------------------------------------------------------------- FECHA
    // Devuelve la PRIMERA fecha válida del texto, en AAAA-MM-DD. Con números se asume día/mes/año.
    private fun findDate(text: String): String {
        val candidates = mutableListOf<Pair<Int, String>>() // posición en el texto, fecha

        fun add(position: Int, year: Int, month: Int, day: Int) {
            if (month in 1..12 && day in 1..31) {
                candidates.add(position to "%04d-%02d-%02d".format(year, month, day))
            }
        }

        // 2025-01-05 o 2025/01/05
        Regex("\\b((?:19|20)\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})\\b").findAll(text).forEach {
            add(it.range.first, it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        // 05/01/2025, 05-01-2025 o 05.01.2025 (día primero)
        Regex("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.]((?:19|20)\\d{2})\\b").findAll(text).forEach {
            add(it.range.first, it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        // January 5, 2025 / Jan 5 2025
        Regex("(?i)\\b([a-zñ]{3,10})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+((?:19|20)\\d{2})\\b")
            .findAll(text).forEach {
                val month = MONTHS[it.groupValues[1].lowercase()] ?: return@forEach
                add(it.range.first, it.groupValues[3].toInt(), month, it.groupValues[2].toInt())
            }
        // 15 de octubre de 2025 / 15 October 2025
        Regex("(?i)\\b(\\d{1,2})\\s+(?:de\\s+)?([a-zñ]{3,10})\\.?,?\\s+(?:de\\s+|del\\s+)?((?:19|20)\\d{2})\\b")
            .findAll(text).forEach {
                val month = MONTHS[it.groupValues[2].lowercase()] ?: return@forEach
                add(it.range.first, it.groupValues[3].toInt(), month, it.groupValues[1].toInt())
            }

        return candidates.minByOrNull { it.first }?.second ?: ""
    }

    private fun clean(value: String): String {
        return value.trim().trim('.', ',', ';', ':', '-').trim().take(MAX_LEN)
    }
}

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
