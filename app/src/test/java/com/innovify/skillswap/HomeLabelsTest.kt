package com.innovify.skillswap

import com.innovify.skillswap.learningpath.application.nextStep
import com.innovify.skillswap.learningpath.domain.LearningPath
import com.innovify.skillswap.learningpath.domain.PathNode
import com.innovify.skillswap.learningpath.presentation.lockedReason
import com.innovify.skillswap.shared.presentation.design.planSummary
import com.innovify.skillswap.subscription.domain.PlanLimits
import com.innovify.skillswap.subscription.domain.PlanSubscription
import com.innovify.skillswap.subscription.domain.StudentPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class HomeLabelsTest {

    private fun node(id: Int, order: Int, tag: String, status: String, requires: List<String>? = null) = PathNode(
        id = id, skillTag = tag, skillName = "Skill $tag", order = order, status = status,
        prerequisiteSkillTags = requires, linkedCertificateId = null, assessmentBlueprintId = null,
        completedByCertificate = false
    )

    @Test
    fun lockedNodesNameThePathNodesTheyWaitFor() {
        val nodes = listOf(
            node(1, 1, "python", "Completed"),
            node(2, 2, "numpy", "Available"),
            node(3, 3, "pandas", "Locked"),
            node(4, 4, "viz", "Locked", requires = listOf("pandas")),
            node(5, 5, "sales", "Locked", requires = listOf("viz", "numpy")),
            node(6, 6, "ml", "Locked", requires = listOf("statistics"))
        )
        assertEquals("Bloqueado", lockedReason(nodes[2], nodes))
        assertEquals("Bloqueado · requiere el nodo 3", lockedReason(nodes[3], nodes))
        assertEquals("Bloqueado · requiere los nodos 2 y 4", lockedReason(nodes[4], nodes))
        // A prerequisite outside this path is named by its skill tag
        assertEquals("Bloqueado · requiere statistics", lockedReason(nodes[5], nodes))
    }

    @Test
    fun theNextStepIsTheFirstAvailableNodeInPathOrder() {
        val path = LearningPath(
            id = 1, studentId = 7, goal = "Data", goalSkillTags = null, status = "Active", advanced = false,
            nodes = listOf(node(9, 3, "c", "Available"), node(8, 2, "b", "Available"), node(7, 1, "a", "Completed"))
        )
        assertEquals(8, path.nextStep()?.id)
        assertNull(path.copy(nodes = listOf(node(7, 1, "a", "Completed"))).nextStep())
    }

    @Test
    fun planSummaryDescribesFreeActiveAndCancelledPlans() {
        val limits = PlanLimits(maxActiveRoutes = 1, maxTotalRoutes = 3, monthlyEscalations = 2, reviewDeadline = null)
        fun premium(status: String) = StudentPlan(
            "Premium", limits,
            PlanSubscription(1, 7, "Mensual", null, 19.9, "PEN", status, "2026-10-28T12:00:00Z", null)
        )
        assertNull(planSummary(null))
        assertEquals("Plan gratuito", planSummary(StudentPlan("Free", limits, null)))
        assertEquals("Plan mensual activo · vence el 28 oct", planSummary(premium("Active"), ZoneOffset.UTC))
        assertEquals("Plan mensual cancelado · termina el 28 oct", planSummary(premium("Cancelled"), ZoneOffset.UTC))
    }
}
