package com.innovify.skillswap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SkillBlue,
    onPrimary = Color.White,
    primaryContainer = SkillBlueContainer,
    onPrimaryContainer = SkillBlueDark,
    secondary = SkillBlueDark,
    onSecondary = Color.White,
    secondaryContainer = SkillBlueContainer,
    onSecondaryContainer = SkillBlueDark,
    tertiary = AiViolet,
    onTertiary = Color.White,
    tertiaryContainer = AiContainer,
    onTertiaryContainer = AiViolet,
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = NeutralSurface,
    onSurfaceVariant = TextSecondary,
    outline = NeutralBorder,
    outlineVariant = NeutralBorder,
    error = Error,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Error
)

// Radios del Design System: 8 chips, 12 inputs, 16 cards (los botones usan forma de píldora, ~26)
private val SkillShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
)

@Composable
fun SkillSwapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = SkillShapes,
        content = content
    )
}
