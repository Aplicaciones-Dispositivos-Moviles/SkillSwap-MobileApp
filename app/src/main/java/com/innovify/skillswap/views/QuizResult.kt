package com.innovify.skillswap.views

import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
            .padding(vertical = 50.dp, horizontal = 20.dp)
    ) {
        OfflineBanner(viewModel)

        if (result == null) {
            Text(text = "Aún no hay un resultado para mostrar.")
        } else {
            Text(text = "Resultado del quiz", style = MaterialTheme.typography.headlineSmall)
            Text(text = quiz?.skillName ?: "", modifier = Modifier.padding(top = 5.dp))

            Text(
                text = "${result.score} de ${result.totalQuestions}",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 15.dp)
            )
            Text(
                text = if (result.passed) "Aprobado" else "No aprobado",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 15.dp)
            )

            // Sub-tema donde más falló (null si no tuvo errores o los errores no tienen sub-tema)
            result.weakSubTopic?.let { weak ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Sub-tema a reforzar", fontWeight = FontWeight.Bold)
                        Text(text = weak, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }

            // Un intento fallido abre un caso de verificación (o agota las escalaciones del plan)
            result.verificationCaseId?.let { caseId ->
                Text(
                    text = "Se abrió el caso de verificación #$caseId" +
                        (result.verificationCaseStatus?.let { " (estado: $it)" } ?: "") +
                        ". Un verificador revisará tu intento."
                )
            }
            result.planLimitReached?.let { limit ->
                Text(
                    text = "Alcanzaste el límite de tu plan ${limit.plan}: usaste ${limit.current} de " +
                        "${limit.max} escalaciones este mes, por eso no se abrió un caso de verificación.",
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            // Solo aparece cuando el backend entregue las preguntas falladas al Estudiante
            val failed = result.failedQuestions
            if (!failed.isNullOrEmpty()) {
                Text(
                    text = "Preguntas que fallaste",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 5.dp)
                )
                failed.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "${item.position}. ${item.question}", fontWeight = FontWeight.Bold)
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
                .padding(top = 20.dp)
        ) {
            Text(text = "Volver a la ruta")
        }

        OutlinedButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Text(text = "Volver al nodo")
        }
    }

}
