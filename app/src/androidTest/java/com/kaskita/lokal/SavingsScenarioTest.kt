package com.kaskita.lokal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.MonthRange
import com.kaskita.lokal.domain.PeriodSummary
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.ui.KasKitaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId

/**
 * Saving money through the real UI and the real Room database: a deposit into a savings account is a transfer (the total
 * balance, income and expense stay as they were), the form shows before/after balances first, and Laporan shows it as
 * savings. Compares against whatever the database held before, so it does not depend on other tests' data.
 */
@RunWith(AndroidJUnit4::class)
class SavingsScenarioTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ledger get() = (rule.activity.application as KasKitaApp).container.ledger
    private fun str(id: Int) = rule.activity.getString(id)
    private fun rp(amount: Long) = RupiahFormatter.format(amount)

    private fun waitForText(text: String) {
        try {
            rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for \"$text\". Screen:\n" + rule.onRoot().printToString(maxDepth = 12), e)
        }
    }

    private data class Snapshot(val balances: Map<String, Long>, val total: Long, val month: PeriodSummary)

    private fun snapshot(): Snapshot = runBlocking {
        val accounts = ledger.observeAccounts().first()
        val txs = ledger.observeTransactions().first()
        val b = Ledger.balances(accounts, txs)
        val range = MonthRange.of(YearMonth.now(), ZoneId.systemDefault())
        Snapshot(accounts.associate { it.name to (b[it.id] ?: 0L) }, Ledger.totalBalance(accounts, b), Ledger.summary(txs, range))
    }

    @Test
    fun savingMovesMoneyWithoutTouchingTotalIncomeOrExpense() {
        runBlocking { ledger.saveAccount(null, "Dana darurat", AccountType.SAVINGS, 0) }
        val before = snapshot()
        val bank = before.balances.getValue("Rekening Bank")
        waitForText(rp(before.total))

        rule.onNodeWithText(str(R.string.add_savings)).performClick()
        waitForText(str(R.string.form_amount_label))
        rule.onNodeWithText(str(R.string.form_amount_label)).performTextInput("300000")
        rule.onNodeWithText(str(R.string.form_title_deposit)).assertIsDisplayed()
        waitForText("${rp(bank)} → ${rp(bank - 300_000)}")
        waitForText("Rp0 → Rp300.000")
        rule.onNodeWithText(str(R.string.form_save_transfer)).performClick()
        waitForText(str(R.string.home_total_label))

        val after = snapshot()
        assertEquals(bank - 300_000, after.balances["Rekening Bank"])
        assertEquals(300_000L, after.balances["Dana darurat"])
        assertEquals("a transfer never changes the total", before.total, after.total)
        assertEquals("not income", before.month.income, after.month.income)
        assertEquals("not expense", before.month.expense, after.month.expense)

        rule.onNodeWithText("Laporan").performClick()
        waitForText(str(R.string.rp_savings_balance))
        rule.onNodeWithText(str(R.string.rp_savings_deposits)).assertIsDisplayed()
        waitForText("+Rp300.000")
        rule.onNodeWithText(str(R.string.rp_savings_note)).assertIsDisplayed()
    }
}
