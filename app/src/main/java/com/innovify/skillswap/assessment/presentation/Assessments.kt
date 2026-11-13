package com.innovify.skillswap.assessment.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.assessment.application.AssessmentKind
import com.innovify.skillswap.assessment.application.AttentionItem
import com.innovify.skillswap.assessment.application.AttentionReason
import com.innovify.skillswap.assessment.application.HistoryItem
import com.innovify.skillswap.assessment.application.QuizViewModel
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.assessment.application.assessmentOverview
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.learningpath.application.knownNodes
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.CardBorder
import com.innovify.skillswap.shared.presentation.design.IconTile
import com.innovify.skillswap.shared.presentation.design.LoadingBlock
import com.innovify.skillswap.shared.presentation.design.MessageBlock
import com.innovify.skillswap.shared.presentation.design.OutlinedSurface
import com.innovify.skillswap.shared.presentation.design.ScreenTitle
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.design.StatePill
import com.innovify.skillswap.shared.presentation.design.StudentTab
import com.innovify.skillswap.shared.presentation.design.StudentTabScaffold
import com.innovify.skillswap.shared.presentation.design.openTab
import com.innovify.skillswap.shared.presentation.design.shortDate
import com.innovify.skillswap.shared.presentation.design.tabContentPadding
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.ErrorContainer
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
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

