package com.innovify.skillswap

import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.NEW_PATH_ALLOWED
import com.innovify.skillswap.model.beans.NEW_PATH_NEEDS_PAUSE
import com.innovify.skillswap.model.beans.NEW_PATH_TOTAL_LIMIT
import com.innovify.skillswap.model.beans.PlanLimits
import com.innovify.skillswap.model.beans.activePathCount
import com.innovify.skillswap.model.beans.canResumePath
import com.innovify.skillswap.model.beans.newPathDecision
import com.innovify.skillswap.model.beans.pathStatusLabel
import com.innovify.skillswap.model.beans.totalPathCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathLimitsTest {

    private val free = PlanLimits(maxActiveRoutes = 1, maxTotalRoutes = 3, monthlyEscalations = 3, reviewDeadline = null)
    private val monthly = PlanLimits(maxActiveRoutes = 3, maxTotalRoutes = null, monthlyEscalations = 10, reviewDeadline = null)

    private fun path(id: Int, status: String, advanced: Boolean = false) = LearningPath(
        id = id, studentId = 1, goal = "meta $id", goalSkillTags = null, status = status, nodes = null, advanced = advanced
    )

    @Test
    fun free_plan_with_no_paths_can_create() {
        assertEquals(NEW_PATH_ALLOWED, newPathDecision(free, emptyList()))
    }

    @Test
    fun free_plan_with_an_active_path_must_pause_first() {
        assertEquals(NEW_PATH_NEEDS_PAUSE, newPathDecision(free, listOf(path(1, "Active"))))
    }

    @Test
    fun free_plan_can_create_after_pausing_the_active_one() {
        assertEquals(NEW_PATH_ALLOWED, newPathDecision(free, listOf(path(1, "Paused"))))
    }

    @Test
    fun free_plan_stops_at_three_paths_in_total_even_if_none_is_active() {
        val paths = listOf(path(1, "Completed"), path(2, "Paused"), path(3, "Paused"))
        assertEquals(NEW_PATH_TOTAL_LIMIT, newPathDecision(free, paths))
    }

    @Test
    fun monthly_plan_allows_three_active_and_has_no_total_cap() {
        assertEquals(NEW_PATH_ALLOWED, newPathDecision(monthly, listOf(path(1, "Active"), path(2, "Active"))))
        assertEquals(
            NEW_PATH_NEEDS_PAUSE,
            newPathDecision(monthly, listOf(path(1, "Active"), path(2, "Active"), path(3, "Active")))
        )
        val many = (1..8).map { path(it, "Completed") }
        assertEquals(NEW_PATH_ALLOWED, newPathDecision(monthly, many))
    }

    @Test
    fun advanced_paths_do_not_count_in_the_limits() {
        val paths = listOf(path(1, "Active", advanced = true), path(2, "Paused"))
        assertEquals(0, activePathCount(paths))
        assertEquals(1, totalPathCount(paths))
        assertEquals(NEW_PATH_ALLOWED, newPathDecision(free, paths))
    }

    @Test
    fun a_paused_path_resumes_only_if_the_plan_has_a_free_slot() {
        val paused = path(2, "Paused")
        assertFalse(canResumePath(free, listOf(path(1, "Active"), paused), paused))
        assertTrue(canResumePath(free, listOf(path(1, "Paused"), paused), paused))
        assertTrue(canResumePath(monthly, listOf(path(1, "Active"), paused), paused))
    }

    @Test
    fun status_labels_are_in_spanish() {
        assertEquals("Activa", pathStatusLabel("Active"))
        assertEquals("Pausada", pathStatusLabel("Paused"))
        assertEquals("Completada", pathStatusLabel("Completed"))
    }
}
