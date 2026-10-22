package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.ui.theme.Warning
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

// Borrador del enlace (WF-07 #06): se guarda en el teléfono por proyecto para retomarlo después.
private fun draftPrefs(context: Context) =
    context.applicationContext.getSharedPreferences("skillswap_project_drafts", Context.MODE_PRIVATE)

private fun loadDraft(context: Context, projectId: Int): String =
    draftPrefs(context).getString("project_$projectId", "") ?: ""

private fun saveDraft(context: Context, projectId: Int, url: String) {
    draftPrefs(context).edit().putString("project_$projectId", url).apply()
}

private fun clearDraft(context: Context, projectId: Int) {
    draftPrefs(context).edit().remove("project_$projectId").apply()
}

// T55 · Mock-up WF-07 #02 (miniproyecto), #03 (entrega enviada), #05 (entrega incompleta) y #06 (borrador).
@Composable
fun ProjectDelivery(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var showSent by remember { mutableStateOf(false) }

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

    // Al abrir el proyecto se recupera el borrador guardado, si hay
    val projectId = viewModel.project?.id
    LaunchedEffect(projectId) {
        if (projectId != null && repositoryUrl.isEmpty()) repositoryUrl = loadDraft(context, projectId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(
            title = if (node != null) "Miniproyecto · Nodo ${node.order}" else "Miniproyecto",
            onBack = { nav.popBackStack() }
        )
        OfflineBanner(viewModel)

        val project = viewModel.project
        if (loading) {
            Text(
                text = "Preparando el miniproyecto… (la IA puede tardar unos segundos)",
                modifier = Modifier.padding(start = 8.dp)
            )
        } else if (project == null || project.pathNodeId != node?.id) {
            Text(text = "No se pudo abrir el miniproyecto de este nodo.", modifier = Modifier.padding(start = 8.dp))
        } else {
            val submission = viewModel.projectSubmission
            val timeLeft = remaining(project.dueAt)
            val expired = timeLeft == null && project.dueAt != null

            Column(modifier = Modifier.padding(start = 8.dp)) {
                // Todo lo generado por IA va en violeta
                StatusChip(text = "Enunciado generado por IA", color = AiViolet, container = AiContainer)
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = if (expired) "El plazo venció el ${localDate(project.dueAt)}"
                    else "Entrega hasta el ${localDate(project.dueAt)}" + (timeLeft?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = Warning,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Enunciado
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeutralSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = project.statement,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Rúbrica
                Text(
                    text = "Criterios de evaluación",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = "Nota mínima para aprobar: ${project.passingScore} / 100",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                project.rubric.forEach { criterion ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = SkillBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "${criterion.name} · peso ${criterion.weight}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = criterion.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (submission != null) {
                    // La entrega es única: se muestra lo entregado y no se puede volver a enviar
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SuccessContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Success)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = "Entregado, en evaluación",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Success
                                )
                                Text(text = submission.repositoryUrl, modifier = Modifier.padding(top = 5.dp))
                                Text(
                                    text = "Entregado el ${localDate(submission.submittedAt)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else if (expired) {
                    Button(
                        enabled = viewModel.connectivity.isOnline,
                        onClick = { open(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (viewModel.connectivity.isOnline) "Generar un miniproyecto nuevo"
                            else "Necesitas internet para generar uno nuevo"
                        )
                    }
                } else {
                    Text(
                        text = "Enlace a tu repositorio",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = repositoryUrl,
                        placeholder = { Text(text = "https://github.com/usuario/proyecto") },
                        singleLine = true,
                        enabled = !sending,
                        isError = urlError != null,
                        supportingText = { urlError?.let { Text(text = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = {
                            repositoryUrl = it
                            urlError = null
                        }
                    )

                    // WF-07 #06: guardar el enlace y retomarlo después (hasta el plazo)
                    TextButton(
                        enabled = !sending && repositoryUrl.isNotBlank(),
                        onClick = {
                            saveDraft(context, project.id, repositoryUrl.trim())
                            Toast.makeText(
                                context,
                                "Guardamos tu borrador. Puedes retomarlo hasta el ${localDate(project.dueAt)}",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(text = "Guardar borrador")
                    }

                    Button(
                        enabled = !sending && viewModel.connectivity.isOnline,
                        onClick = {
                            val url = repositoryUrl.trim()
                            // WF-07 #05: sin enlace válido no se envía y el error se muestra en el campo
                            urlError = when {
                                url.isEmpty() -> "Agrega el enlace a tu repositorio para enviar la entrega"
                                !Regex("^https?://\\S+$").matches(url) || url.length > 500 ->
                                    "Escribe un enlace http o https válido (máx. 500 caracteres)"
                                else -> null
                            }
                            if (urlError == null) {
                                sending = true
                                // La red va en hilo IO (como en la plantilla del profe)
                                viewModel.viewModelScope.launch(Dispatchers.IO) {
                                    val ok = viewModel.submitProject(context, url)
                                    withContext(Dispatchers.Main) {
                                        sending = false
                                        if (ok) {
                                            clearDraft(context, project.id)
                                            showSent = true
                                        } else {
                                            Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = when {
                                sending -> "Enviando…"
                                !viewModel.connectivity.isOnline -> "Necesitas internet para enviar"
                                else -> "Enviar entrega"
                            }
                        )
                    }
                }
            }
        }

    }

    // WF-07 #03: confirmación de la entrega
    if (showSent) {
        AlertDialog(
            onDismissRequest = { showSent = false },
            icon = { Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Success) },
            title = { Text(text = "Entrega enviada") },
            text = {
                Text(
                    text = "La IA hará una evaluación preliminar. Si hace falta, un Verificador la revisará con la rúbrica."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSent = false }) {
                    Text(text = "Entendido")
                }
            }
        )
    }

}
