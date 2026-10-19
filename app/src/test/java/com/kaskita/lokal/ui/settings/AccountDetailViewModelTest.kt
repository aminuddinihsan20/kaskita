@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun repo() = FakeLedgerRepository(
        accounts = listOf(TestData.cash(opening = 10_000), TestData.bank(opening = 2_000_000)),
        transactions = listOf(
            TestData.transfer(1, 500_000, BANK_ID, CASH_ID, millis(2026, 10, 3)),
            TestData.expense(2, 75_000, CASH_ID, millis(2026, 10, 4)),
            TestData.income(3, 1_000, BANK_ID, millis(2026, 10, 5)),
        ),
    )

    @Test
    fun showsOnlyTransactionsInvolvingTheAccountNewestFirst() = runTest {
        val vm = AccountDetailViewModel(repo(), CASH_ID)
        advanceUntilIdle()
        assertEquals(listOf(2L, 1L), vm.state.value.rows.map { it.tx.id })
    }

    @Test
    fun balanceIncludesOpeningBalance() = runTest {
        val vm = AccountDetailViewModel(repo(), CASH_ID)
        advanceUntilIdle()
        assertEquals(435_000L, vm.state.value.balance) // 10.000 + 500.000 − 75.000
        assertEquals(10_000L, vm.state.value.account?.openingBalance)
    }

    @Test
    fun unknownAccountIsNotFound() = runTest {
        val vm = AccountDetailViewModel(repo(), 999)
        advanceUntilIdle()
        assertTrue(vm.state.value.notFound)
    }

    @Test
    fun unarchiveGoesThroughTheRepositoryAndReportsIt() = runTest {
        val repo = FakeLedgerRepository(accounts = listOf(TestData.cash(opening = 0, archived = true), TestData.bank()))
        val vm = AccountDetailViewModel(repo, CASH_ID)
        advanceUntilIdle()
        vm.onUnarchive()
        advanceUntilIdle()
        assertEquals(listOf(CASH_ID), repo.unarchivedAccountIds)
        assertEquals(AccountMessage.Unarchived, vm.state.value.message)
    }

    @Test
    fun cashAlreadyActiveBlocksUnarchiveWithAMessage() = runTest {
        val repo = FakeLedgerRepository(accounts = listOf(TestData.cash(opening = 0, archived = true), TestData.bank()))
        repo.accountResult = com.kaskita.lokal.data.AccountResult.CashAlreadyExists
        val vm = AccountDetailViewModel(repo, CASH_ID)
        advanceUntilIdle()
        vm.onUnarchive()
        advanceUntilIdle()
        assertEquals(AccountMessage.CashExists, vm.state.value.message)
    }
}
