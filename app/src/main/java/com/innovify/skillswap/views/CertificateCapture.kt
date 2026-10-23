package com.innovify.skillswap.views

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.CertificateMismatch
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.viewmodel.CertificateOcr
import com.innovify.skillswap.viewmodel.CertificateParser
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// T44 (captura con la cámara) + T45 (OCR con ML Kit y envío al backend).
// El certificado puede venir de la cámara o de un archivo (PDF o imagen) elegido del teléfono.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateCapture(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode

    var photoUri: Uri? by rememberSaveable { mutableStateOf(null) }  // foto o archivo ya elegido
    var cameraUri: Uri? by rememberSaveable { mutableStateOf(null) } // destino que se le pasa a la cámara
    var institution by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("") }
    var issueDate by rememberSaveable { mutableStateOf("") }
    var ocrText by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }

    // Lee el texto del archivo elegido (foto, imagen o PDF) y rellena los campos editables.
    fun loadEvidence(uri: Uri) {
        photoUri = uri
        resultText = ""
        viewModel.certificateMismatch = null
        reading = true
        // OCR on-device (ML Kit). El callback llega en el hilo principal.
        CertificateOcr.recognize(context, uri) { text ->
            reading = false
            if (text == null) {
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

    val preview = remember(photoUri) { photoUri?.let { CertificateOcr.loadBitmap(context, it, 800) } }

    // ---------------------------------------------------------------- PANTALLA
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Subir certificado", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)
        Text(
            text = "Habilidad: ${node?.skillName ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp)
        )

        Button(
            enabled = !reading && !sending,
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) openCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp)
                .height(52.dp)
        ) {
            Text(text = if (photoUri == null) "Tomar foto del certificado" else "Tomar otra foto")
        }

        OutlinedButton(
            enabled = !reading && !sending,
            onClick = { pickFile.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp)
                .height(52.dp)
        ) {
            Text(text = "Elegir archivo (PDF o imagen)")
        }

        preview?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Vista previa del certificado",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(start = 8.dp, top = 16.dp)
            )
        }

        if (reading) {
            Text(
                text = "Verificando tu certificado",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp)
            )
            Text(
                text = "Lo leemos en tu dispositivo con ML Kit. Toma unos segundos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        val uri = photoUri
        if (uri != null && !reading) {
            Text(
                text = "Revisa los datos leídos y corrígelos si hace falta",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
            )

            OutlinedTextField(
                value = institution,
                label = { Text(text = "Institución") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                onValueChange = { institution = it }
            )
            OutlinedTextField(
                value = course,
                label = { Text(text = "Curso") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 12.dp),
                onValueChange = { course = it }
            )
            // La fecha se elige en un calendario (siempre queda como AAAA-MM-DD); el OCR la precarga si la leyó
            OutlinedTextField(
                value = issueDate,
                label = { Text(text = "Fecha de emisión (AAAA-MM-DD)") },
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(text = "Elegir")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 12.dp),
                onValueChange = { }
            )

            // ---------------- ENVÍO AL BACKEND ----------------
            Button(
                enabled = !sending,
                onClick = {
                    val validDate = Regex("\\d{4}-\\d{2}-\\d{2}").matches(issueDate.trim())
                    if (institution.isBlank() || course.isBlank() || !validDate) {
                        Toast.makeText(
                            context, "Completa institución, curso y fecha (AAAA-MM-DD)", Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        sending = true
                        resultText = ""
                        // La red va en hilo IO (como en la plantilla del profe)
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            // T48: primero se guarda una copia en el teléfono, haya internet o no
                            val localId = viewModel.saveEvidence(
                                context, uri, node?.id ?: 0,
                                institution.trim(), course.trim(), issueDate.trim(), ocrText
                            )
                            val uploaded = localId >= 0 && viewModel.connectivity.isOnline &&
                                viewModel.uploadCertificate(
                                    context, uri, institution.trim(), course.trim(), issueDate.trim(),
                                    ocrText, localId
                                )
                            val message = if (localId < 0) {
                                viewModel.errorMessage
                            } else if (!viewModel.connectivity.isOnline) {
                                // T49: sin internet el certificado queda pendiente (el reenvío es T51)
                                "Sin conexión.\nEl certificado quedó guardado en tu teléfono " +
                                    "y se enviará cuando vuelva internet."
                            } else if (!uploaded) {
                                viewModel.errorMessage + "\nEl certificado quedó guardado en tu teléfono."
                            } else {
                                val cert = viewModel.certificate
                                val summary = "Certificado registrado.\nEstado: ${cert?.status}" +
                                    "\nRiesgo: ${cert?.riskLevel ?: "—"}"
                                val linked = node != null && viewModel.linkCertificate(node.id)
                                if (linked) {
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
                                resultText = message
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 24.dp)
                    .height(52.dp)
            ) {
                Text(text = if (sending) "Enviando…" else "Enviar certificado")
            }
        }

        if (resultText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 16.dp)
            ) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // 422: el certificado no cubre este nodo; se muestran los nodos de la ruta que sí cubre
        val mismatch = viewModel.certificateMismatch
        if (mismatch != null) {
            val nodes = viewModel.path?.nodes ?: emptyList()
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
    Card(
        colors = CardDefaults.cardColors(containerColor = SkillBlueContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Este certificado no cubre «$skillName»",
                style = MaterialTheme.typography.titleMedium,
                color = SkillBlue
            )
            val threshold = mismatch.threshold
            if (mismatch.affinity != null && threshold != null) {
                Text(
                    text = "Afinidad ${"%.0f".format((mismatch.affinity ?: 0.0) * 100)} % " +
                        "(mínimo ${"%.0f".format(threshold * 100)} %).",
                    style = MaterialTheme.typography.bodySmall,
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
                    Text(
                        text = (nodeName(item.nodeId) ?: item.skillTag) +
                            " · afinidad ${"%.0f".format(item.affinity * 100)} %",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        enabled = !linking,
                        onClick = { onUse(item.nodeId) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(text = if (linking) "Vinculando…" else "Usar en este nodo")
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
}
