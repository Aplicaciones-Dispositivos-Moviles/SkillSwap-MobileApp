package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.NEW_PATH_ALLOWED
import com.innovify.skillswap.model.beans.NEW_PATH_NEEDS_PAUSE
import com.innovify.skillswap.model.beans.NEW_PATH_TOTAL_LIMIT
import com.innovify.skillswap.model.beans.activePathCount
import com.innovify.skillswap.model.beans.canResumePath
import com.innovify.skillswap.model.beans.completedNodeCount
import com.innovify.skillswap.model.beans.isPremium
import com.innovify.skillswap.model.beans.newPathDecision
import com.innovify.skillswap.model.beans.nodeCount
import com.innovify.skillswap.model.beans.pathStatusLabel
import com.innovify.skillswap.model.beans.totalPathCount
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.ui.theme.Warning
import com.innovify.skillswap.ui.theme.WarningContainer
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Mis rutas: todas las rutas del Estudiante con su estado, cuántas permite su plan, y pausar, reanudar o abrir cada una.
@Composable
fun MyPaths(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    // Ruta activa que se pausaría para crear otra (null = sin diálogo)
    var pauseToCreate by remember { mutableStateOf<LearningPath?>(null) }
    var limitMessage by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadMyPaths()
            withContext(Dispatchers.Main) {
                loading = false
                error = if (ok) null else viewModel.errorMessage
            }
        }
    }

    // Pausa o reanuda una ruta; si sale bien, onDone (por ejemplo seguir a crear la nueva)
    fun change(pause: Boolean, path: LearningPath, success: String, onDone: () -> Unit = {}) {
        working = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = if (pause) viewModel.pausePath(path.id) else viewModel.resumePath(path.id)
            withContext(Dispatchers.Main) {
                working = false
                Toast.makeText(context, if (ok) success else viewModel.errorMessage, Toast.LENGTH_LONG).show()
                if (ok) onDone()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val plan = viewModel.plan
    val paths = viewModel.myPaths

    pauseToCreate?.let { active ->
        AlertDialog(
            onDismissRequest = { pauseToCreate = null },
            title = { Text(text = "Pausar tu ruta actual") },
            text = {
                Text(
                    text = "Tu plan permite ${plan?.limits?.maxActiveRoutes ?: 1} ruta(s) activa(s) a la vez. " +
                        "Pausaremos \"${active.goal}\": conserva su progreso y podrás reanudarla después."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pauseToCreate = null
                    change(true, active, "Ruta pausada") { nav.navigate("Goal") }
                }) { Text(text = "Pausar y crear otra") }
            },
            dismissButton = { TextButton(onClick = { pauseToCreate = null }) { Text(text = "Cancelar") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Mis rutas", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            if (loading && plan == null) {
                Text(text = "Cargando…", style = MaterialTheme.typography.bodyLarge)
            } else if (error != null || plan == null) {
                Text(text = error ?: "No se pudieron cargar tus rutas", color = Error)
                Button(
                    enabled = viewModel.connectivity.isOnline,
                    onClick = { load() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp)
                ) { Text(text = if (viewModel.connectivity.isOnline) "Reintentar" else "Necesitas internet") }
            } else {
                // Cupos del plan
                val limits = plan.limits
                val total = totalPathCount(paths)
                val active = activePathCount(paths)
                val maxTotal = limits.maxTotalRoutes
                PlanQuotaCard(
                    label = "TU PLAN",
                    title = if (plan.isPremium()) "Plan mensual" else "Plan gratuito",
                    items = listOf(
                        QuotaItem(
                            "Rutas activas", "$active de ${limits.maxActiveRoutes}",
                            active.toFloat() / limits.maxActiveRoutes
                        ),
                        if (maxTotal == null) QuotaItem("Rutas en total", "$total · sin tope")
                        else QuotaItem("Rutas en total", "$total de $maxTotal", total.toFloat() / maxTotal)
                    )
                )
                if (paths.any { it.advanced }) {
                    Text(
                        text = "Las rutas avanzadas no cuentan en estos cupos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (!plan.isPremium()) {
                    TextButton(onClick = { nav.navigate("MyPlan") }) { Text(text = "Ver el plan mensual") }
                }

                if (limitMessage != null) {
                    PlanLimitCard(
                        message = limitMessage ?: "",
                        showUpgrade = !plan.isPremium(),
                        onUpgrade = { nav.navigate("MyPlan") },
                        onContinue = { limitMessage = null }
                    )
                }

                Button(
                    enabled = !working && !loading && viewModel.connectivity.isOnline,
                    onClick = {
                        limitMessage = null
                        when (newPathDecision(limits, paths)) {
                            NEW_PATH_ALLOWED -> nav.navigate("Goal")
                            NEW_PATH_TOTAL_LIMIT -> limitMessage =
                                "Tu plan permite hasta ${limits.maxTotalRoutes} rutas en total y ya tienes $total."
                            NEW_PATH_NEEDS_PAUSE -> {
                                val active = paths.filter { it.status == "Active" && !it.advanced }
                                if (active.size == 1) {
                                    pauseToCreate = active.first()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Ya tienes ${active.size} rutas activas. Pausa una de la lista para crear otra.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp)
                ) {
                    Text(text = if (viewModel.connectivity.isOnline) "Nueva ruta" else "Necesitas internet")
                }

                Text(
                    text = "Tus rutas",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                if (paths.isEmpty()) {
                    Text(
                        text = "Todavía no tienes rutas. Crea la primera con \"Nueva ruta\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                paths.forEach { path ->
                    PathCard(
                        path = path,
                        shown = viewModel.path?.id == path.id,
                        busy = working || !viewModel.connectivity.isOnline,
                        onOpen = {
                            viewModel.openPath(path)
                            if (!nav.popBackStack("Path", false)) nav.navigate("Path")
                        },
                        onPause = { change(true, path, "Ruta pausada") },
                        onResume = {
                            if (!canResumePath(limits, paths, path)) {
                                Toast.makeText(
                                    context,
                                    "Tu plan permite ${limits.maxActiveRoutes} ruta(s) activa(s). Pausa otra primero.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                change(false, path, "Ruta reanudada")
                            }
                        }
                    )
                }

                TextButton(
                    enabled = !loading && !working && viewModel.connectivity.isOnline,
                    onClick = { load() },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text(text = if (loading) "Actualizando…" else "Actualizar") }
            }
        }
    }

}

@Composable
private fun PathCard(
    path: LearningPath,
    shown: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val look = when (path.status) {
        "Active" -> Triple("Activa", Success, SuccessContainer)
        "Paused" -> Triple("Pausada", Warning, WarningContainer)
        else -> Triple(pathStatusLabel(path.status), SkillBlue, SkillBlueContainer)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (shown) 2.dp else 1.dp, if (shown) SkillBlue else NeutralBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(text = look.first, color = look.second, container = look.third)
                if (path.advanced) StatusChip(text = "Avanzada", color = SkillBlue, container = SkillBlueContainer)
                if (shown) StatusChip(text = "Abierta", color = SkillBlue, container = SkillBlueContainer)
            }
            Text(
                text = path.goal,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "${path.completedNodeCount()} de ${path.nodeCount()} nodos completados",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) { Text(text = "Abrir") }
                if (path.status == "Active") {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) { Text(text = "Pausar") }
                } else if (path.status == "Paused") {
                    Button(
                        enabled = !busy,
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) { Text(text = "Reanudar") }
                }
            }
        }
    }
}
