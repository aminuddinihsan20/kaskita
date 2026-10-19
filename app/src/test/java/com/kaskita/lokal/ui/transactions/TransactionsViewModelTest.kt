@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.transactions

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TransactionsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = millis(2026, 10, 19, 12)
    private val transportId = 11L

    private fun income(id: Long, amount: Long, date: Long, to: Long = CASH_ID, note: String? = null) =
        TestData.income(id, amount, to, date).copy(note = note)

    private fun expense(id: Long, amount: Long, date: Long, from: Long = CASH_ID, category: Long = FOOD_ID, note: String? = null) =
        TestData.expense(id, amount, from, date, category).copy(note = note)

    private fun transfer(id: Long, amount: Long, date: Long) = TestData.transfer(id, amount, BANK_ID, CASH_ID, date)

    private val sample: List<Transaction> = listOf(
        expense(1, 35_000, millis(2026, 10, 19, 9), note = "Belanja makan"),
        income(2, 5_000_000, millis(2026, 10, 18, 8), to = BANK_ID, note = "Gaji Oktober"),
        transfer(3, 500_000, millis(2026, 10, 18, 7)),
        expense(4, 20_000, millis(2026, 10, 5, 12), category = transportId, note = "BENSIN motor"),
        expense(5, 99_000, millis(2026, 9, 30, 23), note = "bulan lalu"),
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

    private fun vm(repo: FakeLedgerRepository) = TransactionsViewModel(repo, jakarta) { now }

    @Test
    fun startsLoadingThenShowsCurrentMonth() = runTest {
        val vm = vm(repo())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(YearMonth.of(2026, 10), vm.state.value.filter.month)
    }

    @Test
    fun groupsLabelTodayAndYesterday() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val groups = vm.state.value.groups
        assertEquals(listOf(RelativeDay.TODAY, RelativeDay.YESTERDAY, RelativeDay.OTHER), groups.map { it.relative })
        assertEquals(
            listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 5)),
            groups.map { it.day },
        )
        assertEquals(listOf(1), groups[0].items.map { it.tx.id.toInt() })
        assertEquals(listOf(2, 3), groups[1].items.map { it.tx.id.toInt() }) // same day: later time first
    }

    @Test
    fun rowsResolveCategoryAndAccountNames() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val rows = vm.state.value.groups.flatMap { it.items }.associateBy { it.tx.id }
        assertEquals("Makanan & minuman", rows.getValue(1).category?.name)
        assertEquals("Cash", rows.getValue(1).from?.name)
        assertNull(rows.getValue(3).category)
        assertEquals("Rekening Bank", rows.getValue(3).from?.name)
        assertEquals("Cash", rows.getValue(3).to?.name)
    }

    @Test
    fun filterByTypeAccountCategoryAndQuery() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        fun ids() = vm.state.value.groups.flatMap { it.items }.map { it.tx.id }.toSet()

        vm.onType(TransactionType.EXPENSE)
        advanceUntilIdle()
        assertEquals(setOf(1L, 4L), ids())

        vm.onCategory(transportId)
        advanceUntilIdle()
        assertEquals(setOf(4L), ids())

        vm.onCategory(null)
        vm.onType(null)
        vm.onAccount(BANK_ID)
        advanceUntilIdle()
        assertEquals(setOf(2L, 3L), ids()) // income into Bank + the transfer leaving Bank

        vm.onAccount(null)
        vm.onQuery("makan")
        advanceUntilIdle()
        assertEquals(setOf(1L), ids())
    }

    @Test
    fun searchIsCaseInsensitiveOnNote() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onQuery("bensin")
        advanceUntilIdle()
        assertEquals(listOf(4L), vm.state.value.groups.flatMap { it.items }.map { it.tx.id })
    }

    @Test
    fun accountFilterMatchesBothLegsOfATransfer() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onAccount(CASH_ID)
        advanceUntilIdle()
        assertTrue(3L in vm.state.value.groups.flatMap { it.items }.map { it.tx.id })
        vm.onAccount(BANK_ID)
        advanceUntilIdle()
        assertTrue(3L in vm.state.value.groups.flatMap { it.items }.map { it.tx.id })
    }

    @Test
    fun typeFilterClearsMismatchedCategory() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onCategory(FOOD_ID)
        vm.onType(TransactionType.INCOME)
        advanceUntilIdle()
        assertNull(vm.state.value.filter.categoryId)
    }

    @Test
    fun summaryRespectsFilterAndExcludesTransferFromIncomeExpense() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val all = vm.state.value.summary
        assertEquals(5_000_000L, all.income)
        assertEquals(55_000L, all.expense) // 35.000 + 20.000, not the 500.000 transfer
        assertEquals(500_000L, all.transferTotal)

        vm.onType(TransactionType.TRANSFER)
        advanceUntilIdle()
        val transfersOnly = vm.state.value.summary
        assertEquals(0L, transfersOnly.income)
        assertEquals(0L, transfersOnly.expense)
        assertEquals(500_000L, transfersOnly.transferTotal)
    }

    @Test
    fun emptyPeriodShowsEmptyState() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onMonth(YearMonth.of(2026, 1))
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
        assertEquals(emptyList<DayGroup>(), vm.state.value.groups)
        assertFalse(vm.state.value.filterActive)
    }

    @Test
    fun noMatchesWithAFilterIsEmptyAndFlaggedAsFiltered() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onQuery("tidak ada yang begini")
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
        assertTrue(vm.state.value.filterActive)
    }

    @Test
    fun monthSwitchRecomputes() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.filter.month)
        assertEquals(listOf(5L), vm.state.value.groups.flatMap { it.items }.map { it.tx.id })
        assertEquals(99_000L, vm.state.value.summary.expense)
        vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.filter.month)
    }

    @Test
    fun editedOrAddedTransactionsAppearImmediately() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        repo.transactions.value = repo.transactions.value + expense(9, 1_000, millis(2026, 10, 19, 10), note = "baru")
        advanceUntilIdle()
        assertEquals(setOf(1L, 9L), vm.state.value.groups[0].items.map { it.tx.id }.toSet())
        assertEquals(56_000L, vm.state.value.summary.expense)
    }

    @Test
    fun archivedAccountNameStillResolvedOnRows() = runTest {
        val repo = FakeLedgerRepository(
            accounts = listOf(TestData.cash(archived = true), TestData.bank()),
            transactions = listOf(income(1, 1_000, millis(2026, 10, 19, 9))),
        )
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals("Cash", vm.state.value.groups.single().items.single().to?.name)
    }

}
