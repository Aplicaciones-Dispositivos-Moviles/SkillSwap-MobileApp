package com.innovify.skillswap

import com.innovify.skillswap.assessment.domain.VerificationCase
import com.innovify.skillswap.assessment.presentation.caseTimeline
import com.innovify.skillswap.shared.presentation.design.StepStatus.Active
import com.innovify.skillswap.shared.presentation.design.StepStatus.Done
import com.innovify.skillswap.shared.presentation.design.StepStatus.Failed
import com.innovify.skillswap.shared.presentation.design.StepStatus.Pending
import org.junit.Assert.assertEquals
import org.junit.Test

// Timeline of the verification case screen.
class CaseTimelineTest {

    // ---------------------------------------------------------------- case timeline

    private fun case(status: String, decision: String? = null) = VerificationCase(
        id = 1, attemptId = 10, submissionId = null, studentId = 5, verifierUserId = null, pathNodeId = 3,
        skillTag = "pandas", caseType = "Quiz", status = status, decision = decision, rubricNotes = null,
        evidenceUrl = null, appealCount = 0, openedAt = null, reviewDueAt = null, assignedAt = null,
        resolvedAt = null
    )

    @Test
    fun caseTimelineWaitingForAVerifier() {
        assertEquals(listOf(Done, Active, Pending, Pending), caseTimeline(case("Pending")))
    }

    @Test
    fun caseTimelineUnderReview() {
        assertEquals(listOf(Done, Done, Active, Pending), caseTimeline(case("Assigned")))
    }

    @Test
    fun caseTimelineResolved() {
        assertEquals(listOf(Done, Done, Done, Done), caseTimeline(case("Resolved", "Approved")))
        assertEquals(listOf(Done, Done, Done, Failed), caseTimeline(case("Resolved", "Rejected")))
    }
}
