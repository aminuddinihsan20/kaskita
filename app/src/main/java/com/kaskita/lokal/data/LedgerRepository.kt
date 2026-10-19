package com.kaskita.lokal.data

import androidx.room.withTransaction
import com.kaskita.lokal.data.db.AccountEntity
import com.kaskita.lokal.data.db.CategoryEntity
import com.kaskita.lokal.data.db.KasKitaDatabase
import com.kaskita.lokal.data.db.TransactionEntity
import com.kaskita.lokal.data.db.toDomain
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.MAX_AMOUNT
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionDraft
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.TransactionValidator
import com.kaskita.lokal.domain.ValidationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

sealed interface SaveResult {
    data class Success(val id: Long) : SaveResult
    data class Invalid(val errors: List<ValidationError>) : SaveResult
    data object NotFound : SaveResult
}

sealed interface DeleteResult {
    data object Success : DeleteResult
    data object NotFound : DeleteResult
    data class WouldGoNegative(val errors: List<ValidationError.InsufficientBalance>) : DeleteResult
    data class ArchivedAccountAffected(val errors: List<ValidationError.ArchivedAccountNotZero>) : DeleteResult
}

sealed interface AccountResult {
    data object Success : AccountResult
    data object NotFound : AccountResult
    data object NameBlank : AccountResult
    data object InvalidOpeningBalance : AccountResult
    data object NonZeroBalance : AccountResult
    data object CashAlreadyExists : AccountResult

    /** The opening balance of an archived account cannot change; that would move it away from Rp0. */
    data object ArchivedBalanceLocked : AccountResult
    data class WouldGoNegative(val errors: List<ValidationError.InsufficientBalance>) : AccountResult
}

interface LedgerRepository {
    /** Includes archived accounts. */
    fun observeAccounts(): Flow<List<Account>>
    fun observeCategories(): Flow<List<Category>>

    /** Every stored transaction, newest first. */
    fun observeTransactions(): Flow<List<Transaction>>

    /** Insert when `draft.id == null`, otherwise update; validation and write happen in one DB transaction. */
    suspend fun saveTransaction(draft: TransactionDraft): SaveResult
    suspend fun deleteTransaction(id: Long): DeleteResult

    suspend fun saveAccount(id: Long?, name: String, type: AccountType, openingBalance: Long): AccountResult
    suspend fun archiveAccount(id: Long): AccountResult

    /** Makes an archived account selectable again (an active Cash account must not already exist). */
    suspend fun unarchiveAccount(id: Long): AccountResult

    /** Returns false for a blank name, a TRANSFER type, or an attempt to change an existing category's type. */
    suspend fun saveCategory(id: Long?, name: String, type: TransactionType, iconKey: String, colorKey: String): Boolean
    suspend fun setCategoryArchived(id: Long, archived: Boolean)
}

