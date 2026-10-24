package nz.thys.ashley.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The AI-Agents web page palette (app.css :root), light and dark. */
@Immutable
data class AshleyColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentBg: Color,
    val accentText: Color,
    val ok: Color,
    val warn: Color,
    val warnBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val codeBg: Color,
    val segSystem: Color = Color(0xFF7F77DD),
    val segTools: Color = Color(0xFF1D9E75),
    val segNotes: Color = Color(0xFF888780),
    val segHistory: Color = Color(0xFFD85A30),
)

val LightAshley = AshleyColors(
    bg = Color(0xFFF5F5F2), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF0EFEA),
    text = Color(0xFF1F1F1C), muted = Color(0xFF66655E), faint = Color(0xFF75746B),
    border = Color(0xFFE2E1DA), borderStrong = Color(0xFFCFCDC4),
    accent = Color(0xFF2F6FDB), accentBg = Color(0xFFE7EFFC), accentText = Color(0xFF1B4A9C),
    ok = Color(0xFF1B8559), warn = Color(0xFFA96D12), warnBg = Color(0xFFFBF1DE),
    danger = Color(0xFFBF3A2B), dangerBg = Color(0xFFFBE9E7), codeBg = Color(0xFFF1F0EB),
)

val DarkAshley = AshleyColors(
    bg = Color(0xFF151513), surface = Color(0xFF1D1D1A), surface2 = Color(0xFF252522),
    text = Color(0xFFECEBE4), muted = Color(0xFFA5A49A), faint = Color(0xFF8E8D84),
    border = Color(0xFF2E2E2A), borderStrong = Color(0xFF42423C),
    accent = Color(0xFF6EA0F2), accentBg = Color(0xFF1F2C44), accentText = Color(0xFFA9C8FA),
    ok = Color(0xFF4CC392), warn = Color(0xFFE0A444), warnBg = Color(0xFF33291A),
    danger = Color(0xFFEE7B6D), dangerBg = Color(0xFF3A201D), codeBg = Color(0xFF262622),
)

val LocalAshley = staticCompositionLocalOf { LightAshley }

object Ashley {
    val colors: AshleyColors @Composable get() = LocalAshley.current
}

@Composable
fun AshleyTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val c = if (dark) DarkAshley else LightAshley
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        outline = c.borderStrong, outlineVariant = c.border, error = c.danger, primaryContainer = c.accentBg,
        onPrimaryContainer = c.accentText, surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
        surfaceContainerLow = c.surface,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        outline = c.borderStrong, outlineVariant = c.border, error = c.danger, primaryContainer = c.accentBg,
        onPrimaryContainer = c.accentText, surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
        surfaceContainerLow = c.surface,
    )
    CompositionLocalProvider(LocalAshley provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
