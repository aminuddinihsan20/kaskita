package com.kaskita.lokal.ui.common

import com.kaskita.lokal.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class TxFormatTest {
    @Test
    fun incomeIsPlusSigned() = assertEquals("+Rp100.000", signedAmountText(TransactionType.INCOME, 100_000))

    @Test
    fun expenseUsesTypographicMinus() = assertEquals("−Rp75.000", signedAmountText(TransactionType.EXPENSE, 75_000))

    @Test
    fun transferShowsArrowNotSign() = assertEquals("↔ Rp500.000", signedAmountText(TransactionType.TRANSFER, 500_000))
}
