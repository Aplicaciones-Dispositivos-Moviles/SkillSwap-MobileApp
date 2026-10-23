package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.VerificationCase
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.ErrorContainer
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
            "En revisión", "Un Verificador está revisando tu entrega.", SkillBlue, SkillBlueContainer
        )
        else -> CaseLook(
            "En espera de un Verificador", "Todavía no hay un Verificador asignado. Vuelve a actualizar más tarde.",
            Warning, WarningContainer
        )
    }
}

// WF-08 (mock-up 20 "Estado del caso"): estado, decisión y notas del Verificador, preguntas falladas y apelación.
@Composable
fun CaseStatus(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val caseId = viewModel.selectedCaseId
    var loading by remember { mutableStateOf(true) }
    var appealing by remember { mutableStateOf(false) }
    var confirmAppeal by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Estado del caso", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        // Solo se muestra el detalle del caso que se pidió (no uno anterior)
        val detail = viewModel.caseDetail?.takeIf { it.verificationCase.id == caseId }
        if (detail == null) {
            Text(
                text = if (loading) "Cargando el caso…" else "No se pudo cargar el caso.",
                modifier = Modifier.padding(start = 8.dp)
            )
            if (!loading) {
                OutlinedButton(
                    onClick = { load() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 16.dp)
                        .height(52.dp)
                ) {
                    Text(text = "Reintentar")
                }
            }
        } else {
            val case = detail.verificationCase
            val look = lookOf(case)

            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(text = "Caso #${case.id}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = (if (case.caseType == "MiniProject") "Miniproyecto" else "Quiz") + " · " + case.skillTag,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                StatusChip(text = look.label, color = look.color, container = look.container)
                Text(text = look.message, modifier = Modifier.padding(top = 12.dp))

                // Fechas del caso
                Text(
                    text = "Abierto el ${localDate(case.openedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (case.status != "Resolved" && case.reviewDueAt != null) {
                    Text(
                        text = "Revisión esperada antes del ${localDate(case.reviewDueAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (case.resolvedAt != null) {
                    Text(
                        text = "Resuelto el ${localDate(case.resolvedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Notas del Verificador (solo cuando ya resolvió)
                if (!case.rubricNotes.isNullOrBlank()) {
                    Text(
                        text = "Comentarios del Verificador",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NeutralSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = case.rubricNotes ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Miniproyecto: se revisa el repositorio. Quiz: se muestran las preguntas falladas.
                if (case.caseType == "MiniProject") {
                    Text(
                        text = "Repositorio entregado",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                    Text(text = case.evidenceUrl ?: "—")
                } else {
                    val failed = detail.failedQuestions
                    if (!failed.isNullOrEmpty()) {
                        Text(
                            text = "Preguntas que fallaste",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                        )
                        failed.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "${item.position}. ${item.question}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    // El backend no manda la respuesta correcta: solo se marca la elegida como incorrecta
                                    Text(
                                        text = "Tu respuesta (incorrecta): " +
                                            (item.answers.getOrNull(item.selectedAnswer) ?: "—"),
                                        color = Error,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Apelación: solo un caso rechazado y una sola vez
                if (case.appealCount >= VerificationCase.MAX_APPEALS) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SkillBlueContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                            Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = SkillBlue)
                            Text(
                                text = "Apelación enviada. Otro Verificador revisa tu caso.",
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
                if (case.canAppeal()) {
                    Button(
                        enabled = !appealing && viewModel.connectivity.isOnline,
                        onClick = { confirmAppeal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .height(52.dp)
                    ) {
                        Text(
                            text = when {
                                appealing -> "Enviando…"
                                !viewModel.connectivity.isOnline -> "Necesitas internet para apelar"
                                else -> "Apelar"
                            }
                        )
                    }
                }

                OutlinedButton(
                    enabled = !loading && viewModel.connectivity.isOnline,
                    onClick = { load() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(52.dp)
                ) {
                    Text(text = if (loading) "Actualizando…" else "Actualizar")
                }
            }
        }
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
