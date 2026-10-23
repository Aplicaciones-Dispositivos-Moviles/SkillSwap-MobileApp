package com.innovify.skillswap

import com.innovify.skillswap.model.beans.CertificateMismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateMismatchTest {

    @Test
    fun readsTheSuggestedNodesFromTheTopLevel() {
        val raw = """
            {"title":"CertificateSkillMismatch","status":422,"detail":"No cubre",
             "affinity":0.12,"threshold":0.7,
             "suggestedNodes":[{"nodeId":8,"skillTag":"git-version-control","affinity":0.91}]}
        """.trimIndent()
        val mismatch = CertificateMismatch.fromErrorBody(raw)!!
        assertEquals(0.7, mismatch.threshold!!, 0.0001)
        assertEquals(1, mismatch.suggestedNodes!!.size)
        assertEquals(8, mismatch.suggestedNodes!![0].nodeId)
    }

    @Test
    fun readsTheSuggestedNodesInsideDetails() {
        val raw = """
            {"title":"CertificateSkillMismatch","details":{"affinity":0.2,"threshold":0.7,
             "suggestedNodes":[{"nodeId":3,"skillTag":"oop","affinity":0.8},{"nodeId":5,"skillTag":"java","affinity":0.75}]}}
        """.trimIndent()
        val mismatch = CertificateMismatch.fromErrorBody(raw)!!
        assertEquals(2, mismatch.suggestedNodes!!.size)
        assertEquals("oop", mismatch.suggestedNodes!![0].skillTag)
    }

    @Test
    fun hasNoSuggestionsWhenTheBackendSendsNone() {
        val raw = """{"title":"CertificateSkillMismatch","affinity":0.0,"threshold":0.7,"suggestedNodes":[]}"""
        assertTrue(CertificateMismatch.fromErrorBody(raw)!!.suggestedNodes!!.isEmpty())
    }

    @Test
    fun returnsNullWhenTheBodyIsNotAMismatch() {
        assertNull(CertificateMismatch.fromErrorBody(null))
        assertNull(CertificateMismatch.fromErrorBody("not json"))
        assertNull(CertificateMismatch.fromErrorBody("""{"title":"Other"}"""))
    }
}
