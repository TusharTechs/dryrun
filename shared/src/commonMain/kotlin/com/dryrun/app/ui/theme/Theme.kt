package com.dryrun.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Ink on paper, not a dashboard. The app is somewhere you go to say a hard
// thing out loud, so nothing here is bright or congratulatory.
private val Ink = Color(0xFF16151A)
private val Paper = Color(0xFFFBF9F5)
private val PaperRaised = Color(0xFFF3F0E9)
private val Muted = Color(0xFF6B6862)

// A single accent, used only for the user's own voice and live emphasis.
private val Signal = Color(0xFF3A5A8C)
private val SignalDark = Color(0xFF9BB4DC)

// Hedges are marked, never alarmed about. Amber, not red -- this is a habit
// worth noticing, not an error.
val HedgeMark = Color(0xFFB3801F)
val HedgeMarkDark = Color(0xFFD9A94A)

private val DarkInk = Color(0xFFEDEAE4)
private val DarkPaper = Color(0xFF131316)
private val DarkPaperRaised = Color(0xFF1D1D22)
private val DarkMuted = Color(0xFF97938C)

private val LightScheme = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6F4),
    onPrimaryContainer = Ink,
    // Defined explicitly, and tinted towards the hedge mark: the only thing
    // wearing it is the daily drill, which is about hedging. Left undefined,
    // Material fills it with a lavender that belongs to another app.
    secondary = HedgeMark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7EEDC),
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = Muted,
    outline = Color(0xFFD8D3C9),
    error = Color(0xFF8C3A3A)
)

private val DarkScheme = darkColorScheme(
    primary = SignalDark,
    onPrimary = Color(0xFF10151F),
    primaryContainer = Color(0xFF243247),
    onPrimaryContainer = DarkInk,
    secondary = HedgeMarkDark,
    onSecondary = Color(0xFF1F1808),
    secondaryContainer = Color(0xFF332915),
    onSecondaryContainer = DarkInk,
    background = DarkPaper,
    onBackground = DarkInk,
    surface = DarkPaper,
    onSurface = DarkInk,
    surfaceVariant = DarkPaperRaised,
    onSurfaceVariant = DarkMuted,
    outline = Color(0xFF34343A),
    error = Color(0xFFD98A8A)
)

private val lineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val DryRunTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp,
        lineHeightStyle = lineHeight
    ),
    headlineMedium = TextStyle(
        fontSize = 27.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
        lineHeightStyle = lineHeight
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
        lineHeightStyle = lineHeight
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 17.sp, lineHeight = 26.sp, lineHeightStyle = lineHeight
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 22.sp, lineHeightStyle = lineHeight
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp, lineHeight = 19.sp, lineHeightStyle = lineHeight
    ),
    labelLarge = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp, lineHeight = 15.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.9.sp
    )
)

/** Colours that Material's scheme has no slot for. */
data class DryRunColors(
    val hedge: Color,
    val youBubble: Color,
    val themBubble: Color
)

val LocalDryRunColors = staticCompositionLocalOf {
    DryRunColors(HedgeMark, Color(0xFFDDE6F4), PaperRaised)
}

@Composable
fun DryRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extra = if (darkTheme) {
        DryRunColors(
            hedge = HedgeMarkDark,
            youBubble = Color(0xFF243247),
            themBubble = DarkPaperRaised
        )
    } else {
        DryRunColors(
            hedge = HedgeMark,
            youBubble = Color(0xFFDDE6F4),
            themBubble = PaperRaised
        )
    }

    CompositionLocalProvider(LocalDryRunColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = DryRunTypography,
            content = content
        )
    }
}
