package com.innovify.skillswap.model.beans

import java.time.Duration
import java.time.Instant

// El backend acepta videos de hasta 25 MB. La cámara se limita a 2 minutos en calidad baja para no pasarse.
const val MAX_VIDEO_BYTES = 25L * 1024 * 1024
const val MAX_VIDEO_SECONDS = 120

// La demostración se programa con al menos 24 horas de anticipación (el backend responde 409 si no)
const val MIN_HOURS_AHEAD = 24L

// Body de POST api/v1/final-demonstrations. modality: "AdvancedProject" | "SupervisedExam". scheduledAt: ISO-8601 UTC.
data class ScheduleDemonstrationRequest(
    var learningPathId: Int,
    var modality: String,
    var scheduledAt: String
)

// Body de PATCH api/v1/final-demonstrations/{id}/reschedule
data class RescheduleDemonstrationRequest(
    var scheduledAt: String
)

// FinalDemonstrationResource del backend. status: Scheduled | VideoSubmitted | Approved | Rejected | Cancelled.
// score (0 a 100; 70 o más aprueba) y feedback llegan cuando el Verificador revisa el video.
data class FinalDemonstration(
    var id: Int,
    var studentId: Int,
    var learningPathId: Int,
    var skillTag: String,
    var verifierUserId: Int,
    var modality: String,
    var scheduledAt: String?,
    var status: String,
    var createdAt: String?,
    var updatedAt: String?,
    var hasVideo: Boolean,
    var videoContentType: String?,
    var videoSubmittedAt: String?,
    var score: Int?,
    var feedback: String?,
    var reviewedAt: String?
) {
    // Solo se puede mover mientras sigue programada
    fun canReschedule(): Boolean = status == "Scheduled"

    // El video se sube (o se reemplaza) hasta que el Verificador lo revisa
    fun canUploadVideo(): Boolean = status == "Scheduled" || status == "VideoSubmitted"

    fun isReviewed(): Boolean = status == "Approved" || status == "Rejected"
}

fun modalityLabel(modality: String): String = when (modality) {
    "AdvancedProject" -> "Proyecto avanzado"
    "SupervisedExam" -> "Examen supervisado"
    else -> modality
}

fun demonstrationStatusLabel(status: String): String = when (status) {
    "Scheduled" -> "Programada"
    "VideoSubmitted" -> "En revisión"
    "Approved" -> "Aprobada"
    "Rejected" -> "Rechazada"
    "Cancelled" -> "Cancelada"
    else -> status
}

// true si el momento elegido está al menos 24 horas (más un minuto de margen) por delante de ahora
fun isFarEnoughAhead(scheduledAt: Instant, now: Instant = Instant.now()): Boolean =
    !scheduledAt.isBefore(now.plus(Duration.ofHours(MIN_HOURS_AHEAD)).plusSeconds(60))
