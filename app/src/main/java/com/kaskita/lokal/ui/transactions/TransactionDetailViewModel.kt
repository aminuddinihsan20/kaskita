package com.kaskita.lokal.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.DeleteResult
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.domain.ValidationError
import com.kaskita.lokal.ui.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val isLoading: Boolean = true,
    val row: TxRow? = null,
    val notFound: Boolean = false,
    val confirmingDelete: Boolean = false,
    /** Set when deleting would push these accounts below zero; the transaction is left untouched. */
    val blockedBy: List<ValidationError.InsufficientBalance>? = null,
    /** Set when deleting would move an archived account away from Rp0. */
    val archivedBlockedBy: List<ValidationError.ArchivedAccountNotZero>? = null,
    val deleteFailed: Boolean = false,
    val deleted: Boolean = false,
)

class TransactionDetailViewModel(
    private val repo: LedgerRepository,
    private val id: Long,
) : ViewModel() {

    private data class Flags(
        val confirming: Boolean = false,
        val deleting: Boolean = false,
        val blockedBy: List<ValidationError.InsufficientBalance>? = null,
        val archivedBlockedBy: List<ValidationError.ArchivedAccountNotZero>? = null,
        val deleteFailed: Boolean = false,
        val deleted: Boolean = false,
    )

    private val flags = MutableStateFlow(Flags())

    val state: StateFlow<DetailState> = combine(
        repo.observeAccounts(),
        repo.observeCategories(),
        repo.observeTransactions(),
        flags,
    ) { accounts, categories, transactions, f ->
        val row = buildRows(transactions.filter { it.id == id }, accounts, categories).firstOrNull()
        DetailState(
            isLoading = false,
            row = row,
            notFound = row == null && !f.deleted,
            confirmingDelete = f.confirming,
            blockedBy = f.blockedBy,
            archivedBlockedBy = f.archivedBlockedBy,
            deleteFailed = f.deleteFailed,
            deleted = f.deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailState())

    fun onRequestDelete() = flags.update { it.copy(confirming = true, blockedBy = null, archivedBlockedBy = null, deleteFailed = false) }

    fun onDismissDelete() = flags.update { it.copy(confirming = false) }

    fun onDismissBlocked() = flags.update { it.copy(blockedBy = null, archivedBlockedBy = null, deleteFailed = false) }

    /** Only acts on the first call after the confirmation dialog; further taps are ignored. */
    fun onConfirmDelete() {
        val before = flags.value
        if (!before.confirming || before.deleting) return
        if (!flags.compareAndSet(before, before.copy(confirming = false, deleting = true))) return

        viewModelScope.launch {
            try {
                when (val result = repo.deleteTransaction(id)) {
                    DeleteResult.Success, DeleteResult.NotFound -> flags.update { it.copy(deleting = false, deleted = true) }
                    is DeleteResult.WouldGoNegative -> flags.update { it.copy(deleting = false, blockedBy = result.errors) }
                    is DeleteResult.ArchivedAccountAffected -> flags.update { it.copy(deleting = false, archivedBlockedBy = result.errors) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flags.update { it.copy(deleting = false, deleteFailed = true) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, id: Long) = viewModelFactory {
            initializer { TransactionDetailViewModel(container.ledger, id) }
        }
    }
}
