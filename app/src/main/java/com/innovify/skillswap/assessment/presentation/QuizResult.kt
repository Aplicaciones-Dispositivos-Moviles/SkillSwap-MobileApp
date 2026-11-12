package com.innovify.skillswap.assessment.presentation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.innovify.skillswap.assessment.application.QuizViewModel
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.components.StatusChip
import com.innovify.skillswap.shared.presentation.design.CardBorder
import com.innovify.skillswap.shared.presentation.design.FlowHeadline
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.InfoCard
import com.innovify.skillswap.shared.presentation.design.OutlinedSurface
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.design.SecondaryActionButton
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.ErrorContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.SuccessContainer
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.shared.presentation.theme.Warning
import com.innovify.skillswap.shared.presentation.theme.WarningContainer

// T53 · Mock-up 12 / WF-06 #03 (quiz aprobado) y #05 (quiz no aprobado): resultado con el sub-tema a reforzar.
@Composable
fun QuizResult(
    viewModel: QuizViewModel,
    verificationCaseViewModel: VerificationCaseViewModel,
    context: Context,
    nav: NavHostController
) {

    val result = viewModel.attemptResult
    val quiz = viewModel.blueprint

    FlowScaffold(
        // El mock-up usa una X: cerrar el resultado lleva de vuelta a la ruta
        topBar = { FlowTopBar(title = "Resultado del quiz", close = true, onBack = { nav.popBackStack("Path", false) }) },
        bottomBar = {
            // Acciones: aprobado -> sigue con la ruta; no aprobado -> vuelve al nodo a repasar o a la ruta
            if (result?.passed == true) {
                PrimaryActionButton(text = "Continuar con mi ruta", onClick = { nav.popBackStack("Path", false) })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryActionButton(
                        text = "Volver a la ruta",
                        onClick = { nav.popBackStack("Path", false) },
                        modifier = Modifier.weight(1f)
                    )
                    // El Quiz ya salió de la pila: volver atrás deja al Estudiante en el detalle del nodo
                    PrimaryActionButton(
                        text = "Repasar sub-tema",
                        onClick = { nav.popBackStack() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        if (result == null) {
            Text(text = "Aún no hay un resultado para mostrar.", style = MaterialTheme.typography.bodyLarge)
            return@FlowScaffold
        }

        val percent = if (result.totalQuestions > 0) result.score * 100 / result.totalQuestions else 0
        val tone = if (result.passed) Success else Error

        // Encabezado: ícono + título + explicación (estado con ícono y texto, nunca solo color)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            ResultIcon(
                icon = if (result.passed) Icons.Filled.CheckCircle else FlowIcons.Flag,
                tint = if (result.passed) Success else Warning,
                container = if (result.passed) SuccessContainer else WarningContainer,
                description = if (result.passed) "Nodo aprobado" else "Nodo no aprobado"
            )
            FlowHeadline(
                title = if (result.passed) "¡Nodo aprobado!" else "Aún no alcanzas el mínimo",
                subtitle = if (result.passed) {
                    "Demostraste tu dominio en ${quiz?.skillName ?: "este nodo"}. " +
                        "Se desbloqueó el siguiente nodo de tu ruta."
                } else if (result.weakSubTopic != null) {
                    "Ya identificamos el sub-tema que debes reforzar para tu próximo intento."
                } else {
                    "Repasa el contenido del nodo antes de tu próximo intento."
                },
                center = true,
                modifier = Modifier.padding(top = 20.dp)
            )
        }

        // Resumen del intento en tres cifras separadas por líneas
        OutlinedSurface(modifier = Modifier.padding(top = 24.dp)) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                ResultStat("${result.score}/${result.totalQuestions}", "Respuestas correctas", Modifier.weight(1f))
                VerticalDivider(color = CardBorder, modifier = Modifier.fillMaxHeight())
                ResultStat("$percent%", "Puntaje", Modifier.weight(1f), color = tone)
                VerticalDivider(color = CardBorder, modifier = Modifier.fillMaxHeight())
                ResultStat("${result.totalQuestions - result.score}", "Respuestas incorrectas", Modifier.weight(1f))
            }
        }

        // Sub-tema más débil: en rojo si no aprobó, como sugerencia si aprobó pero aún falló alguna
        result.weakSubTopic?.let { weak ->
            if (result.passed) {
                InfoCard(
                    icon = Icons.Filled.Info,
                    body = "Refuerza \"$weak\": fue el sub-tema donde más fallaste.",
                    tint = Warning,
                    container = WarningContainer,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                SectionHeader(title = "Dónde necesitas reforzar", modifier = Modifier.padding(top = 8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorContainer.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(text = "SUB-TEMA MÁS DÉBIL", style = MaterialTheme.typography.labelMedium, color = Error)
                    Text(
                        text = weak,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Fue el sub-tema donde más preguntas fallaste. Repásalo antes de volver a rendir el quiz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Solo aparece cuando el backend entregue las preguntas falladas al Estudiante
        val failed = result.failedQuestions
        if (!failed.isNullOrEmpty()) {
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
                            text = "Tu respuesta: " + (item.answers.getOrNull(item.selectedAnswer) ?: "—"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
        }

        // Un intento fallido abre un caso de verificación (o agota las escalaciones del plan)
        result.verificationCaseId?.let { caseId ->
            NoticeCard(
                text = "Abrimos un caso: un Verificador revisará tu intento.",
                icon = FlowIcons.Verified,
                tint = SkillBlueDark,
                container = SkillBlueContainer,
                status = result.verificationCaseStatus,
                // WF-08: desde aquí el Estudiante ve el estado del caso y las preguntas que falló
                actionLabel = "Ver estado del caso",
                onAction = {
                    verificationCaseViewModel.selectedCaseId = caseId
                    nav.navigate("CaseStatus")
                }
            )
        }
        result.planLimitReached?.let { limit ->
            NoticeCard(
                text = "Alcanzaste el límite de tu plan ${limit.plan}: usaste ${limit.current} de " +
                    "${limit.max} escalaciones este mes, por eso no se abrió un caso de verificación.",
                icon = Icons.Filled.Info,
                tint = Warning,
                container = WarningContainer
            )
        }
    }
}

// Ícono del resultado dentro de un círculo de color suave
@Composable
private fun ResultIcon(icon: ImageVector, tint: Color, container: Color, description: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .background(container, CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(40.dp))
    }
}

// Una cifra del resumen con su etiqueta debajo
@Composable
private fun ResultStat(value: String, label: String, modifier: Modifier, color: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(horizontal = 4.dp)) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// Aviso con ícono (caso abierto o límite del plan); puede llevar el estado del caso y un enlace de acción
@Composable
private fun NoticeCard(
    text: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    status: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    InfoCard(
        icon = icon,
        body = text,
        tint = tint,
        container = container,
        modifier = Modifier.padding(top = 16.dp)
    ) {
        // Estado del caso con color: pendiente en amarillo, en revisión en azul, resuelto en verde
        val look = when (status) {
            "Pending" -> Triple("Pendiente", Warning, WarningContainer)
            "Assigned" -> Triple("En revisión", SkillBlue, Color.White)
            "Resolved" -> Triple("Resuelto", Success, SuccessContainer)
            else -> null
        }
        if (look != null) {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                StatusChip(text = look.first, color = look.second, container = look.third)
            }
        }
        if (actionLabel != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .heightIn(min = 48.dp)
            ) {
                Text(text = actionLabel, color = SkillBlueDark)
            }
        }
    }
}
