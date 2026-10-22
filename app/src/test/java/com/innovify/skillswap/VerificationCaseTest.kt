package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.model.beans.MiniProjectSubmission
import com.innovify.skillswap.model.beans.VerificationCase
import com.innovify.skillswap.model.beans.VerificationCaseDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationCaseTest {

    private fun case(status: String, decision: String?, appeals: Int) = VerificationCase(
        id = 1, attemptId = 10, submissionId = null, studentId = 5, verifierUserId = 7, pathNodeId = 3,
        skillTag = "java", caseType = "Quiz", status = status, decision = decision, rubricNotes = null,
        evidenceUrl = null, appealCount = appeals, openedAt = null, reviewDueAt = null, assignedAt = null,
        resolvedAt = null
    )

    private fun submission(status: String?) = MiniProjectSubmission(
        id = 1, miniProjectId = 2, pathNodeId = 3, skillTag = "java", repositoryUrl = "https://github.com/a/b",
        submittedAt = null, evaluationStatus = status, preliminaryScore = null, preliminaryConfidence = null,
        preliminaryFeedback = null, evaluatedAt = null, verificationCaseId = null, verificationCaseStatus = null
    )

    @Test
    fun canAppealOnlyARejectedCaseNotAppealedYet() {
        assertTrue(case("Resolved", "Rejected", 0).canAppeal())
    }

    @Test
    fun cannotAppealTwice() {
        assertFalse(case("Resolved", "Rejected", 1).canAppeal())
    }

    @Test
    fun cannotAppealAnApprovedOrOpenCase() {
        assertFalse(case("Resolved", "Approved", 0).canAppeal())
        assertFalse(case("Assigned", null, 0).canAppeal())
        assertFalse(case("Pending", null, 0).canAppeal())
    }

    @Test
    fun submissionIsEvaluatingWhileItHasNoResult() {
        assertTrue(submission("Pending").isEvaluating())
        assertTrue(submission(null).isEvaluating())
        assertFalse(submission("Approved").isEvaluating())
        assertFalse(submission("Escalated").isEvaluating())
        assertFalse(submission("EscalationLimitReached").isEvaluating())
    }

    @Test
    fun readsTheCaseDetailOfAMiniProjectWithoutAttempt() {
        val json = """
            {"verificationCase":{"id":9,"attemptId":null,"submissionId":4,"studentId":5,"verifierUserId":null,
             "pathNodeId":3,"skillTag":"java","caseType":"MiniProject","status":"Pending","decision":null,
             "rubricNotes":null,"evidenceUrl":"https://github.com/a/b","appealCount":0,
             "openedAt":"2026-10-21T20:00:00Z","reviewDueAt":null,"assignedAt":null,"resolvedAt":null},
             "attempt":null,"failedQuestions":[]}
        """.trimIndent()
        val detail = Gson().fromJson(json, VerificationCaseDetail::class.java)
        assertNull(detail.verificationCase.attemptId)
        assertEquals(4, detail.verificationCase.submissionId)
        assertEquals("MiniProject", detail.verificationCase.caseType)
        assertNull(detail.attempt)
        assertTrue(detail.failedQuestions!!.isEmpty())
    }

    @Test
    fun readsTheSubmissionWithItsPreliminaryEvaluation() {
        val json = """
            {"id":1,"miniProjectId":2,"pathNodeId":3,"skillTag":"java","repositoryUrl":"https://github.com/a/b",
             "submittedAt":"2026-10-21T20:00:00Z","evaluationStatus":"Escalated","preliminaryScore":62,
             "preliminaryConfidence":55,"preliminaryFeedback":"Agrega pruebas","evaluatedAt":"2026-10-21T20:01:00Z",
             "verificationCaseId":9,"verificationCaseStatus":"Assigned"}
        """.trimIndent()
        val read = Gson().fromJson(json, MiniProjectSubmission::class.java)
        assertEquals("Escalated", read.evaluationStatus)
        assertEquals(62, read.preliminaryScore)
        assertEquals(9, read.verificationCaseId)
        assertFalse(read.isEvaluating())
    }

    @Test
    fun readsASubmissionSavedBeforeTheEvaluationExisted() {
        val json = """{"id":1,"miniProjectId":2,"pathNodeId":3,"skillTag":"java","repositoryUrl":"https://x.io/r","submittedAt":null}"""
        val read = Gson().fromJson(json, MiniProjectSubmission::class.java)
        assertNull(read.evaluationStatus)
        assertTrue(read.isEvaluating())
    }
}
