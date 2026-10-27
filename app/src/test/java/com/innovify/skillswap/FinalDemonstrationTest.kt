package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.model.beans.FinalDemonstration
import com.innovify.skillswap.model.beans.demonstrationStatusLabel
import com.innovify.skillswap.model.beans.isFarEnoughAhead
import com.innovify.skillswap.model.beans.modalityLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FinalDemonstrationTest {

    private val now = Instant.parse("2026-10-27T15:00:00Z")

    private fun demo(status: String, hasVideo: Boolean = false) = FinalDemonstration(
        id = 1, studentId = 5, learningPathId = 9, skillTag = "rest-api", verifierUserId = 7,
        modality = "AdvancedProject", scheduledAt = "2026-10-30T15:00:00Z", status = status,
        createdAt = null, updatedAt = null, hasVideo = hasVideo, videoContentType = null,
        videoSubmittedAt = null, score = null, feedback = null, reviewedAt = null
    )

    @Test
    fun schedule_needs_at_least_24_hours_ahead() {
        assertFalse(isFarEnoughAhead(now.plus(23, ChronoUnit.HOURS), now))
        assertFalse(isFarEnoughAhead(now.plus(24, ChronoUnit.HOURS), now)) // sin margen, el backend podría rechazarla
        assertTrue(isFarEnoughAhead(now.plus(25, ChronoUnit.HOURS), now))
    }

    @Test
    fun video_can_be_uploaded_until_the_verifier_reviews_it() {
        assertTrue(demo("Scheduled").canUploadVideo())
        assertTrue(demo("VideoSubmitted", hasVideo = true).canUploadVideo())
        assertFalse(demo("Approved").canUploadVideo())
        assertFalse(demo("Rejected").canUploadVideo())
        assertFalse(demo("Cancelled").canUploadVideo())
    }

    @Test
    fun only_a_scheduled_demonstration_can_be_rescheduled() {
        assertTrue(demo("Scheduled").canReschedule())
        assertFalse(demo("VideoSubmitted").canReschedule())
        assertTrue(demo("Approved").isReviewed())
        assertTrue(demo("Rejected").isReviewed())
        assertFalse(demo("Scheduled").isReviewed())
    }

    @Test
    fun labels_are_in_spanish() {
        assertEquals("Proyecto avanzado", modalityLabel("AdvancedProject"))
        assertEquals("Examen supervisado", modalityLabel("SupervisedExam"))
        assertEquals("Programada", demonstrationStatusLabel("Scheduled"))
        assertEquals("En revisión", demonstrationStatusLabel("VideoSubmitted"))
        assertEquals("Aprobada", demonstrationStatusLabel("Approved"))
        assertEquals("Rechazada", demonstrationStatusLabel("Rejected"))
    }

    @Test
    fun demonstration_parses_from_backend_json() {
        val raw = """{"id":3,"studentId":5,"learningPathId":9,"skillTag":"rest-api","verifierUserId":7,
            "modality":"SupervisedExam","scheduledAt":"2026-10-30T15:00:00Z","status":"Approved",
            "createdAt":"2026-10-27T15:00:00Z","updatedAt":"2026-10-30T16:00:00Z","hasVideo":true,
            "videoContentType":"video/mp4","videoSubmittedAt":"2026-10-30T15:30:00Z","score":85,
            "feedback":"Muy bien","reviewedAt":"2026-10-30T16:00:00Z"}"""
        val parsed = Gson().fromJson(raw, FinalDemonstration::class.java)
        assertEquals("Approved", parsed.status)
        assertEquals(85, parsed.score)
        assertTrue(parsed.hasVideo)

        val scheduled = Gson().fromJson(
            """{"id":4,"studentId":5,"learningPathId":9,"skillTag":"x","verifierUserId":7,"modality":"AdvancedProject",
            "scheduledAt":"2026-10-30T15:00:00Z","status":"Scheduled","hasVideo":false}""",
            FinalDemonstration::class.java
        )
        assertNull(scheduled.score)
        assertNull(scheduled.feedback)
    }
}
