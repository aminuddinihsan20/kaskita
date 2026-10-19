@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.form

import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.FOOD_ID
import com.kaskita.lokal.domain.TestData.SALARY_ID
import com.kaskita.lokal.domain.TestData.expense
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.ValidationError
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionFormViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = TestData.millis(2026, 10, 19)

    private fun repo(cashOpening: Long = 500_000, bankOpening: Long = 2_000_000, txs: List<Transaction> = emptyList()) =
        FakeLedgerRepository(
            accounts = listOf(TestData.cash(opening = cashOpening), TestData.bank(opening = bankOpening)),
            transactions = txs,
        )

    private fun vm(repo: FakeLedgerRepository, type: TransactionType = TransactionType.EXPENSE, editId: Long? = null) =
        TransactionFormViewModel(repo, type, editId) { today }

    // --- spec G16
    @Test
    fun doubleTapSaveCreatesOneTransaction() = runTest {
        val repo = repo().apply { gate = CompletableDeferred() }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onAmountChange("75.000")
        vm.onCategory(FOOD_ID)

        vm.onSave()
        vm.onSave()
        advanceUntilIdle()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.saveCalls)
        assertEquals(1, repo.transactions.value.size)
    }

    @Test
    fun savingStateBlocksFurtherSavesUntilFinished() = runTest {
        val repo = repo().apply { gate = CompletableDeferred() }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onAmountChange("1000")
        vm.onCategory(FOOD_ID)

        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.state.value.isSaving)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(1, repo.saveCalls)

        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.isSaving)
        assertTrue(vm.state.value.saved)
        vm.onSave() // after success a stray tap must not create a second row
        advanceUntilIdle()
        assertEquals(1, repo.saveCalls)
    }

    @Test
    fun successSetsSavedAndReportsNewBalance() = runTest {
        val repo = repo(cashOpening = 500_000)
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onAmountChange("75000")
        vm.onCategory(FOOD_ID)
        vm.onSave()
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.saved)
        assertFalse(s.isSaving)
        assertEquals(1, s.savedBalances.size)
        assertEquals(CASH_ID, s.savedBalances[0].account.id)
        assertEquals(425_000L, s.savedBalances[0].balance)
        assertEquals(today, repo.transactions.value.single().dateTime)
    }

    @Test
    fun invalidSaveKeepsFormValues() = runTest {
        val repo = repo(cashOpening = 100_000)
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onAmountChange("150.000")
        vm.onCategory(FOOD_ID)
        vm.onNote("makan siang")

        vm.onSave()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("150.000", s.amountText)
        assertEquals(FOOD_ID, s.categoryId)
        assertEquals("makan siang", s.note)
        assertEquals(CASH_ID, s.fromAccountId)
        assertFalse(s.isSaving)
        assertFalse(s.saved)
        assertEquals(listOf<ValidationError>(ValidationError.InsufficientBalance(CASH_ID, 50_000)), s.errors)
        assertEquals(0, repo.transactions.value.size)
    }

    @Test
    fun blankAmountReportsAmountRequired() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onCategory(FOOD_ID)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf<ValidationError>(ValidationError.AmountRequired), vm.state.value.errors)
    }

    @Test
    fun editingTheFormClearsOldErrors() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.state.value.errors.isNotEmpty())
        vm.onAmountChange("5000")
        assertTrue(vm.state.value.errors.isEmpty())
    }

    @Test
    fun repositoryFailureKeepsFormAndFlagsGeneralError() = runTest {
        val repo = repo().apply { failWith = IllegalStateException("disk full") }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onAmountChange("5000")
        vm.onCategory(FOOD_ID)
        vm.onSave()
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.saveFailed)
        assertFalse(s.isSaving)
        assertFalse(s.saved)
        assertEquals("5.000", s.amountText)
        assertEquals(FOOD_ID, s.categoryId)
        vm.onAmountChange("6000")
        assertFalse(vm.state.value.saveFailed)
    }

    // --- defaults
    @Test
    fun newExpenseDefaultsToCashAndToday() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(CASH_ID, s.fromAccountId)
        assertNull(s.toAccountId)
        assertEquals(today, s.dateTime)
        assertEquals(listOf(FOOD_ID), s.categories.map { it.id })
        assertFalse(s.isLoading)
    }

    @Test
    fun newIncomeDefaultsDestinationToCash() = runTest {
        val vm = vm(repo(), TransactionType.INCOME)
        advanceUntilIdle()
        assertEquals(CASH_ID, vm.state.value.toAccountId)
        assertNull(vm.state.value.fromAccountId)
        assertEquals(listOf(SALARY_ID), vm.state.value.categories.map { it.id })
    }

    @Test
    fun newTransferDefaultsToBankThenCash() = runTest {
        val vm = vm(repo(), TransactionType.TRANSFER)
        advanceUntilIdle()
        assertEquals(BANK_ID, vm.state.value.fromAccountId)
        assertEquals(CASH_ID, vm.state.value.toAccountId)
        assertNull(vm.state.value.categoryId)
    }

    @Test
    fun typeChangeClearsIrrelevantFieldsButKeepsAmountAndNote() = runTest {
        val vm = vm(repo(), TransactionType.EXPENSE)
        advanceUntilIdle()
        vm.onAmountChange("12.000")
        vm.onNote("catatan")
        vm.onCategory(FOOD_ID)

        vm.onTypeChange(TransactionType.INCOME)
        var s = vm.state.value
        assertEquals(TransactionType.INCOME, s.type)
        assertNull(s.categoryId)
        assertNull(s.fromAccountId)
        assertEquals(CASH_ID, s.toAccountId)
        assertEquals("12.000", s.amountText)
        assertEquals("catatan", s.note)

        vm.onTypeChange(TransactionType.TRANSFER)
        s = vm.state.value
        assertNull(s.categoryId)
        assertNotNull(s.fromAccountId)
        assertNotNull(s.toAccountId)
        assertTrue(s.fromAccountId != s.toAccountId)
    }

    @Test
    fun transferPreviewReflectsAmountAndAccounts() = runTest {
        val vm = vm(repo(), TransactionType.TRANSFER)
        advanceUntilIdle()
        assertTrue(vm.state.value.balanceChanges.isEmpty()) // no amount yet
        vm.onAmountChange("500.000")
        assertEquals(
            listOf(Triple(BANK_ID, 2_000_000L, 1_500_000L), Triple(CASH_ID, 500_000L, 1_000_000L)),
            vm.state.value.balanceChanges.map { Triple(it.account.id, it.before, it.after) },
        )
        vm.onFromAccount(CASH_ID) // Cash was the destination, so the destination flips to the old source
        assertEquals(
            listOf(Triple(CASH_ID, 500_000L, 0L), Triple(BANK_ID, 2_000_000L, 2_500_000L)),
            vm.state.value.balanceChanges.map { Triple(it.account.id, it.before, it.after) },
        )
    }

    @Test
    fun choosingTheSameAccountOnOneSideSwapsTheOther() = runTest {
        val vm = vm(repo(), TransactionType.TRANSFER)
        advanceUntilIdle() // from=Bank to=Cash
        vm.onToAccount(BANK_ID)
        assertEquals(BANK_ID, vm.state.value.toAccountId)
        assertEquals(CASH_ID, vm.state.value.fromAccountId)
    }

    // --- edit
    @Test
    fun editLoadsExistingValuesAndSavesAnUpdate() = runTest {
        val original = expense(id = 7, amount = 40_000, from = CASH_ID, date = TestData.millis(2026, 10, 3)).copy(note = "bensin")
        val repo = repo(cashOpening = 500_000, txs = listOf(original))
        val vm = vm(repo, TransactionType.EXPENSE, editId = 7)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("40.000", s.amountText)
        assertEquals(FOOD_ID, s.categoryId)
        assertEquals(CASH_ID, s.fromAccountId)
        assertEquals("bensin", s.note)
        assertEquals(TestData.millis(2026, 10, 3), s.dateTime)

        vm.onAmountChange("45.000")
        vm.onSave()
        advanceUntilIdle()

        assertEquals(7L, repo.savedDrafts.single().id)
        assertEquals(1, repo.transactions.value.size)
        assertEquals(45_000L, repo.transactions.value.single().amount)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun editOfMissingTransactionFlagsNotFound() = runTest {
        val vm = vm(repo(), editId = 12345)
        advanceUntilIdle()
        assertTrue(vm.state.value.notFound)
    }

    @Test
    fun archivedAccountOfOriginalRemainsSelectableWhileEditing() = runTest {
        val archivedCash = TestData.cash(opening = 0, archived = true)
        val original = TestData.income(id = 3, amount = 10_000, to = CASH_ID)
        val repo = FakeLedgerRepository(
            accounts = listOf(archivedCash, TestData.bank()),
            transactions = listOf(original),
        )
        val editing = vm(repo, TransactionType.INCOME, editId = 3)
        advanceUntilIdle()
        assertTrue(editing.state.value.accounts.any { it.id == CASH_ID })

        val fresh = vm(repo, TransactionType.INCOME)
        advanceUntilIdle()
        assertFalse(fresh.state.value.accounts.any { it.id == CASH_ID })
    }

    // Final review finding 2
    @Test
    fun rejectedInputLeavesTheAmountUntouched() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onAmountChange("50.000")
        vm.onAmountChange("2.500.000,00")
        vm.onAmountChange("-5")
        assertEquals("50.000", vm.state.value.amountText)
    }

    @Test
    fun amountAboveTheMaximumReportsAmountTooLargeNotAmountRequired() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onAmountChange("9999999999999") // 13 digits: accepted by the field, above Rp1.000.000.000.000
        vm.onCategory(FOOD_ID)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf<ValidationError>(ValidationError.AmountTooLarge), vm.state.value.errors)
        assertEquals("9.999.999.999.999", vm.state.value.amountText)
    }
}
