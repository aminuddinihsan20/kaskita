package com.kaskita.lokal.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalance: Long,
    val createdAt: Long,
    val isArchived: Boolean,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorKey: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(AccountEntity::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dateTime"), Index("fromAccountId"), Index("toAccountId"), Index("categoryId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

fun AccountEntity.toDomain() = Account(id, name, type, openingBalance, createdAt, isArchived)
fun Account.toEntity() = AccountEntity(id, name, type, openingBalance, createdAt, isArchived)

fun CategoryEntity.toDomain() = Category(id, name, type, iconKey, colorKey, isDefault, isArchived)
fun Category.toEntity() = CategoryEntity(id, name, type, iconKey, colorKey, isDefault, isArchived)

fun TransactionEntity.toDomain() =
    Transaction(id, type, amount, dateTime, categoryId, fromAccountId, toAccountId, note, createdAt, updatedAt)

fun Transaction.toEntity() =
    TransactionEntity(id, type, amount, dateTime, categoryId, fromAccountId, toAccountId, note, createdAt, updatedAt)
