package com.kaskita.lokal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.data.SaveResult
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.AmountParser
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionDraft
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.ValidationError
import com.kaskita.lokal.ui.AppContainer
import com.kaskita.lokal.ui.common.formatAmountInput
import com.kaskita.lokal.ui.common.groupThousands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a transfer will do to each side, rendered as `Rekening − Rp500.000` / `Cash + Rp500.000`. */
data class TransferPreview(val fromName: String, val toName: String, val amount: Long)

data class FormState(
    val type: TransactionType,
    val amountText: String = "",
    val categoryId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val dateTime: Long,
    val note: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
    val notFound: Boolean = false,
    val errors: List<ValidationError> = emptyList(),
    /** Accounts the user may pick (active ones, plus the ones the edited transaction already uses). */
    val accounts: List<Account> = emptyList(),
    /** Categories of the current [type] the user may pick. */
    val categories: List<Category> = emptyList(),
    val transferPreview: TransferPreview? = null,
    /** Balances of the accounts touched by the save, shown in the confirmation message. */
    val savedBalances: List<AccountBalance> = emptyList(),
)

/** Digits only (the field already guarantees that); no range check here so the validator can say "too large". */
private fun parseAmountText(text: String): Long? = text.replace(".", "").toLongOrNull()?.takeIf { it > 0 }

