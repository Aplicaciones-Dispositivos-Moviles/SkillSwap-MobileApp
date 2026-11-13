package com.innovify.skillswap.shared.presentation.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.innovify.skillswap.shared.presentation.theme.SkillBlue
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary

/** Figma side margin of the student screens. */
val ScreenGutter = 20.dp

/**
 * Content padding of a scrolling tab: the Scaffold insets (status bar, bottom bar) plus the Figma gutter.
 * [extraBottom] leaves room for a floating button.
 */
fun tabContentPadding(inner: PaddingValues, extraBottom: Dp = 0.dp): PaddingValues = PaddingValues(
    start = inner.calculateStartPadding(LayoutDirection.Ltr) + ScreenGutter,
    end = inner.calculateEndPadding(LayoutDirection.Ltr) + ScreenGutter,
    top = inner.calculateTopPadding() + 16.dp,
    bottom = inner.calculateBottomPadding() + 24.dp + extraBottom
)

/** Status pill with an optional leading icon ("✓ Validado", "En revisión"); never color alone. */
@Composable
fun StatePill(text: String, color: Color, container: Color, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Rounded square holding an icon (certificate and assessment cards). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, container: Color, size: Dp = 40.dp) {
    Box(
        modifier = Modifier.size(size).background(container, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Centered spinner with a short text, for the first load of a screen. */
@Composable
fun LoadingBlock(text: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp)
    ) {
        CircularProgressIndicator(color = SkillBlue)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** Empty or error state: icon, title, explanation and an optional action. */
@Composable
fun MessageBlock(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = SkillBlueDark,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null
) {
    OutlinedSurface(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconTile(icon, tint = tint, container = tint.copy(alpha = 0.1f), size = 48.dp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = SkillBlueDark),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)
                ) { Text(actionLabel) }
            }
        }
    }
}

/**
 * Row of a settings-like list ("Cuenta" in the profile): icon, title, optional subtitle and a trailing slot
 * (a chevron by default when it is clickable). At least 56dp tall.
 */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val clickable = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = clickable.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == TextPrimary) TextSecondary else tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}
