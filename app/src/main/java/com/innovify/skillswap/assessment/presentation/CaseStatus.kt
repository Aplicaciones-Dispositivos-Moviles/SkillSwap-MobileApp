package com.innovify.skillswap.assessment.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.assessment.domain.VerificationCase
import com.innovify.skillswap.assessment.domain.VerificationCaseDetail
import com.innovify.skillswap.assessment.domain.isHttpUrl
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.components.localDate
import com.innovify.skillswap.shared.presentation.design.CardBorder
import com.innovify.skillswap.shared.presentation.design.FlowChip
import com.innovify.skillswap.shared.presentation.design.FlowHeadline
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowLoading
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.IconBadge
import com.innovify.skillswap.shared.presentation.design.InfoCard
import com.innovify.skillswap.shared.presentation.design.OutlinedSurface
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.design.ProgressStep
import com.innovify.skillswap.shared.presentation.design.SecondaryActionButton
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.design.StepStatus
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.ErrorContainer
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

/**
 * Status of the four timeline steps of a case (Figma 20):
 * case opened, verifier assigned, review with the rubric, decision (Failed when rejected).
 */
fun caseTimeline(case: VerificationCase): List<StepStatus> {
    val resolved = case.status == "Resolved"
    val assigned = when {
        case.status == "Pending" -> StepStatus.Active
        else -> StepStatus.Done
    }
    val review = when {
        resolved -> StepStatus.Done
        case.status == "Assigned" -> StepStatus.Active
        else -> StepStatus.Pending
    }
    val decision = when {
        resolved && case.decision == "Approved" -> StepStatus.Done
        resolved -> StepStatus.Failed
        else -> StepStatus.Pending
    }
    return listOf(StepStatus.Done, assigned, review, decision)
}

// Texto y colores del estado del caso (nunca solo color: el chip siempre lleva texto)
private class CaseLook(val label: String, val message: String, val color: Color, val container: Color)

private fun lookOf(case: VerificationCase): CaseLook {
    return when {
        case.status == "Resolved" && case.decision == "Approved" -> CaseLook(
            "Aprobado", "Un Verificador aprobó tu entrega: la habilidad quedó demostrada.",
            Success, SuccessContainer
        )
        case.status == "Resolved" -> CaseLook(
            "Rechazado",
            if (case.canAppeal()) "Un Verificador no aprobó tu entrega. Puedes apelar una vez: otro Verificador la revisará."
            else "Un Verificador no aprobó tu entrega. Ya usaste tu apelación.",
            Error, ErrorContainer
        )
        case.status == "Assigned" -> CaseLook(
            "En revisión", "Un Verificador está revisando tu entrega.", SkillBlueDark, SkillBlueContainer
        )
        else -> CaseLook(
            "En espera de un Verificador", "Todavía no hay un Verificador asignado. Vuelve a actualizar más tarde.",
            Warning, WarningContainer
        )
    }
}

