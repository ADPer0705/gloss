package rocks.dearanayji.gloss.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import rocks.dearanayji.gloss.data.ThemeMode

data class GlossColors(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val statusReady: Color,
    val statusWarning: Color,
    val statusError: Color,
    val wordUnderline: Color,
    val wordSelectedBg: Color,
    val wordSelectedBorder: Color,
    val isDark: Boolean
)

private val DarkGlossColors = GlossColors(
    bg = Color(0xFF0B0E14),
    surface = Color(0xFF131823),
    surfaceElevated = Color(0xFF1B2232),
    border = Color(0xFF263044),
    primary = Color(0xFF7C73FF),
    secondary = Color(0xFF3FD9C9),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFF94A0B8),
    textHint = Color(0xFF56617A),
    statusReady = Color(0xFF4ADE80),
    statusWarning = Color(0xFFFBBF24),
    statusError = Color(0xFFF87171),
    wordUnderline = Color(0xFF43506B),
    wordSelectedBg = Color(0xFF232B47),
    wordSelectedBorder = Color(0xFF7C73FF),
    isDark = true
)

private val LightGlossColors = GlossColors(
    bg = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEEF1F8),
    border = Color(0xFFDDE2EE),
    primary = Color(0xFF564CE6),
    secondary = Color(0xFF0D9488),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF5B6478),
    textHint = Color(0xFF9CA3AF),
    statusReady = Color(0xFF16A34A),
    statusWarning = Color(0xFFD97706),
    statusError = Color(0xFFDC2626),
    wordUnderline = Color(0xFFB8C2D6),
    wordSelectedBg = Color(0xFFE8E6FF),
    wordSelectedBorder = Color(0xFF564CE6),
    isDark = false
)

val LocalGlossColors = staticCompositionLocalOf { DarkGlossColors }

private val DarkColorScheme = darkColorScheme(
    primary = DarkGlossColors.primary,
    onPrimary = Color.White,
    secondary = DarkGlossColors.secondary,
    onSecondary = Color(0xFF062320),
    background = DarkGlossColors.bg,
    onBackground = DarkGlossColors.textPrimary,
    surface = DarkGlossColors.surface,
    onSurface = DarkGlossColors.textPrimary,
    surfaceVariant = DarkGlossColors.surfaceElevated,
    onSurfaceVariant = DarkGlossColors.textSecondary,
    outline = DarkGlossColors.border,
    error = DarkGlossColors.statusError
)

private val LightColorScheme = lightColorScheme(
    primary = LightGlossColors.primary,
    onPrimary = Color.White,
    secondary = LightGlossColors.secondary,
    onSecondary = Color.White,
    background = LightGlossColors.bg,
    onBackground = LightGlossColors.textPrimary,
    surface = LightGlossColors.surface,
    onSurface = LightGlossColors.textPrimary,
    surfaceVariant = LightGlossColors.surfaceElevated,
    onSurfaceVariant = LightGlossColors.textSecondary,
    outline = LightGlossColors.border,
    error = LightGlossColors.statusError
)

private val GlossTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun GlossTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val glossColors = if (useDark) DarkGlossColors else LightGlossColors
    val materialScheme = if (useDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = glossColors.bg.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = glossColors.bg.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDark
            controller.isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalGlossColors provides glossColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = GlossTypography,
            content = content
        )
    }
}
