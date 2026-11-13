package com.innovify.skillswap.credentialverification.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.credentialverification.application.CertificateViewModel
import com.innovify.skillswap.credentialverification.domain.CertificateMismatch
import com.innovify.skillswap.credentialverification.domain.CertificateParser
import com.innovify.skillswap.credentialverification.infrastructure.CertificateOcr
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.design.ActionTile
import com.innovify.skillswap.shared.presentation.design.FlowHeadline
import com.innovify.skillswap.shared.presentation.design.FlowIcons
import com.innovify.skillswap.shared.presentation.design.FlowScaffold
import com.innovify.skillswap.shared.presentation.design.FlowTopBar
import com.innovify.skillswap.shared.presentation.design.InfoCard
import com.innovify.skillswap.shared.presentation.design.OutlinedSurface
import com.innovify.skillswap.shared.presentation.design.PrimaryActionButton
import com.innovify.skillswap.shared.presentation.design.PrivacyNote
import com.innovify.skillswap.shared.presentation.design.ProgressStep
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.design.StepStatus
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Saved-state key the node detail sets before opening this screen, so it starts the camera or the file picker.
const val CERTIFICATE_SOURCE_KEY = "certificateSource"
const val CERTIFICATE_SOURCE_CAMERA = "camera"
const val CERTIFICATE_SOURCE_FILE = "file"