class TransactionFormViewModel(
    private val repo: LedgerRepository,
    initialType: TransactionType,
    private val editId: Long?,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private var allAccounts: List<Account> = emptyList()
    private var allCategories: List<Category> = emptyList()
    private var original: Transaction? = null

    private val _state = MutableStateFlow(FormState(type = initialType, dateTime = now()))
    val state: StateFlow<FormState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load(initialType) }
    }

    private suspend fun load(initialType: TransactionType) {
        allAccounts = repo.observeAccounts().first()
        allCategories = repo.observeCategories().first()
        if (editId != null) {
            original = repo.observeTransactions().first().firstOrNull { it.id == editId }
            if (original == null) {
                _state.update { it.copy(isLoading = false, notFound = true) }
                return
            }
        }
        val o = original
        val start = if (o != null) {
            FormState(
                type = o.type,
                amountText = groupThousands(o.amount),
                categoryId = o.categoryId,
                fromAccountId = o.fromAccountId,
                toAccountId = o.toAccountId,
                dateTime = o.dateTime,
                note = o.note.orEmpty(),
                isLoading = false,
            )
        } else {
            val (from, to) = defaultAccounts(initialType, from = null, to = null)
            FormState(type = initialType, fromAccountId = from, toAccountId = to, dateTime = _state.value.dateTime, isLoading = false)
        }
        _state.value = start.refreshed()
    }

    // --- input handlers (every edit clears stale errors so the user is not shown outdated messages)
    fun onTypeChange(type: TransactionType) = edit { s ->
        if (s.type == type) return@edit s
        val (from, to) = defaultAccounts(type, s.fromAccountId, s.toAccountId)
        s.copy(type = type, categoryId = null, fromAccountId = from, toAccountId = to)
    }

    /** Text that is not a plain integer is ignored (the field shows a hint); it is never rewritten into another number. */
    fun onAmountChange(text: String) {
        val formatted = formatAmountInput(text) ?: return
        edit { it.copy(amountText = formatted) }
    }

    fun onCategory(id: Long?) = edit { it.copy(categoryId = id) }

    fun onFromAccount(id: Long?) = edit { s ->
        val swapTarget = s.type == TransactionType.TRANSFER && id != null && id == s.toAccountId
        s.copy(fromAccountId = id, toAccountId = if (swapTarget) s.fromAccountId else s.toAccountId)
    }

    fun onToAccount(id: Long?) = edit { s ->
        val swapSource = s.type == TransactionType.TRANSFER && id != null && id == s.fromAccountId
        s.copy(toAccountId = id, fromAccountId = if (swapSource) s.toAccountId else s.fromAccountId)
    }

    fun onDate(millis: Long) = edit { it.copy(dateTime = millis) }

    fun onNote(text: String) = edit { it.copy(note = text) }

    /** Ignored while a save is running or after one succeeded, so a double tap can never create two rows. */
    fun onSave() {
        val before = _state.value
        if (before.isLoading || before.isSaving || before.saved || before.notFound) return
        if (!_state.compareAndSet(before, before.copy(isSaving = true, errors = emptyList(), saveFailed = false))) return

        viewModelScope.launch {
            val draft = TransactionDraft(
                id = editId,
                type = before.type,
                amount = parseAmountText(before.amountText),
                dateTime = before.dateTime,
                categoryId = before.categoryId,
                fromAccountId = before.fromAccountId,
                toAccountId = before.toAccountId,
                note = before.note,
            )
            try {
                when (val result = repo.saveTransaction(draft)) {
                    is SaveResult.Success -> {
                        val balances = touchedBalances(draft.normalized())
                        _state.update { it.copy(isSaving = false, saved = true, savedBalances = balances) }
                    }
                    is SaveResult.Invalid -> _state.update { it.copy(isSaving = false, errors = result.errors) }
                    SaveResult.NotFound -> _state.update { it.copy(isSaving = false, notFound = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    // --- helpers
    private fun edit(transform: (FormState) -> FormState) {
        _state.update { s ->
            if (s.isLoading || s.isSaving || s.saved) s
            else transform(s).copy(errors = emptyList(), saveFailed = false).refreshed()
        }
    }

    private fun FormState.refreshed(): FormState = copy(
        accounts = allAccounts.filter { a ->
            !a.isArchived || a.id == original?.fromAccountId || a.id == original?.toAccountId
        },
        categories = allCategories.filter { c ->
            c.type == type && (!c.isArchived || c.id == original?.categoryId)
        },
        transferPreview = buildPreview(),
    )

    private fun FormState.buildPreview(): TransferPreview? {
        if (type != TransactionType.TRANSFER) return null
        val amount = AmountParser.parse(amountText) ?: return null
        val from = allAccounts.firstOrNull { it.id == fromAccountId } ?: return null
        val to = allAccounts.firstOrNull { it.id == toAccountId } ?: return null
        if (from.id == to.id) return null
        return TransferPreview(from.name, to.name, amount)
    }

    /** Keeps what already fits [type]; fills the rest with Cash (income/expense) or Bank → Cash (transfer). */
    private fun defaultAccounts(type: TransactionType, from: Long?, to: Long?): Pair<Long?, Long?> {
        val usable = allAccounts.filter { !it.isArchived }
        val cash = usable.firstOrNull { it.type == AccountType.CASH } ?: usable.firstOrNull()
        val nonCash = usable.firstOrNull { it.type != AccountType.CASH } ?: usable.firstOrNull()
        return when (type) {
            TransactionType.INCOME -> null to (to ?: cash?.id)
            TransactionType.EXPENSE -> (from ?: cash?.id) to null
            TransactionType.TRANSFER -> {
                val source = from ?: nonCash?.id
                var destination = to ?: cash?.id
                if (destination == source) destination = usable.firstOrNull { it.id != source }?.id
                source to destination
            }
        }
    }

    private suspend fun touchedBalances(draft: TransactionDraft): List<AccountBalance> {
        val accounts = repo.observeAccounts().first()
        val balances = Ledger.balances(accounts, repo.observeTransactions().first())
        return listOfNotNull(draft.fromAccountId, draft.toAccountId).distinct().mapNotNull { id ->
            accounts.firstOrNull { it.id == id }?.let { AccountBalance(it, balances[id] ?: it.openingBalance) }
        }
    }

    companion object {
        fun factory(container: AppContainer, type: TransactionType, editId: Long?) = viewModelFactory {
            initializer { TransactionFormViewModel(container.ledger, type, editId, container::now) }
        }
    }
}
