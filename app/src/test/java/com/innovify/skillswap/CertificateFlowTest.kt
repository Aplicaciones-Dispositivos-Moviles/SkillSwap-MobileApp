package com.innovify.skillswap

import com.innovify.skillswap.credentialverification.presentation.CertificateProgress
import com.innovify.skillswap.credentialverification.presentation.certificateStatusLabel
import com.innovify.skillswap.credentialverification.presentation.certificateSteps
import com.innovify.skillswap.credentialverification.presentation.riskLevelLabel
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.StepStatus.Active
import com.innovify.skillswap.shared.presentation.design.StepStatus.Done
import com.innovify.skillswap.shared.presentation.design.StepStatus.Failed
import com.innovify.skillswap.shared.presentation.design.StepStatus.Pending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Certificate screen helpers (Spanish labels, checklist) and the flow icons they use.
class CertificateFlowTest {

    // ---------------------------------------------------------------- certificate labels

    @Test
    fun certificateStatusIsShownInSpanish() {
        assertEquals("Pendiente", certificateStatusLabel("Pending"))
        assertEquals("Sin verificar", certificateStatusLabel("Unverified"))
        assertEquals("Sospechoso", certificateStatusLabel("Suspicious"))
        assertEquals("Verificado", certificateStatusLabel("Verified"))
        assertEquals("Rechazado", certificateStatusLabel("Rejected"))
        assertEquals("—", certificateStatusLabel(null))
        assertEquals("Otro", certificateStatusLabel("Otro"))
    }

    @Test
    fun riskLevelIsShownInSpanish() {
        assertEquals("Riesgo bajo", riskLevelLabel("LowRisk"))
        assertEquals("Requiere revisión", riskLevelLabel("Review"))
        assertEquals("Riesgo alto", riskLevelLabel("HighRisk"))
        assertEquals("—", riskLevelLabel(null))
    }

    private fun progress(
        reading: Boolean = false,
        extracted: Boolean = false,
        sending: Boolean = false,
        uploaded: Boolean = false,
        uploadFailed: Boolean = false,
        coversNode: Boolean? = null,
        riskLevel: String? = null
    ) = CertificateProgress(reading, extracted, sending, uploaded, uploadFailed, coversNode, riskLevel)

    @Test
    fun certificateStepsWhileReadingTheText() {
        assertEquals(listOf(Active, Pending, Pending, Pending), certificateSteps(progress(reading = true)))
    }

    @Test
    fun certificateStepsAfterTheTextWasRead() {
        assertEquals(listOf(Done, Pending, Pending, Pending), certificateSteps(progress(extracted = true)))
    }

    @Test
    fun certificateStepsWhileUploadingAndComparingWithTheNode() {
        assertEquals(
            listOf(Done, Active, Pending, Pending),
            certificateSteps(progress(extracted = true, sending = true))
        )
        assertEquals(
            listOf(Done, Done, Active, Done),
            certificateSteps(progress(extracted = true, sending = true, uploaded = true, riskLevel = "LowRisk"))
        )
    }

    @Test
    fun certificateStepsWhenTheCertificateCoversTheNode() {
        assertEquals(
            listOf(Done, Done, Done, Done),
            certificateSteps(progress(extracted = true, uploaded = true, coversNode = true, riskLevel = "LowRisk"))
        )
    }

    @Test
    fun certificateStepsWhenTheCertificateDoesNotCoverTheNodeOrIsHighRisk() {
        assertEquals(
            listOf(Done, Done, Failed, Failed),
            certificateSteps(progress(extracted = true, uploaded = true, coversNode = false, riskLevel = "HighRisk"))
        )
    }

    @Test
    fun certificateStepsWhenTheUploadFailed() {
        assertEquals(
            listOf(Done, Failed, Pending, Pending),
            certificateSteps(progress(extracted = true, uploadFailed = true))
        )
    }

    // ---------------------------------------------------------------- flow icons

    @Test
    fun everyFlowIconPathParses() {
        val icons = with(FlowIcons) {
            listOf(Sparkle, Camera, Upload, Flag, Verified, CloudDone, Link, Document, Sync)
        }
        icons.forEach { icon ->
            assertEquals(24f, icon.viewportWidth)
            assertTrue("${icon.name} has no paths", icon.root.size > 0)
        }
    }
}
