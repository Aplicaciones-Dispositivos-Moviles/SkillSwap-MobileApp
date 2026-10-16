package com.innovify.skillswap.views

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.ErrorContainer
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.Warning
import com.innovify.skillswap.ui.theme.WarningContainer
import com.innovify.skillswap.viewmodel.SkillSwapViewModel

// T53: resultado del quiz con el sub-tema a reforzar.
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
        ScreenTopBar(title = "Resultado del quiz", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        if (result == null) {
            Text(text = "Aún no hay un resultado para mostrar.")
        } else {
            val percent = if (result.totalQuestions > 0) result.score * 100 / result.totalQuestions else 0
            val tone = if (result.passed) Success else Warning
            val toneContainer = if (result.passed) SuccessContainer else WarningContainer

            Card(
                colors = CardDefaults.cardColors(containerColor = toneContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (result.passed) "¡Nodo aprobado!" else "Aún no alcanzas el mínimo",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tone
                    )
                    Text(
                        text = quiz?.skillName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${result.score}/${result.totalQuestions}",
                        style = MaterialTheme.typography.displaySmall,
                        color = tone,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = "$percent% de respuestas correctas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Sub-tema donde más falló (null si no tuvo errores o los errores no tienen sub-tema)
            result.weakSubTopic?.let { weak ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 16.dp)
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
                    }
                }
            }

            // Un intento fallido abre un caso de verificación (o agota las escalaciones del plan)
            result.verificationCaseId?.let { caseId ->
                Text(
                    text = "Se abrió el caso de verificación #$caseId" +
                        (result.verificationCaseStatus?.let { " (estado: $it)" } ?: "") +
                        ". Un verificador revisará tu intento.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            result.planLimitReached?.let { limit ->
                Text(
                    text = "Alcanzaste el límite de tu plan ${limit.plan}: usaste ${limit.current} de " +
                        "${limit.max} escalaciones este mes, por eso no se abrió un caso de verificación.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            // Solo aparece cuando el backend entregue las preguntas falladas al Estudiante
            val failed = result.failedQuestions
            if (!failed.isNullOrEmpty()) {
                Text(
                    text = "Preguntas que fallaste",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
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
        }

        // Vuelve a la lista de rutas; al entrar se vuelve a descargar y refleja el resultado del intento
        Button(
            onClick = { nav.popBackStack("Path", false) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 24.dp)
                .height(52.dp)
        ) {
            Text(text = "Volver a la ruta")
        }

        OutlinedButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp)
                .height(52.dp)
        ) {
            Text(text = "Volver al nodo")
        }
    }

}
