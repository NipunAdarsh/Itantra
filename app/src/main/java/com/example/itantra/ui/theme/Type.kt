package com.example.itantra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Neural Tactical Interface typography ─────────────────────────────────────
// Inter for UI text, system monospace for data/timestamps (JetBrains Mono maps
// to system monospace on Android; for actual JetBrains Mono add the font asset)

val MonoFamily = FontFamily.Monospace   // JetBrains Mono equivalent on Android
val SansFamily = FontFamily.Default     // Inter equivalent (Roboto)

val Typography = Typography(
    // ── Display ──────────────────────────────────────────────────────────────
    displaySmall = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = (-0.02).sp
    ),
    // ── Headlines ─────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineMedium = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 24.sp,
        lineHeight    = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp
    ),
    // ── Titles ────────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 17.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 15.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 13.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // ── Body ──────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // ── Labels ────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily    = SansFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily    = MonoFamily,       // JetBrains Mono for tactical metadata
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// ── Custom text styles not in M3 scale ───────────────────────────────────────
// Use these by importing them directly where needed.

/** Uppercase section label — tactical metadata caps */
val SectionLabelStyle = TextStyle(
    fontFamily    = SansFamily,
    fontWeight    = FontWeight.SemiBold,
    fontSize      = 11.sp,
    lineHeight    = 14.sp,
    letterSpacing = 1.5.sp
)

/** JetBrains Mono timestamp */
val TimestampStyle = TextStyle(
    fontFamily    = MonoFamily,
    fontWeight    = FontWeight.Normal,
    fontSize      = 11.sp,
    lineHeight    = 14.sp,
    letterSpacing = 0.5.sp
)

/** Signal / telemetry mono data */
val TelemetryStyle = TextStyle(
    fontFamily    = MonoFamily,
    fontWeight    = FontWeight.Medium,
    fontSize      = 10.sp,
    lineHeight    = 12.sp,
    letterSpacing = 1.0.sp
)