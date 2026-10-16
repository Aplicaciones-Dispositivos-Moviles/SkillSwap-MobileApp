package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// T50 (respuestas guardadas en el teléfono) + T51 (envío y reenvío al reconectar).
@Composable
fun Quiz(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    // T53: apenas el quiz está calificado se pasa a la pantalla de resultado
    // (también cuando el reenvío al reconectar lo califica con esta pantalla abierta)
    LaunchedEffect(loading, viewModel.quizStatus, viewModel.attemptResult) {
        val quiz = viewModel.blueprint
        if (!loading && viewModel.quizStatus == "Sent" && viewModel.attemptResult != null &&
            quiz != null && quiz.pathNodeId == node?.id
        ) {
            nav.navigate("QuizResult") { popUpTo("Quiz") { inclusive = true } }
        }
    }

    // Abre el quiz guardado o descarga uno nuevo (IA: puede tardar)
    LaunchedEffect(node?.id) {
        val nodeId = node?.id
        if (nodeId == null) {
            loading = false
        } else {
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val ok = viewModel.openQuiz(context, nodeId)
                withContext(Dispatchers.Main) {
                    loading = false
                    if (!ok) Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 50.dp, horizontal = 20.dp)
    ) {
        OfflineBanner(viewModel)

        val quiz = viewModel.blueprint
        if (loading) {
            Text(text = "Preparando el quiz… (la IA puede tardar unos segundos)")
        } else if (quiz == null || quiz.pathNodeId != node?.id) {
            Text(text = "No se pudo abrir el quiz de este nodo.")
        } else {
            val status = viewModel.quizStatus
            val editable = status == "Draft" && !sending
            val answered = viewModel.answers.count { it >= 0 }

            Text(text = "Quiz: ${quiz.skillName}", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Respondidas $answered de ${quiz.questions.size}",
                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
            )

            quiz.questions.forEachIndexed { position, question ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${position + 1}. ${question.question}", fontWeight = FontWeight.Bold)
                        question.answers.forEachIndexed { option, text ->
                            val selected = viewModel.answers.getOrNull(position) == option
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        enabled = editable,
                                        onClick = {
                                            // Guardar en SQLite va en hilo IO
                                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                                viewModel.selectAnswer(context, position, option)
                                            }
                                        }
                                    )
                                    .padding(vertical = 6.dp)
                            ) {
                                RadioButton(selected = selected, enabled = editable, onClick = null)
                                Text(text = text, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            if (status == "Draft") {
                Button(
                    enabled = answered == quiz.questions.size && !sending,
                    onClick = {
                        sending = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            viewModel.submitQuiz(context)
                            withContext(Dispatchers.Main) { sending = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (sending) "Enviando…" else "Enviar respuestas")
                }
            }

            if (status == "Pending") {
                Text(
                    text = "Respuestas guardadas en tu teléfono. Se enviarán cuando haya internet.",
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            if (viewModel.quizMessage.isNotEmpty()) {
                Text(text = viewModel.quizMessage, modifier = Modifier.padding(top = 10.dp))
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
