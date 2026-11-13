package com.innovify.skillswap.assessment.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.assessment.domain.StudentReevaluation
import com.innovify.skillswap.shared.presentation.design.InfoCard
import com.innovify.skillswap.shared.presentation.design.shortDate
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.shared.presentation.theme.Warning
import com.innovify.skillswap.shared.presentation.theme.WarningContainer
import java.time.ZoneId

private const val REEVALUATION_NEXT_STEP = "Vuelve a rendir el quiz o entrega el proyecto para completarlo."

// "Ordenada el 13 nov 2026. Vuelve a rendir…" with the date in the phone's zone; without the date if unreadable.
fun reevaluationFooter(createdAt: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = shortDate(createdAt, withYear = true, zone = zone) ?: return REEVALUATION_NEXT_STEP
    return "Ordenada el $date. $REEVALUATION_NEXT_STEP"
}

// Tells the Student why a node went back to "Disponible": a senior Verifier ordered a reevaluation.
@Composable
fun ReevaluationNotice(reevaluation: StudentReevaluation, modifier: Modifier = Modifier) {
    InfoCard(
        icon = Icons.Filled.Info,
        title = "Un Verificador senior pidió reevaluar este nodo",
        body = reevaluation.justification,
        tint = Warning,
        container = WarningContainer,
        modifier = modifier
    ) {
        Text(
            text = reevaluationFooter(reevaluation.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
