package com.kaskita.lokal.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.ValidationError

enum class FormField { AMOUNT, CATEGORY, ACCOUNT }

fun ValidationError.field(): FormField = when (this) {
    ValidationError.AmountRequired, ValidationError.AmountTooLarge, is ValidationError.InsufficientBalance -> FormField.AMOUNT
    ValidationError.CategoryRequired, ValidationError.CategoryTypeMismatch, is ValidationError.CategoryUnavailable -> FormField.CATEGORY
    ValidationError.SourceAccountRequired, ValidationError.DestinationAccountRequired, ValidationError.SameAccount,
    is ValidationError.AccountUnavailable, is ValidationError.ArchivedAccountNotZero -> FormField.ACCOUNT
}

/** Indonesian message that says what to do next. [type] only refines the wording of the "source account" error. */
@Composable
fun validationMessage(error: ValidationError, type: TransactionType?, accounts: List<Account>): String = when (error) {
    ValidationError.AmountRequired -> stringResource(R.string.err_amount_required)
    ValidationError.AmountTooLarge -> stringResource(R.string.err_amount_too_large)
    ValidationError.CategoryRequired -> stringResource(R.string.err_category_required)
    ValidationError.CategoryTypeMismatch -> stringResource(R.string.err_category_mismatch)
    is ValidationError.CategoryUnavailable -> stringResource(R.string.err_category_unavailable)
    ValidationError.SourceAccountRequired ->
        if (type == TransactionType.EXPENSE) stringResource(R.string.err_source_required_expense) else stringResource(R.string.err_source_required)
    ValidationError.DestinationAccountRequired -> stringResource(R.string.err_destination_required)
    ValidationError.SameAccount -> stringResource(R.string.err_same_account)
    is ValidationError.AccountUnavailable -> stringResource(R.string.err_account_unavailable)
    is ValidationError.ArchivedAccountNotZero -> stringResource(
        R.string.err_archived_not_zero,
        accounts.firstOrNull { it.id == error.accountId }?.name.orEmpty(),
        RupiahFormatter.format(error.resultingBalance),
    )
    is ValidationError.InsufficientBalance -> stringResource(
        R.string.err_insufficient_balance,
        accounts.firstOrNull { it.id == error.accountId }?.name.orEmpty(),
        RupiahFormatter.format(error.shortfall),
    )
}
