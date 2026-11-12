package com.innovify.skillswap.learningpath.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.iam.application.SessionViewModel
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.learningpath.domain.LearningPath
import com.innovify.skillswap.learningpath.domain.PathNode
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.InitialsAvatar
import com.innovify.skillswap.shared.presentation.design.LoadingBlock
import com.innovify.skillswap.shared.presentation.design.MessageBlock
import com.innovify.skillswap.shared.presentation.design.StatePill
import com.innovify.skillswap.shared.presentation.design.StretchTimelineRow
import com.innovify.skillswap.shared.presentation.design.StudentTab
import com.innovify.skillswap.shared.presentation.design.StudentTabScaffold
import com.innovify.skillswap.shared.presentation.design.TimelineState
import com.innovify.skillswap.shared.presentation.design.firstNameOf
import com.innovify.skillswap.shared.presentation.design.openTab
import com.innovify.skillswap.shared.presentation.design.planSummary
import com.innovify.skillswap.shared.presentation.design.tabContentPadding
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.shared.presentation.theme.Warning
import com.innovify.skillswap.shared.presentation.theme.WarningContainer
import com.innovify.skillswap.subscription.application.SubscriptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Figma "Home estudiante" · Mi ruta: greeting with the plan, the active path card and the path as a timeline.
@Composable
fun PathList(
    viewModel: LearningPathViewModel,
    sessionViewModel: SessionViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    context: Context,
    nav: NavHostController
) {

    var loading by remember { mutableStateOf(viewModel.path == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var resuming by remember { mutableStateOf(false) }

    fun load() {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadPath(context)
            val error = if (ok) null else viewModel.errorMessage
            withContext(Dispatchers.Main) {
                loading = false
                loadError = error
                // With a path already on screen the failure is only a notice
                if (error != null && viewModel.path != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
            // Header data: the real name and the plan. Optional, the greeting falls back to the username.
            sessionViewModel.loadProfile(context)
            if (subscriptionViewModel.plan == null) subscriptionViewModel.loadPlan()
        }
    }

    // Carga la ruta al entrar y otra vez cada vez que cambia la conexión (T49):
    // sin internet muestra lo guardado; al volver internet se actualiza sola.
    LaunchedEffect(viewModel.connectivity.isOnline) { load() }

    val path = viewModel.path
    val displayName = sessionViewModel.displayName(context)

    StudentTabScaffold(selected = StudentTab.Path, nav = nav) { inner ->
        LazyColumn(
            contentPadding = tabContentPadding(inner),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Header(
                    firstName = firstNameOf(displayName, sessionViewModel.user?.username ?: ""),
                    displayName = displayName,
                    plan = planSummary(subscriptionViewModel.plan),
                    onAvatar = { nav.openTab(StudentTab.Profile) }
                )
                OfflineBanner(viewModel.connectivity, viewModel.showingCache)
            }

            when {
                loading -> item { LoadingBlock("Cargando tu ruta…") }

                path == null && loadError != null -> item {
                    MessageBlock(
                        icon = Icons.Filled.Warning,
                        title = "No pudimos cargar tu ruta",
                        body = loadError ?: "",
                        tint = Error,
                        actionLabel = if (viewModel.connectivity.isOnline) "Reintentar" else "Necesitas internet",
                        actionEnabled = viewModel.connectivity.isOnline,
                        onAction = {
                            loading = true
                            load()
                        }
                    )
                }

                path == null -> item {
                    // T47: la meta lleva a confirmar la habilidad y construir la ruta
                    MessageBlock(
                        icon = Icons.Filled.Add,
                        title = "Aún no tienes una ruta de aprendizaje",
                        body = if (viewModel.connectivity.isOnline) {
                            "Cuéntanos tu meta y armamos una ruta con las habilidades que necesitas."
                        } else "Necesitas internet para crear tu ruta.",
                        actionLabel = "Crear mi ruta",
                        actionEnabled = viewModel.connectivity.isOnline,
                        onAction = { nav.navigate("Goal") }
                    )
                }

                else -> {
                    val nodes = path.nodes.orEmpty().sortedBy { it.order }
                    val done = nodes.count { it.status == "Completed" }
                    val firstAvailable = nodes.firstOrNull { it.status == "Available" }?.id

                    item {
                        ActivePathCard(path, done, nodes.size)

                        // Una ruta pausada conserva su progreso, pero no se puede avanzar hasta reanudarla
                        if (path.status == "Paused") {
                            PausedCard(
                                resuming = resuming,
                                online = viewModel.connectivity.isOnline,
                                onResume = {
                                    resuming = true
                                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                                        val ok = viewModel.resumePath(path.id)
                                        withContext(Dispatchers.Main) {
                                            resuming = false
                                            Toast.makeText(
                                                context,
                                                if (ok) "Ruta reanudada" else viewModel.errorMessage,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }

                        // US28: con todos los nodos completados se puede programar la demostración final
                        if (path.status == "Completed" || (nodes.isNotEmpty() && done == nodes.size)) {
                            Button(
                                onClick = { nav.navigate("Demonstration") },
                                colors = ButtonDefaults.buttonColors(containerColor = SkillBlueDark),
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Demostración final")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)
                        ) {
                            Text(
                                "Tu ruta",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { nav.navigate("MyPaths") }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Mis rutas", color = SkillBlueDark)
                            }
                        }
                    }

                    itemsIndexed(nodes, key = { _, node -> node.id }) { index, node ->
                        val isNext = node.id == firstAvailable
                        StretchTimelineRow(
                            state = when (node.status) {
                                "Completed" -> TimelineState.Done
                                "Available" -> if (isNext) TimelineState.Current else TimelineState.Pending
                                else -> TimelineState.Locked
                            },
                            number = node.order,
                            isLast = index == nodes.lastIndex,
                            onClickLabel = "Abrir nodo",
                            onClick = {
                                if (node.status == "Locked") {
                                    Toast.makeText(
                                        context, "Completa los requisitos previos para desbloquear este nodo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    viewModel.selectedNode = node
                                    nav.navigate("Node")
                                }
                            }
                        ) {
                            NodeContent(node, nodes, isNext, paused = path.status == "Paused")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(firstName: String, displayName: String, plan: String?, onAvatar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Hola, $firstName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (plan != null) {
                Text(plan, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onAvatar)
                .semantics { contentDescription = "Abrir mi perfil" }
        ) {
            InitialsAvatar(displayName, size = 48.dp)
        }
    }
}

@Composable
private fun ActivePathCard(path: LearningPath, done: Int, total: Int) {
    val percent = if (total == 0) 0 else done * 100 / total
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkillBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                when (path.status) {
                    "Paused" -> "RUTA PAUSADA"
                    "Completed" -> "RUTA COMPLETADA"
                    else -> "TU RUTA ACTIVA"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                path.goal,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$done de $total nodos completados", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text("$percent%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 6.dp).clip(RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun PausedCard(resuming: Boolean, online: Boolean, onResume: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WarningContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Esta ruta está pausada. Conserva tu progreso, pero no puedes avanzar hasta reanudarla.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Button(
                enabled = !resuming && online,
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = Warning),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)
            ) {
                Text(if (resuming) "Reanudando…" else "Reanudar ruta")
            }
        }
    }
}

@Composable
private fun NodeContent(node: PathNode, nodes: List<PathNode>, isNext: Boolean, paused: Boolean) {
    when {
        node.status == "Completed" -> {
            Text(node.skillName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                if (node.completedByCertificate) "Completado · con certificado" else "Completado",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Success
            )
        }

        node.status == "Available" && isNext -> {
            // Siguiente paso: tarjeta con borde azul y flecha
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, SkillBlueDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StatePill("Siguiente paso", SkillBlueDark, SkillBlueContainer)
                        Text(
                            node.skillName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            when {
                                paused -> "Reanuda la ruta para continuar"
                                node.linkedCertificateId == null -> "Sube tu certificado para empezar"
                                else -> "Certificado vinculado · rinde el quiz o el proyecto"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SkillBlueDark)
                }
            }
        }

        node.status == "Available" -> {
            Text(node.skillName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Disponible", style = MaterialTheme.typography.labelMedium, color = SkillBlue)
        }

        else -> {
            Text(node.skillName, style = MaterialTheme.typography.titleSmall, color = TextSecondary)
            Text(lockedReason(node, nodes), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

/**
 * Why a node is locked: its prerequisites named by their node in this path ("requiere el nodo 3",
 * "requiere los nodos 2 y 4"); a prerequisite outside the path is named by its skill tag.
 */
internal fun lockedReason(node: PathNode, nodes: List<PathNode>): String {
    val required = node.prerequisiteSkillTags.orEmpty().filter { it.isNotBlank() }
    if (required.isEmpty()) return "Bloqueado"
    val orders = required.mapNotNull { tag -> nodes.firstOrNull { it.skillTag == tag }?.order }.sorted()
    val outside = required.filter { tag -> nodes.none { it.skillTag == tag } }
    val parts = mutableListOf<String>()
    when (orders.size) {
        0 -> {}
        1 -> parts += "el nodo ${orders.single()}"
        else -> parts += "los nodos ${orders.dropLast(1).joinToString(", ")} y ${orders.last()}"
    }
    parts += outside
    return "Bloqueado · requiere ${parts.joinToString(", ")}"
}
