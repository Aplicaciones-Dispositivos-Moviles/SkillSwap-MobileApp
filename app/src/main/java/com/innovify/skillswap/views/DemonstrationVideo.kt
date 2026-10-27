package com.innovify.skillswap.views

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.MAX_VIDEO_BYTES
import com.innovify.skillswap.model.beans.MAX_VIDEO_SECONDS
import com.innovify.skillswap.model.beans.modalityLabel
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// US29 · Grabación y subida del video de la demostración final.
// Usa la cámara del sistema (sin dependencias nuevas), pidiendo antes los permisos de cámara y micrófono.
@Composable
fun DemonstrationVideo(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val demo = viewModel.demonstration
    // Rutas guardadas con rememberSaveable: la app de cámara puede hacer que Android recree esta pantalla
    var recordingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var videoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    val video = videoPath?.let { File(it) }?.takeIf { it.exists() }
    val tooBig = video != null && video.length() > MAX_VIDEO_BYTES

    // ---------------------------------------------------------------- CÁMARA DE VIDEO
    val record = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recorded = recordingPath?.let { File(it) }
        if (result.resultCode == Activity.RESULT_OK && recorded != null && recorded.exists() && recorded.length() > 0) {
            videoPath?.let { File(it).delete() } // reemplaza la grabación anterior
            videoPath = recorded.path
        } else {
            recorded?.delete()
        }
        recordingPath = null
    }

    // El video se guarda en cache/demonstrations y se comparte con la cámara por FileProvider
    fun openCamera() {
        val folder = File(context.cacheDir, "demonstrations").apply { mkdirs() }
        val file = File(folder, "demo_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        recordingPath = file.path
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, MAX_VIDEO_SECONDS)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0) // calidad baja: cabe en los 25 MB del backend
            putExtra(MediaStore.EXTRA_SIZE_LIMIT, MAX_VIDEO_BYTES)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            record.launch(intent)
        } catch (e: ActivityNotFoundException) {
            recordingPath = null
            Toast.makeText(context, "No hay una aplicación de cámara para grabar video", Toast.LENGTH_LONG).show()
        }
    }

    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            openCamera()
        } else {
            Toast.makeText(
                context, "Se necesitan los permisos de cámara y micrófono para grabar tu demostración",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------------------------------------------------------- PANTALLA
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Video de tu demostración", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            if (demo == null || !demo.canUploadVideo()) {
                Text(
                    text = "Primero programa tu demostración final. El video se sube después de programarla.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { nav.popBackStack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .height(52.dp)
                ) { Text(text = "Volver") }
            } else {
                Text(text = modalityLabel(demo.modality), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Graba un video corto de tu demostración. Debe verse y oírse con claridad que eres tú quien lo realiza.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Hasta ${MAX_VIDEO_SECONDS / 60} minutos y 25 MB. Se graba en calidad baja para que quepa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                if (demo.hasVideo) {
                    Text(
                        text = "Ya enviaste un video el ${localDate(demo.videoSubmittedAt)}. Puedes reemplazarlo hasta que el Verificador lo revise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                OutlinedButton(
                    enabled = !sending,
                    onClick = {
                        val granted = permissions.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (granted) openCamera() else permissionLauncher.launch(permissions)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) { Text(text = if (video == null) "Grabar video" else "Grabar de nuevo") }

                if (video != null) {
                    Text(
                        text = "Video grabado: " + String.format("%.1f MB", video.length() / 1048576.0),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    if (tooBig) {
                        Text(
                            text = "Pesa más de 25 MB. Graba uno más corto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                    Button(
                        enabled = !sending && !tooBig && viewModel.connectivity.isOnline,
                        onClick = {
                            sending = true
                            // La red va en hilo IO (como en la plantilla del profe)
                            viewModel.viewModelScope.launch(Dispatchers.IO) {
                                val ok = viewModel.uploadDemonstrationVideo(video)
                                withContext(Dispatchers.Main) {
                                    sending = false
                                    if (ok) {
                                        video.delete()
                                        videoPath = null
                                        Toast.makeText(
                                            context, "Video enviado. El Verificador lo revisará", Toast.LENGTH_LONG
                                        ).show()
                                        nav.popBackStack()
                                    } else {
                                        Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(52.dp)
                    ) {
                        Text(
                            text = when {
                                sending -> "Subiendo…"
                                !viewModel.connectivity.isOnline -> "Necesitas internet para subir"
                                else -> "Subir video"
                            }
                        )
                    }
                    if (sending) {
                        Text(
                            text = "Puede tardar según tu conexión. No cierres la pantalla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

}
