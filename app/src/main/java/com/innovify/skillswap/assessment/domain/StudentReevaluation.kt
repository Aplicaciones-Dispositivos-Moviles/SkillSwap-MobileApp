package com.innovify.skillswap.assessment.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

// StudentReevaluationResource (GET api/v1/reevaluations/me): a senior Verifier ordered a reevaluation of one of
// the Student's results. sourceType = "Attempt" | "Case". pathNodeId and skillTag can be null.
// createdAt is a UTC ISO-8601 instant ("2026-11-13T19:35:51.050840Z").
data class StudentReevaluation(
    var id: Int,
    var sourceType: String,
    var sourceId: Int,
    var pathNodeId: Int?,
    var skillTag: String?,
    var justification: String,
    var createdAt: String
)

// Newest reevaluation ordered for this node (by createdAt, then id), or null if none points to it.
fun latestReevaluationFor(nodeId: Int, items: List<StudentReevaluation>): StudentReevaluation? =
    items.filter { it.pathNodeId == nodeId }
        .maxWithOrNull(compareBy<StudentReevaluation>({ instantOrNull(it.createdAt) }, { it.id }))

private fun instantOrNull(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso)
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) // without "Z": the backend always sends UTC
        } catch (e: Exception) {
            null
        }
    }
}
