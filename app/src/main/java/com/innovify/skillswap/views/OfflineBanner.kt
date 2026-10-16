package com.innovify.skillswap.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.viewmodel.SkillSwapViewModel

// T49: franja que avisa que se están mostrando datos guardados. No se dibuja si hay conexión.
@Composable
fun OfflineBanner(viewModel: SkillSwapViewModel) {
    val offline = !viewModel.connectivity.isOnline
    if (offline || viewModel.showingCache) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Text(
                text = if (offline) "Sin conexión. Mostrando datos guardados en tu teléfono."
                else "No se pudo conectar con el servidor. Mostrando datos guardados.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
