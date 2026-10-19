@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.form

import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.MonthRange
import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.SAVINGS_ID
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class TransactionFormSavingsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = TestData.millis(2026, 10, 19)

    private fun repo(withSavings: Boolean = true, txs: List<Transaction> = emptyList()) = FakeLedgerRepository(
        accounts = listOfNotNull(
            TestData.cash(opening = 100_000),
            TestData.bank(opening = 2_000_000),
            TestData.savings(opening = 500_000).takeIf { withSavings },
        ),
        transactions = txs,
    )

    private fun vm(repo: FakeLedgerRepository, type: TransactionType = TransactionType.TRANSFER, editId: Long? = null, savings: Boolean = false) =
        TransactionFormViewModel(repo, type, editId, savingsPreset = savings) { today }

    private fun changes(vm: TransactionFormViewModel) = vm.state.value.balanceChanges.map { Triple(it.account.id, it.before, it.after) }

    @Test
    fun theSavingsShortcutPicksASavingsAccountAsTheDestinationAndANonSavingsSource() = runTest {
        val vm = vm(repo(), savings = true)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(TransactionType.TRANSFER, s.type)
        assertEquals(SAVINGS_ID, s.toAccountId)
        assertEquals(BANK_ID, s.fromAccountId)
        assertTrue(s.hasSavingsAccount)
        assertEquals(TransferKind.DEPOSIT, s.transferKind)
    }

    @Test
    fun withoutASavingsAccountTheShortcutFallsBackAndSaysSo() = runTest {
        val vm = vm(repo(withSavings = false), savings = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.hasSavingsAccount)
        assertEquals(TransferKind.NORMAL, vm.state.value.transferKind)
        assertTrue(vm.state.value.toAccountId != null)
    }

    @Test
    fun theKindFollowsWhichSideIsSavings() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onFromAccount(BANK_ID); vm.onToAccount(SAVINGS_ID)
        assertEquals(TransferKind.DEPOSIT, vm.state.value.transferKind)
        vm.onFromAccount(SAVINGS_ID); vm.onToAccount(CASH_ID)
        assertEquals(TransferKind.WITHDRAW, vm.state.value.transferKind)
        vm.onFromAccount(BANK_ID); vm.onToAccount(CASH_ID)
        assertEquals(TransferKind.NORMAL, vm.state.value.transferKind)
    }

    @Test
    fun theBalancePreviewShowsBeforeAndAfterForBothSides() = runTest {
        val vm = vm(repo(), savings = true)
        advanceUntilIdle()
        assertTrue(vm.state.value.balanceChanges.isEmpty()) // no amount yet
        vm.onAmountChange("300.000")
        advanceUntilIdle()
        assertEquals(listOf(Triple(BANK_ID, 2_000_000L, 1_700_000L), Triple(SAVINGS_ID, 500_000L, 800_000L)), changes(vm))
    }

    @Test
    fun swappingFlipsTheDirectionAndTheKind() = runTest {
        val vm = vm(repo(), savings = true)
        advanceUntilIdle()
        vm.onAmountChange("100.000")
        vm.onSwapAccounts()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(SAVINGS_ID, s.fromAccountId)
        assertEquals(BANK_ID, s.toAccountId)
        assertEquals(TransferKind.WITHDRAW, s.transferKind)
        assertEquals(listOf(Triple(SAVINGS_ID, 500_000L, 400_000L), Triple(BANK_ID, 2_000_000L, 2_100_000L)), changes(vm))
    }

    @Test
    fun swappingIsIgnoredOutsideTransfers() = runTest {
        val vm = vm(repo(), type = TransactionType.EXPENSE)
        advanceUntilIdle()
        val before = vm.state.value
        vm.onSwapAccounts()
        assertEquals(before.fromAccountId, vm.state.value.fromAccountId)
        assertEquals(before.toAccountId, vm.state.value.toAccountId)
    }

    @Test
    fun everyAccountChipHasItsCurrentBalanceAndEditingLeavesOutTheEditedTransaction() = runTest {
        val saved = TestData.transfer(9, 300_000, BANK_ID, SAVINGS_ID, today)
        val plain = vm(repo(txs = listOf(saved)))
        advanceUntilIdle()
        assertEquals(mapOf(CASH_ID to 100_000L, BANK_ID to 1_700_000L, SAVINGS_ID to 800_000L), plain.state.value.balances)
        val editing = vm(repo(txs = listOf(saved)), editId = 9)
        advanceUntilIdle()
        assertEquals(mapOf(CASH_ID to 100_000L, BANK_ID to 2_000_000L, SAVINGS_ID to 500_000L), editing.state.value.balances)
    }

    @Test
    fun incomeAndExpenseAlsoGetAPreview() = runTest {
        val vm = vm(repo(), type = TransactionType.EXPENSE)
        advanceUntilIdle()
        vm.onAmountChange("25.000")
        advanceUntilIdle()
        assertEquals(listOf(Triple(CASH_ID, 100_000L, 75_000L)), changes(vm))
        vm.onTypeChange(TransactionType.INCOME)
        advanceUntilIdle()
        assertEquals(listOf(Triple(CASH_ID, 100_000L, 125_000L)), changes(vm))
    }

    @Test
    fun editingPreviewsFromTheBalanceWithoutTheTransactionBeingEdited() = runTest {
        val saved = TestData.transfer(9, 300_000, BANK_ID, SAVINGS_ID, today)
        val vm = vm(repo(txs = listOf(saved)), editId = 9)
        advanceUntilIdle()
        vm.onAmountChange("100.000")
        advanceUntilIdle()
        assertEquals(listOf(Triple(BANK_ID, 2_000_000L, 1_900_000L), Triple(SAVINGS_ID, 500_000L, 600_000L)), changes(vm))
    }

    @Test
    fun savingMoneyRecordsOneTransferAndIsNeverAnExpense() = runTest {
        val repo = repo()
        val vm = vm(repo, savings = true)
        advanceUntilIdle()
        vm.onAmountChange("300.000")
        vm.onSave()
        advanceUntilIdle()

        val tx = repo.transactions.value.single()
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals(300_000L, tx.amount)
        val balances = Ledger.balances(repo.accounts.value, repo.transactions.value)
        assertEquals(1_700_000L, balances[BANK_ID])
        assertEquals(800_000L, balances[SAVINGS_ID])
        assertEquals(2_600_000L, Ledger.totalBalance(repo.accounts.value, balances))
        assertEquals(0L, Ledger.summary(repo.transactions.value, MonthRange.of(YearMonth.of(2026, 10), jakarta)).expense)
    }

    @Test
    fun savingMoreThanTheSourceHasIsRefusedWhenNegativeBalanceIsOff() = runTest {
        val repo = repo()
        val vm = vm(repo, savings = true)
        advanceUntilIdle()
        vm.onAmountChange("2.500.000")
        vm.onSave()
        advanceUntilIdle()
        assertTrue(repo.transactions.value.isEmpty())
        assertTrue(vm.state.value.errors.isNotEmpty())
    }
}
