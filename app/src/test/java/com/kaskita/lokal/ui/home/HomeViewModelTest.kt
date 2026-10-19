@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.home

import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = millis(2026, 10, 19, 12)

    private fun repo(txs: List<Transaction> = emptyList(), cashArchived: Boolean = false) = FakeLedgerRepository(
        accounts = listOf(TestData.cash(opening = 0, archived = cashArchived), TestData.bank(opening = 2_000_000)),
        transactions = txs,
    )

    private fun vm(repo: FakeLedgerRepository) = HomeViewModel(repo, jakarta) { now }

    @Test
    fun totalBalanceIsSumOfActiveAccounts() = runTest {
        val txs = listOf(TestData.transfer(1, 500_000, BANK_ID, CASH_ID, millis(2026, 10, 10)), TestData.expense(2, 75_000, CASH_ID, millis(2026, 10, 11)))
        val vm = vm(repo(txs))
        advanceUntilIdle()
        assertEquals(1_925_000L, vm.state.value.totalBalance)
    }

    @Test
    fun cashCardShowsCashAccountBalance() = runTest {
        val txs = listOf(TestData.transfer(1, 500_000, BANK_ID, CASH_ID, millis(2026, 10, 10)), TestData.expense(2, 75_000, CASH_ID, millis(2026, 10, 11)))
        val vm = vm(repo(txs))
        advanceUntilIdle()
        val cash = vm.state.value.cash
        assertEquals(CASH_ID, cash?.account?.id)
        assertEquals(425_000L, cash?.balance)
    }

    @Test
    fun archivedCashIsNotShownAsTheCashCard() = runTest {
        val vm = vm(repo(cashArchived = true))
        advanceUntilIdle()
        assertNull(vm.state.value.cash)
    }

    @Test
    fun monthlyCardsExcludeTransfers() = runTest {
        val txs = listOf(
            TestData.income(1, 5_000_000, BANK_ID, millis(2026, 10, 1)),
            TestData.expense(2, 35_000, CASH_ID, millis(2026, 10, 19)),
            TestData.transfer(3, 500_000, BANK_ID, CASH_ID, millis(2026, 10, 5)),
            TestData.expense(4, 99_000, CASH_ID, millis(2026, 9, 30, 23)),
        )
        val vm = vm(repo(txs))
        advanceUntilIdle()
        assertEquals(5_000_000L, vm.state.value.income)
        assertEquals(35_000L, vm.state.value.expense)
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
    }

    @Test
    fun monthSwitchChangesCardsButNotTotalBalance() = runTest {
        val txs = listOf(
            TestData.expense(1, 35_000, CASH_ID, millis(2026, 10, 19)),
            TestData.expense(2, 99_000, CASH_ID, millis(2026, 9, 30, 23)),
        )
        val vm = vm(repo(txs))
        advanceUntilIdle()
        val totalBefore = vm.state.value.totalBalance
        vm.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(99_000L, vm.state.value.expense)
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
        assertEquals(totalBefore, vm.state.value.totalBalance)
        vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(35_000L, vm.state.value.expense)
    }

    @Test
    fun recentIsFiveNewestAcrossMonths() = runTest {
        val txs = (1L..7L).map { TestData.expense(it, it * 1_000, CASH_ID, millis(2026, 10, it.toInt())) } +
            TestData.expense(8, 8_000, CASH_ID, millis(2026, 8, 20))
        val vm = vm(repo(txs))
        advanceUntilIdle()
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), vm.state.value.recent.map { it.tx.id })
    }

    @Test
    fun miniChartIsSixMonthsEndingAtSelectedMonth() = runTest {
        val vm = vm(repo(listOf(TestData.income(1, 100, BANK_ID, millis(2026, 5, 3)))))
        advanceUntilIdle()
        val chart = vm.state.value.miniChart
        assertEquals(6, chart.size)
        assertEquals(YearMonth.of(2026, 5), chart.first().month)
        assertEquals(YearMonth.of(2026, 10), chart.last().month)
        assertEquals(100L, chart.first().income)
        assertTrue(chart.drop(1).all { it.income == 0L && it.expense == 0L })
    }

    @Test
    fun noTransactionsStillShowsBalancesAndEmptyRecent() = runTest {
        val vm = vm(repo())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(2_000_000L, s.totalBalance)
        assertEquals(0L, s.cash?.balance)
        assertEquals(0L, s.income)
        assertEquals(0L, s.expense)
        assertTrue(s.recent.isEmpty())
    }
}
