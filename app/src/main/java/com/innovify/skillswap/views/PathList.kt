package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.PathNode
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Mock-up 07 · Mi ruta
@Composable
fun PathList(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }

    // Carga la ruta al entrar y otra vez cada vez que cambia la conexión (T49):
    // sin internet muestra lo guardado; al volver internet se actualiza sola.
    LaunchedEffect(viewModel.connectivity.isOnline) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadPath(context)
            withContext(Dispatchers.Main) {
                loading = false
                if (!ok) {
                    Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val path = viewModel.path

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 48.dp, bottom = 32.dp, start = 24.dp, end = 24.dp
        )
    ) {
        item {
            OfflineBanner(viewModel)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Hola, ${viewModel.user?.username ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.signOut(context) }) {
                    Text(text = "Salir")
                }
            }
        }

        if (loading) {
            item { Text(text = "Cargando…", style = MaterialTheme.typography.bodyLarge) }
        } else if (path == null) {
            item {
                Text(
                    text = "Aún no tienes una ruta de aprendizaje.",
                    style = MaterialTheme.typography.bodyLarge
                )
                // T47: la meta lleva a confirmar la habilidad y construir la ruta
                Button(
                    enabled = viewModel.connectivity.isOnline,
                    onClick = { nav.navigate("Goal") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp)
                ) {
                    Text(text = "Crear mi ruta")
                }
            }
        } else {
            val nodes = (path.nodes ?: emptyList()).sortedBy { it.order }
            val done = nodes.count { it.status == "Completed" }
            val firstAvailable = nodes.firstOrNull { it.status == "Available" }?.id

            item {
                // Tarjeta de la ruta activa con el avance
                Card(
                    colors = CardDefaults.cardColors(containerColor = SkillBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "TU RUTA ACTIVA",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = path.goal,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$done de ${nodes.size} nodos completados",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Text(
                                text = "${if (nodes.isEmpty()) 0 else done * 100 / nodes.size}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (nodes.isEmpty()) 0f else done.toFloat() / nodes.size },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
                if (path.status != "Active") {
                    Text(
                        text = "Estado de la ruta: " + when (path.status) {
                            "Paused" -> "en pausa"
                            "Completed" -> "completada"
                            else -> path.status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    text = "Tu ruta",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                )
            }

            itemsIndexed(nodes) { index, node ->
                PathNodeRow(
                    node = node,
                    last = index == nodes.lastIndex,
                    isNext = node.id == firstAvailable,
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
                )
            }
        }
    }

}

// Una fila de la línea de tiempo: marcador (completado, actual o bloqueado) + título y estado
@Composable
private fun PathNodeRow(node: PathNode, last: Boolean, isNext: Boolean, onClick: () -> Unit) {
    val state = when (node.status) {
        "Completed" -> StepState.Completed
        "Available" -> StepState.Current
        else -> StepState.Locked
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            StepMarker(state = state, number = node.order)
            if (!last) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp)
        ) {
            when {
                state == StepState.Completed -> {
                    Text(text = node.skillName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (node.completedByCertificate) "Completado · con certificado" else "Completado",
                        style = MaterialTheme.typography.labelMedium,
                        color = Success
                    )
                }

                state == StepState.Current && isNext -> {
                    // Siguiente paso: tarjeta con borde azul
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, SkillBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            StatusChip(text = "Siguiente paso", color = SkillBlue, container = SkillBlueContainer)
                            Text(
                                text = node.skillName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = if (node.linkedCertificateId == null) "Sube tu certificado para empezar"
                                else "Certificado vinculado · rinde el quiz o el proyecto",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state == StepState.Current -> {
                    Text(text = node.skillName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Disponible",
                        style = MaterialTheme.typography.labelMedium,
                        color = SkillBlue
                    )
                }

                else -> {
                    val requires = node.prerequisiteSkillTags?.joinToString(", ")?.ifBlank { null }
                    Text(
                        text = node.skillName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Bloqueado" + (requires?.let { " · requiere $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
