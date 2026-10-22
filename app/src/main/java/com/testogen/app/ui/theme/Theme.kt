package com.testogen.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.testogen.app.R

/**
 * Мягкая тёплая палитра: кремовый фон, приглушённый зелёно-бирюзовый акцент,
 * тёмно-серый тёплый текст вместо чёрного. Только светлая тема — системная тёмная
 * тема и цвета Material You намеренно не используются.
 */
object Palette {
    val Background = Color(0xFFFBF6EE)
    val Surface = Color(0xFFFFFDF9)
    val SurfaceSoft = Color(0xFFF5EEE3)
    val SurfaceSunken = Color(0xFFEFE7DA)

    val Primary = Color(0xFF3F7D70)
    val PrimaryPressed = Color(0xFF346A5F)
    val PrimarySoft = Color(0xFFDDEDE7)
    val OnPrimarySoft = Color(0xFF1E4A41)

    val Accent = Color(0xFFC07B5C)
    val AccentSoft = Color(0xFFF6E3D8)
    val OnAccentSoft = Color(0xFF5A3221)

    val Text = Color(0xFF3B3633)
    val TextSecondary = Color(0xFF7B726B)
    val TextHint = Color(0xFFA79D94)

    val Outline = Color(0xFFE5DACC)
    val OutlineSoft = Color(0xFFEFE6DA)

    val Error = Color(0xFFB4554C)
    val ErrorSoft = Color(0xFFF7E0DB)

    /** Тёплая тень вместо серо-чёрной. */
    val Shadow = Color(0xFF8C6B4A)
}

/** Цвета меток сложности: шалфей, янтарь, розовая глина. */
@Immutable
data class DifficultyColors(val container: Color, val content: Color)

object DifficultyPalette {
    val Easy = DifficultyColors(Color(0xFFE4EFE3), Color(0xFF4C7653))
    val Medium = DifficultyColors(Color(0xFFF6EBD3), Color(0xFF86672A))
    val Hard = DifficultyColors(Color(0xFFF5E0DB), Color(0xFF94493F))
}

private val ColorScheme = lightColorScheme(
    primary = Palette.Primary,
    onPrimary = Color.White,
    primaryContainer = Palette.PrimarySoft,
    onPrimaryContainer = Palette.OnPrimarySoft,
    inversePrimary = Palette.PrimarySoft,
    secondary = Palette.Accent,
    onSecondary = Color.White,
    secondaryContainer = Palette.AccentSoft,
    onSecondaryContainer = Palette.OnAccentSoft,
    tertiary = Color(0xFFB08A3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6EBD3),
    onTertiaryContainer = Color(0xFF5C451A),
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceSoft,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceBright = Palette.Surface,
    surfaceDim = Palette.SurfaceSunken,
    surfaceContainerLowest = Palette.Surface,
    surfaceContainerLow = Color(0xFFFCF8F2),
    surfaceContainer = Palette.SurfaceSoft,
    surfaceContainerHigh = Color(0xFFF1E9DD),
    surfaceContainerHighest = Palette.SurfaceSunken,
    inverseSurface = Color(0xFF45403C),
    inverseOnSurface = Palette.Background,
    outline = Palette.Outline,
    outlineVariant = Palette.OutlineSoft,
    error = Palette.Error,
    onError = Color.White,
    errorContainer = Palette.ErrorSoft,
    onErrorContainer = Color(0xFF5E211B),
    scrim = Color(0xFF3B3633),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun golos(weight: Int) = Font(
    resId = R.font.golos_text,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Golos Text — шрифт, сделанный для кириллицы; хорошо читается крупно. */
val Golos = FontFamily(golos(400), golos(500), golos(600), golos(700))

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, line: Int, weight: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Golos,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
    lineHeightStyle = Trim,
)

/** Крупнее стандартной: основной текст 18 sp. */
private val AppTypography = Typography(
    displaySmall = style(34, 42, 700),
    headlineLarge = style(30, 38, 700),
    headlineMedium = style(27, 34, 700),
    headlineSmall = style(24, 31, 650),
    titleLarge = style(22, 29, 650),
    titleMedium = style(19, 26, 600),
    titleSmall = style(17, 23, 600),
    bodyLarge = style(18, 27, 400),
    bodyMedium = style(16, 24, 400),
    bodySmall = style(14, 20, 400),
    labelLarge = style(17, 22, 600),
    labelMedium = style(15, 20, 500),
    labelSmall = style(13, 17, 500),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Отступы и размеры, общие для всех экранов. */
@Immutable
data class Dimens(
    val screenPadding: Dp = 20.dp,
    val cardPadding: Dp = 20.dp,
    val gap: Dp = 14.dp,
    val buttonHeight: Dp = 60.dp,
)

val LocalDimens = staticCompositionLocalOf { Dimens() }

@Composable
fun TestoGenTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDimens provides Dimens()) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
