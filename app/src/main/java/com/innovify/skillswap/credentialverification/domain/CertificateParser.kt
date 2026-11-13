package com.innovify.skillswap.credentialverification.domain

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
        "completó", "aprobó", "culminó",
        // "por completar el curso" / "for completing the course": the course name is usually on the next line
        "por completar", "por culminar", "por aprobar", "for completing", "for successfully completing"
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
