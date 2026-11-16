package app.veneer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.veneer.R
import app.veneer.data.ThemeConfig

val LocalLauncherTheme = compositionLocalOf { ThemeConfig() }
val LocalThemeFont = compositionLocalOf<FontFamily> { FontFamily.Default }

fun Int.asColor() = Color(this)

/** Black or white, whichever reads better on [background]. */
fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF111114) else Color.White

fun Typography.withFont(font: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = font)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private fun accentScheme(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = contentColorFor(accent)
    return if (dark) {
        val surface = Color(0xFF16171B)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.28f).compositeOver(surface),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.2f).compositeOver(surface),
            onSecondaryContainer = Color.White,
            tertiary = accent,
            background = surface,
            surface = surface,
            surfaceContainerLowest = Color(0xFF0F1013),
            surfaceContainerLow = Color(0xFF1A1B20),
            surfaceContainer = Color(0xFF1E1F24),
            surfaceContainerHigh = Color(0xFF26272D),
            surfaceContainerHighest = Color(0xFF303138),
        )
    } else {
        val surface = Color(0xFFFAFAFC)
        // Light accents (pastels, near-white) disappear on white; darken them for text and controls.
        val readable = if (accent.luminance() > 0.4f) lerp(accent, Color.Black, 0.45f) else accent
        lightColorScheme(
            primary = readable,
            onPrimary = contentColorFor(readable),
            primaryContainer = accent.copy(alpha = 0.22f).compositeOver(surface),
            onPrimaryContainer = Color(0xFF111114),
            secondary = readable,
            onSecondary = contentColorFor(readable),
            secondaryContainer = accent.copy(alpha = 0.18f).compositeOver(surface),
            onSecondaryContainer = Color(0xFF111114),
            tertiary = readable,
            background = surface,
            surface = surface,
            surfaceVariant = Color(0xFFE4E4EA),
            onSurfaceVariant = Color(0xFF4A4A52),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF5F5F8),
            surfaceContainer = Color(0xFFF0F0F4),
            surfaceContainerHigh = Color(0xFFEBEBF0),
            surfaceContainerHighest = Color(0xFFE4E4EA),
            outline = Color(0xFF7A7A84),
            outlineVariant = Color(0xFFCACAD2),
        )
    }
}

/** The look of the launcher's own menus, sheets and drawer: always dark, tinted by the theme accent and font. */
@Composable
fun LauncherTheme(theme: ThemeConfig, font: FontFamily, content: @Composable () -> Unit) {
    val scheme = remember(theme.accent) { accentScheme(Color(theme.accent), dark = true) }
    val typography = remember(font) { Typography().withFont(font) }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalLauncherTheme provides theme, LocalThemeFont provides font, content = content)
    }
}

/** Bright colors for Veneer's own screens. Each Customize section has its own pair. */
object Vivid {
    val Violet = Color(0xFF7C5CFF)
    val Pink = Color(0xFFFF4D8D)
    val Orange = Color(0xFFFF8A3D)
    val Amber = Color(0xFFFFB020)
    val Teal = Color(0xFF12C2A9)
    val Blue = Color(0xFF3D7BFF)
    val Sky = Color(0xFF2EC5FF)
    val Green = Color(0xFF22C55E)
    val Purple = Color(0xFFB04DFF)
    val Coral = Color(0xFFFF5E62)
    val Slate = Color(0xFF64748B)
}

/** The rounded, friendly font used across Veneer's own screens. */
val FriendlyFont = FontFamily(Font(R.font.outfit_regular, FontWeight.Normal), Font(R.font.outfit_bold, FontWeight.Bold))

/**
 * Readable on a light background: bright colors are deepened for text and controls, keeping their
 * saturation (mixing in black would turn orange into brown).
 */
fun Color.readableOnLight(): Color {
    if (luminance() <= 0.3f) return this
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.12f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceAtMost(0.8f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** The look of the Customize screens: follows the phone's light/dark setting and the theme accent. */
@Composable
fun SettingsTheme(theme: ThemeConfig, font: FontFamily, useWallpaperColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        useWallpaperColors && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(theme.accent, dark) { accentScheme(Color(theme.accent), dark) }
    }
    MaterialTheme(colorScheme = scheme, typography = Typography().withFont(FriendlyFont), shapes = FriendlyShapes) {
        CompositionLocalProvider(LocalLauncherTheme provides theme, LocalThemeFont provides font, content = content)
    }
}

private val FriendlyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Re-tints switches, sliders, chips, headers and cards with one bright [color]. */
@Composable
fun SectionColors(color: Color, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val main = if (dark) color else color.readableOnLight()
    val scheme = remember(base, color, dark) {
        base.copy(
            primary = main,
            onPrimary = contentColorFor(main),
            primaryContainer = color.copy(alpha = if (dark) 0.32f else 0.2f).compositeOver(base.surface),
            onPrimaryContainer = base.onSurface,
            secondary = main,
            secondaryContainer = color.copy(alpha = if (dark) 0.3f else 0.18f).compositeOver(base.surface),
            onSecondaryContainer = base.onSurface,
            surfaceContainerHigh = color.copy(alpha = if (dark) 0.12f else 0.08f).compositeOver(base.surfaceContainerLow),
            surfaceContainerHighest = color.copy(alpha = if (dark) 0.2f else 0.13f).compositeOver(base.surfaceContainerLow),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}
