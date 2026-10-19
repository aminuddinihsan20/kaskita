package com.kaskita.lokal.ui.theme

import androidx.compose.ui.graphics.Color

/** Every color role of one theme (light or dark). The values live in the generated [PastelLightTokens]/[PastelDarkTokens]. */
internal data class ThemeTokens(
    val background: Color, val onBackground: Color,
    val surface: Color, val onSurface: Color, val surfaceVariant: Color, val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color, val surfaceContainerLow: Color, val surfaceContainer: Color,
    val surfaceContainerHigh: Color, val surfaceContainerHighest: Color,
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color, val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color, val errorContainer: Color, val onErrorContainer: Color,
    val outline: Color, val outlineVariant: Color,
    val inverseSurface: Color, val inverseOnSurface: Color, val inversePrimary: Color,
    val cta: Color, val onCta: Color,
    val income: Color, val incomeContainer: Color, val onIncomeContainer: Color,
    val expense: Color, val expenseContainer: Color, val onExpenseContainer: Color,
    val transfer: Color, val transferContainer: Color, val onTransferContainer: Color,
    val savings: Color, val savingsContainer: Color, val onSavingsContainer: Color,
    val incomeBar: Color, val expenseBar: Color, val transferBar: Color, val savingsBar: Color,
)

/** Icon tint, soft container and chart fill for one category color key. */
internal data class CategoryTone(
    val lightIcon: Color, val lightContainer: Color, val lightChart: Color,
    val darkIcon: Color, val darkContainer: Color, val darkChart: Color,
)
