@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.data.AccountResult
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.ValidationError
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun repo() = FakeLedgerRepository(
        accounts = listOf(TestData.cash(opening = 0), TestData.bank(opening = 100_000), TestData.bank(opening = 0, archived = true).copy(id = 3, name = "Lama")),
        transactions = listOf(TestData.income(1, 50_000, CASH_ID, millis(2026, 10, 3))),
    )

    private fun vm(repo: FakeLedgerRepository) = AccountsViewModel(repo)

    @Test
    fun listSplitsActiveAndArchivedWithBalances() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(listOf("Cash", "Rekening Bank"), s.active.map { it.account.name })
        assertEquals(listOf(50_000L, 100_000L), s.active.map { it.balance })
        assertEquals(listOf("Lama"), s.archived.map { it.account.name })
        assertEquals(150_000L, s.totalBalance)
    }

    @Test
    fun archiveWithBalanceShowsMessage() = runTest {
        val repo = repo().apply { accountResult = AccountResult.NonZeroBalance }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onArchive(BANK_ID)
        advanceUntilIdle()
        assertEquals(AccountMessage.NonZeroBalance, vm.state.value.message)
        assertEquals(listOf(BANK_ID), repo.archivedAccountIds)
    }

    @Test
    fun archiveSuccessShowsArchivedMessage() = runTest {
        val vm = vm(repo())
        advanceUntilIdle()
        vm.onArchive(BANK_ID)
        advanceUntilIdle()
        assertEquals(AccountMessage.Archived, vm.state.value.message)
        vm.onMessageShown()
        advanceUntilIdle()
        assertNull(vm.state.value.message)
    }

    @Test
    fun openingBalanceChangeNeedsConfirmation() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        val bank = repo.accounts.value.first { it.id == BANK_ID }

        vm.onSave(bank, "Rekening Bank", AccountType.BANK, "250.000")
        advanceUntilIdle()
        assertTrue(repo.accountSaves.isEmpty()) // nothing written yet
        assertEquals(PendingOpeningChange(BANK_ID, "Rekening Bank", AccountType.BANK, 100_000, 250_000), vm.state.value.pending)

        vm.onConfirmOpeningChange()
        advanceUntilIdle()
        assertEquals(listOf(Triple<Long?, String, Long>(BANK_ID, "Rekening Bank", 250_000)), repo.accountSaves)
        assertNull(vm.state.value.pending)
        assertEquals(AccountMessage.Saved, vm.state.value.message)
    }

    @Test
    fun cancellingTheConfirmationKeepsTheOldValue() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(repo.accounts.value.first { it.id == BANK_ID }, "Rekening Bank", AccountType.BANK, "1.000")
        advanceUntilIdle()
        vm.onCancelOpeningChange()
        advanceUntilIdle()
        assertNull(vm.state.value.pending)
        assertTrue(repo.accountSaves.isEmpty())
    }

    @Test
    fun renamingWithoutChangingOpeningBalanceNeedsNoConfirmation() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(repo.accounts.value.first { it.id == BANK_ID }, "BCA", AccountType.BANK, "100.000")
        advanceUntilIdle()
        assertNull(vm.state.value.pending)
        assertEquals(listOf(Triple<Long?, String, Long>(BANK_ID, "BCA", 100_000)), repo.accountSaves)
    }

    @Test
    fun newAccountIsSavedDirectlyAndBlankOpeningMeansZero() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(null, "GoPay", AccountType.EWALLET, "")
        advanceUntilIdle()
        assertEquals(listOf(Triple<Long?, String, Long>(null, "GoPay", 0)), repo.accountSaves)
        assertEquals(AccountMessage.Saved, vm.state.value.message)
    }

    @Test
    fun invalidOpeningTextIsRejectedWithoutCallingTheRepository() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(null, "GoPay", AccountType.EWALLET, "12,5")
        advanceUntilIdle()
        assertEquals(AccountMessage.InvalidOpening, vm.state.value.message)
        assertTrue(repo.accountSaves.isEmpty())
    }

    @Test
    fun repositoryRejectionsAreMappedToMessages() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        repo.accountResult = AccountResult.NameBlank
        vm.onSave(null, "  ", AccountType.OTHER, "")
        advanceUntilIdle()
        assertEquals(AccountMessage.NameBlank, vm.state.value.message)

        repo.accountResult = AccountResult.CashAlreadyExists
        vm.onSave(null, "Dompet", AccountType.CASH, "")
        advanceUntilIdle()
        assertEquals(AccountMessage.CashExists, vm.state.value.message)

        val errors = listOf(ValidationError.InsufficientBalance(BANK_ID, 30_000))
        repo.accountResult = AccountResult.WouldGoNegative(errors)
        vm.onSave(repo.accounts.value.first { it.id == BANK_ID }, "Rekening Bank", AccountType.BANK, "")
        vm.onConfirmOpeningChange()
        advanceUntilIdle()
        assertEquals(AccountMessage.WouldGoNegative(errors), vm.state.value.message)
    }

    // Final review finding 4: a double tap on "Simpan" must not create two accounts (Total saldo would double)
    @Test
    fun doubleTapSaveCreatesOneAccount() = runTest {
        val repo = repo().apply { accountGate = CompletableDeferred() }
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onSave(null, "BCA", AccountType.BANK, "2.000.000")
        vm.onSave(null, "BCA", AccountType.BANK, "2.000.000")
        advanceUntilIdle()
        assertTrue(vm.state.value.isSaving)
        assertEquals(1, repo.accountSaves.size)

        repo.accountGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.accountSaves.size)
        assertFalse(vm.state.value.isSaving)
        assertEquals(AccountMessage.Saved, vm.state.value.message)
    }

    @Test
    fun aLaterSaveIsAllowedOnceTheFirstFinished() = runTest {
        val repo = repo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(null, "A", AccountType.EWALLET, "")
        advanceUntilIdle()
        vm.onSave(null, "B", AccountType.EWALLET, "")
        advanceUntilIdle()
        assertEquals(listOf("A", "B"), repo.accountSaves.map { it.second })
    }

    @Test
    fun doubleConfirmOfAnOpeningBalanceChangeSavesOnce() = runTest {
        val repo = repo().apply { accountGate = CompletableDeferred() }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(repo.accounts.value.first { it.id == BANK_ID }, "Rekening Bank", AccountType.BANK, "250.000")
        advanceUntilIdle()
        vm.onConfirmOpeningChange()
        vm.onConfirmOpeningChange()
        advanceUntilIdle()
        repo.accountGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.accountSaves.size)
    }

    @Test
    fun anUnexpectedFailureIsReportedAndTheGuardIsReleased() = runTest {
        val repo = repo().apply { failWith = IllegalStateException("disk full") }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.onSave(null, "BCA", AccountType.BANK, "")
        advanceUntilIdle()
        assertEquals(AccountMessage.Failed, vm.state.value.message)
        assertFalse(vm.state.value.isSaving)
        repo.failWith = null
        vm.onSave(null, "BCA", AccountType.BANK, "")
        advanceUntilIdle()
        assertEquals(AccountMessage.Saved, vm.state.value.message)
    }
}
