package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.assessment.application.AssessmentKind
import com.innovify.skillswap.assessment.application.AttentionReason
import com.innovify.skillswap.assessment.application.assessmentOverview
import com.innovify.skillswap.assessment.domain.AssessmentAttempt
import com.innovify.skillswap.assessment.domain.MiniProjectSubmission
import com.innovify.skillswap.assessment.domain.QuizProgress
import com.innovify.skillswap.assessment.infrastructure.SavedQuizAttempt
import com.innovify.skillswap.learningpath.domain.PathNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentOverviewTest {

    private fun node(id: Int, status: String, certificate: Int? = null) = PathNode(
        id = id, skillTag = "tag-$id", skillName = "Skill $id", order = id, status = status,
        prerequisiteSkillTags = null, linkedCertificateId = certificate, assessmentBlueprintId = null,
        completedByCertificate = false
    )

    private fun quiz(nodeId: Int, status: String, answers: List<Int>, result: AssessmentAttempt? = null, at: Long = 0) =
        SavedQuizAttempt(
            progress = QuizProgress(nodeId * 10, nodeId, answers, status, result?.let { Gson().toJson(it) }),
            skillName = "Quiz skill $nodeId",
            updatedAt = at
        )

    private fun result(score: Int, total: Int, passed: Boolean, caseId: Int? = null) = AssessmentAttempt(
        id = 1, blueprintId = 1, studentId = 7, score = score, totalQuestions = total, passed = passed,
        completedAt = null, verificationCaseId = caseId, verificationCaseStatus = caseId?.let { "Pending" },
        planLimitReached = null, weakSubTopic = null, failedQuestions = null
    )

    private fun project(nodeId: Int, status: String?, caseId: Int? = null, evaluatedAt: String? = null) =
        MiniProjectSubmission(
            id = nodeId, miniProjectId = 1, pathNodeId = nodeId, skillTag = "tag", repositoryUrl = "https://x",
            submittedAt = "2026-09-01T10:00:00Z", evaluationStatus = status, preliminaryScore = null,
            preliminaryConfidence = null, preliminaryFeedback = null, evaluatedAt = evaluatedAt,
            verificationCaseId = caseId, verificationCaseStatus = null
        )

    @Test
    fun unsentAndStartedQuizzesNeedAttentionButAnUntouchedDraftDoesNot() {
        val nodes = listOf(node(1, "Available", 5), node(2, "Available", 6), node(3, "Locked"))
        val overview = assessmentOverview(
            nodes = nodes,
            activeNodeIds = emptySet(),
            quizzes = listOf(
                quiz(1, "Pending", listOf(0, 1, 2)),
                quiz(2, "Draft", listOf(0, -1, -1)),
                quiz(3, "Draft", listOf(-1, -1))
            ),
            projects = emptyList()
        )
        assertEquals(listOf(AttentionReason.UnsentQuiz, AttentionReason.QuizInProgress), overview.attention.map { it.reason })
        assertEquals(1, overview.attention[1].answered)
        assertEquals(3, overview.attention[1].total)
        assertTrue(overview.history.isEmpty())
    }

    @Test
    fun gradedQuizzesGoToHistoryNewestFirstWithTheirScore() {
        val nodes = listOf(node(1, "Completed", 5), node(2, "Available", 6))
        val overview = assessmentOverview(
            nodes = nodes,
            activeNodeIds = emptySet(),
            quizzes = listOf(
                quiz(1, "Sent", listOf(0, 1), result(9, 10, passed = true), at = 100),
                quiz(2, "Sent", listOf(0, 1), result(5, 10, passed = false), at = 200)
            ),
            projects = emptyList()
        )
        assertEquals(listOf(2, 1), overview.history.map { it.node.id })
        assertEquals(listOf(false, true), overview.history.map { it.passed })
        assertEquals(listOf("5/10", "9/10"), overview.history.map { it.score })
        assertEquals(AssessmentKind.Quiz, overview.history[0].kind)
    }

    @Test
    fun aFailedQuizWithACaseOnAnOpenNodeIsACaseInReview() {
        val nodes = listOf(node(1, "Available", 5), node(2, "Completed", 6))
        val overview = assessmentOverview(
            nodes = nodes,
            activeNodeIds = setOf(1, 2),
            quizzes = listOf(
                quiz(1, "Sent", listOf(0), result(2, 10, passed = false, caseId = 2057), at = 1),
                // The node was completed afterwards: its old case is no longer shown as pending
                quiz(2, "Sent", listOf(0), result(3, 10, passed = false, caseId = 2058), at = 2)
            ),
            projects = emptyList()
        )
        assertEquals(listOf(AttentionReason.CaseInReview), overview.attention.map { it.reason })
        assertEquals(2057, overview.attention.single().caseId)
        assertEquals(2, overview.history.size)
    }

    @Test
    fun anAvailableNodeWithItsCertificateIsReadyForItsQuizOnlyOnTheActivePath() {
        val nodes = listOf(
            node(1, "Available", 5), // ready
            node(2, "Available"), // no certificate yet
            node(3, "Available", 7), // not on the active (or a paused) path
            node(4, "Available", 8) // already has an unsent quiz
        )
        val overview = assessmentOverview(
            nodes = nodes,
            activeNodeIds = setOf(1, 2, 4),
            quizzes = listOf(quiz(4, "Pending", listOf(1))),
            projects = emptyList()
        )
        assertEquals(
            listOf(AttentionReason.UnsentQuiz to 4, AttentionReason.ReadyForQuiz to 1),
            overview.attention.map { it.reason to it.node.id }
        )
    }

    @Test
    fun miniProjectsAreClassifiedByTheirEvaluation() {
        val nodes = listOf(node(1, "Available", 5), node(2, "Completed", 6), node(3, "Available", 7), node(4, "Available", 8))
        val overview = assessmentOverview(
            nodes = nodes,
            activeNodeIds = emptySet(),
            quizzes = emptyList(),
            projects = listOf(
                project(1, "Pending"),
                project(2, "Approved", evaluatedAt = "2026-09-18T10:00:00Z"),
                project(3, "Escalated", caseId = 99),
                project(4, "EscalationLimitReached", evaluatedAt = "2026-09-10T10:00:00Z")
            )
        )
        assertEquals(
            listOf(AttentionReason.CaseInReview to 3, AttentionReason.ProjectEvaluating to 1),
            overview.attention.map { it.reason to it.node.id }
        )
        assertEquals(listOf(2 to true, 4 to false), overview.history.map { it.node.id to it.passed })
        assertTrue(overview.history.all { it.kind == AssessmentKind.MiniProject })
    }

    @Test
    fun attemptsOfNodesTheStudentDoesNotHaveAreIgnored() {
        // The phone may keep quizzes of another account: only nodes of this Student's paths count
        val overview = assessmentOverview(
            nodes = listOf(node(1, "Available", 5)),
            activeNodeIds = emptySet(),
            quizzes = listOf(quiz(42, "Pending", listOf(0)), quiz(43, "Sent", listOf(0), result(1, 1, true))),
            projects = listOf(project(44, "Pending"))
        )
        assertTrue(overview.attention.isEmpty())
        assertTrue(overview.history.isEmpty())
    }
}
