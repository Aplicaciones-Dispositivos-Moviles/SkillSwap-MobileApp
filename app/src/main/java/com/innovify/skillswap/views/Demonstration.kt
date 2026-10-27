package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.FinalDemonstration
import com.innovify.skillswap.model.beans.demonstrationStatusLabel
import com.innovify.skillswap.model.beans.isFarEnoughAhead
import com.innovify.skillswap.model.beans.modalityLabel
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.ErrorContainer
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.ui.theme.Warning
import com.innovify.skillswap.ui.theme.WarningContainer
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val TOO_SOON = "Elige un horario con al menos 24 horas de anticipación"

// US28 · Programación de la demostración final: modalidad, fecha y hora; luego muestra su estado y el resultado.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Demonstration(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    // true: tras una demostración rechazada, el Estudiante quiere programar otra
    var scheduleAnother by remember { mutableStateOf(false) }
    var modality by rememberSaveable { mutableStateOf("AdvancedProject") }
    var chosen by remember { mutableStateOf<LocalDateTime?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    // Qué calendario está abierto: "schedule" (formulario) o "reschedule" (mover la programada)
    var pickFor by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = null
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadDemonstration()
            withContext(Dispatchers.Main) {
                loading = false
                error = if (ok) null else viewModel.errorMessage
            }
        }
    }

    // Ejecuta una acción de red y avisa el resultado
    fun act(success: String, action: suspend () -> Boolean) {
        working = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = action()
            withContext(Dispatchers.Main) {
                working = false
                Toast.makeText(context, if (ok) success else viewModel.errorMessage, Toast.LENGTH_LONG).show()
                if (ok) {
                    scheduleAnother = false
                    chosen = null
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val demo = viewModel.demonstration
    val showForm = demo == null || (scheduleAnother && demo.status == "Rejected")

    if (pickFor != null) {
        DateTimeDialogs(
            initial = chosen ?: LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0),
            onDismiss = { pickFor = null },
            onPicked = { moment ->
                val target = pickFor
                pickFor = null
                if (target == "reschedule") {
                    val instant = moment.atZone(ZoneId.systemDefault()).toInstant()
                    if (!isFarEnoughAhead(instant)) {
                        Toast.makeText(context, TOO_SOON, Toast.LENGTH_LONG).show()
                    } else {
                        act("Demostración reprogramada") { viewModel.rescheduleDemonstration(instant) }
                    }
                } else {
                    chosen = moment
                    formError = null
                }
            }
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(text = "Cancelar la demostración") },
            text = { Text(text = "Podrás programar otra después. ¿Cancelar esta demostración?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    act("Demostración cancelada") { viewModel.cancelDemonstration() }
                }) { Text(text = "Sí, cancelar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(text = "Volver") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Demostración final", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            if (loading) {
                Text(text = "Cargando…", style = MaterialTheme.typography.bodyLarge)
            } else if (error != null) {
                Text(text = error ?: "", style = MaterialTheme.typography.bodyMedium, color = Error)
                Button(
                    enabled = viewModel.connectivity.isOnline,
                    onClick = { load() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp)
                ) { Text(text = if (viewModel.connectivity.isOnline) "Reintentar" else "Necesitas internet") }
            } else if (showForm) {
                // ---------------------------------------------------- PROGRAMAR
                Text(text = "Programa tu demostración", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Demuestra el dominio de tu ruta completa. Un Verificador habilitado en tu habilidad te evaluará.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Text(
                    text = "Modalidad",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ModalityCard(
                    selected = modality == "AdvancedProject",
                    enabled = !working,
                    title = modalityLabel("AdvancedProject"),
                    description = "Muestras un proyecto avanzado que integra las habilidades de tu ruta.",
                    onSelect = { modality = "AdvancedProject" }
                )
                ModalityCard(
                    selected = modality == "SupervisedExam",
                    enabled = !working,
                    title = modalityLabel("SupervisedExam"),
                    description = "Resuelves un examen supervisado por el Verificador.",
                    onSelect = { modality = "SupervisedExam" }
                )

                Text(
                    text = "Fecha y hora",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                OutlinedButton(
                    enabled = !working,
                    onClick = { pickFor = "schedule" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = chosen?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            ?: "Elegir fecha y hora"
                    )
                }
                Text(
                    text = formError ?: "Con al menos 24 horas de anticipación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (formError != null) Error else TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Button(
                    enabled = !working && viewModel.connectivity.isOnline,
                    onClick = {
                        val moment = chosen
                        if (moment == null) {
                            formError = "Elige la fecha y la hora de tu demostración"
                        } else {
                            val instant = moment.atZone(ZoneId.systemDefault()).toInstant()
                            if (!isFarEnoughAhead(instant)) {
                                formError = TOO_SOON
                            } else {
                                act("Demostración programada") { viewModel.scheduleDemonstration(modality, instant) }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .height(52.dp)
                ) {
                    Text(
                        text = when {
                            working -> "Programando…"
                            !viewModel.connectivity.isOnline -> "Necesitas internet para programar"
                            else -> "Programar demostración"
                        }
                    )
                }
                if (demo != null) {
                    TextButton(onClick = { scheduleAnother = false }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(text = "Volver al resultado")
                    }
                }
            } else if (demo != null) {
                // ---------------------------------------------------- ESTADO
                val look = lookOf(demo)
                StatusChip(text = look.label, color = look.color, container = look.container)
                Text(
                    text = modalityLabel(demo.modality),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = look.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = NeutralSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("Fecha y hora", localDate(demo.scheduledAt))
                        DetailRow("Habilidad", demo.skillTag.replace('-', ' '))
                        DetailRow("Verificador", "Verificador #${demo.verifierUserId}")
                        if (demo.hasVideo) DetailRow("Video enviado", localDate(demo.videoSubmittedAt))
                        if (demo.isReviewed()) {
                            DetailRow("Puntaje", "${demo.score ?: "—"} / 100")
                            DetailRow("Comentarios", demo.feedback?.ifBlank { null } ?: "Sin comentarios")
                        }
                    }
                }

                if (demo.canUploadVideo()) {
                    Button(
                        enabled = !working,
                        onClick = { nav.navigate("DemonstrationVideo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .height(52.dp)
                    ) { Text(text = if (demo.hasVideo) "Reemplazar video" else "Grabar y subir video") }
                }
                if (demo.canReschedule()) {
                    OutlinedButton(
                        enabled = !working && viewModel.connectivity.isOnline,
                        onClick = { pickFor = "reschedule" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(52.dp)
                    ) { Text(text = "Reprogramar") }
                    TextButton(
                        enabled = !working && viewModel.connectivity.isOnline,
                        onClick = { confirmCancel = true },
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text(text = "Cancelar demostración", color = Error) }
                }
                if (demo.status == "Rejected") {
                    Button(
                        onClick = { scheduleAnother = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .height(52.dp)
                    ) { Text(text = "Programar otra demostración") }
                }
                TextButton(enabled = !working, onClick = { load() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "Actualizar estado")
                }
            }
        }
    }

}

// Texto y colores del estado (nunca solo color: el chip siempre lleva texto)
private class StatusLook(val label: String, val message: String, val color: Color, val container: Color)

private fun lookOf(demo: FinalDemonstration): StatusLook = when (demo.status) {
    "Scheduled" -> StatusLook(
        "Programada", "Cuando estés listo, graba y sube el video de tu demostración.", SkillBlue, SkillBlueContainer
    )
    "VideoSubmitted" -> StatusLook(
        "En revisión", "El Verificador está revisando tu video. Puedes reemplazarlo hasta que lo revise.",
        Warning, WarningContainer
    )
    "Approved" -> StatusLook("Aprobada", "¡Aprobaste tu demostración final!", Success, SuccessContainer)
    "Rejected" -> StatusLook(
        "Rechazada", "El Verificador no aprobó tu demostración. Puedes programar otra.", Error, ErrorContainer
    )
    else -> StatusLook(demonstrationStatusLabel(demo.status), "", TextSecondary, NeutralSurface)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.4f)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

// Tarjeta de una modalidad: radio + nombre + descripción
@Composable
private fun ModalityCard(selected: Boolean, enabled: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SkillBlueContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SkillBlue else NeutralBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable(enabled = enabled, onClick = onSelect)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
            Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// Primero el calendario (desde mañana) y luego la hora. Devuelve la fecha y hora elegidas en la zona del teléfono.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialogs(initial: LocalDateTime, onDismiss: () -> Unit, onPicked: (LocalDateTime) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var date by remember { mutableStateOf(initial.toLocalDate()) }

    if (step == 0) {
        val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        step = 1
                    }
                }) { Text(text = "Siguiente") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancelar") } }
        ) {
            DatePicker(state = state)
        }
    } else {
        val time = rememberTimePickerState(initialHour = initial.hour, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Elige la hora") },
            text = { TimePicker(state = time) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(LocalDateTime.of(date, LocalTime.of(time.hour, time.minute)))
                }) { Text(text = "Aceptar") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(text = "Atrás") } }
        )
    }
}
