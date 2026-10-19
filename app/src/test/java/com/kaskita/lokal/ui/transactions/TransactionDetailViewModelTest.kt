@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.transactions

import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.FOOD_ID
import com.kaskita.lokal.domain.ValidationError
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun repo() = FakeLedgerRepository(
        accounts = listOf(TestData.cash(opening = 0), TestData.bank(opening = 0)),
        transactions = listOf(
            TestData.income(1, 100_000, CASH_ID).copy(note = "gaji"),
            TestData.expense(2, 80_000, CASH_ID, category = FOOD_ID),
        ),
    )

    @Test
    fun loadsRowWithNames() = runTest {
        val vm = TransactionDetailViewModel(repo(), 2)
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("Makanan & minuman", s.row?.category?.name)
        assertEquals("Cash", s.row?.from?.name)
    }

    @Test
    fun missingTransactionIsNotFound() = runTest {
        val vm = TransactionDetailViewModel(repo(), 999)
        advanceUntilIdle()
        assertTrue(vm.state.value.notFound)
        assertNull(vm.state.value.row)
    }

    @Test
    fun deleteAsksForConfirmationFirst() = runTest {
        val repo = repo()
        val vm = TransactionDetailViewModel(repo, 2)
        advanceUntilIdle()
        vm.onRequestDelete()
        advanceUntilIdle()
        assertTrue(vm.state.value.confirmingDelete)
        assertEquals(2, repo.transactions.value.size) // nothing deleted yet

        vm.onDismissDelete()
        advanceUntilIdle()
        assertFalse(vm.state.value.confirmingDelete)
        assertEquals(2, repo.transactions.value.size)
    }

    @Test
    fun confirmedDeleteRemovesTheRowAndSignalsDone() = runTest {
        val repo = repo()
        val vm = TransactionDetailViewModel(repo, 2)
        advanceUntilIdle()
        vm.onRequestDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertTrue(vm.state.value.deleted)
        assertEquals(listOf(1L), repo.transactions.value.map { it.id })
    }

    @Test
    fun deleteThatWouldOverdrawIsBlockedAndExplained() = runTest {
        val repo = repo() // income 100.000 funded the 80.000 expense
        val vm = TransactionDetailViewModel(repo, 1)
        advanceUntilIdle()
        vm.onRequestDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.deleted)
        assertFalse(s.confirmingDelete)
        assertNotNull(s.blockedBy)
        assertEquals(listOf(ValidationError.InsufficientBalance(CASH_ID, 80_000)), s.blockedBy)
        assertEquals(2, repo.transactions.value.size)
        vm.onDismissBlocked()
        advanceUntilIdle()
        assertNull(vm.state.value.blockedBy)
    }

    @Test
    fun doubleConfirmDeletesOnlyOnce() = runTest {
        val repo = repo()
        val vm = TransactionDetailViewModel(repo, 2)
        advanceUntilIdle()
        vm.onRequestDelete()
        vm.onConfirmDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.transactions.value.map { it.id })
        assertTrue(vm.state.value.deleted)
    }

    @Test
    fun deleteThatWouldUnbalanceAnArchivedAccountIsBlockedAndExplained() = runTest {
        val repo = FakeLedgerRepository(
            accounts = listOf(TestData.cash(opening = 0, archived = true), TestData.bank(opening = 0)),
            transactions = listOf(TestData.income(1, 100_000, CASH_ID), TestData.expense(2, 100_000, CASH_ID)),
        )
        val vm = TransactionDetailViewModel(repo, 2)
        advanceUntilIdle()
        vm.onRequestDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.deleted)
        assertEquals(listOf(ValidationError.ArchivedAccountNotZero(CASH_ID, 100_000)), s.archivedBlockedBy)
        assertEquals(2, repo.transactions.value.size)
        vm.onDismissBlocked()
        advanceUntilIdle()
        assertNull(vm.state.value.archivedBlockedBy)
    }

    @Test
    fun anUnexpectedFailureWhileDeletingIsReportedNotCrashed() = runTest {
        val repo = repo().apply { failWith = IllegalStateException("disk full") }
        val vm = TransactionDetailViewModel(repo, 2)
        advanceUntilIdle()
        vm.onRequestDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertTrue(vm.state.value.deleteFailed)
        assertFalse(vm.state.value.deleted)
        vm.onDismissBlocked()
        advanceUntilIdle()
        assertFalse(vm.state.value.deleteFailed)
    }
}
