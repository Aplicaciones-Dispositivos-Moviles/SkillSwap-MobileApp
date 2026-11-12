package com.innovify.skillswap.assessment.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.assessment.application.QuizViewModel
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowLoading
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.InfoCard
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.design.SecondaryActionButton
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.shared.presentation.theme.Warning
import com.innovify.skillswap.shared.presentation.theme.WarningContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Page the quiz opens on: the first unanswered question, or the last one when every question is answered. */
fun initialQuizPage(answers: List<Int>): Int {
    val firstOpen = answers.indexOfFirst { it < 0 }
    return if (firstOpen >= 0) firstOpen else answers.lastIndex.coerceAtLeast(0)
}

/** The quiz can be sent only when every question has an answer (-1 = unanswered). */
fun canSubmitQuiz(answers: List<Int>): Boolean = answers.isNotEmpty() && answers.all { it >= 0 }

/** Letter of an option badge: 0 → "A", 1 → "B"… */
fun optionLetter(index: Int): String = ('A' + index).toString()

// T50 (respuestas guardadas en el teléfono) + T51 (envío y reenvío al reconectar). Mock-up 10 · una pregunta por página.
@Composable
fun Quiz(
    viewModel: QuizViewModel,
    learningPathViewModel: LearningPathViewModel,
    context: Context,
    nav: NavHostController
) {

    val node = learningPathViewModel.selectedNode
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    // Reenvío de un quiz "Pending": se intenta solo al abrir la pantalla y también a mano
    var retrying by remember { mutableStateOf(false) }
    var autoTried by remember { mutableStateOf(false) }
    // Pregunta visible; -1 hasta abrir el quiz (entonces va a la primera sin responder)
    var page by rememberSaveable { mutableIntStateOf(-1) }

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

    val quiz = viewModel.blueprint
    val ready = !loading && quiz != null && quiz.pathNodeId == node?.id
    val total = quiz?.questions?.size ?: 0
    LaunchedEffect(ready, total) {
        if (ready && (page < 0 || page >= total)) page = initialQuizPage(viewModel.answers)
    }
    val current = page.coerceIn(0, (total - 1).coerceAtLeast(0))
    val status = viewModel.quizStatus
    val answered = viewModel.answers.count { it >= 0 }

    fun submit() {
        sending = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            viewModel.submitQuiz(context)
            withContext(Dispatchers.Main) { sending = false }
        }
    }

    fun retry() {
        retrying = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            viewModel.retryPendingQuiz(context)
            withContext(Dispatchers.Main) { retrying = false }
        }
    }

    FlowScaffold(
        topBar = {
            FlowTopBar(
                title = if (quiz != null && ready) "Quiz · ${quiz.skillName}" else "Quiz",
                subtitle = if (ready) "Respondidas $answered de $total" else null,
                close = true,
                onBack = { nav.popBackStack() }
            )
        },
        bottomBar = if (!ready || quiz == null) null else {
            {
                if (status == "Draft") {
                    val isLast = current == total - 1
                    if (isLast && !canSubmitQuiz(viewModel.answers)) {
                        Text(
                            text = "Responde todas las preguntas para enviar (faltan ${total - answered}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryActionButton(
                            text = "Anterior",
                            enabled = current > 0 && !sending,
                            onClick = { page = current - 1 },
                            modifier = Modifier.weight(1f)
                        )
                        if (isLast) {
                            PrimaryActionButton(
                                text = if (sending) "Enviando…" else "Enviar respuestas",
                                enabled = canSubmitQuiz(viewModel.answers) && !sending,
                                loading = sending,
                                onClick = { submit() },
                                modifier = Modifier.weight(1.4f)
                            )
                        } else {
                            PrimaryActionButton(
                                text = "Siguiente",
                                onClick = { page = current + 1 },
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                    }
                }
                if (status == "Pending") {
                    val online = viewModel.connectivity.isOnline
                    PrimaryActionButton(
                        text = when {
                            retrying -> "Enviando…"
                            !online -> "Necesitas internet para enviar"
                            else -> "Reenviar ahora"
                        },
                        enabled = online && !retrying,
                        loading = retrying,
                        onClick = { retry() }
                    )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) { Text(text = "Descartar este intento y pedir otro quiz") }
                    }
                }
            }
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        if (loading) {
            FlowLoading("Preparando el quiz… (la IA puede tardar unos segundos)")
            return@FlowScaffold
        }
        if (!ready || quiz == null) {
            Text(text = "No se pudo abrir el quiz de este nodo.", style = MaterialTheme.typography.bodyLarge)
            return@FlowScaffold
        }

        val editable = status == "Draft" && !sending

        // Pregunta n de N + indicador de guardado
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "Pregunta ${current + 1} de $total",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (status == "Draft") {
                Icon(FlowIcons.CloudDone, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                Text(
                    text = "Guardado",
                    style = MaterialTheme.typography.labelLarge,
                    color = Success,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        // Barra de progreso por pregunta: azul la actual, celeste las respondidas; tocar una salta a ella
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            viewModel.answers.forEachIndexed { index, answer ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clickable(role = Role.Tab) { page = index }
                        .semantics {
                            contentDescription = "Pregunta ${index + 1}" + if (answer >= 0) ", respondida" else ", sin responder"
                        }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                when {
                                    index == current -> SkillBlue
                                    answer >= 0 -> SkillBlue.copy(alpha = 0.35f)
                                    else -> NeutralBorder
                                },
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }

        if (status == "Pending") {
            val online = viewModel.connectivity.isOnline
            InfoCard(
                icon = FlowIcons.CloudDone,
                body = when {
                    retrying -> "Enviando tus respuestas…"
                    online -> "Tus respuestas están guardadas en tu teléfono, pero todavía no se calificaron."
                    else -> "Respuestas guardadas en tu teléfono. Se enviarán cuando haya internet."
                },
                tint = Warning,
                container = WarningContainer,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        val question = quiz.questions.getOrNull(current)
        if (question != null) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.selectableGroup()
            ) {
                question.answers.forEachIndexed { option, text ->
                    OptionCard(
                        letter = optionLetter(option),
                        text = text,
                        selected = viewModel.answers.getOrNull(current) == option,
                        enabled = editable,
                        onSelect = {
                            // Guardar en SQLite va en hilo IO
                            val position = current
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                viewModel.selectAnswer(context, position, option)
                            }
                        }
                    )
                }
            }
        }

        if (viewModel.quizMessage.isNotEmpty()) {
            Text(
                text = viewModel.quizMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// Opción como tarjeta: insignia con la letra, texto y check cuando está elegida
@Composable
private fun OptionCard(letter: String, text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (selected) SkillBlueContainer else Color.White, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) SkillBlueDark else NeutralBorder, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(if (selected) SkillBlueDark else NeutralSurface, RoundedCornerShape(8.dp))
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else TextSecondary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        )
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Elegida", tint = SkillBlueDark, modifier = Modifier.size(24.dp))
        }
    }
}