class RoomLedgerRepository(
    private val db: KasKitaDatabase,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) : LedgerRepository {

    private val accountDao get() = db.accountDao()
    private val categoryDao get() = db.categoryDao()
    private val transactionDao get() = db.transactionDao()

    override fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll().map { it.map(AccountEntity::toDomain) }

    override fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll().map { it.map(CategoryEntity::toDomain) }

    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { it.map(TransactionEntity::toDomain) }

    override suspend fun saveTransaction(draft: TransactionDraft): SaveResult {
        val allowNegative = settings.allowNegativeBalance.first()
        return db.withTransaction {
            val accounts = accountDao.getAll().map(AccountEntity::toDomain)
            val categories = categoryDao.getAll().map(CategoryEntity::toDomain)
            val existing = transactionDao.getAll().map(TransactionEntity::toDomain)

            val original = draft.id?.let { id -> existing.firstOrNull { it.id == id } ?: return@withTransaction SaveResult.NotFound }
            val errors = TransactionValidator.validate(draft, accounts, categories, existing, allowNegative)
            if (errors.isNotEmpty()) return@withTransaction SaveResult.Invalid(errors)

            val d = draft.normalized()
            val stamp = now()
            val entity = TransactionEntity(
                id = original?.id ?: 0L,
                type = d.type,
                amount = requireNotNull(d.amount),
                dateTime = d.dateTime,
                categoryId = d.categoryId,
                fromAccountId = d.fromAccountId,
                toAccountId = d.toAccountId,
                note = d.note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = original?.createdAt ?: stamp,
                updatedAt = stamp,
            )
            if (original == null) {
                SaveResult.Success(transactionDao.insert(entity))
            } else {
                transactionDao.update(entity)
                SaveResult.Success(original.id)
            }
        }
    }

    override suspend fun deleteTransaction(id: Long): DeleteResult {
        val allowNegative = settings.allowNegativeBalance.first()
        return db.withTransaction {
            val accounts = accountDao.getAll().map(AccountEntity::toDomain)
            val existing = transactionDao.getAll().map(TransactionEntity::toDomain)
            if (existing.none { it.id == id }) return@withTransaction DeleteResult.NotFound

            val before = Ledger.balances(accounts, existing)
            val after = Ledger.balances(accounts, existing.filterNot { it.id == id })
            val drift = TransactionValidator.archivedAccountDrift(accounts, before, after)
            if (drift.isNotEmpty()) return@withTransaction DeleteResult.ArchivedAccountAffected(drift)
            if (!allowNegative) {
                val errors = TransactionValidator.newlyNegative(accounts, before, after)
                if (errors.isNotEmpty()) return@withTransaction DeleteResult.WouldGoNegative(errors)
            }
            transactionDao.deleteById(id)
            DeleteResult.Success
        }
    }

    override suspend fun saveAccount(id: Long?, name: String, type: AccountType, openingBalance: Long): AccountResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AccountResult.NameBlank
        val allowNegative = settings.allowNegativeBalance.first()
        if (openingBalance > MAX_AMOUNT || (openingBalance < 0 && !allowNegative)) return AccountResult.InvalidOpeningBalance

        return db.withTransaction {
            val accounts = accountDao.getAll().map(AccountEntity::toDomain)
            val willBeActive = accounts.firstOrNull { it.id == id }?.isArchived != true
            if (willBeActive && type == AccountType.CASH && accounts.any { it.type == AccountType.CASH && !it.isArchived && it.id != id }) {
                return@withTransaction AccountResult.CashAlreadyExists
            }
            if (id == null) {
                accountDao.insert(
                    AccountEntity(name = trimmed, type = type, openingBalance = openingBalance, createdAt = now(), isArchived = false),
                )
                return@withTransaction AccountResult.Success
            }

            val current = accounts.firstOrNull { it.id == id } ?: return@withTransaction AccountResult.NotFound
            val updated = current.copy(name = trimmed, type = type, openingBalance = openingBalance)
            if (current.isArchived && updated.openingBalance != current.openingBalance) {
                return@withTransaction AccountResult.ArchivedBalanceLocked
            }
            if (!allowNegative && updated.openingBalance != current.openingBalance) {
                val existing = transactionDao.getAll().map(TransactionEntity::toDomain)
                val errors = TransactionValidator.newlyNegative(
                    accounts,
                    before = Ledger.balances(accounts, existing),
                    after = Ledger.balances(accounts.map { if (it.id == id) updated else it }, existing),
                )
                if (errors.isNotEmpty()) return@withTransaction AccountResult.WouldGoNegative(errors)
            }
            accountDao.update(AccountEntity(updated.id, updated.name, updated.type, updated.openingBalance, updated.createdAt, updated.isArchived))
            AccountResult.Success
        }
    }

    override suspend fun archiveAccount(id: Long): AccountResult = db.withTransaction {
        val accounts = accountDao.getAll().map(AccountEntity::toDomain)
        val account = accounts.firstOrNull { it.id == id } ?: return@withTransaction AccountResult.NotFound
        if (account.isArchived) return@withTransaction AccountResult.Success

        val balance = Ledger.balances(accounts, transactionDao.getAll().map(TransactionEntity::toDomain))[id] ?: account.openingBalance
        if (balance != 0L) return@withTransaction AccountResult.NonZeroBalance

        accountDao.update(AccountEntity(account.id, account.name, account.type, account.openingBalance, account.createdAt, isArchived = true))
        AccountResult.Success
    }

    override suspend fun unarchiveAccount(id: Long): AccountResult = db.withTransaction {
        val accounts = accountDao.getAll().map(AccountEntity::toDomain)
        val account = accounts.firstOrNull { it.id == id } ?: return@withTransaction AccountResult.NotFound
        if (!account.isArchived) return@withTransaction AccountResult.Success
        if (account.type == AccountType.CASH && accounts.any { it.type == AccountType.CASH && !it.isArchived }) {
            return@withTransaction AccountResult.CashAlreadyExists
        }
        accountDao.update(AccountEntity(account.id, account.name, account.type, account.openingBalance, account.createdAt, isArchived = false))
        AccountResult.Success
    }

    override suspend fun saveCategory(id: Long?, name: String, type: TransactionType, iconKey: String, colorKey: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || type == TransactionType.TRANSFER) return false
        return db.withTransaction {
            if (id == null) {
                categoryDao.insert(
                    CategoryEntity(name = trimmed, type = type, iconKey = iconKey, colorKey = colorKey, isDefault = false, isArchived = false),
                )
                true
            } else {
                val current = categoryDao.getById(id)
                if (current == null || current.type != type) {
                    false
                } else {
                    categoryDao.update(current.copy(name = trimmed, iconKey = iconKey, colorKey = colorKey))
                    true
                }
            }
        }
    }

    override suspend fun setCategoryArchived(id: Long, archived: Boolean) {
        db.withTransaction {
            categoryDao.getById(id)?.let { categoryDao.update(it.copy(isArchived = archived)) }
        }
    }
}
