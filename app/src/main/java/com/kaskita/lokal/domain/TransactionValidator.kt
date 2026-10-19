package com.kaskita.lokal.domain

/** Form input as the user left it; [id] is null for a new transaction. */
data class TransactionDraft(
    val id: Long?,
    val type: TransactionType,
    val amount: Long?,
    val dateTime: Long,
    val categoryId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val note: String?,
) {
    /** Clears fields that do not apply to [type] so a type change never leaves stale values behind. */
    fun normalized(): TransactionDraft = when (type) {
        TransactionType.INCOME -> copy(fromAccountId = null)
        TransactionType.EXPENSE -> copy(toAccountId = null)
        TransactionType.TRANSFER -> copy(categoryId = null)
    }
}

sealed interface ValidationError {
    data object AmountRequired : ValidationError
    data object AmountTooLarge : ValidationError
    data object CategoryRequired : ValidationError
    data object CategoryTypeMismatch : ValidationError
    data class CategoryUnavailable(val categoryId: Long) : ValidationError
    data object SourceAccountRequired : ValidationError
    data object DestinationAccountRequired : ValidationError
    data object SameAccount : ValidationError
    data class AccountUnavailable(val accountId: Long) : ValidationError
    data class InsufficientBalance(val accountId: Long, val shortfall: Long) : ValidationError

    /** An archived account must stay at Rp0, otherwise its money silently drops out of "Total saldo". */
    data class ArchivedAccountNotZero(val accountId: Long, val resultingBalance: Long) : ValidationError
}

object TransactionValidator {

    /**
     * [existing] is every stored transaction, including the one being edited (matched by `draft.id`),
     * so the edit is judged against the balances it would really produce.
     * Structural errors are returned first and skip the balance check.
     */
    fun validate(
        draft: TransactionDraft,
        accounts: List<Account>,
        categories: List<Category>,
        existing: List<Transaction>,
        allowNegative: Boolean,
    ): List<ValidationError> {
        val d = draft.normalized()
        val original = d.id?.let { id -> existing.firstOrNull { it.id == id } }
        val errors = mutableListOf<ValidationError>()

        val amount = d.amount
        when {
            amount == null || amount <= 0 -> errors += ValidationError.AmountRequired
            amount > MAX_AMOUNT -> errors += ValidationError.AmountTooLarge
        }

        fun checkAccount(id: Long) {
            val account = accounts.firstOrNull { it.id == id }
            val keptFromHistory = original != null && (original.fromAccountId == id || original.toAccountId == id)
            if (account == null || (account.isArchived && !keptFromHistory)) {
                errors += ValidationError.AccountUnavailable(id)
            }
        }

        fun checkCategory() {
            val id = d.categoryId
            if (id == null) {
                errors += ValidationError.CategoryRequired
                return
            }
            val category = categories.firstOrNull { it.id == id }
            when {
                category == null -> errors += ValidationError.CategoryUnavailable(id)
                category.type != d.type -> errors += ValidationError.CategoryTypeMismatch
                category.isArchived && original?.categoryId != id -> errors += ValidationError.CategoryUnavailable(id)
            }
        }

        when (d.type) {
            TransactionType.INCOME -> {
                checkCategory()
                d.toAccountId?.let(::checkAccount) ?: run { errors += ValidationError.DestinationAccountRequired }
            }
            TransactionType.EXPENSE -> {
                checkCategory()
                d.fromAccountId?.let(::checkAccount) ?: run { errors += ValidationError.SourceAccountRequired }
            }
            TransactionType.TRANSFER -> {
                val from = d.fromAccountId
                val to = d.toAccountId
                if (from == null) errors += ValidationError.SourceAccountRequired else checkAccount(from)
                if (to == null) errors += ValidationError.DestinationAccountRequired else checkAccount(to)
                if (from != null && from == to) errors += ValidationError.SameAccount
            }
        }

        if (errors.isNotEmpty()) return errors

        val before = Ledger.balances(accounts, existing)
        val after = Ledger.balances(accounts, existing.filterNot { it.id == d.id } + d.toTransaction())
        val drift = archivedAccountDrift(accounts, before, after)
        if (drift.isNotEmpty()) return drift // applies even when negative balances are allowed
        return if (allowNegative) emptyList() else newlyNegative(accounts, before, after)
    }

    /** Archived accounts whose balance this change would move away from Rp0 (untouched accounts are never blamed). */
    fun archivedAccountDrift(
        accounts: List<Account>,
        before: Map<Long, Long>,
        after: Map<Long, Long>,
    ): List<ValidationError.ArchivedAccountNotZero> = accounts.filter { it.isArchived }.mapNotNull { account ->
        val b = before[account.id]
        val a = after[account.id]
        if (a != null && b != null && a != b && a != 0L) ValidationError.ArchivedAccountNotZero(account.id, a) else null
    }

    /** Accounts that end below zero *and* lower than before; an account that was already negative is not blamed again. */
    fun newlyNegative(
        accounts: List<Account>,
        before: Map<Long, Long>,
        after: Map<Long, Long>,
    ): List<ValidationError.InsufficientBalance> = accounts.mapNotNull { account ->
        val afterBalance = after[account.id] ?: return@mapNotNull null
        val beforeBalance = before[account.id] ?: afterBalance
        if (afterBalance < 0 && afterBalance < beforeBalance) {
            ValidationError.InsufficientBalance(account.id, shortfall = -afterBalance)
        } else {
            null
        }
    }

    private fun TransactionDraft.toTransaction() = Transaction(
        id = id ?: 0L,
        type = type,
        amount = amount ?: 0L,
        dateTime = dateTime,
        categoryId = categoryId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        note = note,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
