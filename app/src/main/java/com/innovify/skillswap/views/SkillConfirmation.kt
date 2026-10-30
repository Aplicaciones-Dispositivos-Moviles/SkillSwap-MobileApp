package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.innovify.skillswap.model.beans.SkillOption
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// T47 · Mock-up WF-03 #02: el Estudiante elige y confirma la habilidad antes de construir la ruta.
@Composable
fun SkillConfirmation(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val options = viewModel.skillOptions
    // Por defecto queda elegida la de mayor afinidad (la primera)
    var selectedTag by rememberSaveable { mutableStateOf(options.firstOrNull()?.skillTag) }
    var creating by remember { mutableStateOf(false) }
    // Mensaje del 409 PlanLimitReached: se muestra como tarjeta con "Ver mi plan"
    var limitMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Confirma tu habilidad", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            // La meta escrita; el lápiz vuelve a editarla
            Card(
                colors = CardDefaults.cardColors(containerColor = NeutralSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)) {
                        Text(text = "Tu meta", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(text = "\"${viewModel.goal}\"", style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Editar meta", tint = SkillBlue)
                    }
                }
            }

            Text(
                text = if (options.size == 1) "Encontramos 1 habilidad afín"
                else "Encontramos ${options.size} habilidades afines",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = "Elige la que mejor describe lo que quieres lograr. Tu ruta se generará a partir de ella.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            options.forEach { option ->
                SkillOptionCard(
                    option = option,
                    selected = option.skillTag == selectedTag,
                    enabled = !creating,
                    onSelect = { selectedTag = option.skillTag }
                )
            }

            TextButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = "¿Ninguna se ajusta? Reformular mi meta")
            }

            if (limitMessage != null) {
                PlanLimitCard(
                    message = limitMessage ?: "",
                    showUpgrade = viewModel.plan?.plan != "Premium",
                    onUpgrade = { nav.navigate("MyPlan") },
                    onContinue = { nav.navigate("MyPaths") }
                )
            }

            Button(
                enabled = selectedTag != null && !creating && viewModel.connectivity.isOnline,
                onClick = {
                    creating = true
                    limitMessage = null
                    // La red va en hilo IO (como en la plantilla del profe)
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val ok = viewModel.declareGoal(context, selectedTag)
                        withContext(Dispatchers.Main) {
                            creating = false
                            if (ok) {
                                // WF-03 #03: la ruta generada reemplaza todo el flujo de la meta
                                nav.navigate("Path") { popUpTo(0) }
                            } else if (viewModel.planLimitMessage != null) {
                                limitMessage = viewModel.planLimitMessage
                            } else {
                                Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = when {
                        creating -> "Generando tu ruta…"
                        !viewModel.connectivity.isOnline -> "Necesitas internet para generar tu ruta"
                        else -> "Confirmar y generar ruta"
                    }
                )
            }
        }
    }

}

// Tarjeta de una habilidad candidata: radio + nombre + descripción + tamaño de la ruta + afinidad
@Composable
private fun SkillOptionCard(option: SkillOption, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SkillBlueContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SkillBlue else NeutralBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(enabled = enabled, onClick = onSelect)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
            Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                Text(text = option.skillName, style = MaterialTheme.typography.titleSmall)
                option.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val size = listOfNotNull(
                    option.nodeCount?.let { "$it nodos" },
                    option.estimatedWeeks?.let { "~$it semanas" }
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(text = "${(option.affinity * 100).toInt()}% afín")
                }
            }
        }
    }
}
