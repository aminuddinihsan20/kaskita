package com.kaskita.lokal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.ui.KasKitaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI_RULES §B3 through the real UI and the real Room database on a device/emulator:
 * Bank Rp2.000.000 → transfer Rp500.000 to Cash → spend Rp75.000 from Cash → receive Rp100.000 in Cash.
 * Run on a freshly installed app (`adb shell pm clear com.kaskita.lokal`) so the seeded defaults are untouched.
 */
@RunWith(AndroidJUnit4::class)
class CashFlowScenarioTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    /** The form loads asynchronously (Room), so wait for the element instead of assuming it is already there. */
    private fun clickWhenShown(text: String) {
        waitForText(text)
        rule.onNodeWithText(text).performClick()
    }

    private fun amountField(value: String) {
        waitForText(str(R.string.form_amount_label))
        rule.onNodeWithText(str(R.string.form_amount_label)).performTextInput(value)
    }

    private fun assertShown(text: String) = rule.onNodeWithText(text).assertIsDisplayed()

    /** Waits for [text]; on timeout the failure message carries what the screen actually showed. */
    private fun waitForText(text: String) {
        try {
            rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for \"$text\". Screen:\n" + rule.onRoot().printToString(maxDepth = 12), e)
        }
    }

    @Test
    fun aiRulesScenarioEndToEnd() {
        val container = (rule.activity.application as KasKitaApp).container
        runBlocking {
            val bank = container.ledger.observeAccounts().first().first { it.type == AccountType.BANK }
            container.ledger.saveAccount(bank.id, bank.name, bank.type, 2_000_000)
        }
        waitForText("Rp2.000.000")
        assertShown("Rp2.000.000") // Total saldo

        // 1. Tarik ke Cash: transfer Rp500.000, Bank → Cash
        rule.onNodeWithText(str(R.string.add_transfer)).performClick()
        amountField("500000")
        rule.onNodeWithText(str(R.string.form_save_transfer)).performClick()
        waitForText("Rp500.000")
        assertShown("Rp500.000") // Cash card
        assertShown("Rp2.000.000") // total unchanged by a transfer
        assertShown("+Rp0") // not income
        assertShown("−Rp0") // not expense

        // 2. Pengeluaran tunai Rp75.000 dari Cash
        rule.onNodeWithText(str(R.string.add_expense)).performClick()
        clickWhenShown("Makanan & minuman")
        amountField("75000")
        rule.onNodeWithText(str(R.string.form_save_expense)).performClick()
        waitForText("Rp425.000")
        assertShown("Rp425.000") // Cash
        assertShown("Rp1.925.000") // Total saldo
        assertShown("−Rp75.000") // Pengeluaran bulan ini

        // 3. Pemasukan tunai Rp100.000
        rule.onNodeWithText(str(R.string.add_income)).performClick()
        clickWhenShown("Gaji")
        amountField("100000")
        rule.onNodeWithText(str(R.string.form_save_income)).performClick()
        waitForText("Rp525.000")
        assertShown("Rp525.000") // Cash
        assertShown("Rp2.025.000") // Total saldo
        assertShown("+Rp100.000") // Pemasukan bulan ini

        // 4. The same numbers survive recreating the activity (data comes from the database, not UI state)
        rule.activityRule.scenario.recreate()
        waitForText("Rp2.025.000")
        assertShown("Rp525.000")
        assertShown("+Rp100.000")
        assertShown("−Rp75.000")
    }
}
