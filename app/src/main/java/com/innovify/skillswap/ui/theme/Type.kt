package com.innovify.skillswap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.innovify.skillswap.R

// Inter (archivos en res/font, licencia SIL OFL)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

private fun style(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp
)

// Escala del Design System: Display 28/36 ExtraBold, H1 24/32 y H2 20/28 Bold, Title 16/24 SemiBold,
// Body 16/24 y Body-S 14/20 Regular, Label 14/20 y Caption 12/16 Medium.
val Typography = Typography(
    displaySmall = style(FontWeight.ExtraBold, 28, 36),
    headlineMedium = style(FontWeight.ExtraBold, 28, 36),
    headlineSmall = style(FontWeight.Bold, 24, 32),
    titleLarge = style(FontWeight.Bold, 20, 28),
    titleMedium = style(FontWeight.SemiBold, 16, 24),
    titleSmall = style(FontWeight.SemiBold, 14, 20),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Normal, 12, 16),
    labelLarge = style(FontWeight.Medium, 14, 20),
    labelMedium = style(FontWeight.Medium, 12, 16),
    labelSmall = style(FontWeight.Medium, 12, 16)
)