// WF-08 (mock-up 20 "Estado del caso"): estado, línea de tiempo, notas del Verificador, preguntas falladas,
// evidencia adjunta y apelación.
@Composable
fun CaseStatus(viewModel: VerificationCaseViewModel, context: Context, nav: NavHostController) {

    val caseId = viewModel.selectedCaseId
    var loading by remember { mutableStateOf(true) }
    var appealing by remember { mutableStateOf(false) }
    var confirmAppeal by remember { mutableStateOf(false) }
    var showEvidence by remember { mutableStateOf(false) }
    var evidenceUrl by remember { mutableStateOf("") }
    var evidenceError by remember { mutableStateOf<String?>(null) }
    var attaching by remember { mutableStateOf(false) }

    // Lee el caso del backend (la red va en hilo IO, como en la plantilla del profe)
    fun load() {
        if (caseId == null) {
            loading = false
            return
        }
        loading = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadCase(caseId)
            withContext(Dispatchers.Main) {
                loading = false
                if (!ok) Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(caseId) { load() }

    // Solo se muestra el detalle del caso que se pidió (no uno anterior)
    val detail = viewModel.caseDetail?.takeIf { it.verificationCase.id == caseId }
    val case = detail?.verificationCase
    val online = viewModel.connectivity.isOnline

    FlowScaffold(
        topBar = {
            FlowTopBar(
                title = if (case != null) "Caso #${case.id}" else "Estado del caso",
                onBack = { nav.popBackStack() }
            ) {
                // Actualizar: vuelve a leer el caso
                if (loading && case != null) {
                    CircularProgressIndicator(
                        color = SkillBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(20.dp)
                    )
                } else if (case != null) {
                    IconButton(onClick = { load() }, enabled = online) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = TextPrimary)
                    }
                }
            }
        },
        bottomBar = when {
            case == null -> null
            // Apelación: solo un caso rechazado y una sola vez
            case.canAppeal() -> {
                {
                    PrimaryActionButton(
                        text = when {
                            appealing -> "Enviando…"
                            !online -> "Necesitas internet para apelar"
                            else -> "Apelar"
                        },
                        enabled = !appealing && online,
                        loading = appealing,
                        onClick = { confirmAppeal = true }
                    )
                }
            }
            // US26: evidencia adicional (repositorio o portafolio) mientras el caso siga abierto
            case.status != "Resolved" -> {
                {
                    PrimaryActionButton(
                        text = if (!online) "Necesitas internet para adjuntar" else "Agregar evidencia",
                        enabled = !attaching && online,
                        loading = attaching,
                        icon = Icons.Filled.Add,
                        onClick = {
                            evidenceError = null
                            showEvidence = true
                        }
                    )
                }
            }
            else -> null
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        if (detail == null || case == null) {
            if (loading) {
                FlowLoading("Cargando el caso…")
            } else {
                Text(text = "No se pudo cargar el caso.", style = MaterialTheme.typography.bodyLarge)
                SecondaryActionButton(
                    text = "Reintentar",
                    onClick = { load() },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            return@FlowScaffold
        }

        val look = lookOf(case)
        val typeLabel = if (case.caseType == "MiniProject") "Miniproyecto" else "Quiz"
        FlowChip(text = look.label, color = look.color, container = look.container)
        FlowHeadline(
            title = look.message,
            subtitle = "$typeLabel · ${case.skillTag}",
            modifier = Modifier.padding(top = 12.dp)
        )

        // Línea de tiempo del caso con las fechas que manda el backend
        val steps = caseTimeline(case)
        val attempt = detail.attempt
        Column(modifier = Modifier.padding(top = 24.dp)) {
            ProgressStep(
                status = steps[0],
                title = "Caso abierto",
                detail = "Abierto el ${localDate(case.openedAt)}" +
                    (attempt?.let { " · quiz no aprobado (${it.score}/${it.totalQuestions})" } ?: ""),
                connector = true
            )
            ProgressStep(
                status = steps[1],
                title = "Verificador asignado",
                detail = if (steps[1] == StepStatus.Active) "Esperando a un Verificador"
                else (case.assignedAt?.let { localDate(it) + " · " } ?: "") + "su identidad es anónima",
                connector = true
            )
            ProgressStep(
                status = steps[2],
                title = "En revisión con rúbrica",
                detail = when {
                    case.status == "Resolved" -> "Revisión terminada"
                    case.reviewDueAt != null -> "Revisión esperada antes del ${localDate(case.reviewDueAt)}"
                    else -> "Pendiente"
                },
                connector = true
            )
            ProgressStep(
                status = steps[3],
                title = "Decisión",
                detail = when (steps[3]) {
                    StepStatus.Done -> "Aprobado" + (case.resolvedAt?.let { " · Resuelto el ${localDate(it)}" } ?: "")
                    StepStatus.Failed -> "Rechazado" + (case.resolvedAt?.let { " · Resuelto el ${localDate(it)}" } ?: "")
                    else -> "Aprobado, o rechazado con observaciones"
                },
                connector = true,
                isLast = true
            )
        }

        // Notas del Verificador (solo cuando ya resolvió)
        if (!case.rubricNotes.isNullOrBlank()) {
            SectionHeader(title = "Comentarios del Verificador", modifier = Modifier.padding(top = 8.dp))
            InfoCard(icon = Icons.Filled.Info, body = case.rubricNotes ?: "", container = NeutralSurface)
        }

        // Quiz: se muestran las preguntas falladas (el backend no manda la respuesta correcta)
        val failed = detail.failedQuestions
        if (case.caseType != "MiniProject" && !failed.isNullOrEmpty()) {
            SectionHeader(title = "Preguntas que fallaste", modifier = Modifier.padding(top = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                failed.forEach { item ->
                    OutlinedSurface {
                        Text(
                            text = "${item.position}. ${item.question}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tu respuesta (incorrecta): " + (item.answers.getOrNull(item.selectedAnswer) ?: "—"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Error,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
        }

        EvidenceCard(detail)
        if (case.status != "Resolved") {
            Text(
                text = "Puedes agregar evidencia hasta que el Verificador emita su decisión.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (case.appealCount >= VerificationCase.MAX_APPEALS) {
            InfoCard(
                icon = Icons.Filled.Info,
                body = "Apelación enviada. Otro Verificador revisa tu caso.",
                container = SkillBlueContainer,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    // US26: enlace de evidencia adicional (repositorio o portafolio)
    if (showEvidence && case != null && caseId != null) {
        AlertDialog(
            onDismissRequest = { if (!attaching) showEvidence = false },
            title = { Text(text = "Agregar evidencia") },
            text = {
                Column {
                    Text(
                        text = if (case.caseType == "MiniProject")
                            "Pega el enlace de un repositorio o portafolio. Reemplaza el enlace entregado."
                        else "Pega el enlace de un repositorio o portafolio que respalde tu entrega.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = evidenceUrl,
                        label = { Text(text = "https://…") },
                        singleLine = true,
                        enabled = !attaching,
                        isError = evidenceError != null,
                        supportingText = { if (evidenceError != null) Text(text = evidenceError ?: "") },
                        onValueChange = {
                            evidenceUrl = it
                            evidenceError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !attaching && online,
                    onClick = {
                        if (!isHttpUrl(evidenceUrl)) {
                            evidenceError = "El enlace debe empezar con http:// o https://"
                        } else {
                            attaching = true
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                val ok = viewModel.attachEvidence(caseId, evidenceUrl)
                                withContext(Dispatchers.Main) {
                                    attaching = false
                                    if (ok) {
                                        evidenceUrl = ""
                                        showEvidence = false
                                    }
                                    Toast.makeText(
                                        context,
                                        if (ok) "Evidencia adjuntada" else viewModel.errorMessage,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = when {
                            attaching -> "Adjuntando…"
                            !online -> "Necesitas internet para adjuntar"
                            else -> "Adjuntar enlace"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !attaching, onClick = { showEvidence = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    // Confirmación: la apelación solo se puede usar una vez
    if (confirmAppeal && caseId != null) {
        AlertDialog(
            onDismissRequest = { confirmAppeal = false },
            title = { Text(text = "¿Apelar este caso?") },
            text = { Text(text = "Solo puedes apelar una vez. Otro Verificador revisará tu entrega.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAppeal = false
                    appealing = true
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val ok = viewModel.appealCase(caseId)
                        withContext(Dispatchers.Main) {
                            appealing = false
                            Toast.makeText(
                                context,
                                if (ok) "Apelación enviada" else viewModel.errorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) {
                    Text(text = "Apelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAppeal = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }
}

// "Evidencia adjunta": el intento del quiz (automático) y el enlace del repositorio o portafolio
@Composable
private fun EvidenceCard(detail: VerificationCaseDetail) {
    val case = detail.verificationCase
    val attempt = detail.attempt
    val url = case.evidenceUrl?.takeIf { it.isNotBlank() }
    SectionHeader(title = "Evidencia adjunta", modifier = Modifier.padding(top = 8.dp))
    OutlinedSurface {
        var rows = 0
        if (case.caseType != "MiniProject" && attempt != null) {
            EvidenceRow(
                icon = FlowIcons.Document,
                title = "Intento del quiz · ${attempt.score}/${attempt.totalQuestions}",
                detail = "Adjuntado automáticamente · ${localDate(attempt.completedAt)}"
            )
            rows++
        }
        if (url != null) {
            if (rows > 0) HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 12.dp))
            EvidenceRow(
                icon = FlowIcons.Link,
                title = url,
                detail = if (case.caseType == "MiniProject") "Repositorio entregado" else "Adjunto actual",
                link = true
            )
            rows++
        }
        if (rows == 0) {
            Text(
                text = if (case.caseType == "MiniProject") "Repositorio entregado: —" else "Aún no hay evidencia adjunta.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EvidenceRow(icon: ImageVector, title: String, detail: String, link: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = icon, tint = if (link) SkillBlueDark else TextPrimary, container = if (link) SkillBlueContainer else NeutralSurface, size = 36)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (link) SkillBlueDark else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
