package com.innovify.skillswap

import com.innovify.skillswap.credentialverification.domain.Certificate
import com.innovify.skillswap.credentialverification.domain.CertificateFilter
import com.innovify.skillswap.credentialverification.domain.CertificateStage
import com.innovify.skillswap.credentialverification.domain.filterBy
import com.innovify.skillswap.credentialverification.domain.stage
import com.innovify.skillswap.shared.presentation.design.shortDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class MyCertificatesTest {

    private fun certificate(id: Int, status: String) = Certificate(
        id = id, ownerId = 7, institutionName = "Coursera", courseName = "Curso $id", issueDate = "2026-08-14",
        status = status, verificationMethod = null, riskLevel = null, fileUrl = null, holderNameMismatch = false
    )

    @Test
    fun statusMapsToTheThreeStagesShownToTheStudent() {
        assertEquals(CertificateStage.Validated, certificate(1, "Verified").stage())
        assertEquals(CertificateStage.InProgress, certificate(2, "Pending").stage())
        assertEquals(CertificateStage.InProgress, certificate(3, "Unverified").stage())
        assertEquals(CertificateStage.InProgress, certificate(4, "Suspicious").stage())
        assertEquals(CertificateStage.Rejected, certificate(5, "Rejected").stage())
        // A status the app does not know yet is still waiting for a decision
        assertEquals(CertificateStage.InProgress, certificate(6, "SomethingNew").stage())
    }

    @Test
    fun filtersKeepOnlyTheirStageAndAllKeepsEverything() {
        val all = listOf(
            certificate(1, "Verified"), certificate(2, "Pending"), certificate(3, "Suspicious"),
            certificate(4, "Rejected"), certificate(5, "Unverified")
        )
        assertEquals(listOf(1, 2, 3, 4, 5), all.filterBy(CertificateFilter.All).map { it.id })
        assertEquals(listOf(1), all.filterBy(CertificateFilter.Validated).map { it.id })
        assertEquals(listOf(2, 3, 5), all.filterBy(CertificateFilter.InProgress).map { it.id })
        assertEquals(listOf(4), all.filterBy(CertificateFilter.Rejected).map { it.id })
    }

    @Test
    fun datesUseTheShortSpanishFormatOfTheMockUps() {
        assertEquals("14 ago 2026", shortDate("2026-08-14", withYear = true))
        assertEquals("28 oct", shortDate("2026-10-28T03:00:00Z", zone = ZoneOffset.UTC))
        assertEquals("28 oct", shortDate("2026-10-28T03:00:00", zone = ZoneOffset.UTC))
        assertEquals(null, shortDate(null))
        assertEquals(null, shortDate("not a date"))
    }
}
