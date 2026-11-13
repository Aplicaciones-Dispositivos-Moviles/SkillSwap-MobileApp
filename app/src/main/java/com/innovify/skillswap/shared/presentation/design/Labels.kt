package com.innovify.skillswap.shared.presentation.design

import com.innovify.skillswap.subscription.domain.StudentPlan
import com.innovify.skillswap.subscription.domain.isCancelled
import com.innovify.skillswap.subscription.domain.isPremium
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// Spanish month abbreviations used by the Figma mock-ups ("14 ago 2026", "vence el 28 oct").
// Fixed here instead of DateTimeFormatter so the text does not depend on the phone's ICU data.
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "14 ago" or "14 ago 2026". */
fun shortDate(date: LocalDate, withYear: Boolean = false): String {
    val base = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    return if (withYear) "$base ${date.year}" else base
}

/** Epoch milliseconds (local quiz attempts) in the phone's zone. */
fun shortDate(epochMillis: Long, withYear: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String =
    shortDate(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), withYear)

/**
 * Backend date: a plain date ("2026-08-14", issue dates) or a UTC instant ("2026-08-14T10:00:00Z", with or
 * without the "Z"). Null when it is missing or cannot be read.
 */
fun shortDate(iso: String?, withYear: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String? {
    val date = localDateOf(iso, zone) ?: return null
    return shortDate(date, withYear)
}

/** Epoch milliseconds of a backend date or instant, or null (used to sort mixed histories). */
fun epochMillisOf(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).toEpochMilli()
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            try {
                LocalDate.parse(iso).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
    }
}

private fun localDateOf(iso: String?, zone: ZoneId): LocalDate? {
    if (iso.isNullOrBlank()) return null
    if (iso.length == 10) {
        return try {
            LocalDate.parse(iso)
        } catch (e: Exception) {
            null
        }
    }
    val millis = epochMillisOf(iso) ?: return null
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

/**
 * One-line summary of the plan for the home header and the profile ("Plan mensual activo · vence el 28 oct").
 * Null while the plan has not been loaded.
 */
fun planSummary(plan: StudentPlan?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (plan == null) return null
    if (!plan.isPremium()) return "Plan gratuito"
    val subscription = plan.subscription ?: return "Plan mensual activo"
    val end = shortDate(subscription.currentPeriodEnd, zone = zone)
    return when {
        subscription.isCancelled() && end != null -> "Plan mensual cancelado · termina el $end"
        subscription.isCancelled() -> "Plan mensual cancelado"
        end != null -> "Plan mensual activo · vence el $end"
        else -> "Plan mensual activo"
    }
}