// Figma 18 · Evaluaciones: what needs the Student's attention and the graded history, built from the path and
// the assessments saved on this phone (the backend has no list of assessments for the Student).
@Composable
fun Assessments(
    quizViewModel: QuizViewModel,
    learningPathViewModel: LearningPathViewModel,
    verificationCaseViewModel: VerificationCaseViewModel,
    context: Context,
    nav: NavHostController
) {
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        quizViewModel.viewModelScope.launch(Dispatchers.IO) {
            // Opened before Mi ruta finished loading: read the path first (network or cache)
            if (learningPathViewModel.path == null) learningPathViewModel.loadPath(context)
            val nodes = knownNodes(learningPathViewModel.path, learningPathViewModel.myPaths)
            quizViewModel.loadSavedAssessments(context, nodes.map { it.id })
            withContext(Dispatchers.Main) { loading = false }
        }
    }

    val path = learningPathViewModel.path
    val nodes = knownNodes(path, learningPathViewModel.myPaths)
    val activeNodeIds = path?.takeIf { it.status == "Active" }?.nodes.orEmpty().map { it.id }.toSet()
    val overview = remember(nodes, activeNodeIds, quizViewModel.savedAttempts, quizViewModel.savedProjects) {
        assessmentOverview(nodes, activeNodeIds, quizViewModel.savedAttempts, quizViewModel.savedProjects)
    }

    fun openNode(item: AttentionItem, route: String) {
        learningPathViewModel.selectedNode = item.node
        nav.navigate(route)
    }

    fun openAttention(item: AttentionItem) {
        when (item.reason) {
            AttentionReason.CaseInReview -> {
                verificationCaseViewModel.selectedCaseId = item.caseId
                nav.navigate("CaseStatus")
            }
            AttentionReason.ProjectEvaluating -> openNode(item, "Project")
            else -> openNode(item, "Node")
        }
    }

    fun openHistory(item: HistoryItem) {
        learningPathViewModel.selectedNode = item.node
        if (item.kind == AssessmentKind.MiniProject) {
            nav.navigate("Project")
            return
        }
        quizViewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = quizViewModel.openLastResult(context, item.node.id)
            withContext(Dispatchers.Main) {
                if (ok) nav.navigate("QuizResult")
                else Toast.makeText(context, quizViewModel.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    StudentTabScaffold(selected = StudentTab.Assessments, nav = nav) { inner ->
        LazyColumn(
            contentPadding = tabContentPadding(inner),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                OfflineBanner(learningPathViewModel.connectivity, learningPathViewModel.showingCache)
                ScreenTitle("Evaluaciones")
            }

            when {
                loading -> item { LoadingBlock("Cargando tus evaluaciones…") }

                path == null && nodes.isEmpty() -> item {
                    MessageBlock(
                        icon = Icons.Filled.Home,
                        title = "Aún no tienes evaluaciones",
                        body = "Cuando tengas una ruta, aquí verás tus quizzes, miniproyectos y casos de revisión.",
                        actionLabel = "Ir a Mi ruta",
                        onAction = { nav.openTab(StudentTab.Path) }
                    )
                }

                else -> {
                    item { SectionHeader("Requieren tu atención", modifier = Modifier.padding(top = 0.dp)) }
                    if (overview.attention.isEmpty()) {
                        item {
                            Text(
                                "Nada pendiente por ahora. Cuando un nodo esté listo para su quiz o tengas un caso abierto, aparecerá aquí.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    } else {
                        items(overview.attention, key = { "${it.reason}-${it.node.id}-${it.caseId}" }) { item ->
                            AttentionCard(
                                item = item,
                                onOpen = { openAttention(item) },
                                onQuiz = { openNode(item, "Quiz") }
                            )
                        }
                    }

                    item { SectionHeader("Historial") }
                    if (overview.history.isEmpty()) {
                        item {
                            Text(
                                "Aún no tienes evaluaciones calificadas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    } else {
                        item {
                            OutlinedSurface {
                                overview.history.forEachIndexed { index, entry ->
                                    if (index > 0) HorizontalDivider(color = CardBorder)
                                    HistoryRow(entry, onClick = { openHistory(entry) })
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Se muestran las evaluaciones guardadas en este teléfono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionCard(item: AttentionItem, onOpen: () -> Unit, onQuiz: () -> Unit) {
    if (item.reason == AttentionReason.CaseInReview) {
        OutlinedSurface(borderColor = SkillBlueContainer, onClick = onOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatePill("Caso de verificación", SkillBlueDark, SkillBlueContainer, Icons.Filled.Refresh)
                    Text(
                        "Caso #${item.caseId} · ${item.skillName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Toca para ver su estado y la decisión del Verificador",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Ver caso", tint = TextSecondary)
            }
        }
        return
    }

    val project = item.reason == AttentionReason.ProjectEvaluating
    val title = if (project) "Miniproyecto · ${item.skillName}" else "Quiz de ${item.skillName}"
    val subtitle = when (item.reason) {
        AttentionReason.UnsentQuiz -> "Respuestas guardadas sin enviar · se envían con internet"
        AttentionReason.QuizInProgress -> "En curso · ${item.answered} de ${item.total} respondidas"
        AttentionReason.ReadyForQuiz -> "Certificado vinculado · listo para rendir"
        AttentionReason.ProjectEvaluating -> "La IA está evaluando tu entrega"
        AttentionReason.CaseInReview -> ""
    }
    val action = when (item.reason) {
        AttentionReason.UnsentQuiz -> "Enviar"
        AttentionReason.QuizInProgress -> "Continuar"
        AttentionReason.ReadyForQuiz -> "Rendir"
        else -> null
    }
    OutlinedSurface(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                if (project) Icons.Filled.Build else Icons.AutoMirrored.Filled.List,
                tint = if (item.reason == AttentionReason.UnsentQuiz) Warning else SkillBlueDark,
                container = if (item.reason == AttentionReason.UnsentQuiz) WarningContainer else SkillBlueContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onQuiz,
                    border = BorderStroke(1.dp, SkillBlueDark),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(action, color = SkillBlueDark) }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    val quiz = item.kind == AssessmentKind.Quiz
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        IconTile(
            if (quiz) Icons.AutoMirrored.Filled.List else Icons.Filled.Build,
            tint = TextSecondary,
            container = NeutralSurface
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (quiz) "Quiz · " else "Miniproyecto · ") + item.skillName,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    item.dateMillis?.let { shortDate(it) },
                    if (quiz) "ver resultado" else "ver entrega"
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            when (item.passed) {
                true -> StatePill("Aprobado", Success, SuccessContainer, Icons.Filled.CheckCircle)
                false -> StatePill("No aprobado", Error, ErrorContainer, Icons.Filled.Close)
                null -> StatePill("Revisado", SkillBlueDark, SkillBlueContainer, Icons.Filled.Info)
            }
            if (item.score != null) {
                Text(
                    item.score,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
