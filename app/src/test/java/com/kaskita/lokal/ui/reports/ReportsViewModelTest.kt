@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.reports

import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.ai.FakeGeminiClient
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.FOOD_ID
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class ReportsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = millis(2026, 10, 19, 12)
    private val transportId = 11L

    private fun expense(id: Long, amount: Long, date: Long, category: Long = FOOD_ID) =
        TestData.expense(id, amount, CASH_ID, date, category)

    private val sample: List<Transaction> = listOf(
        TestData.income(1, 5_000_000, BANK_ID, millis(2026, 10, 1)),
        expense(2, 35_000, millis(2026, 10, 19, 9)),
        expense(3, 15_000, millis(2026, 10, 19, 18)),
        expense(4, 50_000, millis(2026, 10, 5), category = transportId),
        TestData.transfer(5, 500_000, BANK_ID, CASH_ID, millis(2026, 10, 7)),
        expense(6, 99_000, millis(2026, 9, 30, 23)),
        TestData.income(7, 1_000_000, BANK_ID, millis(2026, 1, 15)),
    )

    private fun repo(txs: List<Transaction> = sample) = FakeLedgerRepository(
        accounts = listOf(TestData.cash(opening = 1_000_000), TestData.bank(opening = 5_000_000)),
        categories = listOf(
            TestData.foodCategory(),
            Category(transportId, "Transportasi / bensin", TransactionType.EXPENSE, "directions_car", "teal", true, false),
            TestData.salaryCategory(),
        ),
        transactions = txs,
    )

    private fun vm(repo: FakeLedgerRepository) = ReportsViewModel(repo, jakarta, FakeSettingsStore(), FakeGeminiClient()) { now }

    @Test
    fun transferNeverInReportTotals() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val s = vm.state.value.summary
        assertEquals(5_000_000L, s.income)
        assertEquals(100_000L, s.expense) // 35.000 + 15.000 + 50.000
        assertEquals(500_000L, s.transferTotal)
        assertEquals(4_900_000L, s.net)
        assertEquals(100_000L, vm.state.value.yearTotals[9].expense)
        assertEquals(5_000_000L, vm.state.value.yearTotals[9].income)
    }

    @Test
    fun categoryChartUsesExpenseOnly() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val rows = vm.state.value.categories
        assertEquals(listOf(FOOD_ID, transportId), rows.map { it.category?.id })
        assertEquals(listOf(50_000L, 50_000L), rows.map { it.total }.sortedDescending())
        assertEquals(100_000L, rows.sumOf { it.total })
        assertTrue(rows.none { it.category?.type == TransactionType.INCOME })
        assertEquals(1.0f, rows.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun categoryDrillDownMatchesChartTotal() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onSelectCategory(FOOD_ID)
        advanceUntilIdle()
        val s = vm.state.value
        val row = s.categories.first { it.category?.id == FOOD_ID }
        assertEquals(FOOD_ID, s.selectedCategoryId)
        assertEquals(row.total, s.drillDown.sumOf { it.tx.amount })
        assertEquals(row.count, s.drillDown.size)
        assertEquals(listOf(3L, 2L), s.drillDown.map { it.tx.id }) // newest first
    }

    @Test
    fun selectingTheSameCategoryAgainClearsTheSelection() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onSelectCategory(FOOD_ID)
        vm.onSelectCategory(FOOD_ID)
        advanceUntilIdle()
        assertNull(vm.state.value.selectedCategoryId)
        assertTrue(vm.state.value.drillDown.isEmpty())
    }

    @Test
    fun changingMonthClearsTheSelection() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onSelectCategory(FOOD_ID)
        vm.onPreviousMonth()
        advanceUntilIdle()
        assertNull(vm.state.value.selectedCategoryId)
    }

    @Test
    fun yearTotalsHas12MonthsAndZeroFilled() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val months = vm.state.value.yearTotals
        assertEquals(12, months.size)
        assertEquals((1..12).map { YearMonth.of(2026, it) }, months.map { it.month })
        assertEquals(1_000_000L, months[0].income)
        assertEquals(0L, months[1].income + months[1].expense)
        assertEquals(99_000L, months[8].expense)
    }

    @Test
    fun dailyExpenseMatchesMonthExpense() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(31, s.daily.size)
        assertEquals(s.summary.expense, s.daily.sumOf { it.expense })
        assertEquals(50_000L, s.daily[4].expense) // 5 Oct
        assertEquals(50_000L, s.daily[18].expense) // 19 Oct: 35.000 + 15.000
    }

    @Test
    fun emptyMonthHasEmptyCategoriesAndZeroDaily() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onMonth(YearMonth.of(2026, 3))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(0L, s.summary.income + s.summary.expense)
        assertTrue(s.categories.isEmpty())
        assertTrue(s.daily.all { it.expense == 0L })
        assertEquals(31, s.daily.size)
    }

    @Test
    fun accountBalancesAreAllTimeAndSeparateFromMonthFlow() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val before = vm.state.value.accountBalances.associate { it.account.id to it.balance }
        vm.onMonth(YearMonth.of(2026, 3))
        advanceUntilIdle()
        val after = vm.state.value.accountBalances.associate { it.account.id to it.balance }
        assertEquals(before, after)
        // Cash: 1.000.000 + 500.000 (transfer in) − 35.000 − 15.000 − 50.000 − 99.000
        assertEquals(1_301_000L, after.getValue(CASH_ID))
    }

    @Test
    fun editedTransactionUpdatesReportImmediately() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals(100_000L, vm.state.value.summary.expense)
        repo.transactions.value = repo.transactions.value.map { if (it.id == 2L) it.copy(amount = 135_000) else it }
        advanceUntilIdle()
        assertEquals(200_000L, vm.state.value.summary.expense)
        repo.transactions.value = repo.transactions.value.filterNot { it.id == 4L }
        advanceUntilIdle()
        assertEquals(150_000L, vm.state.value.summary.expense)
        assertEquals(listOf(FOOD_ID), vm.state.value.categories.map { it.category?.id })
    }

    @Test
    fun yearBoundaryDecemberJanuary() = runTest {
        val txs = listOf(
            expense(1, 1_000, millis(2026, 12, 31, 23, 59)),
            expense(2, 2_000, millis(2027, 1, 1, 0, 0)),
        )
        val vm = vm(repo(txs))
        advanceUntilIdle()
        vm.onMonth(YearMonth.of(2026, 12))
        advanceUntilIdle()
        assertEquals(1_000L, vm.state.value.summary.expense)
        assertEquals(1_000L, vm.state.value.yearTotals[11].expense)

        vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2027, 1), vm.state.value.month)
        assertEquals(2_000L, vm.state.value.summary.expense)
        assertEquals(2_000L, vm.state.value.yearTotals[0].expense)
        assertEquals(0L, vm.state.value.yearTotals[11].expense) // 2027 has no December yet
    }

    @Test
    fun yearButtonsKeepTheMonthAndMoveTheYear() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onPreviousYear()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2025, 10), vm.state.value.month)
        vm.onNextYear()
        vm.onNextYear()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2027, 10), vm.state.value.month)
    }
}
