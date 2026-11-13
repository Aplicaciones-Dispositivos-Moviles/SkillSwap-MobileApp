package com.innovify.skillswap.shared.presentation.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary

// Figma: screens use #F7F7FB behind white cards with a #E5E7EB hairline border.
val ScreenBackground = Color(0xFFF7F7FB)
val CardBorder = Color(0xFFE5E7EB)
val MutedText = Color(0xFF8A919E)

/** The four top-level destinations of the Student app (Figma bottom navigation). */
enum class StudentTab(val label: String, val icon: ImageVector) {
    Path("Mi ruta", Icons.Filled.Home),
    Certificates("Certificados", Icons.Filled.Star),
    Assessments("Evaluaciones", Icons.AutoMirrored.Filled.List),
    Profile("Perfil", Icons.Filled.Person)
}

/** Bottom navigation shared by the four top-level screens. */
@Composable
fun StudentBottomBar(selected: StudentTab, onSelect: (StudentTab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        StudentTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(
                        tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SkillBlueDark,
                    selectedTextColor = SkillBlueDark,
                    indicatorColor = SkillBlueContainer,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}

/** Large screen title used by the top-level tabs ("Mis certificados", "Evaluaciones", "Perfil"). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = TextPrimary,
        modifier = modifier.padding(top = 8.dp, bottom = 12.dp)
    )
}

/** Section heading with an optional trailing hint ("Tu ruta · Sincronizado hace 2 min"). */
@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** White card with the Figma hairline border; clickable when [onClick] is set. */
@Composable
fun OutlinedSurface(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    containerColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

/** Circle with the user's initials (Figma avatar "VR"). */
@Composable
fun InitialsAvatar(name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).background(SkillBlueContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name),
            color = SkillBlueDark,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Up to two initials from a full name or username ("Valeria Ramos" → "VR", "estudiante.prueba" → "EP"). */
fun initialsOf(name: String): String =
    name.split(' ', '.', '_', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** First name for greetings ("Valeria Ramos Quispe" → "Valeria"). */
fun firstNameOf(fullName: String?, fallback: String): String =
    fullName?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() } ?: fallback

/** State of a step in a vertical timeline (learning path nodes, case progress). */
enum class TimelineState { Done, Current, Locked, Pending }

/**
 * One row of a vertical timeline: a marker (check, number or lock) joined to the next row by a line.
 * [content] is drawn to the right of the marker.
 */
@Composable
fun TimelineRow(
    state: TimelineState,
    number: Int?,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val rowModifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(rowModifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
            TimelineMarker(state, number)
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(if (state == TimelineState.Done) SkillBlue else NeutralBorder)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 12.dp), content = content)
    }
}

@Composable
private fun TimelineMarker(state: TimelineState, number: Int?) {
    val (container, content) = when (state) {
        TimelineState.Done -> Success to Color.White
        TimelineState.Current -> SkillBlueDark to Color.White
        TimelineState.Locked -> Color(0xFFE5E7EB) to TextSecondary
        TimelineState.Pending -> Color.White to TextSecondary
    }
    val base = Modifier.size(36.dp).background(container, CircleShape)
    val marker = if (state == TimelineState.Pending) base.border(2.dp, NeutralBorder, CircleShape) else base
    val ring = if (state == TimelineState.Current) Modifier.border(4.dp, SkillBlueContainer, CircleShape).padding(4.dp) else Modifier
    Box(ring, contentAlignment = Alignment.Center) {
        Box(marker, contentAlignment = Alignment.Center) {
            when (state) {
                TimelineState.Done -> Icon(Icons.Filled.Check, contentDescription = "Completado", tint = content, modifier = Modifier.size(20.dp))
                TimelineState.Locked -> Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = content, modifier = Modifier.size(18.dp))
                else -> Text(number?.toString() ?: "", color = content, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Small numbers card used in rows of three ("2 de 5 · Nodos aprobados"). */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    OutlinedSurface(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** Horizontal row that spaces children evenly (used for stat tiles). */
@Composable
fun EvenRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
