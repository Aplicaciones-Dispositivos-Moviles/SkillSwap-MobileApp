package com.innovify.skillswap.credentialverification.presentation

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.credentialverification.application.CertificateViewModel
import com.innovify.skillswap.credentialverification.domain.Certificate
import com.innovify.skillswap.credentialverification.domain.CertificateFilter
import com.innovify.skillswap.credentialverification.domain.CertificateStage
import com.innovify.skillswap.credentialverification.domain.filterBy
import com.innovify.skillswap.credentialverification.domain.stage
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.learningpath.application.knownNodes
import com.innovify.skillswap.learningpath.application.nextStep
import com.innovify.skillswap.learningpath.domain.PathNode
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.IconTile
import com.innovify.skillswap.shared.presentation.design.LoadingBlock
import com.innovify.skillswap.shared.presentation.design.MessageBlock
import com.innovify.skillswap.shared.presentation.design.CardBorder
import com.innovify.skillswap.shared.presentation.design.OutlinedSurface
import com.innovify.skillswap.shared.presentation.design.ScreenTitle
import com.innovify.skillswap.shared.presentation.design.StatePill
import com.innovify.skillswap.shared.presentation.design.StudentTab
import com.innovify.skillswap.shared.presentation.design.StudentTabScaffold
import com.innovify.skillswap.shared.presentation.design.openTab
import com.innovify.skillswap.shared.presentation.design.shortDate
import com.innovify.skillswap.shared.presentation.design.tabContentPadding
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.ErrorContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.SuccessContainer
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Figma 17 · Mis certificados: every certificate of the Student with its status, filtered by stage.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCertificates(
    viewModel: CertificateViewModel,
    learningPathViewModel: LearningPathViewModel,
    context: Context,
    nav: NavHostController
) {
    var loading by remember { mutableStateOf(viewModel.certificates.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(CertificateFilter.All) }

    fun load(pull: Boolean) {
        if (pull) refreshing = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadCertificates()
            withContext(Dispatchers.Main) {
                loading = false
                refreshing = false
                error = if (ok) null else viewModel.errorMessage
            }
        }
    }

    // Loads on entering and again when the connection comes back
    LaunchedEffect(viewModel.connectivity.isOnline) { load(pull = false) }

    // "Subir certificado": the certificate is uploaded from the node it validates, so it opens the next step
    // of the path (or Mi ruta when there is no available node).
    fun uploadForNextNode() {
        val next = learningPathViewModel.path?.takeIf { it.status != "Paused" }?.nextStep()
        if (next != null) {
            learningPathViewModel.selectedNode = next
            nav.navigate("Node")
        } else {
            nav.openTab(StudentTab.Path)
        }
    }

    val nodes = knownNodes(learningPathViewModel.path, learningPathViewModel.myPaths)
    val certificates = viewModel.certificates
    val visible = certificates.filterBy(filter)

    StudentTabScaffold(
        selected = StudentTab.Certificates,
        nav = nav,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { uploadForNextNode() },
                containerColor = SkillBlueDark,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Subir certificado") }
            )
        }
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load(pull = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = tabContentPadding(inner, extraBottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    OfflineBanner(viewModel.connectivity, false)
                    ScreenTitle("Mis certificados")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        CertificateFilter.entries.forEach { option ->
                            val selected = option == filter
                            FilterChip(
                                selected = selected,
                                onClick = { filter = option },
                                label = { Text(filterLabel(option)) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SkillBlueContainer,
                                    selectedLabelColor = SkillBlueDark,
                                    selectedLeadingIconColor = SkillBlueDark
                                )
                            )
                        }
                    }
                }

                when {
                    loading -> item { LoadingBlock("Cargando tus certificados…") }

                    error != null && certificates.isEmpty() -> item {
                        MessageBlock(
                            icon = Icons.Filled.Warning,
                            title = "No pudimos cargar tus certificados",
                            body = error ?: "",
                            tint = Error,
                            actionLabel = if (viewModel.connectivity.isOnline) "Reintentar" else "Necesitas internet",
                            actionEnabled = viewModel.connectivity.isOnline,
                            onAction = { loading = true; load(pull = false) }
                        )
                    }

                    certificates.isEmpty() -> item {
                        MessageBlock(
                            icon = Icons.Filled.Star,
                            title = "Aún no tienes certificados",
                            body = "Sube el certificado de un curso desde el nodo de tu ruta que valida.",
                            actionLabel = "Subir certificado",
                            onAction = { uploadForNextNode() }
                        )
                    }

                    visible.isEmpty() -> item {
                        Text(
                            emptyFilterText(filter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }

                    else -> items(visible, key = { it.id }) { certificate ->
                        CertificateCard(
                            certificate = certificate,
                            linkedNode = nodes.firstOrNull { it.linkedCertificateId == certificate.id },
                            onUploadAgain = { uploadForNextNode() }
                        )
                    }
                }
            }
        }
    }
}

