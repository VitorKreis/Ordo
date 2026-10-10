package com.vitorkrichter.ordo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

private val OrdoColorScheme: ColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentTeal,
    tertiary = AccentGreen,
    background = BgApp,
    surface = BgCard,
    surfaceVariant = BgCard2,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    outline = Border,
)

@Composable
fun OrdoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OrdoColorScheme,
        typography = OrdoTypography,
        shapes = OrdoShapes,
        content = content,
    )
}

object Spacing {
    val xs = Dp(4f)
    val sm = Dp(8f)
    val md = Dp(12f)
    val lg = Dp(16f)
    val xl = Dp(20f)
    val xxl = Dp(24f)
    val xxxl = Dp(32f)
}
