package com.innovify.skillswap.learningpath.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.learningpath.domain.SkillOption
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.components.StatusChip
import com.innovify.skillswap.shared.presentation.design.FlowHeadline
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.subscription.application.SubscriptionViewModel
import com.innovify.skillswap.subscription.presentation.PlanLimitCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// T47 · Mock-up 06 / WF-03 #02: el Estudiante elige y confirma la habilidad antes de construir la ruta.
@Composable
fun SkillConfirmation(
    viewModel: LearningPathViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    context: Context,
    nav: NavHostController
) {

    val options = viewModel.skillOptions
    // Por defecto queda elegida la de mayor afinidad (la primera)
    var selectedTag by rememberSaveable { mutableStateOf(options.firstOrNull()?.skillTag) }
    var creating by remember { mutableStateOf(false) }
    // Mensaje del 409 PlanLimitReached: se muestra como tarjeta con "Ver mi plan"
    var limitMessage by remember { mutableStateOf<String?>(null) }

    fun confirm() {
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
                } else if (subscriptionViewModel.planLimitMessage != null) {
                    limitMessage = subscriptionViewModel.planLimitMessage
                } else {
                    Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    FlowScaffold(
        topBar = { FlowTopBar(title = "Confirma tu habilidad", onBack = { nav.popBackStack() }) },
        bottomBar = {
            PrimaryActionButton(
                text = when {
                    creating -> "Generando tu ruta…"
                    !viewModel.connectivity.isOnline -> "Necesitas internet para generar tu ruta"
                    else -> "Confirmar y generar ruta"
                },
                enabled = selectedTag != null && !creating && viewModel.connectivity.isOnline,
                loading = creating,
                onClick = { confirm() }
            )
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        // La meta escrita; el lápiz vuelve a editarla
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralSurface, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Tu meta", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(
                    text = "“${viewModel.goal}”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = "Editar meta", tint = SkillBlueDark)
            }
        }

        FlowHeadline(
            title = if (options.size == 1) "Encontramos 1 habilidad afín"
            else "Encontramos ${options.size} habilidades afines",
            subtitle = "Elige la que mejor describe lo que quieres lograr. Tu ruta se generará a partir de ella.",
            modifier = Modifier.padding(top = 24.dp, bottom = 20.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.selectableGroup()
        ) {
            options.forEachIndexed { index, option ->
                SkillOptionCard(
                    option = option,
                    selected = option.skillTag == selectedTag,
                    best = index == 0,
                    enabled = !creating,
                    onSelect = { selectedTag = option.skillTag }
                )
            }
        }

        TextButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextSecondary)) { append("¿Ninguna se ajusta? ") }
                    withStyle(
                        SpanStyle(color = SkillBlueDark, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
                    ) { append("Reformular mi meta") }
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (limitMessage != null) {
            PlanLimitCard(
                message = limitMessage ?: "",
                showUpgrade = subscriptionViewModel.plan?.plan != "Premium",
                onUpgrade = { nav.navigate("MyPlan") },
                onContinue = { nav.navigate("MyPaths") }
            )
        }
    }

}

// Tarjeta de una habilidad candidata: radio + nombre + descripción + tamaño de la ruta + afinidad
@Composable
private fun SkillOptionCard(option: SkillOption, selected: Boolean, best: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) SkillBlueContainer else Color.White, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) SkillBlueDark else NeutralBorder, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = SkillBlueDark, unselectedColor = TextSecondary),
            modifier = Modifier.padding(12.dp)
        )
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Text(text = option.skillName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            option.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
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
                // La de mayor afinidad en verde; las demás en gris
                val affinity = "${(option.affinity * 100).toInt()}% afín"
                if (best) StatusChip(text = affinity)
                else StatusChip(text = affinity, color = TextSecondary, container = NeutralSurface)
            }
        }
    }
}