private fun filterLabel(filter: CertificateFilter): String = when (filter) {
    CertificateFilter.All -> "Todos"
    CertificateFilter.Validated -> "Validados"
    CertificateFilter.InProgress -> "En proceso"
    CertificateFilter.Rejected -> "Rechazados"
}

private fun emptyFilterText(filter: CertificateFilter): String = when (filter) {
    CertificateFilter.Validated -> "Todavía no tienes certificados validados."
    CertificateFilter.InProgress -> "No tienes certificados en proceso."
    CertificateFilter.Rejected -> "No tienes certificados rechazados."
    CertificateFilter.All -> "Aún no tienes certificados."
}

// Line under the institution: which node it validates, or why it is still waiting / was rejected.
internal fun certificateNote(certificate: Certificate, linkedNode: PathNode?): String = when (certificate.stage()) {
    CertificateStage.Validated ->
        linkedNode?.let { "Vinculado al nodo ${it.order} · ${it.skillName}" } ?: "Validado · aún no está vinculado a un nodo"
    CertificateStage.InProgress -> when {
        certificate.holderNameMismatch -> "El nombre del certificado no coincide con tu cuenta"
        certificate.status == "Suspicious" || certificate.riskLevel == "Review" || certificate.riskLevel == "HighRisk" ->
            "Un Verificador está revisando el documento"
        else -> "Revisando el certificado"
    }
    CertificateStage.Rejected -> "No pudimos validarlo"
}

@Composable
private fun CertificateCard(certificate: Certificate, linkedNode: PathNode?, onUploadAgain: () -> Unit) {
    val stage = certificate.stage()
    val (tint, container) = when (stage) {
        CertificateStage.Validated -> Success to SuccessContainer
        CertificateStage.InProgress -> SkillBlueDark to SkillBlueContainer
        CertificateStage.Rejected -> Error to ErrorContainer
    }
    val subtitle = listOfNotNull(
        certificate.institutionName?.ifBlank { null },
        shortDate(certificate.issueDate, withYear = true)
    ).joinToString(" · ")

    OutlinedSurface(borderColor = if (stage == CertificateStage.Rejected) ErrorContainer else CardBorder) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(Icons.Filled.Star, tint = tint, container = container)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        certificate.courseName?.ifBlank { null } ?: "Certificado",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    when (stage) {
                        CertificateStage.Validated -> StatePill("Validado", tint, container, Icons.Filled.CheckCircle)
                        CertificateStage.InProgress -> StatePill("En proceso", tint, container, Icons.Filled.Refresh)
                        CertificateStage.Rejected -> StatePill("Rechazado", tint, container, Icons.Filled.Close)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        certificateNote(certificate, linkedNode),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (stage == CertificateStage.Rejected) {
                        TextButton(onClick = onUploadAgain, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Volver a subir", color = SkillBlueDark)
                        }
                    }
                }
            }
        }
    }
}
