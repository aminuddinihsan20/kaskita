package com.kaskita.lokal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.ui.AppContainer
import com.kaskita.lokal.ui.transactions.TxRow
import com.kaskita.lokal.ui.transactions.buildRows
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccountsUiState(
    val active: List<AccountBalance> = emptyList(),
    val archived: List<AccountBalance> = emptyList(),
    val totalBalance: Long = 0,
    val message: AccountMessage? = null,
    val pending: PendingOpeningChange? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
)

class AccountsViewModel(private val repo: LedgerRepository) : ViewModel() {
    private val editor = AccountEditor(repo, viewModelScope)

    val state: StateFlow<AccountsUiState> = combine(
        repo.observeAccounts(),
        repo.observeTransactions(),
        editor.ui,
    ) { accounts, transactions, ui ->
        val balances = Ledger.balances(accounts, transactions)
        fun List<Account>.withBalances() = map { AccountBalance(it, balances[it.id] ?: it.openingBalance) }
        AccountsUiState(
            active = accounts.filterNot { it.isArchived }.withBalances(),
            archived = accounts.filter { it.isArchived }.withBalances(),
            totalBalance = Ledger.totalBalance(accounts, balances),
            message = ui.message,
            pending = ui.pending,
            isSaving = ui.saving,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountsUiState())

    fun onSave(existing: Account?, name: String, type: AccountType, openingText: String) = editor.save(existing, name, type, openingText)
    fun onConfirmOpeningChange() = editor.confirmPending()
    fun onCancelOpeningChange() = editor.cancelPending()
    fun onArchive(id: Long) = editor.archive(id)
    fun onMessageShown() = editor.messageShown()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { AccountsViewModel(container.ledger) } }
    }
}

data class AccountDetailState(
    val account: Account? = null,
    val balance: Long = 0,
    val rows: List<TxRow> = emptyList(),
    val message: AccountMessage? = null,
    val pending: PendingOpeningChange? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)

class AccountDetailViewModel(repo: LedgerRepository, private val id: Long) : ViewModel() {
    private val editor = AccountEditor(repo, viewModelScope)

    val state: StateFlow<AccountDetailState> = combine(
        repo.observeAccounts(),
        repo.observeCategories(),
        repo.observeTransactions(),
        editor.ui,
    ) { accounts, categories, transactions, ui ->
        val account = accounts.firstOrNull { it.id == id }
        AccountDetailState(
            account = account,
            balance = account?.let { Ledger.balances(accounts, transactions)[it.id] ?: it.openingBalance } ?: 0,
            rows = buildRows(transactions.filter { it.fromAccountId == id || it.toAccountId == id }, accounts, categories),
            message = ui.message,
            pending = ui.pending,
            isSaving = ui.saving,
            isLoading = false,
            notFound = account == null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountDetailState())

    fun onSave(name: String, type: AccountType, openingText: String) {
        state.value.account?.let { editor.save(it, name, type, openingText) }
    }

    fun onConfirmOpeningChange() = editor.confirmPending()
    fun onCancelOpeningChange() = editor.cancelPending()
    fun onArchive() = editor.archive(id)
    fun onUnarchive() = editor.unarchive(id)
    fun onMessageShown() = editor.messageShown()

    companion object {
        fun factory(container: AppContainer, id: Long) = viewModelFactory { initializer { AccountDetailViewModel(container.ledger, id) } }
    }
}
