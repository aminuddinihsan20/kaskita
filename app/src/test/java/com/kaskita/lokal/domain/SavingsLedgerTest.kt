package com.kaskita.lokal.domain

import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.SAVINGS_ID
import com.kaskita.lokal.domain.TestData.bank
import com.kaskita.lokal.domain.TestData.cash
import com.kaskita.lokal.domain.TestData.expense
import com.kaskita.lokal.domain.TestData.income
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.TestData.savings
import com.kaskita.lokal.domain.TestData.transfer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class SavingsLedgerTest {
    private val accounts = listOf(bank(opening = 5_000_000), cash(opening = 0), savings(opening = 1_000_000))
    private val october = MonthRange.of(YearMonth.of(2026, 10), jakarta)

    @Test
    fun savingIsATransferNeverAnExpenseAndKeepsTheTotalBalance() {
        val tx = listOf(transfer(1, 800_000, BANK_ID, SAVINGS_ID))
        val balances = Ledger.balances(accounts, tx)
        assertEquals(4_200_000L, balances[BANK_ID])
        assertEquals(1_800_000L, balances[SAVINGS_ID])
        assertEquals(6_000_000L, Ledger.totalBalance(accounts, balances))
        val summary = Ledger.summary(tx, october)
        assertEquals(0L, summary.expense)
        assertEquals(0L, summary.income)
    }

    @Test
    fun depositsAndWithdrawalsAreCountedFromTransfersOnly() {
        val tx = listOf(
            transfer(1, 800_000, BANK_ID, SAVINGS_ID, millis(2026, 10, 2)),
            transfer(2, 200_000, CASH_ID, SAVINGS_ID, millis(2026, 10, 9)),
            transfer(3, 300_000, SAVINGS_ID, CASH_ID, millis(2026, 10, 20)),
            transfer(4, 999_000, BANK_ID, CASH_ID, millis(2026, 10, 5)), // unrelated to savings
            income(5, 50_000, SAVINGS_ID, millis(2026, 10, 12)), // interest: balance only
            expense(6, 10_000, SAVINGS_ID, millis(2026, 10, 13)), // spent straight from savings: balance only
            transfer(7, 700_000, BANK_ID, SAVINGS_ID, millis(2026, 9, 28)), // other month
        )
        assertEquals(SavingsFlow(deposits = 1_000_000, withdrawals = 300_000), Ledger.savingsFlow(tx, accounts, october))
        assertEquals(700_000L, Ledger.savingsFlow(tx, accounts, october).net)
    }

    @Test
    fun movingMoneyBetweenTwoSavingsAccountsIsNeitherDepositNorWithdrawal() {
        val second = Account(4, "Tabungan Haji", AccountType.SAVINGS, 0, 0, false)
        val both = accounts + second
        val tx = listOf(transfer(1, 100_000, SAVINGS_ID, 4, millis(2026, 10, 3)))
        assertEquals(SavingsFlow(0, 0), Ledger.savingsFlow(tx, both, october))
    }

    @Test
    fun theSavingsBalanceIsTheSumOfActiveSavingsAccounts() {
        val archived = Account(4, "Lama", AccountType.SAVINGS, 0, 0, true)
        val all = accounts + archived
        val balances = Ledger.balances(all, listOf(transfer(1, 800_000, BANK_ID, SAVINGS_ID)))
        assertEquals(1_800_000L, Ledger.savingsBalance(all, balances))
    }

    @Test
    fun withoutAnySavingsAccountEverythingIsZero() {
        val none = listOf(bank(), cash())
        val tx = listOf(transfer(1, 100_000, BANK_ID, CASH_ID, millis(2026, 10, 3)))
        assertEquals(SavingsFlow(0, 0), Ledger.savingsFlow(tx, none, october))
        assertEquals(0L, Ledger.savingsBalance(none, Ledger.balances(none, tx)))
    }

    @Test
    fun theMonthlySeriesIsZeroFilledOldestFirst() {
        val tx = listOf(
            transfer(1, 800_000, BANK_ID, SAVINGS_ID, millis(2026, 10, 2)),
            transfer(2, 300_000, SAVINGS_ID, CASH_ID, millis(2026, 8, 20)),
        )
        val series = Ledger.savingsByMonth(tx, accounts, YearMonth.of(2026, 10), 3, jakarta)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), series.map { it.month })
        assertEquals(listOf(SavingsFlow(0, 300_000), SavingsFlow(0, 0), SavingsFlow(800_000, 0)), series.map { it.flow })
    }
}
