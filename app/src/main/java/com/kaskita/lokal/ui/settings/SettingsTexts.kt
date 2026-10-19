package com.kaskita.lokal.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaskita.lokal.R
import com.kaskita.lokal.data.backup.BackupError
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.RupiahFormatter

/** Success messages go to the snackbar; everything else is an error shown next to the form that caused it. */
fun AccountMessage.isError(): Boolean =
    this != AccountMessage.Saved && this != AccountMessage.Archived && this != AccountMessage.Unarchived

@Composable
fun accountMessageText(message: AccountMessage, accounts: List<Account>): String = when (message) {
    AccountMessage.Saved -> stringResource(R.string.acc_msg_saved)
    AccountMessage.Archived -> stringResource(R.string.acc_msg_archived)
    AccountMessage.Unarchived -> stringResource(R.string.acc_msg_unarchived)
    AccountMessage.ArchivedLocked -> stringResource(R.string.acc_msg_archived_locked)
    AccountMessage.Failed -> stringResource(R.string.acc_msg_failed)
    AccountMessage.NameBlank -> stringResource(R.string.acc_msg_name_blank)
    AccountMessage.InvalidOpening -> stringResource(R.string.acc_msg_invalid_opening)
    AccountMessage.NonZeroBalance -> stringResource(R.string.acc_msg_nonzero)
    AccountMessage.CashExists -> stringResource(R.string.acc_msg_cash_exists)
    AccountMessage.NotFound -> stringResource(R.string.acc_msg_not_found)
    is AccountMessage.WouldGoNegative -> message.errors.map { error ->
        stringResource(
            R.string.acc_msg_negative,
            accounts.firstOrNull { it.id == error.accountId }?.name.orEmpty(),
            RupiahFormatter.format(error.shortfall),
        )
    }.joinToString(" ")
}

@Composable
fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> stringResource(R.string.acc_type_cash)
    AccountType.BANK -> stringResource(R.string.acc_type_bank)
    AccountType.EWALLET -> stringResource(R.string.acc_type_ewallet)
    AccountType.SAVINGS -> stringResource(R.string.acc_type_savings)
    AccountType.OTHER -> stringResource(R.string.acc_type_other)
}

@Composable
fun categoryMessageText(message: CategoryMessage): String = when (message) {
    CategoryMessage.Saved -> stringResource(R.string.cat_msg_saved)
    CategoryMessage.NameBlank -> stringResource(R.string.cat_msg_blank)
    CategoryMessage.Rejected -> stringResource(R.string.cat_msg_rejected)
    CategoryMessage.Failed -> stringResource(R.string.cat_msg_failed)
}

@Composable
fun backupFailureText(failure: BackupFailure): String = when (failure) {
    is BackupFailure.Invalid -> when (val error = failure.error) {
        BackupError.Empty -> stringResource(R.string.bk_err_empty)
        BackupError.Malformed -> stringResource(R.string.bk_err_malformed)
        is BackupError.UnsupportedVersion -> stringResource(R.string.bk_err_version, error.found, error.supported)
        is BackupError.DuplicateId, is BackupError.MissingAccount, is BackupError.MissingCategory,
        is BackupError.InvalidTransaction -> stringResource(R.string.bk_err_inconsistent)
        BackupError.SafetyBackupFailed -> stringResource(R.string.bk_err_safety)
    }
    BackupFailure.Unreadable -> stringResource(R.string.bk_err_unreadable)
    BackupFailure.WriteFailed -> stringResource(R.string.bk_err_write)
    is BackupFailure.RestoreRejected ->
        if (failure.error == BackupError.SafetyBackupFailed) stringResource(R.string.bk_err_safety)
        else if (failure.error != null) backupFailureText(BackupFailure.Invalid(failure.error))
        else stringResource(R.string.bk_err_restore)
}
