package com.kaskita.lokal.domain

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

enum class AccountType { CASH, BANK, EWALLET, OTHER }

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val openingBalance: Long,
    val createdAt: Long,
    val isArchived: Boolean,
)

/** [type] is INCOME or EXPENSE; transfers have no category. */
data class Category(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorKey: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
)

/**
 * One stored money movement. [amount] is always positive; [type] decides the direction:
 * INCOME credits [toAccountId], EXPENSE debits [fromAccountId], TRANSFER does both.
 */
data class Transaction(
    val id: Long,
    val type: TransactionType,
    val amount: Long,
    val dateTime: Long,
    val categoryId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
