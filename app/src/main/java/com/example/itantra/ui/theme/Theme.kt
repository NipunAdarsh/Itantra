package com.example.itantra.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Neural Tactical Interface — always dark ───────────────────────────────────
// The app is exclusively dark-mode as per Stitch design system specification.

private val NeuralColorScheme = darkColorScheme(
    primary              = Primary,
    onPrimary            = OnPrimary,
    primaryContainer     = PrimaryContainer,
    onPrimaryContainer   = OnPrimaryContainer,

    secondary            = Secondary,
    onSecondary          = OnSecondary,
    secondaryContainer   = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary             = Tertiary,
    onTertiary           = OnTertiary,
    tertiaryContainer    = TertiaryContainer,
    onTertiaryContainer  = OnTertiaryContainer,

    error                = Error,
    onError              = OnError,
    errorContainer       = ErrorContainer,
    onErrorContainer     = OnErrorContainer,

    background           = Background,
    onBackground         = OnBackground,
    surface              = Background,
    onSurface            = OnSurface,
    surfaceVariant       = SurfaceContainerHighest,
    onSurfaceVariant     = OnSurfaceVariant,
    outline              = Outline,
    outlineVariant       = OutlineVariant,
    inverseSurface       = OnSurface,
    inverseOnSurface     = Background,
    inversePrimary       = Color(0xFF006D36),
    surfaceTint          = Primary,
    scrim                = Scrim
)

@Composable
fun ITantraTheme(
    darkTheme: Boolean = true, // Always dark for Neural Tactical Interface
    content: @Composable () -> Unit
) {
    // Edge-to-edge: transparent status bar — content bleeds under status bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor    = Background.toArgb()
            window.navigationBarColor = Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = NeuralColorScheme,
        typography  = Typography,
        content     = content
    )
}