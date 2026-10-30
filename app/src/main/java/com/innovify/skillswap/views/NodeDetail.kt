package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Mock-up 08 · Detalle del nodo
@Composable
fun NodeDetail(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode

    // T48: busca si ya hay un certificado guardado en el teléfono para este nodo
    LaunchedEffect(node?.id) {
        val nodeId = node?.id ?: return@LaunchedEffect
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            viewModel.loadLocalCertificate(context, nodeId)
        }
    }

    // Si el nodo ya tiene un quiz calificado se ofrece volver a ver su resultado (Rendir quiz pide uno nuevo)
    var hasQuizResult by remember { mutableStateOf(false) }
    LaunchedEffect(node?.id, viewModel.quizStatus, viewModel.attemptResult) {
        val nodeId = node?.id ?: return@LaunchedEffect
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val has = viewModel.hasQuizResult(context, nodeId)
            withContext(Dispatchers.Main) { hasQuizResult = has }
        }
    }

    val total = viewModel.path?.nodes?.size ?: 0
    var enabling by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(
            title = if (node != null && total > 0) "Nodo ${node.order} de $total" else "Nodo",
            onBack = { nav.popBackStack() }
        )

        Column(modifier = Modifier.padding(start = 8.dp)) {
            OfflineBanner(viewModel)

            if (node == null) {
                Text(text = "Selecciona un nodo desde la ruta.", style = MaterialTheme.typography.bodyLarge)
            } else {
                when (node.status) {
                    "Completed" -> StatusChip("Completado")
                    "Available" -> StatusChip("Disponible")
                    else -> StatusChip("Bloqueado", color = TextSecondary, container = NeutralSurface)
                }
                Text(
                    text = node.skillName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "Habilidad: ${node.skillTag}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Cómo completar este nodo: los pasos se marcan según el avance real
                val certificateDone = node.linkedCertificateId != null || node.completedByCertificate
                val nodeDone = node.status == "Completed"
                Text(
                    text = "Cómo completar este nodo",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                )
                CompletionStep(1, "Sube tu certificado", certificateDone, current = !certificateDone)
                CompletionStep(
                    2, "La IA confirma que cubre esta habilidad", certificateDone,
                    current = false
                )
                CompletionStep(
                    3, "Rinde el quiz o entrega el proyecto", nodeDone,
                    current = certificateDone && !nodeDone
                )

                Text(
                    text = "Requisitos: " + (node.prerequisiteSkillTags?.joinToString(", ")
                        ?.ifBlank { null } ?: "ninguno"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Certificado: " + (node.linkedCertificateId?.let { "#$it" } ?: "sin certificado"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val local = viewModel.localCertificate
                if (local != null && local.nodeId == node.id) {
                    Text(
                        text = "Copia en el teléfono: " + File(local.filePath).name +
                            if (local.status == "Pending") " (pendiente de envío)" else " (enviado)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    // T44: solo un nodo disponible y sin certificado puede recibir uno
                    if (node.status == "Available" && node.linkedCertificateId == null) {
                        Button(
                            onClick = { nav.navigate("Certificate") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Subir certificado")
                        }
                    }

                    // Flujo del informe: el certificado vinculado habilita la evaluación práctica del nodo
                    val evaluationEnabled = node.status == "Available" && node.linkedCertificateId != null
                    if (node.status == "Available" && !evaluationEnabled) {
                        Text(
                            text = "Sube tu certificado para habilitar el quiz y el proyecto de este nodo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // T50: el quiz aparece solo cuando el certificado ya quedó vinculado al nodo
                    if (evaluationEnabled) {
                        Button(
                            onClick = { nav.navigate("Quiz") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Rendir quiz")
                        }
                    }

                    if (hasQuizResult) {
                        OutlinedButton(
                            onClick = {
                                viewModel.viewModelScope.launch(Dispatchers.IO) {
                                    val ok = viewModel.openLastResult(context, node.id)
                                    withContext(Dispatchers.Main) {
                                        if (ok) nav.navigate("QuizResult")
                                        else Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Ver último resultado del quiz")
                        }
                    }

                    // T55: el proyecto también espera al certificado vinculado
                    if (evaluationEnabled) {
                        OutlinedButton(
                            onClick = { nav.navigate("Project") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(text = "Proyecto")
                        }
                    }

                    // WF-10: con el nodo completado el Estudiante puede habilitarse como Verificador de esa habilidad
                    if (node.status == "Completed") {
                        OutlinedButton(
                            enabled = !enabling && viewModel.connectivity.isOnline,
                            onClick = {
                                enabling = true
                                viewModel.viewModelScope.launch(Dispatchers.IO) {
                                    val message = viewModel.enableAsVerifier(node.skillTag)
                                    withContext(Dispatchers.Main) {
                                        enabling = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = when {
                                    enabling -> "Habilitando…"
                                    !viewModel.connectivity.isOnline -> "Necesitas internet para habilitarte"
                                    else -> "Habilitarme como Verificador"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}

// Un paso de "Cómo completar este nodo": hecho (check verde), actual (número azul) o pendiente (gris)
@Composable
private fun CompletionStep(number: Int, text: String, done: Boolean, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(
                    when {
                        done -> Success
                        current -> SkillBlue
                        else -> NeutralBorder
                    },
                    CircleShape
                )
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Hecho",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current) Color.White else TextSecondary
                )
            }
        }
        Text(
            text = text + if (current) " · Estás aquí" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (done || current) MaterialTheme.colorScheme.onSurface else TextSecondary
        )
    }
}
