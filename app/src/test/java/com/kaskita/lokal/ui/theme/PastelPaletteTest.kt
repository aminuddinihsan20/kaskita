package com.kaskita.lokal.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The pastel look must never cost readability: every text pair keeps WCAG AA, graphics keep 3:1. */
class PastelPaletteTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private data class Pair(val name: String, val fg: Color, val bg: Color)

    private fun textPairs(t: ThemeTokens) = listOf(
        Pair("onSurface/surface", t.onSurface, t.surface),
        Pair("onSurface/background", t.onBackground, t.background),
        Pair("onSurface/containerLowest", t.onSurface, t.surfaceContainerLowest),
        Pair("onSurface/containerLow", t.onSurface, t.surfaceContainerLow),
        Pair("onSurface/containerHighest", t.onSurface, t.surfaceContainerHighest),
        Pair("onSurfaceVariant/surface", t.onSurfaceVariant, t.surface),
        Pair("onSurfaceVariant/containerLow", t.onSurfaceVariant, t.surfaceContainerLow),
        Pair("onSurfaceVariant/containerHighest", t.onSurfaceVariant, t.surfaceContainerHighest),
        Pair("primary/surface", t.primary, t.surface),
        Pair("onPrimary/primary", t.onPrimary, t.primary),
        Pair("onPrimaryContainer/primaryContainer", t.onPrimaryContainer, t.primaryContainer),
        Pair("onSecondaryContainer/secondaryContainer", t.onSecondaryContainer, t.secondaryContainer),
        Pair("onTertiaryContainer/tertiaryContainer", t.onTertiaryContainer, t.tertiaryContainer),
        Pair("onError/error", t.onError, t.error),
        Pair("onErrorContainer/errorContainer", t.onErrorContainer, t.errorContainer),
        Pair("onCta/cta", t.onCta, t.cta),
        Pair("inverseOnSurface/inverseSurface", t.inverseOnSurface, t.inverseSurface),
        Pair("income/surface", t.income, t.surface),
        Pair("income/containerLowest", t.income, t.surfaceContainerLowest),
        Pair("income/incomeContainer", t.income, t.incomeContainer),
        Pair("onIncomeContainer/incomeContainer", t.onIncomeContainer, t.incomeContainer),
        Pair("expense/surface", t.expense, t.surface),
        Pair("expense/containerLowest", t.expense, t.surfaceContainerLowest),
        Pair("expense/expenseContainer", t.expense, t.expenseContainer),
        Pair("onExpenseContainer/expenseContainer", t.onExpenseContainer, t.expenseContainer),
        Pair("transfer/surface", t.transfer, t.surface),
        Pair("transfer/transferContainer", t.transfer, t.transferContainer),
        Pair("onTransferContainer/transferContainer", t.onTransferContainer, t.transferContainer),
        Pair("savings/surface", t.savings, t.surface),
        Pair("onSavingsContainer/savingsContainer", t.onSavingsContainer, t.savingsContainer),
        Pair("onSurface/incomeContainer", t.onSurface, t.incomeContainer),
        Pair("onSurface/expenseContainer", t.onSurface, t.expenseContainer),
        Pair("onSurface/transferContainer", t.onSurface, t.transferContainer),
        Pair("onSurface/primaryContainer", t.onSurface, t.primaryContainer),
    )

    @Test
    fun everyTextRoleMeetsWcagAaInLightAndDark() {
        for ((label, tokens) in listOf("light" to PastelLightTokens, "dark" to PastelDarkTokens)) {
            for (p in textPairs(tokens)) {
                val ratio = contrast(p.fg, p.bg)
                assertTrue("$label ${p.name} is %.2f:1, needs 4.5:1".format(ratio), ratio >= 4.5)
            }
        }
    }

    @Test
    fun barsAndOutlinesMeetThreeToOneAgainstTheirSurfaces() {
        for ((label, t) in listOf("light" to PastelLightTokens, "dark" to PastelDarkTokens)) {
            val checks = listOf(
                Pair("incomeBar", t.incomeBar, t.surfaceContainerLowest), Pair("expenseBar", t.expenseBar, t.surfaceContainerLowest),
                Pair("transferBar", t.transferBar, t.surfaceContainerLowest), Pair("savingsBar", t.savingsBar, t.surfaceContainerLowest),
                Pair("outline", t.outline, t.surface),
            )
            for (p in checks) {
                val ratio = contrast(p.fg, p.bg)
                assertTrue("$label ${p.name} is %.2f:1, needs 3:1".format(ratio), ratio >= 3.0)
            }
        }
    }

    @Test
    fun lightContainersArePastelAndDarkContainersAreMuted() {
        fun containers(t: ThemeTokens) = listOf(
            t.primaryContainer, t.secondaryContainer, t.tertiaryContainer, t.incomeContainer, t.expenseContainer,
            t.transferContainer, t.savingsContainer, t.cta,
        )
        containers(PastelLightTokens).forEach { assertTrue("light container too dark for pastel: $it", luminance(it) >= 0.45) }
        containers(PastelDarkTokens).take(7).forEach { assertTrue("dark container too bright: $it", luminance(it) <= 0.12) }
    }

    @Test
    fun everyCategoryColorKeyHasATonedPalette() {
        val keys = listOf("orange", "blue", "teal", "purple", "brown", "red", "indigo", "pink", "amber", "green", "gray")
        assertEquals(keys.toSet(), CategoryTones.keys)
        for ((key, tone) in CategoryTones) {
            assertTrue("$key light icon on container", contrast(tone.lightIcon, tone.lightContainer) >= 3.0)
            assertTrue("$key dark icon on container", contrast(tone.darkIcon, tone.darkContainer) >= 3.0)
            assertTrue("$key light chart on white", contrast(tone.lightChart, PastelLightTokens.surfaceContainerLowest) >= 3.0)
            assertTrue("$key dark chart on dark surface", contrast(tone.darkChart, PastelDarkTokens.surfaceContainerLowest) >= 3.0)
        }
    }
}
