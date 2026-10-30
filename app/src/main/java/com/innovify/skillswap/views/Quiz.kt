package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
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
    // Reenvío de un quiz "Pending": se intenta solo al abrir la pantalla y también a mano
    var retrying by remember { mutableStateOf(false) }
    var autoTried by remember { mutableStateOf(false) }

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

    // Un quiz que quedó "Pending" se reenvía solo al abrirlo con internet (antes solo se reenviaba al volver la red)
    LaunchedEffect(loading, viewModel.quizStatus, viewModel.connectivity.isOnline) {
        if (!loading && !autoTried && viewModel.quizStatus == "Pending" && viewModel.connectivity.isOnline) {
            autoTried = true
            retrying = true
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                viewModel.retryPendingQuiz(context)
                withContext(Dispatchers.Main) { retrying = false }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Quiz", onBack = { nav.popBackStack() })
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp)
            ) {
                Text(
                    text = quiz.skillName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                if (status == "Draft") StatusChip(text = "Guardado")
            }
            Text(
                text = "Respondidas $answered de ${quiz.questions.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
            )
            LinearProgressIndicator(
                progress = { if (quiz.questions.isEmpty()) 0f else answered.toFloat() / quiz.questions.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, bottom = 16.dp)
                    .height(8.dp)
            )

            quiz.questions.forEachIndexed { position, question ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, NeutralBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PREGUNTA ${position + 1} DE ${quiz.questions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = question.question,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
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
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (selected) SkillBlueContainer else MaterialTheme.colorScheme.surface,
                                        MaterialTheme.shapes.small
                                    )
                                    .border(
                                        1.dp, if (selected) SkillBlue else NeutralBorder,
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                RadioButton(selected = selected, enabled = editable, onClick = null)
                                Text(
                                    text = "${'A' + option}. $text",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                        .height(52.dp)
                ) {
                    Text(text = if (sending) "Enviando…" else "Enviar respuestas")
                }
            }

            if (status == "Pending") {
                val online = viewModel.connectivity.isOnline
                Text(
                    text = when {
                        retrying -> "Enviando tus respuestas…"
                        online -> "Tus respuestas están guardadas en tu teléfono, pero todavía no se calificaron."
                        else -> "Respuestas guardadas en tu teléfono. Se enviarán cuando haya internet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
                OutlinedButton(
                    enabled = online && !retrying,
                    onClick = {
                        retrying = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            viewModel.retryPendingQuiz(context)
                            withContext(Dispatchers.Main) { retrying = false }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 12.dp)
                        .height(52.dp)
                ) {
                    Text(
                        text = when {
                            retrying -> "Enviando…"
                            !online -> "Necesitas internet para enviar"
                            else -> "Reenviar ahora"
                        }
                    )
                }
                // Si el servidor rechazó el intento (4xx), reintentar no sirve: se puede descartar y pedir otro quiz
                if (viewModel.quizWasRejected() && node != null) {
                    TextButton(
                        enabled = !retrying && online,
                        onClick = {
                            retrying = true
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                val ok = viewModel.discardPendingQuiz(context, node.id)
                                withContext(Dispatchers.Main) {
                                    retrying = false
                                    if (!ok) Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text(text = "Descartar este intento y pedir otro quiz") }
                }
            }

            if (viewModel.quizMessage.isNotEmpty()) {
                Text(
                    text = viewModel.quizMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                )
            }
        }

    }

}
