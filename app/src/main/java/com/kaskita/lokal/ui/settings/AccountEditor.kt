package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.data.AccountResult
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.AmountParser
import com.kaskita.lokal.domain.ValidationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface AccountMessage {
    data object Saved : AccountMessage
    data object Archived : AccountMessage
    data object Unarchived : AccountMessage
    data object ArchivedLocked : AccountMessage

    /** Something unexpected went wrong (e.g. the database); nothing was changed. */
    data object Failed : AccountMessage
    data object NameBlank : AccountMessage
    data object InvalidOpening : AccountMessage
    data object NonZeroBalance : AccountMessage
    data object CashExists : AccountMessage
    data object NotFound : AccountMessage
    data class WouldGoNegative(val errors: List<ValidationError.InsufficientBalance>) : AccountMessage
}

/** An opening-balance edit that waits for the user's explicit confirmation (it rewrites every later balance). */
data class PendingOpeningChange(
    val accountId: Long,
    val name: String,
    val type: AccountType,
    val oldOpening: Long,
    val newOpening: Long,
)

/** Everything the account dialogs need to render, as one value. */
data class EditorUi(val message: AccountMessage?, val pending: PendingOpeningChange?, val saving: Boolean)

/** Shared by the account list and account detail view models. Only one write runs at a time (no double-submit). */
class AccountEditor(private val repo: LedgerRepository, private val scope: CoroutineScope) {
    private val message = MutableStateFlow<AccountMessage?>(null)
    private val pending = MutableStateFlow<PendingOpeningChange?>(null)
    private val saving = MutableStateFlow(false)

    val ui: Flow<EditorUi> = combine(message, pending, saving, ::EditorUi)

    /** [openingText] may be blank (meaning Rp0); otherwise it must be a valid amount. */
    fun save(existing: Account?, name: String, type: AccountType, openingText: String) {
        val opening = parseOpening(openingText)
        if (opening == null) {
            message.value = AccountMessage.InvalidOpening
            return
        }
        if (existing != null && opening != existing.openingBalance) {
            pending.value = PendingOpeningChange(existing.id, name, type, existing.openingBalance, opening)
            return
        }
        write { repo.saveAccount(existing?.id, name, type, opening).toMessage() }
    }

    fun confirmPending() {
        val change = pending.value ?: return
        pending.value = null
        write { repo.saveAccount(change.accountId, change.name, change.type, change.newOpening).toMessage() }
    }

    fun cancelPending() {
        pending.value = null
    }

    fun archive(id: Long) = write {
        when (val result = repo.archiveAccount(id)) {
            AccountResult.Success -> AccountMessage.Archived
            else -> result.toMessage()
        }
    }

    fun unarchive(id: Long) = write {
        when (val result = repo.unarchiveAccount(id)) {
            AccountResult.Success -> AccountMessage.Unarchived
            else -> result.toMessage()
        }
    }

    fun messageShown() {
        message.value = null
    }

    /** Ignored while another write is running, so a double tap can never create two accounts. */
    private fun write(block: suspend () -> AccountMessage) {
        if (!saving.compareAndSet(false, true)) return
        scope.launch {
            try {
                message.value = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = AccountMessage.Failed
            } finally {
                saving.value = false
            }
        }
    }

    private fun parseOpening(text: String): Long? {
        val trimmed = text.trim()
        return if (trimmed.isEmpty() || trimmed == "0") 0L else AmountParser.parse(trimmed)
    }

    private fun AccountResult.toMessage(): AccountMessage = when (this) {
        AccountResult.Success -> AccountMessage.Saved
        AccountResult.NotFound -> AccountMessage.NotFound
        AccountResult.NameBlank -> AccountMessage.NameBlank
        AccountResult.InvalidOpeningBalance -> AccountMessage.InvalidOpening
        AccountResult.NonZeroBalance -> AccountMessage.NonZeroBalance
        AccountResult.CashAlreadyExists -> AccountMessage.CashExists
        AccountResult.ArchivedBalanceLocked -> AccountMessage.ArchivedLocked
        is AccountResult.WouldGoNegative -> AccountMessage.WouldGoNegative(errors)
    }
}
