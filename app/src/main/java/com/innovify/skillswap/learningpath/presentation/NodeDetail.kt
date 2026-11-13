package com.innovify.skillswap.learningpath.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.assessment.application.QuizViewModel
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.credentialverification.application.CertificateViewModel
import com.innovify.skillswap.credentialverification.presentation.CERTIFICATE_SOURCE_CAMERA
import com.innovify.skillswap.credentialverification.presentation.CERTIFICATE_SOURCE_FILE
import com.innovify.skillswap.credentialverification.presentation.CERTIFICATE_SOURCE_KEY
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.ActionTile
import com.innovify.skillswap.shared.presentation.design.FlowChip
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.InfoCard
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.design.PrivacyNote
import com.innovify.skillswap.shared.presentation.design.SecondaryActionButton
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
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
import java.io.File

// Mock-up 08 · Detalle del nodo
@Composable
fun NodeDetail(
    viewModel: LearningPathViewModel,
    quizViewModel: QuizViewModel,
    certificateViewModel: CertificateViewModel,
    verificationCaseViewModel: VerificationCaseViewModel,
    context: Context,
    nav: NavHostController
) {

    val node = viewModel.selectedNode

    // T48: busca si ya hay un certificado guardado en el teléfono para este nodo
    LaunchedEffect(node?.id) {
        val nodeId = node?.id ?: return@LaunchedEffect
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            certificateViewModel.loadLocalCertificate(context, nodeId)
        }
    }

    // Si el nodo ya tiene un quiz calificado se ofrece volver a ver su resultado (Rendir quiz pide uno nuevo)
    var hasQuizResult by remember { mutableStateOf(false) }
    LaunchedEffect(node?.id, quizViewModel.quizStatus, quizViewModel.attemptResult) {
        val nodeId = node?.id ?: return@LaunchedEffect
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val has = quizViewModel.hasQuizResult(context, nodeId)
            withContext(Dispatchers.Main) { hasQuizResult = has }
        }
    }

    val total = viewModel.path?.nodes?.size ?: 0
    var enabling by remember { mutableStateOf(false) }
    val pathPaused = viewModel.path?.status == "Paused"
    // Flujo del informe: el certificado vinculado habilita la evaluación práctica del nodo (T50 quiz, T55 proyecto)
    val evaluationEnabled = node != null && node.status == "Available" && node.linkedCertificateId != null && !pathPaused

    // Opens the certificate screen; it launches the camera or the file picker right away
    fun openCertificate(source: String) {
        nav.currentBackStackEntry?.savedStateHandle?.set(CERTIFICATE_SOURCE_KEY, source)
        nav.navigate("Certificate")
    }

    FlowScaffold(
        topBar = {
            FlowTopBar(
                title = if (node != null && total > 0) "Nodo ${node.order} de $total" else "Nodo",
                onBack = { nav.popBackStack() }
            )
        },
        bottomBar = if (evaluationEnabled) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // T55: el proyecto también espera al certificado vinculado
                    SecondaryActionButton(
                        text = "Proyecto",
                        onClick = { nav.navigate("Project") },
                        modifier = Modifier.weight(1f)
                    )
                    // T50: el quiz aparece solo cuando el certificado ya quedó vinculado al nodo
                    PrimaryActionButton(
                        text = "Rendir quiz",
                        onClick = { nav.navigate("Quiz") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else null
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        if (node == null) {
            Text(text = "Selecciona un nodo desde la ruta.", style = MaterialTheme.typography.bodyLarge)
            return@FlowScaffold
        }

        when (node.status) {
            "Completed" -> FlowChip("Completado", Success, SuccessContainer, Icons.Filled.CheckCircle)
            "Available" -> FlowChip("Disponible", Success, SuccessContainer, FlowIcons.Verified)
            else -> FlowChip("Bloqueado", TextSecondary, NeutralSurface, Icons.Filled.Lock)
        }
        Text(
            text = node.skillName,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Habilidad: ${node.skillTag}",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Cómo completar este nodo: los pasos se marcan según el avance real
        val certificateDone = node.linkedCertificateId != null || node.completedByCertificate
        val nodeDone = node.status == "Completed"
        SectionHeader(title = "Cómo completar este nodo", modifier = Modifier.padding(top = 8.dp))
        CompletionStep(1, "Sube tu certificado", certificateDone, current = !certificateDone && !nodeDone)
        CompletionStep(2, "La IA confirma que cubre esta habilidad", certificateDone, current = false)
        CompletionStep(3, "Rinde el quiz o entrega el proyecto", nodeDone, current = certificateDone && !nodeDone)

        // Datos del nodo: requisitos, certificado vinculado y copia guardada en el teléfono
        val local = certificateViewModel.localCertificate
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            NodeFact(
                "Requisitos: " + (node.prerequisiteSkillTags?.joinToString(", ")?.ifBlank { null } ?: "ninguno")
            )
            NodeFact("Certificado: " + (node.linkedCertificateId?.let { "#$it" } ?: "sin certificado"))
            if (local != null && local.nodeId == node.id) {
                NodeFact(
                    "Copia en el teléfono: " + File(local.filePath).name +
                        if (local.status == "Pending") " (pendiente de envío)" else " (enviado)"
                )
            }
        }

        // T44: solo un nodo disponible y sin certificado puede recibir uno
        if (node.status == "Available" && node.linkedCertificateId == null && !pathPaused) {
            SectionHeader(title = "Agrega tu certificado", modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(
                    icon = FlowIcons.Camera,
                    title = "Tomar foto",
                    subtitle = "Certificado impreso o en otra pantalla",
                    onClick = { openCertificate(CERTIFICATE_SOURCE_CAMERA) },
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = FlowIcons.Upload,
                    title = "Subir archivo",
                    subtitle = "PDF, JPG o PNG",
                    onClick = { openCertificate(CERTIFICATE_SOURCE_FILE) },
                    modifier = Modifier.weight(1f)
                )
            }
            InfoCard(
                icon = FlowIcons.Verified,
                title = "Certificados aceptados",
                body = "Coursera, edX, DataCamp, Platzi o constancias universitarias que cubran ${node.skillName}.",
                modifier = Modifier.padding(top = 12.dp)
            )
            PrivacyNote("El texto se lee en tu dispositivo con ML Kit. Tu certificado no se muestra a otros estudiantes.")
        }

        if (node.status == "Available" && !evaluationEnabled) {
            InfoCard(
                icon = Icons.Filled.Info,
                body = if (pathPaused) "Esta ruta está pausada. Reanúdala para continuar."
                else "Sube tu certificado para habilitar el quiz y el proyecto de este nodo.",
                tint = if (pathPaused) Warning else SkillBlueDark,
                container = if (pathPaused) WarningContainer else NeutralSurface,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 24.dp)
        ) {
            if (hasQuizResult) {
                SecondaryActionButton(
                    text = "Ver último resultado del quiz",
                    onClick = {
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            val ok = quizViewModel.openLastResult(context, node.id)
                            withContext(Dispatchers.Main) {
                                if (ok) nav.navigate("QuizResult")
                                else Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // WF-10: con el nodo completado el Estudiante puede habilitarse como Verificador de esa habilidad
            if (node.status == "Completed") {
                SecondaryActionButton(
                    text = when {
                        enabling -> "Habilitando…"
                        !viewModel.connectivity.isOnline -> "Necesitas internet para habilitarte"
                        else -> "Habilitarme como Verificador"
                    },
                    enabled = !enabling && viewModel.connectivity.isOnline,
                    icon = FlowIcons.Verified,
                    onClick = {
                        enabling = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            val message = verificationCaseViewModel.enableAsVerifier(node.skillTag)
                            withContext(Dispatchers.Main) {
                                enabling = false
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

// Línea de dato secundario del nodo (requisitos, certificado, copia local)
@Composable
private fun NodeFact(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
}

// Un paso de "Cómo completar este nodo": hecho (check verde), actual (número azul) o pendiente (círculo gris)
@Composable
private fun CompletionStep(number: Int, text: String, done: Boolean, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        val marker = Modifier.size(24.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = when {
                done -> marker.background(Success, CircleShape)
                current -> marker.background(SkillBlueDark, CircleShape)
                else -> marker.border(1.5.dp, NeutralBorder, CircleShape)
            }
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Hecho",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (current) Color.White else TextSecondary
                )
            }
        }
        Text(
            text = buildAnnotatedString {
                if (current) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text) } else append(text)
                if (current) withStyle(SpanStyle(color = SkillBlue, fontWeight = FontWeight.Medium)) { append(" · Estás aquí") }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (done || current) TextPrimary else TextSecondary
        )
    }
}
