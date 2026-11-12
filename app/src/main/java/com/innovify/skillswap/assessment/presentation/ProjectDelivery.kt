package com.innovify.skillswap.assessment.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.assessment.application.MiniProjectViewModel
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.assessment.domain.MiniProjectSubmission
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.components.localDate
import com.innovify.skillswap.shared.presentation.design.FlowChip
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowLoading
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.design.SecondaryActionButton
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.theme.AiContainer
import com.innovify.skillswap.shared.presentation.theme.AiViolet
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.SuccessContainer
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.shared.presentation.theme.Warning
import com.innovify.skillswap.shared.presentation.theme.WarningContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

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

// T55 · Mock-up 19 / WF-07 #02 (miniproyecto), #03 (entrega enviada), #05 (entrega incompleta) y #06 (borrador).
@Composable
fun ProjectDelivery(
    viewModel: MiniProjectViewModel,
    learningPathViewModel: LearningPathViewModel,
    verificationCaseViewModel: VerificationCaseViewModel,
    context: Context,
    nav: NavHostController
) {

    val node = learningPathViewModel.selectedNode
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var showSent by remember { mutableStateOf(false) }
    // T56: true si se acabó el tiempo de espera del resultado; evalRetry reinicia la espera con "Actualizar"
    var evalTimedOut by remember { mutableStateOf(false) }
    var evalRetry by remember { mutableIntStateOf(0) }

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

    // T56: la IA evalúa la entrega en segundo plano. Mientras siga Pending se consulta el backend
    // (cada 3 s, máx. ~90 s); la espera se cancela sola al salir de la pantalla.
    val currentSubmission = viewModel.projectSubmission
    val evaluating = currentSubmission?.isEvaluating() == true
    LaunchedEffect(currentSubmission?.id, evaluating, viewModel.connectivity.isOnline, evalRetry) {
        if (evaluating && viewModel.connectivity.isOnline) {
            evalTimedOut = false
            val done = withContext(Dispatchers.IO) { viewModel.waitForEvaluation(context) }
            evalTimedOut = !done
        }
    }

    val online = viewModel.connectivity.isOnline
    val project = viewModel.project?.takeIf { !loading && it.pathNodeId == node?.id }
    val submission = viewModel.projectSubmission
    val timeLeft = remaining(project?.dueAt)
    val expired = project != null && timeLeft == null && project.dueAt != null

    // Envía el enlace del repositorio (WF-07 #05: sin enlace válido no se envía y el error se muestra en el campo)
    fun send(projectIdToClear: Int) {
        val url = repositoryUrl.trim()
        urlError = when {
            url.isEmpty() -> "Agrega el enlace a tu repositorio para enviar la entrega"
            !Regex("^https?://\\S+$").matches(url) || url.length > 500 ->
                "Escribe un enlace http o https válido (máx. 500 caracteres)"
            else -> null
        }
        if (urlError != null) return
        sending = true
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.submitProject(context, url)
            withContext(Dispatchers.Main) {
                sending = false
                if (ok) {
                    clearDraft(context, projectIdToClear)
                    showSent = true
                } else {
                    Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    FlowScaffold(
        topBar = {
            FlowTopBar(
                title = if (node != null) "Miniproyecto · Nodo ${node.order}" else "Miniproyecto",
                onBack = { nav.popBackStack() }
            )
        },
        bottomBar = when {
            project == null -> null
            submission != null -> submissionActions(
                submission = submission,
                timedOut = evalTimedOut,
                online = online,
                onRefresh = {
                    evalTimedOut = false
                    evalRetry += 1
                },
                onViewCase = { caseId ->
                    verificationCaseViewModel.selectedCaseId = caseId
                    nav.navigate("CaseStatus")
                },
                onContinue = { nav.popBackStack("Path", false) }
            )
            expired -> {
                {
                    PrimaryActionButton(
                        text = if (online) "Generar un miniproyecto nuevo"
                        else "Necesitas internet para generar uno nuevo",
                        enabled = online,
                        icon = FlowIcons.Sparkle,
                        onClick = { open(true) }
                    )
                }
            }
            else -> {
                {
                    PrimaryActionButton(
                        text = when {
                            sending -> "Enviando…"
                            !online -> "Necesitas internet para enviar"
                            else -> "Enviar entrega"
                        },
                        enabled = !sending && online,
                        loading = sending,
                        onClick = { send(project.id) }
                    )
                }
            }
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        if (loading) {
            FlowLoading("Preparando el miniproyecto… (la IA puede tardar unos segundos)")
            return@FlowScaffold
        }
        if (project == null) {
            Text(text = "No se pudo abrir el miniproyecto de este nodo.", style = MaterialTheme.typography.bodyLarge)
            return@FlowScaffold
        }

        // Todo lo generado por IA va en violeta
        FlowChip(text = "Enunciado generado por IA", color = AiViolet, container = AiContainer, icon = FlowIcons.Sparkle)
        Text(
            text = project.title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                tint = if (expired) Error else Warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (expired) "El plazo venció el ${localDate(project.dueAt)}"
                else "Entrega hasta el ${localDate(project.dueAt)}" + (timeLeft?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = if (expired) Error else Warning,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        // Enunciado
        Text(
            text = project.statement,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .background(NeutralSurface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        )

        // Rúbrica
        SectionHeader(title = "Criterios de evaluación", modifier = Modifier.padding(top = 8.dp))
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
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
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
            // La entrega es única: se muestra lo entregado y el resultado de la evaluación con IA (T56)
            SubmissionResult(submission = submission, timedOut = evalTimedOut, online = online)
        } else if (!expired) {
            SectionHeader(title = "Enlace a tu repositorio", modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = repositoryUrl,
                placeholder = { Text(text = "https://github.com/usuario/proyecto") },
                leadingIcon = { Icon(FlowIcons.Link, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                enabled = !sending,
                isError = urlError != null,
                supportingText = { urlError?.let { Text(text = it) } },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
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
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp)
            ) {
                Text(text = "Guardar borrador", color = SkillBlueDark)
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

// T56 · Resultado de la entrega: Pending (la IA evalúa), Approved, Escalated o EscalationLimitReached.
@Composable
private fun SubmissionResult(submission: MiniProjectSubmission, timedOut: Boolean, online: Boolean) {
    val look = when (submission.evaluationStatus) {
        "Approved" -> Triple("Proyecto aprobado", Success, SuccessContainer)
        "Escalated" -> Triple("Un Verificador revisará tu entrega", SkillBlueDark, SkillBlueContainer)
        "EscalationLimitReached" -> Triple("Entrega recibida, sin revisión de un Verificador", Warning, WarningContainer)
        else -> Triple("Entregado, en evaluación", AiViolet, AiContainer)
    }
    val color = look.second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .background(look.third, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Icon(
            imageVector = if (submission.evaluationStatus == "Approved") Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = null,
            tint = color
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text(text = look.first, style = MaterialTheme.typography.titleMedium, color = color)
            Text(text = submission.repositoryUrl, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = "Entregado el ${localDate(submission.submittedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            val body = Modifier.padding(top = 10.dp)
            when (submission.evaluationStatus) {
                "Approved" -> Text(
                    text = "La IA calificó tu entrega con ${submission.preliminaryScore ?: "—"} / 100 y el nodo quedó completado.",
                    color = TextPrimary,
                    modifier = body
                )
                "Escalated" -> Text(
                    text = "La IA calificó tu entrega con ${submission.preliminaryScore ?: "—"} / 100, " +
                        "pero no pudo aprobarla por sí sola: un Verificador la revisa con la rúbrica.",
                    color = TextPrimary,
                    modifier = body
                )
                "EscalationLimitReached" -> Text(
                    text = "Usaste todas las escalaciones de tu plan este mes, por eso no se abrió un caso de " +
                        "verificación. Mejora tu plan para que un Verificador la revise.",
                    color = TextPrimary,
                    modifier = body
                )
                else -> if (!online) {
                    Text(text = "Sin conexión: el resultado se mostrará cuando vuelva internet.", color = TextPrimary, modifier = body)
                } else if (timedOut) {
                    Text(text = "La evaluación está tardando más de lo normal.", color = TextPrimary, modifier = body)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = body) {
                        CircularProgressIndicator(color = AiViolet, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "La IA está evaluando tu entrega…",
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            // Lo que la IA sugiere mejorar (todo lo generado por IA va en violeta)
            if (!submission.preliminaryFeedback.isNullOrBlank()) {
                Text(
                    text = "Sugerencias de la IA",
                    style = MaterialTheme.typography.labelLarge,
                    color = AiViolet,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(text = submission.preliminaryFeedback ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}

// Acciones fijas abajo según el resultado de la entrega (null si no hay ninguna)
private fun submissionActions(
    submission: MiniProjectSubmission,
    timedOut: Boolean,
    online: Boolean,
    onRefresh: () -> Unit,
    onViewCase: (Int) -> Unit,
    onContinue: () -> Unit
): (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? {
    val caseId = submission.verificationCaseId
    return when {
        submission.evaluationStatus == "Approved" -> {
            { PrimaryActionButton(text = "Continuar con mi ruta", onClick = onContinue) }
        }
        submission.evaluationStatus == "Escalated" && caseId != null -> {
            { PrimaryActionButton(text = "Ver estado del caso", onClick = { onViewCase(caseId) }) }
        }
        submission.isEvaluating() && online && timedOut -> {
            { SecondaryActionButton(text = "Actualizar", onClick = onRefresh) }
        }
        else -> null
    }
}
