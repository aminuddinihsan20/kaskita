package com.kaskita.lokal.domain

import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.SAVINGS_ID
import com.kaskita.lokal.domain.TestData.bank
import com.kaskita.lokal.domain.TestData.cash
import com.kaskita.lokal.domain.TestData.expense
import com.kaskita.lokal.domain.TestData.income
import com.kaskita.lokal.domain.TestData.savings
import com.kaskita.lokal.domain.TestData.transfer
import org.junit.Assert.assertEquals
import org.junit.Test

class BalancePreviewTest {
    private val accounts = listOf(bank(opening = 2_000_000), cash(opening = 100_000), savings(opening = 500_000))

    private fun preview(
        type: TransactionType,
        amount: Long,
        from: Long? = null,
        to: Long? = null,
        tx: List<Transaction> = emptyList(),
        exclude: Long? = null,
    ) = BalancePreview.of(accounts, tx, type, amount, from, to, exclude).map { Triple(it.account.id, it.before, it.after) }

    @Test
    fun savingMovesMoneyFromTheSourceToTheSavingsAccount() {
        val p = preview(TransactionType.TRANSFER, 300_000, from = BANK_ID, to = SAVINGS_ID)
        assertEquals(listOf(Triple(BANK_ID, 2_000_000L, 1_700_000L), Triple(SAVINGS_ID, 500_000L, 800_000L)), p)
    }

    @Test
    fun withdrawingFromSavingsGoesTheOtherWay() {
        val p = preview(TransactionType.TRANSFER, 200_000, from = SAVINGS_ID, to = CASH_ID)
        assertEquals(listOf(Triple(SAVINGS_ID, 500_000L, 300_000L), Triple(CASH_ID, 100_000L, 300_000L)), p)
    }

    @Test
    fun incomeAndExpenseTouchOnlyOneAccount() {
        assertEquals(listOf(Triple(CASH_ID, 100_000L, 175_000L)), preview(TransactionType.INCOME, 75_000, to = CASH_ID))
        assertEquals(listOf(Triple(BANK_ID, 2_000_000L, 1_950_000L)), preview(TransactionType.EXPENSE, 50_000, from = BANK_ID))
    }

    @Test
    fun theBeforeBalanceIncludesEarlierTransactions() {
        val tx = listOf(income(1, 1_000_000, BANK_ID), expense(2, 400_000, BANK_ID))
        assertEquals(listOf(Triple(BANK_ID, 2_600_000L, 2_500_000L)), preview(TransactionType.EXPENSE, 100_000, from = BANK_ID, tx = tx))
    }

    @Test
    fun editingShowsTheBalanceWithoutTheTransactionBeingEdited() {
        val tx = listOf(transfer(7, 300_000, BANK_ID, SAVINGS_ID))
        // the saved 300.000 is taken out first, so the preview of a new amount 100.000 starts from the original balances
        val p = preview(TransactionType.TRANSFER, 100_000, from = BANK_ID, to = SAVINGS_ID, tx = tx, exclude = 7)
        assertEquals(listOf(Triple(BANK_ID, 2_000_000L, 1_900_000L), Triple(SAVINGS_ID, 500_000L, 600_000L)), p)
    }

    @Test
    fun aTransferToTheSameAccountOrWithoutAnAmountPreviewsNothing() {
        assertEquals(emptyList<Triple<Long, Long, Long>>(), preview(TransactionType.TRANSFER, 100, from = BANK_ID, to = BANK_ID))
        assertEquals(emptyList<Triple<Long, Long, Long>>(), preview(TransactionType.EXPENSE, 0, from = BANK_ID))
        assertEquals(emptyList<Triple<Long, Long, Long>>(), preview(TransactionType.INCOME, 10, to = null))
    }
}
