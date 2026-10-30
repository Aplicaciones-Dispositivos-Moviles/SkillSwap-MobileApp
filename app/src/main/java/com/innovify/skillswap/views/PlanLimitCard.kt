package com.innovify.skillswap.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.ui.theme.Warning
import com.innovify.skillswap.ui.theme.WarningContainer

// Pantalla "Alcanzaste el límite de tu plan" (informe, escenario 6): ofrece pasar al plan mensual o continuar con el gratuito
@Composable
fun PlanLimitCard(message: String, showUpgrade: Boolean, onUpgrade: () -> Unit, onContinue: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Alcanzaste el límite de tu plan",
                style = MaterialTheme.typography.titleSmall,
                color = Warning
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (showUpgrade) {
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(52.dp)
                ) { Text(text = "Pasar al plan mensual") }
            }
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(52.dp)
            ) { Text(text = "Continuar con el plan gratuito") }
        }
    }
}
