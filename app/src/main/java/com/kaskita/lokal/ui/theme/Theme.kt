package com.kaskita.lokal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kaskita.lokal.data.ThemeMode

/**
 * Colors that are not part of Material's scheme: the money directions (always paired with +, − or ↔ and text, never the only cue),
 * the pastel call-to-action fill, and mid-tone fills for chart bars.
 */
data class AppColors(
    val income: Color, val incomeContainer: Color, val onIncomeContainer: Color,
    val expense: Color, val expenseContainer: Color, val onExpenseContainer: Color,
    val transfer: Color, val transferContainer: Color, val onTransferContainer: Color,
    val savings: Color, val savingsContainer: Color, val onSavingsContainer: Color,
    val cta: Color, val onCta: Color,
    val incomeBar: Color, val expenseBar: Color, val transferBar: Color, val savingsBar: Color,
)

internal fun ThemeTokens.toAppColors() = AppColors(
    income, incomeContainer, onIncomeContainer,
    expense, expenseContainer, onExpenseContainer,
    transfer, transferContainer, onTransferContainer,
    savings, savingsContainer, onSavingsContainer,
    cta, onCta,
    incomeBar, expenseBar, transferBar, savingsBar,
)

val LocalFinanceColors = staticCompositionLocalOf { PastelLightTokens.toAppColors() }

/** True when the app is drawn with the dark scheme (which can differ from the system setting). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Kept under this name so call sites read `MaterialTheme.finance.income`. */
val MaterialTheme.finance: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFinanceColors.current

internal fun ThemeTokens.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
    background = background, onBackground = onBackground,
    surface = surface, onSurface = onSurface, surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceTint = primary, outline = outline, outlineVariant = outlineVariant,
    inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow, surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
)

internal fun ThemeTokens.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
    secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
    error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
    background = background, onBackground = onBackground,
    surface = surface, onSurface = onSurface, surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
    surfaceTint = primary, outline = outline, outlineVariant = outlineVariant,
    inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface, inversePrimary = inversePrimary,
    surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow, surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
)

/**
 * Expressive type: one family (the system font), hierarchy from size and weight. Numbers are the hero of a money app, so
 * [Typography.displaySmall] is a big bold figure and titles are semibold. Captions never go below 12sp.
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Larger radii than the older baseline: soft containers read as the expressive look. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun KasKitaTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val tokens = if (dark) PastelDarkTokens else PastelLightTokens
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalFinanceColors provides tokens.toAppColors(),
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) tokens.toDarkColorScheme() else tokens.toLightColorScheme(),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
