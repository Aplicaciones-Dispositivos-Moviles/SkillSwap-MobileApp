package com.innovify.skillswap.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.ui.theme.SkillBlue

// Una línea de la tarjeta: nombre, valor y, si hay tope, qué parte del cupo se usó (0 a 1)
class QuotaItem(val label: String, val value: String, val progress: Float? = null)

// Tarjeta azul del plan (el mismo estilo de "Tu ruta activa"): cupos y límites con su barra de uso
@Composable
fun PlanQuotaCard(label: String, title: String?, items: List<QuotaItem>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SkillBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Text(text = item.label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(text = item.value, style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
                if (item.progress != null) {
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
