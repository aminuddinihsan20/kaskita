package com.kaskita.lokal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaskita.lokal.ui.common.formatMonthYearShort
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Final-review findings: double taps must not stack screens, and money cards must name the month they show. */
@RunWith(AndroidJUnit4::class)
class NavigationAndLabelsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    /** Invokes a node's click action twice in the same UI-thread turn: a double tap that beats recomposition. */
    private fun clickTwiceInOneFrame(node: SemanticsNodeInteraction) {
        val action = checkNotNull(node.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.action)
        rule.runOnUiThread {
            action()
            action()
        }
        rule.waitForIdle()
    }

    private fun assertOnBeranda() = rule.onNodeWithText(str(R.string.home_total_label)).assertIsDisplayed()

    @Test
    fun doubleTapOnAddIncomeOpensOnlyOneForm() {
        assertOnBeranda()
        clickTwiceInOneFrame(rule.onNodeWithText(str(R.string.add_income)))
        rule.onNodeWithText(str(R.string.form_title_income)).assertIsDisplayed()

        // one system back must lead to Beranda, not to a second copy of the form
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertOnBeranda()
    }

    @Test
    fun doubleTapOnTheBackArrowDoesNotPopBeranda() {
        assertOnBeranda()
        rule.onNodeWithText(str(R.string.add_expense)).performClick()
        rule.onNodeWithText(str(R.string.form_title_expense)).assertIsDisplayed()

        clickTwiceInOneFrame(rule.onNodeWithContentDescription(str(R.string.common_back)))
        assertOnBeranda()
    }

    @Test
    fun homeCardsNameTheSelectedMonthInsteadOfSayingBulanIni() {
        assertOnBeranda()
        rule.onNodeWithContentDescription(str(R.string.month_previous)).performClick()
        val previous = formatMonthYearShort(YearMonth.now().minusMonths(1))

        rule.onNodeWithText(str(R.string.home_income_named, previous)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_expense_named, previous)).assertIsDisplayed()
        rule.onAllNodesWithText("Pemasukan bulan ini").assertCountEquals(0)
        rule.onAllNodesWithText("Pengeluaran bulan ini").assertCountEquals(0)
    }
}
