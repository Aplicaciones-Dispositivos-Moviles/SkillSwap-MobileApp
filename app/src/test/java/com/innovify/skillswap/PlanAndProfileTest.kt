package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.model.beans.PlanLimits
import com.innovify.skillswap.model.beans.ReviewDeadline
import com.innovify.skillswap.model.beans.StudentPlan
import com.innovify.skillswap.model.beans.canCancel
import com.innovify.skillswap.model.beans.cleanInterest
import com.innovify.skillswap.model.beans.interestError
import com.innovify.skillswap.model.beans.isCancelled
import com.innovify.skillswap.model.beans.isHttpUrl
import com.innovify.skillswap.model.beans.isPremium
import com.innovify.skillswap.model.beans.label
import com.innovify.skillswap.model.beans.totalRoutesLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAndProfileTest {

    @Test
    fun free_plan_parses_from_backend_json() {
        val raw = """{"plan":"Free","limits":{"maxActiveRoutes":1,"maxTotalRoutes":3,"monthlyEscalations":1,
            "reviewDeadline":{"amount":5,"unit":"BusinessDays"}},"subscription":null}"""
        val plan = Gson().fromJson(raw, StudentPlan::class.java)
        assertFalse(plan.isPremium())
        assertNull(plan.subscription)
        assertEquals("3", plan.limits.totalRoutesLabel())
        assertEquals("5 días hábiles", plan.limits.reviewDeadline?.label())
    }

    @Test
    fun premium_plan_with_cancelled_subscription_keeps_period() {
        val raw = """{"plan":"Premium","limits":{"maxActiveRoutes":3,"maxTotalRoutes":null,"monthlyEscalations":5,
            "reviewDeadline":{"amount":24,"unit":"Hours"}},"subscription":{"id":8,"studentId":5,
            "planName":"Premium","productId":"skillswap_premium_monthly","price":9.99,"currency":"USD",
            "status":"Cancelled","currentPeriodEnd":"2026-11-26T10:00:00Z","cancelledAt":"2026-10-27T10:00:00Z"}}"""
        val plan = Gson().fromJson(raw, StudentPlan::class.java)
        assertTrue(plan.isPremium())
        assertEquals("Sin límite", plan.limits.totalRoutesLabel())
        assertEquals("24 horas", plan.limits.reviewDeadline?.label())
        assertTrue(plan.subscription!!.isCancelled())
        assertFalse(plan.subscription!!.canCancel())
    }

    @Test
    fun review_deadline_uses_singular_for_one() {
        assertEquals("1 hora", ReviewDeadline(1, "Hours").label())
        assertEquals("1 día hábil", ReviewDeadline(1, "BusinessDays").label())
        assertEquals("Sin límite", PlanLimits(1, null, 1, null).totalRoutesLabel())
    }

    @Test
    fun interests_are_cleaned_and_validated() {
        assertEquals("machine learning", cleanInterest("  machine   learning "))
        assertEquals("Escribe un tema", interestError(emptyList(), "   "))
        assertEquals("Ya agregaste ese tema", interestError(listOf("Kotlin"), " kotlin "))
        assertEquals("Máximo 60 caracteres", interestError(emptyList(), "a".repeat(61)))
        assertNull(interestError(emptyList(), "a".repeat(60)))
        assertEquals("Máximo 10 temas", interestError(List(10) { "tema$it" }, "otro"))
        assertNull(interestError(List(9) { "tema$it" }, "otro"))
    }

    @Test
    fun evidence_link_must_be_http_or_https() {
        assertTrue(isHttpUrl("https://github.com/usuario/proyecto"))
        assertTrue(isHttpUrl("  HTTP://portafolio.dev "))
        assertFalse(isHttpUrl("github.com/usuario/proyecto"))
        assertFalse(isHttpUrl("ftp://servidor/archivo"))
        assertFalse(isHttpUrl("https://con espacios.com"))
        assertFalse(isHttpUrl("https://"))
    }
}
