package com.innovify.skillswap.views

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.ErrorContainer
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.ui.theme.Warning
import com.innovify.skillswap.ui.theme.WarningContainer
import com.innovify.skillswap.viewmodel.SkillSwapViewModel

// T53 · Mock-up WF-06 #03 (quiz aprobado) y #05 (quiz no aprobado): resultado con el sub-tema a reforzar.
@Composable
fun QuizResult(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val result = viewModel.attemptResult
    val quiz = viewModel.blueprint

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        // El mock-up usa una X: cerrar el resultado lleva de vuelta a la ruta
        IconButton(onClick = { nav.popBackStack("Path", false) }) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
        }
        OfflineBanner(viewModel)

        if (result == null) {
            Text(
                text = "Aún no hay un resultado para mostrar.",
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            val percent = if (result.totalQuestions > 0) result.score * 100 / result.totalQuestions else 0
            val tone = if (result.passed) Success else Warning

            // Encabezado: ícono + título + explicación (estado con ícono y texto, nunca solo color)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp)
            ) {
                ResultIcon(
                    icon = if (result.passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    tint = tone,
                    container = if (result.passed) SuccessContainer else WarningContainer,
                    description = if (result.passed) "Nodo aprobado" else "Nodo no aprobado"
                )
                Text(
                    text = if (result.passed) "¡Nodo aprobado!" else "Aún no alcanzas el mínimo",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = if (result.passed) {
                        "Demostraste tu dominio en ${quiz?.skillName ?: "este nodo"}. " +
                            "Se desbloqueó el siguiente nodo de tu ruta."
                    } else if (result.weakSubTopic != null) {
                        "Ya identificamos el sub-tema que debes reforzar para tu próximo intento."
                    } else {
                        "Repasa el contenido del nodo antes de tu próximo intento."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Resumen del intento en tres cifras
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeutralBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 24.dp)
            ) {
                Row(modifier = Modifier.padding(vertical = 16.dp)) {
                    ResultStat("${result.score}/${result.totalQuestions}", "Respuestas correctas", Modifier.weight(1f))
                    ResultStat("$percent%", "Puntaje", Modifier.weight(1f), color = tone)
                    ResultStat(
                        "${result.totalQuestions - result.score}", "Respuestas incorrectas", Modifier.weight(1f)
                    )
                }
            }

            // Sub-tema más débil: en rojo si no aprobó, como sugerencia si aprobó pero aún falló alguna
            result.weakSubTopic?.let { weak ->
                if (result.passed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = Warning)
                            Text(
                                text = "Refuerza \"$weak\": fue el sub-tema donde más fallaste.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Dónde necesitas reforzar",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                        border = BorderStroke(1.dp, Error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "SUB-TEMA MÁS DÉBIL",
                                style = MaterialTheme.typography.labelMedium,
                                color = Error
                            )
                            Text(
                                text = weak,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "Fue el sub-tema donde más preguntas fallaste. Repásalo antes de volver a rendir el quiz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Solo aparece cuando el backend entregue las preguntas falladas al Estudiante
            val failed = result.failedQuestions
            if (!failed.isNullOrEmpty()) {
                Text(
                    text = "Preguntas que fallaste",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)
                )
                failed.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${item.position}. ${item.question}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Tu respuesta: " + (item.answers.getOrNull(item.selectedAnswer) ?: "—"),
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }

            // Un intento fallido abre un caso de verificación (o agota las escalaciones del plan)
            result.verificationCaseId?.let { caseId ->
                NoticeCard(
                    text = "Abrimos el caso #$caseId: un Verificador revisará tu intento" +
                        (result.verificationCaseStatus?.let { " (estado: $it)." } ?: "."),
                    tint = SkillBlue,
                    container = SkillBlueContainer
                )
            }
            result.planLimitReached?.let { limit ->
                NoticeCard(
                    text = "Alcanzaste el límite de tu plan ${limit.plan}: usaste ${limit.current} de " +
                        "${limit.max} escalaciones este mes, por eso no se abrió un caso de verificación.",
                    tint = Warning,
                    container = WarningContainer
                )
            }
        }

        // Acciones: aprobado -> sigue con la ruta; no aprobado -> vuelve al nodo a repasar o a la ruta
        if (result?.passed == true) {
            Button(
                onClick = { nav.popBackStack("Path", false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 24.dp)
                    .height(52.dp)
            ) {
                Text(text = "Continuar con mi ruta")
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 24.dp)
            ) {
                OutlinedButton(
                    onClick = { nav.popBackStack("Path", false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(text = "Volver a la ruta")
                }
                // El Quiz ya salió de la pila: volver atrás deja al Estudiante en el detalle del nodo
                Button(
                    onClick = { nav.popBackStack() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(text = "Repasar sub-tema")
                }
            }
        }
    }

}

// Ícono del resultado dentro de un círculo de color suave
@Composable
private fun ResultIcon(icon: ImageVector, tint: Color, container: Color, description: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .background(container, CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(40.dp))
    }
}

// Una cifra del resumen con su etiqueta debajo
@Composable
private fun ResultStat(value: String, label: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// Aviso con ícono de información (caso abierto o límite del plan)
@Composable
private fun NoticeCard(text: String, tint: Color, container: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = tint)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}
