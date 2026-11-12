package com.innovify.skillswap.learningpath.presentation

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.FlowChip
import com.innovify.skillswap.shared.presentation.design.FlowHeadline
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.theme.AiContainer
import com.innovify.skillswap.shared.presentation.theme.AiViolet
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Mock-up 05 / WF-03 #01 (meta) y #04 (meta a reformular): entrada a la confirmación de habilidad (T47).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalInput(viewModel: LearningPathViewModel, context: Context, nav: NavHostController) {

    var goal by rememberSaveable { mutableStateOf(viewModel.goal) }
    var goalError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val examples = listOf("APIs REST con JWT", "Diseño UX móvil", "SQL para análisis de datos")

    fun generate() {
        val text = goal.trim()
        if (text.isEmpty()) {
            goalError = "Escribe tu meta para generar tu ruta"
            return
        }
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

    FlowScaffold(
        topBar = { FlowTopBar(title = "Nueva ruta", close = true, onBack = { nav.popBackStack() }) },
        bottomBar = {
            PrimaryActionButton(
                text = when {
                    loading -> "Buscando habilidades…"
                    !viewModel.connectivity.isOnline -> "Necesitas internet para generar tu ruta"
                    else -> "Generar mi ruta"
                },
                enabled = !loading && viewModel.connectivity.isOnline,
                loading = loading,
                icon = FlowIcons.Sparkle,
                onClick = { generate() }
            )
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        FlowChip(text = "Asistente de rutas con IA", color = AiViolet, container = AiContainer, icon = FlowIcons.Sparkle)
        FlowHeadline(
            title = "¿Qué quieres aprender?",
            subtitle = "Descríbelo con tus palabras. No necesitas saber el nombre exacto del curso ni de la certificación.",
            modifier = Modifier.padding(top = 12.dp)
        )

        Text(
            text = "Tu meta de aprendizaje",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = goal,
            placeholder = { Text(text = "Quiero aprender a analizar datos con Python para…") },
            minLines = 4,
            enabled = !loading,
            isError = goalError != null,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SkillBlueDark,
                unfocusedBorderColor = NeutralBorder
            ),
            supportingText = {
                // Ayuda (o error) a la izquierda y contador a la derecha
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = goalError ?: "Incluye para qué lo necesitas (prácticas, proyecto o trabajo): así tu ruta será más precisa.",
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "${goal.length} / 500", modifier = Modifier.padding(start = 8.dp))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            onValueChange = {
                if (it.length <= 500) goal = it
                goalError = null
            }
        )

        Text(
            text = "Metas de otros estudiantes",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            examples.forEach { example ->
                OutlinedButton(
                    onClick = {
                        goal = example
                        goalError = null
                    },
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeutralBorder),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = example, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        }
    }

}
