package com.kaskita.lokal.domain

/** What one account's balance is before and after a transaction. */
data class BalanceChange(val account: Account, val before: Long, val after: Long)

object BalancePreview {
    /**
     * The balances of the accounts a transaction would touch, before and after it. [excludeId] removes the transaction being
     * edited first, so "before" is the balance as if it had never been recorded. Empty for a missing amount, a missing
     * account, or a transfer to the same account.
     */
    fun of(
        accounts: List<Account>,
        transactions: List<Transaction>,
        type: TransactionType,
        amount: Long,
        fromId: Long?,
        toId: Long?,
        excludeId: Long? = null,
    ): List<BalanceChange> {
        if (amount <= 0) return emptyList()
        val balances = Ledger.balances(accounts, transactions.filterNot { it.id == excludeId })
        fun change(id: Long?, delta: Long): BalanceChange? {
            val account = accounts.firstOrNull { it.id == id } ?: return null
            val before = balances[account.id] ?: account.openingBalance
            return BalanceChange(account, before, before + delta)
        }
        return when (type) {
            TransactionType.INCOME -> listOfNotNull(change(toId, amount))
            TransactionType.EXPENSE -> listOfNotNull(change(fromId, -amount))
            TransactionType.TRANSFER -> if (fromId == toId) emptyList() else listOfNotNull(change(fromId, -amount), change(toId, amount))
        }
    }
}
