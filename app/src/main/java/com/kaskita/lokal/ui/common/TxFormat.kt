package com.kaskita.lokal.ui.common

import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.TransactionType

private const val MINUS = '−'
private const val ARROW = '↔'

/** `+Rp…` for income, `−Rp…` for expense, `↔ Rp…` for transfer, so direction never relies on color alone. */
fun signedAmountText(type: TransactionType, amount: Long): String = when (type) {
    TransactionType.INCOME -> "+${RupiahFormatter.format(amount)}"
    TransactionType.EXPENSE -> "$MINUS${RupiahFormatter.format(amount)}"
    TransactionType.TRANSFER -> "$ARROW ${RupiahFormatter.format(amount)}"
}
