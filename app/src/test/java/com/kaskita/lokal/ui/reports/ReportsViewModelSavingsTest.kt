@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.reports

import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.ai.FakeGeminiClient
import com.kaskita.lokal.domain.SavingsFlow
import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.SAVINGS_ID
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class ReportsViewModelSavingsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = millis(2026, 10, 19, 12)

    private fun vm(txs: List<Transaction>, withSavings: Boolean = true): ReportsViewModel {
        val repo = FakeLedgerRepository(
            accounts = listOfNotNull(TestData.cash(opening = 0), TestData.bank(opening = 5_000_000), TestData.savings(opening = 1_000_000).takeIf { withSavings }),
            transactions = txs,
        )
        return ReportsViewModel(repo, jakarta, FakeSettingsStore(), FakeGeminiClient()) { now }
    }

    private val txs = listOf(
        TestData.income(1, 5_000_000, BANK_ID, millis(2026, 10, 1)),
        TestData.transfer(2, 800_000, BANK_ID, SAVINGS_ID, millis(2026, 10, 2)),
        TestData.transfer(3, 300_000, SAVINGS_ID, CASH_ID, millis(2026, 10, 20)),
        TestData.transfer(4, 400_000, BANK_ID, SAVINGS_ID, millis(2026, 8, 15)),
    )

    @Test
    fun theSavingsCardHasBalanceAndTheMonthsDepositsAndWithdrawals() = runTest {
        val vm = vm(txs)
        advanceUntilIdle()
        val s = vm.state.value.savings!!
        assertEquals(SavingsFlow(800_000, 300_000), s.monthFlow)
        // 1.000.000 opening + 800.000 + 400.000 − 300.000
        assertEquals(1_900_000L, s.balance)
        assertEquals(listOf(SavingsAccountRow("Tabungan", 1_900_000L)), s.accounts)
    }

    @Test
    fun theSixMonthHistoryEndsAtTheSelectedMonthAndIsZeroFilled() = runTest {
        val vm = vm(txs)
        advanceUntilIdle()
        val recent = vm.state.value.savings!!.recent
        assertEquals((5 downTo 0).map { YearMonth.of(2026, 10).minusMonths(it.toLong()) }, recent.map { it.month })
        assertEquals(SavingsFlow(400_000, 0), recent.first { it.month == YearMonth.of(2026, 8) }.flow)
        assertEquals(SavingsFlow(0, 0), recent.first { it.month == YearMonth.of(2026, 9) }.flow)
    }

    @Test
    fun savingsNeverCountAsExpenseInTheMonthFlow() = runTest {
        val vm = vm(txs)
        advanceUntilIdle()
        assertEquals(0L, vm.state.value.summary.expense)
        assertEquals(5_000_000L, vm.state.value.summary.income)
    }

    @Test
    fun withoutASavingsAccountThereIsNoSavingsCard() = runTest {
        val vm = vm(emptyList(), withSavings = false)
        advanceUntilIdle()
        assertNull(vm.state.value.savings)
    }

    @Test
    fun movingToAnotherMonthMovesTheFlowButNotTheBalance() = runTest {
        val vm = vm(txs)
        advanceUntilIdle()
        vm.onMonth(YearMonth.of(2026, 8))
        advanceUntilIdle()
        val s = vm.state.value.savings!!
        assertEquals(SavingsFlow(400_000, 0), s.monthFlow)
        assertEquals(1_900_000L, s.balance)
    }
}
