package app.intervals.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.intervals.core.ThemeMode

/** The app palette agreed in the mockups (purple UI, pink work, yellow rest, teal warm-up / rest between rounds). */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val sheet: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val tint: Color,
    val group: Color,
    val groupLine: Color,
    val danger: Color,
    val dangerText: Color,
    val teal: Color,
) {
    val pink = Color(0xFFE91E63)
    val yellow = Color(0xFFFFC107)
}

private val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF000000), surface = Color(0xFF1A1C21), sheet = Color(0xFF16181C), line = Color(0xFF23262C),
    text = Color(0xFFF2F3F5), muted = Color(0xFF9AA0AA), soft = Color(0xFFC9CDD3), faint = Color(0xFF6E747E),
    accent = Color(0xFF7C3AED), onAccent = Color.White, accentText = Color(0xFFC4B1FF), tint = Color(0xFF2A2340),
    group = Color(0xFF1C1828), groupLine = Color(0xFF3B2F63), danger = Color(0xFFC62828), dangerText = Color(0xFFFF8A80),
    teal = Color(0xFF00BFA5),
)

private val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F5FA), surface = Color.White, sheet = Color.White, line = Color(0xFFE2E0EA),
    text = Color(0xFF17151F), muted = Color(0xFF5E5A6B), soft = Color(0xFF3A3646), faint = Color(0xFF8C889A),
    accent = Color(0xFF6D28D9), onAccent = Color.White, accentText = Color(0xFF5B21B6), tint = Color(0xFFEDE7FB),
    group = Color(0xFFF7F3FF), groupLine = Color(0xFFD9CCF7), danger = Color(0xFFC62828), dangerText = Color(0xFFB71C1C),
    teal = Color(0xFF00897B),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Numbers (times, rounds) use a tighter display face, like Space Grotesk in the mockups. */
val NumberStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)

@Composable
fun IntervalsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.sheet, onSurfaceVariant = p.muted, outline = p.line,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.sheet, onSurfaceVariant = p.muted, outline = p.line,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