// T44 (captura con la cámara) + T45 (OCR con ML Kit y envío al backend). Mock-up 09 · Verificando tu certificado.
// El certificado puede venir de la cámara o de un archivo (PDF o imagen) elegido del teléfono.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateCapture(
    viewModel: CertificateViewModel,
    learningPathViewModel: LearningPathViewModel,
    context: Context,
    nav: NavHostController
) {

    val node = learningPathViewModel.selectedNode

    var photoUri: Uri? by rememberSaveable { mutableStateOf(null) }  // foto o archivo ya elegido
    var cameraUri: Uri? by rememberSaveable { mutableStateOf(null) } // destino que se le pasa a la cámara
    var institution by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("") }
    var issueDate by rememberSaveable { mutableStateOf("") }
    var ocrText by rememberSaveable { mutableStateOf("") }
    var ocrFailed by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    // Progress of the last send, for the checklist (null = not tried yet)
    var uploaded by remember { mutableStateOf(false) }
    var uploadFailed by remember { mutableStateOf(false) }
    var linked by remember { mutableStateOf<Boolean?>(null) }

    // Lee el texto del archivo elegido (foto, imagen o PDF) y rellena los campos editables.
    fun loadEvidence(uri: Uri) {
        photoUri = uri
        resultText = ""
        uploaded = false
        uploadFailed = false
        linked = null
        ocrFailed = false
        viewModel.certificateMismatch = null
        reading = true
        // OCR on-device (ML Kit). El callback llega en el hilo principal.
        CertificateOcr.recognize(context, uri) { text ->
            reading = false
            if (text == null) {
                ocrFailed = true
                Toast.makeText(
                    context, "No se pudo leer el texto. Completa los datos a mano", Toast.LENGTH_LONG
                ).show()
            } else {
                ocrText = text
                val parsed = CertificateParser.parse(text)
                institution = parsed.institution
                course = parsed.course
                issueDate = parsed.issueDate
            }
        }
    }

    // ---------------------------------------------------------------- CÁMARA
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) loadEvidence(uri)
    }

    // La foto se guarda en cache/certificates y se comparte con la cámara por FileProvider
    fun openCamera() {
        val folder = File(context.cacheDir, "certificates").apply { mkdirs() }
        val file = File(folder, "cert_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    // ---------------------------------------------------------------- ARCHIVO (PDF o imagen)
    // El selector del sistema no necesita permisos.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadEvidence(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(context, "Se necesita el permiso de cámara para tomar la foto", Toast.LENGTH_LONG).show()
        }
    }

    fun requestCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun openFilePicker() {
        pickFile.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
    }

    // "Tomar foto" / "Subir archivo" in the node detail: start that source once (remove() consumes it)
    LaunchedEffect(Unit) {
        when (nav.previousBackStackEntry?.savedStateHandle?.remove<String>(CERTIFICATE_SOURCE_KEY)) {
            CERTIFICATE_SOURCE_CAMERA -> requestCamera()
            CERTIFICATE_SOURCE_FILE -> openFilePicker()
        }
    }

    // ---------------- ENVÍO AL BACKEND ----------------
    fun send(uri: Uri) {
        val validDate = Regex("\\d{4}-\\d{2}-\\d{2}").matches(issueDate.trim())
        if (institution.isBlank() || course.isBlank() || !validDate) {
            Toast.makeText(
                context, "Completa institución, curso y fecha (AAAA-MM-DD)", Toast.LENGTH_SHORT
            ).show()
            return
        }
        sending = true
        resultText = ""
        uploaded = false
        uploadFailed = false
        linked = null
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            // T48: primero se guarda una copia en el teléfono, haya internet o no
            val localId = viewModel.saveEvidence(
                context, uri, node?.id ?: 0,
                institution.trim(), course.trim(), issueDate.trim(), ocrText
            )
            val uploadedNow = localId >= 0 && viewModel.connectivity.isOnline &&
                viewModel.uploadCertificate(
                    context, uri, institution.trim(), course.trim(), issueDate.trim(),
                    ocrText, localId
                )
            val online = viewModel.connectivity.isOnline
            withContext(Dispatchers.Main) {
                uploaded = uploadedNow
                uploadFailed = !uploadedNow && (localId < 0 || online)
            }
            var linkedNow: Boolean? = null
            val message = if (localId < 0) {
                viewModel.errorMessage
            } else if (!online) {
                // T49: sin internet el certificado queda pendiente (el reenvío es T51)
                "Sin conexión.\nEl certificado quedó guardado en tu teléfono " +
                    "y se enviará cuando vuelva internet."
            } else if (!uploadedNow) {
                viewModel.errorMessage + "\nEl certificado quedó guardado en tu teléfono."
            } else {
                val cert = viewModel.certificate
                val summary = "Certificado registrado.\nEstado: ${certificateStatusLabel(cert?.status)}" +
                    "\nRiesgo: ${riskLevelLabel(cert?.riskLevel)}"
                val ok = node != null && viewModel.linkCertificate(node.id)
                linkedNow = ok
                if (ok) {
                    val link = viewModel.certificateLink
                    summary + "\n\nCubre la habilidad del nodo " +
                        "(afinidad ${"%.2f".format(link?.affinity ?: 0.0)})." +
                        if (link?.assessmentEnabled == true) "\nYa puedes rendir el quiz." else ""
                } else {
                    summary + "\n\n" + viewModel.errorMessage
                }
            }
            // Texto en pantalla: hilo principal
            withContext(Dispatchers.Main) {
                sending = false
                linked = linkedNow
                resultText = message
            }
        }
    }

    val preview = remember(photoUri) { photoUri?.let { CertificateOcr.loadBitmap(context, it, 800) } }
    val uri = photoUri
    val busy = reading || sending

    // ---------------------------------------------------------------- PANTALLA
    FlowScaffold(
        topBar = { FlowTopBar(title = "Subir certificado", close = true, onBack = { nav.popBackStack() }) },
        bottomBar = if (uri == null || reading) null else {
            {
                if (linked == true && !sending) {
                    // El certificado ya quedó vinculado: se vuelve al nodo, que ahora habilita el quiz
                    PrimaryActionButton(text = "Volver al nodo", onClick = { nav.popBackStack() })
                } else {
                    PrimaryActionButton(
                        text = if (sending) "Enviando…" else "Enviar certificado",
                        enabled = !sending,
                        loading = sending,
                        onClick = { send(uri) }
                    )
                }
            }
        }
    ) {
        OfflineBanner(viewModel.connectivity, viewModel.showingCache)

        if (uri == null) {
            // Todavía no hay certificado: se elige de dónde viene
            FlowHeadline(title = "Agrega tu certificado", subtitle = "Habilidad: ${node?.skillName ?: "—"}")
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 24.dp)
            ) {
                ActionTile(
                    icon = FlowIcons.Camera,
                    title = "Tomar foto",
                    subtitle = "Certificado impreso o en otra pantalla",
                    enabled = !busy,
                    onClick = { requestCamera() },
                    modifier = Modifier.weight(1f)
                )
                ActionTile(
                    icon = FlowIcons.Upload,
                    title = "Subir archivo",
                    subtitle = "PDF, JPG o PNG",
                    enabled = !busy,
                    onClick = { openFilePicker() },
                    modifier = Modifier.weight(1f)
                )
            }
            InfoCard(
                icon = FlowIcons.Verified,
                title = "Certificados aceptados",
                body = "Coursera, edX, DataCamp, Platzi o constancias universitarias que cubran " +
                    "${node?.skillName ?: "esta habilidad"}.",
                modifier = Modifier.padding(top = 12.dp)
            )
            PrivacyNote("El texto se lee en tu dispositivo con ML Kit. Tu certificado no se muestra a otros estudiantes.")
            return@FlowScaffold
        }

        FlowHeadline(
            title = when {
                busy -> "Verificando tu certificado"
                resultText.isEmpty() -> "Revisa los datos leídos"
                else -> "Resultado de la verificación"
            },
            subtitle = if (reading) "Lo leemos en tu dispositivo con ML Kit. Toma unos segundos."
            else "Habilidad: ${node?.skillName ?: "—"}"
        )

        // Vista previa sobre un fondo gris, como la tarjeta del mock-up
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .background(NeutralSurface, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Vista previa del certificado",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(FlowIcons.Document, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                    Text(
                        text = "Archivo seleccionado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            OutlinedButton(
                enabled = !busy,
                onClick = { requestCamera() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(FlowIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "Tomar otra foto", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
            OutlinedButton(
                enabled = !busy,
                onClick = { openFilePicker() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(FlowIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "Otro archivo", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }

        if (!reading) {
            SectionHeader(title = "Revisa los datos leídos y corrígelos si hace falta")
            OutlinedTextField(
                value = institution,
                label = { Text(text = "Institución") },
                enabled = !sending,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { institution = it }
            )
            OutlinedTextField(
                value = course,
                label = { Text(text = "Curso") },
                enabled = !sending,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                onValueChange = { course = it }
            )
            // La fecha se elige en un calendario (siempre queda como AAAA-MM-DD); el OCR la precarga si la leyó
            OutlinedTextField(
                value = issueDate,
                label = { Text(text = "Fecha de emisión (AAAA-MM-DD)") },
                singleLine = true,
                readOnly = true,
                enabled = !sending,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }, enabled = !sending) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Elegir fecha", tint = SkillBlue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                onValueChange = { }
            )
        }

        // Checklist del mock-up 09 con el avance real de esta pantalla
        val steps = certificateSteps(
            CertificateProgress(
                reading = reading,
                extracted = !reading,
                sending = sending,
                uploaded = uploaded,
                uploadFailed = uploadFailed,
                coversNode = when {
                    linked == true -> true
                    linked == false && viewModel.certificateMismatch != null -> false
                    else -> null
                },
                riskLevel = viewModel.certificate?.riskLevel
            )
        )
        val certificate = viewModel.certificate
        SectionHeader(title = "Verificación")
        ProgressStep(
            status = steps[0],
            title = if (reading) "Leyendo el certificado" else "Datos extraídos",
            detail = when {
                reading -> "Con ML Kit, en tu dispositivo"
                ocrFailed -> "No se pudo leer el texto. Completa los datos a mano"
                else -> listOf(institution, course, issueDate).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifEmpty { "Revisa los campos de arriba" }
            },
            inProgress = reading
        )
        ProgressStep(
            status = steps[1],
            title = "Sin duplicados",
            detail = when (steps[1]) {
                StepStatus.Done -> "Es un certificado nuevo en SkillSwap"
                StepStatus.Failed -> "No se pudo registrar el certificado"
                StepStatus.Active -> "Registrando en SkillSwap…"
                StepStatus.Pending -> "Se revisa al enviarlo"
            },
            inProgress = sending
        )
        ProgressStep(
            status = steps[2],
            title = "Comparando con el nodo ${node?.skillName ?: ""}".trim(),
            detail = when (steps[2]) {
                StepStatus.Done -> "Cubre la habilidad (afinidad ${"%.2f".format(viewModel.certificateLink?.affinity ?: 0.0)})"
                StepStatus.Failed -> "No cubre esta habilidad"
                StepStatus.Active -> "En curso"
                StepStatus.Pending -> "Pendiente"
            },
            inProgress = sending
        )
        ProgressStep(
            status = steps[3],
            title = "Análisis de riesgo documental",
            detail = if (steps[3] == StepStatus.Pending) "Pendiente"
            else "${riskLevelLabel(certificate?.riskLevel)} · ${certificateStatusLabel(certificate?.status)}",
            isLast = true
        )

        if (resultText.isNotEmpty()) {
            OutlinedSurface(modifier = Modifier.padding(top = 20.dp)) {
                Text(text = resultText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }

        // 422: el certificado no cubre este nodo; se muestran los nodos de la ruta que sí cubre
        val mismatch = viewModel.certificateMismatch
        if (mismatch != null) {
            val nodes = learningPathViewModel.path?.nodes ?: emptyList()
            MismatchCard(
                skillName = node?.skillName ?: "este nodo",
                mismatch = mismatch,
                nodeName = { id -> nodes.firstOrNull { it.id == id }?.skillName },
                linking = linking,
                onUse = { nodeId ->
                    linking = true
                    // Se vincula el certificado ya subido al nodo elegido (la red va en hilo IO)
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val ok = viewModel.linkCertificate(nodeId)
                        withContext(Dispatchers.Main) {
                            linking = false
                            if (ok) {
                                Toast.makeText(context, "Certificado vinculado a ese nodo", Toast.LENGTH_LONG).show()
                                // selectedNode ya es el nodo vinculado: se abre su detalle
                                nav.navigate("Node") { popUpTo("Node") { inclusive = true } }
                            } else {
                                Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
    }

    // Calendario para la fecha de emisión: no deja elegir fechas futuras
    if (showDatePicker) {
        val initial = try {
            LocalDate.parse(issueDate.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        issueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) {
                    Text(text = "Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "Cancelar")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

}

// El certificado no cubre el nodo actual: lista los nodos de la ruta que sí cubre y explica qué hacer después
@Composable
private fun MismatchCard(
    skillName: String,
    mismatch: CertificateMismatch,
    nodeName: (Int) -> String?,
    linking: Boolean,
    onUse: (Int) -> Unit
) {
    val suggestions = mismatch.suggestedNodes ?: emptyList()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(SkillBlueContainer, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Este certificado no cubre «$skillName»",
            style = MaterialTheme.typography.titleMedium,
            color = SkillBlueDark
        )
        val threshold = mismatch.threshold
        if (mismatch.affinity != null && threshold != null) {
            Text(
                text = "Afinidad ${"%.0f".format((mismatch.affinity ?: 0.0) * 100)} % " +
                    "(mínimo ${"%.0f".format(threshold * 100)} %).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (suggestions.isNotEmpty()) {
            Text(
                text = "Encaja con estos nodos de tu ruta:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            suggestions.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = (nodeName(item.nodeId) ?: item.skillTag) +
                            " · afinidad ${"%.0f".format(item.affinity * 100)} %",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(enabled = !linking, onClick = { onUse(item.nodeId) }) {
                        Text(text = if (linking) "Vinculando…" else "Usar en este nodo")
                    }
                }
            }
        }
        Text(
            text = "Qué hacer ahora",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = (if (suggestions.isNotEmpty())
                "Toca «Usar en este nodo» para vincular este mismo certificado a ese nodo, o "
            else "") +
                "vuelve y sube un certificado de «$skillName». También puedes rendir el quiz o entregar " +
                "el proyecto de este nodo sin certificado.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
