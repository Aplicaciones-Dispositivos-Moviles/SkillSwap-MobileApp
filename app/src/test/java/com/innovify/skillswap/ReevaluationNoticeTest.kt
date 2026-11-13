package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.assessment.domain.StudentReevaluation
import com.innovify.skillswap.assessment.domain.latestReevaluationFor
import com.innovify.skillswap.assessment.presentation.reevaluationFooter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class ReevaluationNoticeTest {

    private fun reevaluation(id: Int, pathNodeId: Int?, createdAt: String, justification: String = "Revisar") =
        StudentReevaluation(
            id = id, sourceType = "Attempt", sourceId = 10 + id, pathNodeId = pathNodeId,
            skillTag = "kotlin", justification = justification, createdAt = createdAt
        )

    @Test
    fun reevaluations_parse_from_backend_json_including_null_node() {
        val raw = """[
            {"id":5,"sourceType":"Case","sourceId":12,"pathNodeId":7,"skillTag":"kotlin",
             "justification":"El quiz no cubre la habilidad","createdAt":"2026-11-13T19:35:51.050840Z"},
            {"id":4,"sourceType":"Attempt","sourceId":30,"pathNodeId":null,"skillTag":null,
             "justification":"Sin nodo","createdAt":"2026-11-12T10:00:00Z"}
        ]"""
        val parsed = Gson().fromJson(raw, Array<StudentReevaluation>::class.java).toList()

        assertEquals(2, parsed.size)
        assertEquals(5, parsed[0].id)
        assertEquals("Case", parsed[0].sourceType)
        assertEquals(12, parsed[0].sourceId)
        assertEquals(7, parsed[0].pathNodeId)
        assertEquals("kotlin", parsed[0].skillTag)
        assertEquals("El quiz no cubre la habilidad", parsed[0].justification)
        assertEquals("2026-11-13T19:35:51.050840Z", parsed[0].createdAt)
        assertNull(parsed[1].pathNodeId)
        assertNull(parsed[1].skillTag)
    }

    @Test
    fun picks_the_newest_reevaluation_of_the_node_and_ignores_other_nodes() {
        val items = listOf(
            reevaluation(1, 7, "2026-11-10T08:00:00Z", "Antigua"),
            reevaluation(2, 9, "2026-11-14T08:00:00Z", "Otro nodo"),
            reevaluation(3, 7, "2026-11-13T19:35:51.050840Z", "Nueva"),
            reevaluation(4, null, "2026-11-15T08:00:00Z", "Sin nodo")
        )

        assertEquals("Nueva", latestReevaluationFor(7, items)?.justification)
        assertEquals("Otro nodo", latestReevaluationFor(9, items)?.justification)
    }

    @Test
    fun no_notice_when_no_reevaluation_matches_the_node() {
        val items = listOf(reevaluation(1, 9, "2026-11-10T08:00:00Z"), reevaluation(2, null, "2026-11-11T08:00:00Z"))

        assertNull(latestReevaluationFor(7, items))
        assertNull(latestReevaluationFor(7, emptyList()))
    }

    @Test
    fun footer_shows_the_local_order_date_and_what_to_do() {
        val lima = ZoneId.of("America/Lima")

        assertEquals(
            "Ordenada el 13 nov 2026. Vuelve a rendir el quiz o entrega el proyecto para completarlo.",
            reevaluationFooter("2026-11-13T19:35:51.050840Z", lima)
        )
        // 03:00 UTC on the 14th is still the 13th in Lima
        assertEquals(
            "Ordenada el 13 nov 2026. Vuelve a rendir el quiz o entrega el proyecto para completarlo.",
            reevaluationFooter("2026-11-14T03:00:00Z", lima)
        )
    }
}
