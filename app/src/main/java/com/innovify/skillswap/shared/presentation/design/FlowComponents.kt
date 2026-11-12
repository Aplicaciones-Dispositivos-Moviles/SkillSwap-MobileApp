package com.innovify.skillswap.shared.presentation.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.NeutralSurface
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary

// Building blocks of the full-screen flow steps (goal, node, certificate, quiz, result, case, mini-project).
// Figma: white screens, 20dp side gutter, back/close icon + title, primary action pinned at the bottom.

/** Horizontal padding of every flow screen. */
val FlowGutter = 20.dp

/** Height of the primary and secondary actions (touch target well above 48dp). */
val FlowButtonHeight = 52.dp

/**
 * Full-screen layout of a flow step: [topBar], scrollable [content] with the 20dp gutter and an optional
 * [bottomBar] pinned to the bottom. System bars and the keyboard push the bottom bar up (imePadding), so the
 * primary action never hides behind them.
 */
@Composable
fun FlowScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        topBar()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = FlowGutter, end = FlowGutter, bottom = 24.dp),
            content = content
        )
        if (bottomBar != null) {
            // Hairline only while there is more content under the pinned actions
            if (scrollState.canScrollForward) HorizontalDivider(color = CardBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = FlowGutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = bottomBar
            )
        }
    }
}

/** Top bar of a flow step: back arrow (or close X), title with optional subtitle and trailing actions. */
@Composable
fun FlowTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    close: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = if (close) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (close) "Cerrar" else "Volver",
                tint = TextPrimary
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        actions()
    }
}

/** Large heading with an optional supporting line ("¿Qué quieres aprender?"). */
@Composable
fun FlowHeadline(title: String, subtitle: String? = null, modifier: Modifier = Modifier, center: Boolean = false) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = align,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = align,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Pill chip with an optional leading icon ("Asistente de rutas con IA", "En revisión"). */
@Composable
fun FlowChip(text: String, color: Color, container: Color, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Filled pill button of a flow; [loading] swaps the leading icon for a spinner. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = SkillBlue),
        modifier = modifier
            .fillMaxWidth()
            .height(FlowButtonHeight)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = spinnerColor(enabled),
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Spinner color that stays visible on the enabled (white text) and disabled (grey text) button
private fun spinnerColor(enabled: Boolean): Color = if (enabled) Color.White else TextSecondary

/** Outlined pill button of a flow (secondary action next to or under the primary one). */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, if (enabled) TextPrimary.copy(alpha = 0.6f) else NeutralBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        modifier = modifier
            .fillMaxWidth()
            .height(FlowButtonHeight)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Square icon on a tinted rounded background (Figma action tiles and evidence rows). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = SkillBlueDark, container: Color = SkillBlueContainer, size: Int = 44) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .background(container, RoundedCornerShape(12.dp))
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** Tappable card with an icon, a title and a short hint ("Tomar foto" / "Subir archivo"). */
@Composable
fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, disabledContainerColor = NeutralSurface),
        border = BorderStroke(1.dp, NeutralBorder),
        modifier = modifier.heightIn(min = 136.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            IconBadge(icon)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Soft card with a leading icon, an optional title and a body ("Certificados aceptados"). */
@Composable
fun InfoCard(
    icon: ImageVector,
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = SkillBlueDark,
    container: Color = NeutralSurface,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (title != null) TextSecondary else TextPrimary,
                modifier = Modifier.padding(top = if (title != null) 2.dp else 0.dp)
            )
            content()
        }
    }
}

/** Small lock + caption about privacy ("El texto se lee en tu dispositivo con ML Kit…"). */
@Composable
fun PrivacyNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Centered spinner with a message, for screens that wait on the backend or the AI. */
@Composable
fun FlowLoading(text: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator(color = SkillBlue)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** State of a step in a progress checklist or a vertical status timeline. */
enum class StepStatus { Done, Active, Pending, Failed }

/**
 * One step of a checklist/timeline: a 24dp marker (check, sync, empty circle or X), a title and a detail line.
 * With [connector] the marker is joined to the next step by a vertical line (case timeline).
 * [inProgress] shows a spinner for an [StepStatus.Active] step that the phone is working on right now.
 */
@Composable
fun ProgressStep(
    status: StepStatus,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    isLast: Boolean = false,
    connector: Boolean = false,
    inProgress: Boolean = false
) {
    val titleColor = when (status) {
        StepStatus.Active -> SkillBlueDark
        StepStatus.Pending -> TextSecondary
        StepStatus.Failed -> Error
        StepStatus.Done -> TextPrimary
    }
    val detailColor = if (status == StepStatus.Active) SkillBlue else TextSecondary
    Row(modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            StepMarker(status, inProgress)
            if (connector && !isLast) {
                Box(
                    Modifier
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .height(36.dp)
                        .background(if (status == StepStatus.Done) Success else NeutralBorder)
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = if (isLast) 0.dp else if (connector) 4.dp else 18.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = detailColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StepMarker(status: StepStatus, inProgress: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        when (status) {
            StepStatus.Done -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .background(Success, CircleShape)
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Hecho", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            StepStatus.Failed -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .background(Error, CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Con problemas", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            StepStatus.Active -> if (inProgress) {
                CircularProgressIndicator(color = SkillBlue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(FlowIcons.Sync, contentDescription = "En curso", tint = SkillBlue, modifier = Modifier.size(22.dp))
            }
            StepStatus.Pending -> Box(
                Modifier
                    .size(20.dp)
                    .border(2.dp, TextSecondary.copy(alpha = 0.7f), CircleShape)
            )
        }
    }
}

/**
 * Icons from the Material set that are not part of material-icons-core (no new dependency):
 * the original 24dp path data, built once on first use.
 */
object FlowIcons {
    val Sparkle: ImageVector by lazy {
        icon(
            "Sparkle",
            "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9z" +
                "M11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5z" +
                "M19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
        )
    }
    val Camera: ImageVector by lazy {
        icon(
            "Camera",
            "M12,8.8a3.2,3.2 0,1 0,0 6.4a3.2,3.2 0,1 0,0 -6.4z",
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
        )
    }
    val Upload: ImageVector by lazy { icon("Upload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }
    val Flag: ImageVector by lazy { icon("Flag", "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z") }
    val Verified: ImageVector by lazy {
        icon(
            "Verified",
            "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z" +
                "M10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z"
        )
    }
    val CloudDone: ImageVector by lazy {
        icon(
            "CloudDone",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
                "M10,17l-3.5,-3.5 1.41,-1.41L10,14.18 15.18,9l1.41,1.41L10,17z"
        )
    }
    val Link: ImageVector by lazy {
        icon(
            "Link",
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1" +
                "s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"
        )
    }
    val Document: ImageVector by lazy {
        icon(
            "Document",
            "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6z" +
                "M16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"
        )
    }
    val Sync: ImageVector by lazy {
        icon(
            "Sync",
            "M12,6v3l4,-4 -4,-4v3c-4.42,0 -8,3.58 -8,8 0,1.57 0.46,3.03 1.24,4.26L6.7,14.8" +
                "c-0.45,-0.83 -0.7,-1.79 -0.7,-2.8 0,-3.31 2.69,-6 6,-6z" +
                "M18.76,7.74L17.3,9.2c0.44,0.84 0.7,1.79 0.7,2.8 0,3.31 -2.69,6 -6,6v-3l-4,4 4,4v-3" +
                "c4.42,0 8,-3.58 8,-8 0,-1.57 -0.46,-3.03 -1.24,-4.26z"
        )
    }

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()
}
