package com.innovify.skillswap.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Fecha del backend (UTC, ISO-8601, normalmente con "Z") -> hora local del teléfono (dd/MM/yyyy HH:mm).
// Si no se puede leer, se muestra tal cual. La hora sigue la zona del teléfono (el emulador usa la suya).
fun localDate(iso: String?): String {
    if (iso == null) return "—"
    return try {
        val instant = try {
            Instant.parse(iso)
        } catch (e: Exception) {
            LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) // sin "Z": el backend siempre manda UTC
        }
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(instant)
    } catch (e: Exception) {
        iso
    }
}

// Barra superior de las pantallas internas: flecha de volver + título (mock-ups 06, 08, 20)
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}

// Chip de estado con fondo suave y texto (nunca solo color), p. ej. "Disponible" o "Completado"
@Composable
fun StatusChip(
    text: String,
    color: Color = Success,
    container: Color = SuccessContainer
) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Marcador de un paso de la ruta (mock-up 07): completado (check), actual (número) o bloqueado (candado)
enum class StepState { Completed, Current, Locked }

@Composable
fun StepMarker(state: StepState, number: Int) {
    val container = when (state) {
        StepState.Completed -> Success
        StepState.Current -> SkillBlue
        StepState.Locked -> NeutralSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .background(container, CircleShape)
    ) {
        when (state) {
            StepState.Completed -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Completado",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            StepState.Current -> Text(
                text = number.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            StepState.Locked -> Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Bloqueado",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
