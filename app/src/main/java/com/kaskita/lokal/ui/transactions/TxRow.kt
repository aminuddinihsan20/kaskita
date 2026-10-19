package com.kaskita.lokal.ui.transactions

import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Transaction

/** A transaction with its category/accounts resolved (archived ones included) so rows can show names and icons. */
data class TxRow(val tx: Transaction, val category: Category?, val from: Account?, val to: Account?)

fun buildRows(transactions: List<Transaction>, accounts: List<Account>, categories: List<Category>): List<TxRow> {
    val accountById = accounts.associateBy { it.id }
    val categoryById = categories.associateBy { it.id }
    return transactions.map { tx ->
        TxRow(
            tx = tx,
            category = tx.categoryId?.let(categoryById::get),
            from = tx.fromAccountId?.let(accountById::get),
            to = tx.toAccountId?.let(accountById::get),
        )
    }
}
