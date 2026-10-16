package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Fecha UTC (ISO-8601) del backend -> hora local del teléfono. Si no se puede leer, se muestra tal cual.
private fun localDate(iso: String?): String {
    if (iso == null) return "—"
    return try {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(iso))
    } catch (e: Exception) {
        iso
    }
}

// Tiempo que falta para el plazo, o null si ya venció o no se pudo leer.
private fun remaining(dueIso: String?): String? {
    if (dueIso == null) return null
    return try {
        val left = Duration.between(Instant.now(), Instant.parse(dueIso))
        when {
            left.isNegative || left.isZero -> null
            left.toDays() >= 1 -> "faltan ${left.toDays()} días"
            left.toHours() >= 1 -> "faltan ${left.toHours()} horas"
            else -> "faltan ${left.toMinutes()} minutos"
        }
    } catch (e: Exception) {
        null
    }
}

// T55: enunciado del proyecto, rúbrica, plazo y entrega del repositorio.
@Composable
fun ProjectDelivery(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var repositoryUrl by rememberSaveable { mutableStateOf("") }

    // Abre el proyecto del nodo (con internet puede generarlo la IA: puede tardar)
    fun open(forceNew: Boolean) {
        val nodeId = node?.id
        if (nodeId == null) {
            loading = false
            return
        }
        loading = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.openProject(context, nodeId, forceNew)
            withContext(Dispatchers.Main) {
                loading = false
                if (!ok) Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(node?.id) { open(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 50.dp, horizontal = 20.dp)
    ) {
        OfflineBanner(viewModel)

        val project = viewModel.project
        if (loading) {
            Text(text = "Preparando el proyecto… (la IA puede tardar unos segundos)")
        } else if (project == null || project.pathNodeId != node?.id) {
            Text(text = "No se pudo abrir el proyecto de este nodo.")
        } else {
            val submission = viewModel.projectSubmission
            val timeLeft = remaining(project.dueAt)
            val expired = timeLeft == null && project.dueAt != null

            Text(text = "Proyecto: ${project.skillName}", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(text = project.statement, modifier = Modifier.padding(top = 10.dp))

            Text(
                text = "Rúbrica de evaluación",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 5.dp)
            )
            project.rubric.forEach { criterion ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "${criterion.name} (peso ${criterion.weight})", fontWeight = FontWeight.Bold)
                        Text(text = criterion.description)
                    }
                }
            }
            Text(text = "Nota mínima para aprobar: ${project.passingScore} / 100")
            Text(
                text = "Plazo: ${localDate(project.dueAt)}" + (timeLeft?.let { " ($it)" } ?: ""),
                modifier = Modifier.padding(top = 5.dp)
            )

            if (submission != null) {
                // La entrega es única: se muestra lo entregado y no se puede volver a enviar
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Entregado, en evaluación", fontWeight = FontWeight.Bold)
                        Text(text = submission.repositoryUrl, modifier = Modifier.padding(top = 5.dp))
                        Text(text = "Entregado el ${localDate(submission.submittedAt)}")
                    }
                }
            } else if (expired) {
                Text(text = "El plazo de este proyecto venció.", modifier = Modifier.padding(top = 20.dp))
                Button(
                    enabled = viewModel.connectivity.isOnline,
                    onClick = { open(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = if (viewModel.connectivity.isOnline) "Generar un proyecto nuevo"
                        else "Necesitas internet para generar uno nuevo"
                    )
                }
            } else {
                OutlinedTextField(
                    value = repositoryUrl,
                    label = { Text(text = "Enlace del repositorio (https://…)") },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    onValueChange = { repositoryUrl = it }
                )
                Button(
                    enabled = !sending && viewModel.connectivity.isOnline,
                    onClick = {
                        val url = repositoryUrl.trim()
                        if (!Regex("^https?://\\S+$").matches(url) || url.length > 500) {
                            Toast.makeText(
                                context, "Escribe un enlace http o https válido (máx. 500 caracteres)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            sending = true
                            // La red va en hilo IO (como en la plantilla del profe)
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                val ok = viewModel.submitProject(context, url)
                                withContext(Dispatchers.Main) {
                                    sending = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "Proyecto entregado" else viewModel.errorMessage,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = when {
                            sending -> "Entregando…"
                            !viewModel.connectivity.isOnline -> "Necesitas internet para entregar"
                            else -> "Entregar proyecto"
                        }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Text(text = "Volver al nodo")
        }
    }

}
