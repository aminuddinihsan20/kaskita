package com.kaskita.lokal.testing

import com.kaskita.lokal.data.AccountResult
import com.kaskita.lokal.data.DeleteResult
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.data.SaveResult
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionDraft
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.TransactionValidator
import com.kaskita.lokal.domain.TestData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [LedgerRepository] for ViewModel tests. It runs the real [TransactionValidator] and [Ledger], so balances
 * and rejections behave like production; only storage is replaced. Hooks: [gate] suspends a save, [failWith] throws.
 */
class FakeLedgerRepository(
    accounts: List<Account> = listOf(TestData.cash(), TestData.bank()),
    categories: List<Category> = listOf(TestData.foodCategory(), TestData.salaryCategory()),
    transactions: List<Transaction> = emptyList(),
    var allowNegative: Boolean = false,
) : LedgerRepository {
    val accounts = MutableStateFlow(accounts)
    val categories = MutableStateFlow(categories)
    val transactions = MutableStateFlow(transactions)

    var saveCalls = 0
        private set
    val savedDrafts = mutableListOf<TransactionDraft>()
    var gate: CompletableDeferred<Unit>? = null
    var failWith: Throwable? = null

    var accountGate: CompletableDeferred<Unit>? = null
    var categoryGate: CompletableDeferred<Unit>? = null
    var accountResult: AccountResult = AccountResult.Success
    val accountSaves = mutableListOf<Triple<Long?, String, Long>>()
    val archivedAccountIds = mutableListOf<Long>()
    val categorySaves = mutableListOf<String>()
    private var nextId = 1_000L

    override fun observeAccounts(): Flow<List<Account>> = accounts
    override fun observeCategories(): Flow<List<Category>> = categories
    override fun observeTransactions(): Flow<List<Transaction>> =
        transactions.map { list -> list.sortedWith(compareByDescending<Transaction> { it.dateTime }.thenByDescending { it.id }) }

    override suspend fun saveTransaction(draft: TransactionDraft): SaveResult {
        saveCalls++
        savedDrafts += draft
        gate?.await()
        failWith?.let { throw it }
        val existing = transactions.value
        val original = draft.id?.let { id -> existing.firstOrNull { it.id == id } ?: return SaveResult.NotFound }
        val errors = TransactionValidator.validate(draft, accounts.value, categories.value, existing, allowNegative)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val d = draft.normalized()
        val saved = Transaction(
            id = original?.id ?: nextId++,
            type = d.type,
            amount = requireNotNull(d.amount),
            dateTime = d.dateTime,
            categoryId = d.categoryId,
            fromAccountId = d.fromAccountId,
            toAccountId = d.toAccountId,
            note = d.note,
            createdAt = original?.createdAt ?: 1L,
            updatedAt = 2L,
        )
        transactions.value = existing.filterNot { it.id == saved.id } + saved
        return SaveResult.Success(saved.id)
    }

    override suspend fun deleteTransaction(id: Long): DeleteResult {
        failWith?.let { throw it }
        val existing = transactions.value
        if (existing.none { it.id == id }) return DeleteResult.NotFound
        val before = Ledger.balances(accounts.value, existing)
        val after = Ledger.balances(accounts.value, existing.filterNot { it.id == id })
        TransactionValidator.archivedAccountDrift(accounts.value, before, after).let {
            if (it.isNotEmpty()) return DeleteResult.ArchivedAccountAffected(it)
        }
        if (!allowNegative) {
            val errors = TransactionValidator.newlyNegative(accounts.value, before, after)
            if (errors.isNotEmpty()) return DeleteResult.WouldGoNegative(errors)
        }
        transactions.value = existing.filterNot { it.id == id }
        return DeleteResult.Success
    }

    override suspend fun saveAccount(id: Long?, name: String, type: AccountType, openingBalance: Long): AccountResult {
        accountSaves += Triple(id, name, openingBalance)
        accountGate?.await()
        failWith?.let { throw it }
        return accountResult
    }

    override suspend fun archiveAccount(id: Long): AccountResult {
        archivedAccountIds += id
        return accountResult
    }

    val unarchivedAccountIds = mutableListOf<Long>()

    override suspend fun unarchiveAccount(id: Long): AccountResult {
        unarchivedAccountIds += id
        return accountResult
    }

    override suspend fun saveCategory(id: Long?, name: String, type: TransactionType, iconKey: String, colorKey: String): Boolean {
        categorySaves += name
        categoryGate?.await()
        failWith?.let { throw it }
        return name.isNotBlank()
    }

    override suspend fun setCategoryArchived(id: Long, archived: Boolean) {
        categories.value = categories.value.map { if (it.id == id) it.copy(isArchived = archived) else it }
    }
}
