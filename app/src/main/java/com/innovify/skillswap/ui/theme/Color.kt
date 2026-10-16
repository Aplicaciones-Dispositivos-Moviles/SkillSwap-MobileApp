package com.innovify.skillswap.ui.theme

import androidx.compose.ui.graphics.Color

// Design System de SkillSwap (informe, sección Style Guidelines)
val SkillBlue = Color(0xFF0022AA)          // primario (contraste 11.6:1 sobre blanco)
val SkillBlueDark = Color(0xFF001580)      // onboarding y encabezados
val SkillBlueContainer = Color(0xFFE6EAFF) // chips seleccionados e indicador
val TextPrimary = Color(0xFF111827)        // texto principal (17.7:1)
val TextSecondary = Color(0xFF4B5563)      // texto secundario (7.6:1)

// Estados: siempre con ícono y texto, nunca solo color
val Success = Color(0xFF15803D)
val Error = Color(0xFFB91C1C)
val Warning = Color(0xFFB45309)            // advertencia y SkillCredits
val AiViolet = Color(0xFF5B21B6)           // todo lo generado por IA

// Fondos suaves de cada estado y neutros (derivados de la paleta; el informe no fija su valor exacto)
val SuccessContainer = Color(0xFFDCFCE7)
val ErrorContainer = Color(0xFFFEE2E2)
val WarningContainer = Color(0xFFFEF3C7)
val AiContainer = Color(0xFFEDE9FE)
val NeutralBorder = Color(0xFFD1D5DB)
val NeutralSurface = Color(0xFFF3F4F6)
