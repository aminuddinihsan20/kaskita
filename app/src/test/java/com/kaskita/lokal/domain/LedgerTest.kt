package com.kaskita.lokal.domain

import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.bank
import com.kaskita.lokal.domain.TestData.cash
import com.kaskita.lokal.domain.TestData.expense
import com.kaskita.lokal.domain.TestData.income
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.TestData.transfer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class LedgerTest {
    private val accounts = listOf(bank(opening = 2_000_000), cash(opening = 0))
    private val october = MonthRange.of(YearMonth.of(2026, 10), jakarta)

    private fun balances(vararg tx: Transaction) = Ledger.balances(accounts, tx.toList())

    // --- AI_RULES G1–G6
    @Test
    fun incomeToCashAddsToCash() {
        val b = balances(income(1, 100_000, CASH_ID))
        assertEquals(100_000L, b[CASH_ID])
        assertEquals(2_000_000L, b[BANK_ID])
    }

    @Test
    fun incomeToBankAddsToBank() {
        val b = balances(income(1, 300_000, BANK_ID))
        assertEquals(2_300_000L, b[BANK_ID])
        assertEquals(0L, b[CASH_ID])
    }

    @Test
    fun expenseFromCashSubtractsFromCash() {
        val b = balances(income(1, 200_000, CASH_ID), expense(2, 75_000, CASH_ID))
        assertEquals(125_000L, b[CASH_ID])
    }

    @Test
    fun expenseFromBankSubtractsFromBank() {
        val b = balances(expense(1, 250_000, BANK_ID))
        assertEquals(1_750_000L, b[BANK_ID])
        assertEquals(0L, b[CASH_ID])
    }

    @Test
    fun transferBankToCashMovesExactAmount() {
        val b = balances(transfer(1, 500_000, BANK_ID, CASH_ID))
        assertEquals(1_500_000L, b[BANK_ID])
        assertEquals(500_000L, b[CASH_ID])
    }

    @Test
    fun transferKeepsTotalBalanceAt2_000_000() {
        val tx = listOf(transfer(1, 500_000, BANK_ID, CASH_ID))
        val b = Ledger.balances(accounts, tx)
        assertEquals(2_000_000L, Ledger.totalBalance(accounts, b))
    }

    // --- AI_RULES G7
    @Test
    fun transferNotInIncomeOrExpense() {
        val s = Ledger.summary(listOf(transfer(1, 500_000, BANK_ID, CASH_ID)), october)
        assertEquals(0L, s.income)
        assertEquals(0L, s.expense)
        assertEquals(500_000L, s.transferTotal)
        assertEquals(1, s.transferCount)
    }

    // --- AI_RULES B3 full scenario
    @Test
    fun fullScenarioFromAiRules() {
        val t1 = transfer(1, 500_000, BANK_ID, CASH_ID)
        val t2 = expense(2, 75_000, CASH_ID)
        val t3 = income(3, 100_000, CASH_ID)

        val afterExpense = Ledger.balances(accounts, listOf(t1, t2))
        assertEquals(1_500_000L, afterExpense[BANK_ID])
        assertEquals(425_000L, afterExpense[CASH_ID])
        assertEquals(1_925_000L, Ledger.totalBalance(accounts, afterExpense))
        assertEquals(75_000L, Ledger.summary(listOf(t1, t2), october).expense)

        val all = listOf(t1, t2, t3)
        val afterIncome = Ledger.balances(accounts, all)
        assertEquals(525_000L, afterIncome[CASH_ID])
        assertEquals(2_025_000L, Ledger.totalBalance(accounts, afterIncome))
        assertEquals(100_000L, Ledger.summary(all, october).income)
    }

    // --- AI_RULES G8/G9
    @Test
    fun editRecomputesLikeFinalValueRecordedFromStart() {
        val original = listOf(income(1, 100_000, CASH_ID), expense(2, 20_000, CASH_ID))
        val edited = original.map { if (it.id == 1L) it.copy(amount = 60_000) else it }
        val recordedFromStart = listOf(income(1, 60_000, CASH_ID), expense(2, 20_000, CASH_ID))
        assertEquals(Ledger.balances(accounts, recordedFromStart), Ledger.balances(accounts, edited))
        assertEquals(40_000L, Ledger.balances(accounts, edited)[CASH_ID])
    }

    @Test
    fun deleteRecomputes() {
        val txs = listOf(income(1, 100_000, CASH_ID), expense(2, 30_000, CASH_ID))
        val afterDelete = txs.filterNot { it.id == 2L }
        assertEquals(100_000L, Ledger.balances(accounts, afterDelete)[CASH_ID])
        assertEquals(70_000L, Ledger.balances(accounts, txs)[CASH_ID])
    }

    // --- AI_RULES G10
    @Test
    fun openingBalanceNotIncome() {
        assertEquals(0L, Ledger.summary(emptyList(), october).income)
        val months = Ledger.monthlyTotals(emptyList(), 2026, jakarta)
        assertEquals(12, months.size)
        assertEquals(0L, months.sumOf { it.income })
        assertEquals(2_000_000L, Ledger.balances(accounts, emptyList())[BANK_ID])
    }

    // --- DESIGN §4: total saldo = active accounts only
    @Test
    fun archivedAccountExcludedFromTotalBalance() {
        val accs = listOf(bank(opening = 1_000_000), cash(opening = 0, archived = true).copy(openingBalance = 50_000))
        val b = Ledger.balances(accs, emptyList())
        assertEquals(1_000_000L, Ledger.totalBalance(accs, b))
    }

    @Test
    fun archivedAccountStillHasItsBalanceComputed() {
        val accs = listOf(bank(), cash(archived = true))
        val b = Ledger.balances(accs, listOf(income(1, 5_000, CASH_ID)))
        assertEquals(5_000L, b[CASH_ID])
    }

    // --- aggregates used by reports
    @Test
    fun expenseByCategoryIgnoresIncomeAndTransferAndSortsDescending() {
        val txs = listOf(
            expense(1, 10_000, CASH_ID, category = 10),
            expense(2, 50_000, CASH_ID, category = 11),
            expense(3, 5_000, CASH_ID, category = 10),
            income(4, 999_000, CASH_ID),
            transfer(5, 700_000, BANK_ID, CASH_ID),
        )
        val rows = Ledger.expenseByCategory(txs, october)
        assertEquals(listOf(11L, 10L), rows.map { it.categoryId })
        assertEquals(listOf(50_000L, 15_000L), rows.map { it.total })
        assertEquals(listOf(1, 2), rows.map { it.count })
    }

    @Test
    fun monthlyTotalsBucketsByLocalMonthAndZeroFills() {
        val txs = listOf(
            income(1, 100, CASH_ID, date = millis(2026, 1, 31, 23, 59)),
            income(2, 200, CASH_ID, date = millis(2026, 2, 1, 0, 0)),
            expense(3, 50, CASH_ID, date = millis(2026, 12, 31, 23, 59)),
            income(4, 999, CASH_ID, date = millis(2027, 1, 1, 0, 0)),
        )
        val m = Ledger.monthlyTotals(txs, 2026, jakarta)
        assertEquals(12, m.size)
        assertEquals(100L, m[0].income)
        assertEquals(200L, m[1].income)
        assertEquals(0L, m[5].income)
        assertEquals(50L, m[11].expense)
        assertEquals(300L, m.sumOf { it.income }) // the 2027-01-01 income is excluded
    }

    @Test
    fun dailyExpenseHasOneEntryPerDayAndMatchesMonthExpense() {
        val txs = listOf(
            expense(1, 10_000, CASH_ID, date = millis(2026, 10, 1)),
            expense(2, 5_000, CASH_ID, date = millis(2026, 10, 1, 20, 0)),
            expense(3, 7_000, CASH_ID, date = millis(2026, 10, 31, 23, 59)),
            expense(4, 9_999, CASH_ID, date = millis(2026, 11, 1, 0, 0)),
        )
        val days = Ledger.dailyExpense(txs, YearMonth.of(2026, 10), jakarta)
        assertEquals(31, days.size)
        assertEquals(15_000L, days.first().expense)
        assertEquals(7_000L, days.last().expense)
        assertEquals(Ledger.summary(txs, october).expense, days.sumOf { it.expense })
    }

    @Test
    fun legWithUnknownAccountIsIgnored() {
        val b = Ledger.balances(accounts, listOf(income(1, 10, 999L)))
        assertEquals(setOf(BANK_ID, CASH_ID), b.keys)
        assertEquals(2_000_000L, b[BANK_ID])
    }

    @Test
    fun lastMonthsReturnsWindowEndingAtGivenMonthAcrossYears() {
        val txs = listOf(
            income(1, 100, CASH_ID, date = millis(2025, 11, 15)),
            expense(2, 40, CASH_ID, date = millis(2025, 12, 31, 23, 59)),
            income(3, 200, CASH_ID, date = millis(2026, 1, 1, 0, 0)),
            expense(4, 70, CASH_ID, date = millis(2026, 2, 10)),
            income(5, 999, CASH_ID, date = millis(2026, 3, 1, 0, 0)), // after the window
            transfer(6, 5_000, BANK_ID, CASH_ID, date = millis(2026, 2, 11)),
        )
        val window = Ledger.lastMonths(txs, YearMonth.of(2026, 2), 4, jakarta)
        assertEquals(
            listOf(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
            window.map { it.month },
        )
        assertEquals(listOf(100L, 0L, 200L, 0L), window.map { it.income })
        assertEquals(listOf(0L, 40L, 0L, 70L), window.map { it.expense })
    }
}
