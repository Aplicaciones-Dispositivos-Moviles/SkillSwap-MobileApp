package com.innovify.skillswap

import com.innovify.skillswap.credentialverification.domain.CertificateField
import com.innovify.skillswap.credentialverification.domain.CertificateForm
import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateFormTest {

    @Test
    fun completeFormHasNoMissingFields() {
        val missing = CertificateForm.missingFields("Coursera", "SQL Fundamentals", "2026-10-20")

        assertEquals(emptySet<CertificateField>(), missing)
    }

    @Test
    fun blankInstitutionIsReportedWithItsMessage() {
        val missing = CertificateForm.missingFields("  ", "Data Analysis", "2026-11-02")

        assertEquals(setOf(CertificateField.Institution), missing)
        assertEquals("Escribe la institución que emitió el certificado", CertificateForm.messageFor(CertificateField.Institution))
    }

    @Test
    fun everyMissingFieldIsReportedAtOnce() {
        val missing = CertificateForm.missingFields("", "", "2026/11/02")

        assertEquals(
            setOf(CertificateField.Institution, CertificateField.Course, CertificateField.IssueDate),
            missing
        )
        assertEquals("Escribe el nombre del curso", CertificateForm.messageFor(CertificateField.Course))
        assertEquals("Elige la fecha de emisión", CertificateForm.messageFor(CertificateField.IssueDate))
    }
}
