package com.innovify.skillswap.views

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.ui.theme.AiContainer
import com.innovify.skillswap.ui.theme.AiViolet
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Mock-up WF-03 #01 (meta) y #04 (meta a reformular): entrada a la confirmación de habilidad (T47).
@Composable
fun GoalInput(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var goal by rememberSaveable { mutableStateOf(viewModel.goal) }
    var goalError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val examples = listOf("APIs REST con JWT", "Diseño UX móvil", "SQL para análisis de datos")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
            }
            Text(text = "Nueva ruta", style = MaterialTheme.typography.titleMedium)
        }
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            StatusChip(text = "Asistente de rutas con IA", color = AiViolet, container = AiContainer)
            Text(
                text = "¿Qué quieres aprender?",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Descríbelo con tus palabras. No necesitas saber el nombre exacto del curso ni de la certificación.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Tu meta de aprendizaje",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = goal,
                placeholder = { Text(text = "Quiero aprender a analizar datos con Python para…") },
                minLines = 4,
                enabled = !loading,
                isError = goalError != null,
                supportingText = {
                    Text(text = goalError ?: "${goal.length} / 500 · incluye para qué lo necesitas")
                },
                modifier = Modifier.fillMaxWidth(),
                onValueChange = {
                    if (it.length <= 500) goal = it
                    goalError = null
                }
            )

            Text(
                text = "Metas de otros estudiantes",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.padding(top = 16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                examples.forEach { example ->
                    AssistChip(onClick = { goal = example }, label = { Text(text = example) })
                }
            }

            Button(
                enabled = !loading && viewModel.connectivity.isOnline,
                onClick = {
                    val text = goal.trim()
                    if (text.isEmpty()) {
                        goalError = "Escribe tu meta para generar tu ruta"
                    } else {
                        loading = true
                        // La red va en hilo IO (como en la plantilla del profe)
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            val ok = viewModel.loadSkillOptions(text)
                            withContext(Dispatchers.Main) {
                                loading = false
                                // WF-03 #04: si no hay habilidades afines se pide reformular la meta
                                if (ok) nav.navigate("SkillConfirmation") else goalError = viewModel.errorMessage
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = when {
                        loading -> "Buscando habilidades…"
                        !viewModel.connectivity.isOnline -> "Necesitas internet para generar tu ruta"
                        else -> "Generar mi ruta"
                    }
                )
            }
        }
    }

}
