package com.kaskita.lokal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.ValidationError
import com.kaskita.lokal.ui.common.AmountField
import com.kaskita.lokal.ui.common.CategoryBadge
import com.kaskita.lokal.ui.common.CategoryVisuals
import com.kaskita.lokal.ui.common.ConnectedToggleGroup
import com.kaskita.lokal.ui.common.EmptyState
import com.kaskita.lokal.ui.common.FormField
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.PrimaryButton
import com.kaskita.lokal.ui.common.ScreenScaffold
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.field
import com.kaskita.lokal.ui.common.formatDateLong
import com.kaskita.lokal.ui.common.validationMessage
import com.kaskita.lokal.ui.theme.finance
import java.time.ZoneId

@Composable
fun TransactionFormScreen(
    viewModel: TransactionFormViewModel,
    isEditing: Boolean,
    zone: ZoneId,
    onBack: () -> Unit,
    onSaved: (message: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = savedMessage(state.savedBalances)
    val failedText = stringResource(R.string.form_save_failed)

    LaunchedEffect(state.saved) { if (state.saved) onSaved(savedText) }
    LaunchedEffect(state.saveFailed) { if (state.saveFailed) snackbar.showSnackbar(failedText) }

    val title = when {
        isEditing -> R.string.form_title_edit
        state.type == TransactionType.INCOME -> R.string.form_title_income
        state.type == TransactionType.EXPENSE -> R.string.form_title_expense
        state.transferKind == TransferKind.DEPOSIT -> R.string.form_title_deposit
        state.transferKind == TransferKind.WITHDRAW -> R.string.form_title_withdraw
        else -> R.string.form_title_transfer
    }
    val saveLabel = when {
        state.isSaving -> R.string.form_saving
        isEditing -> R.string.form_save_edit
        state.type == TransactionType.INCOME -> R.string.form_save_income
        state.type == TransactionType.EXPENSE -> R.string.form_save_expense
        else -> R.string.form_save_transfer
    }

    ScreenScaffold(
        title = stringResource(title),
        onBack = onBack,
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isLoading && !state.notFound) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        text = stringResource(saveLabel),
                        onClick = viewModel::onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(inner))
            state.notFound -> EmptyState(
                message = stringResource(R.string.form_not_found),
                modifier = Modifier.padding(inner),
                actionLabel = stringResource(R.string.common_back),
                onAction = onBack,
            )
            else -> FormContent(state = state, viewModel = viewModel, zone = zone, modifier = Modifier.padding(inner))
        }
    }
}

@Composable
private fun FormContent(state: FormState, viewModel: TransactionFormViewModel, zone: ZoneId, modifier: Modifier) {
    fun errorsFor(field: FormField): List<ValidationError> = state.errors.filter { it.field() == field }
    val types = listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER)
    val colors = MaterialTheme.finance

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ConnectedToggleGroup(
            options = listOf(stringResource(R.string.type_income), stringResource(R.string.type_expense), stringResource(R.string.type_transfer)),
            selectedIndex = types.indexOf(state.type),
            onSelect = { viewModel.onTypeChange(types[it]) },
            selectedContainers = listOf(colors.incomeContainer, colors.expenseContainer, colors.transferContainer),
            selectedContents = listOf(colors.onIncomeContainer, colors.onExpenseContainer, colors.onTransferContainer),
        )

        SoftCard {
            AmountField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = stringResource(R.string.form_amount_label),
                modifier = Modifier.fillMaxWidth(),
                error = errorsFor(FormField.AMOUNT).firstOrNull()?.let { validationMessage(it, state.type, state.accounts) },
            )
        }

        if (state.type != TransactionType.TRANSFER) {
            FormSection(stringResource(R.string.form_category_label), errorsFor(FormField.CATEGORY), state) {
                if (state.categories.isEmpty()) {
                    Text(stringResource(R.string.form_no_categories), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    CategoryGrid(state.categories, state.categoryId, viewModel::onCategory)
                }
            }
        }

        when (state.type) {
            TransactionType.INCOME -> AccountSection(
                label = stringResource(R.string.form_account_income),
                accounts = state.accounts, selectedId = state.toAccountId, onSelect = viewModel::onToAccount,
                errors = errorsFor(FormField.ACCOUNT), state = state,
            )
            TransactionType.EXPENSE -> AccountSection(
                label = stringResource(R.string.form_account_expense),
                accounts = state.accounts, selectedId = state.fromAccountId, onSelect = viewModel::onFromAccount,
                errors = errorsFor(FormField.ACCOUNT), state = state,
            )
            TransactionType.TRANSFER -> {
                Text(
                    stringResource(
                        when (state.transferKind) {
                            TransferKind.DEPOSIT -> R.string.form_transfer_explain_deposit
                            TransferKind.WITHDRAW -> R.string.form_transfer_explain_withdraw
                            TransferKind.NORMAL -> R.string.form_transfer_explain
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                AccountSection(
                    label = stringResource(R.string.form_transfer_from),
                    accounts = state.accounts, selectedId = state.fromAccountId, onSelect = viewModel::onFromAccount,
                    errors = errorsFor(FormField.ACCOUNT), state = state,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledTonalButton(onClick = viewModel::onSwapAccounts) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.form_transfer_swap))
                    }
                }
                AccountSection(
                    label = stringResource(R.string.form_transfer_to),
                    accounts = state.accounts, selectedId = state.toAccountId, onSelect = viewModel::onToAccount,
                    errors = emptyList(), state = state,
                )
                if (state.savingsRequested && !state.hasSavingsAccount) NoSavingsHint()
            }
        }
        BalancePreviewCard(state)

        SoftCard(padding = 16.dp) {
            DateField(state.dateTime, zone, viewModel::onDate)
            Spacer(Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.form_note_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun FormSection(title: String, errors: List<ValidationError>, state: FormState, content: @Composable () -> Unit) {
    SoftCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.padding(top = 12.dp))
        content()
        errors.forEach {
            Text(
                validationMessage(it, state.type, state.accounts),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSection(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    errors: List<ValidationError>,
    state: FormState,
) {
    FormSection(label, errors, state) {
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.form_no_accounts), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = account.id == selectedId,
                        onClick = { onSelect(account.id) },
                        label = {
                            Column {
                                Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                state.balances[account.id]?.let {
                                    Text(RupiahFormatter.format(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<Category>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    val columns = 3
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    CategoryCell(category, category.id == selectedId, { onSelect(category.id) }, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryCell(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = CategoryVisuals.tint(category.colorKey)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) CategoryVisuals.container(category.colorKey) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, tint) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            CategoryBadge(category.iconKey, category.colorKey, size = 40.dp)
            Text(
                category.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Shown when the "Nabung" shortcut is used but no savings account exists, so it does not silently become a plain transfer. */
@Composable
private fun NoSavingsHint() {
    Text(
        stringResource(R.string.form_no_savings_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Balance of every account this save touches, before → after, so nothing is a surprise after tapping Simpan. */
@Composable
private fun BalancePreviewCard(state: FormState) {
    val colors = MaterialTheme.finance
    val isTransfer = state.type == TransactionType.TRANSFER
    SoftCard(containerColor = if (isTransfer) colors.transferContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        val content = if (isTransfer) colors.onTransferContainer else MaterialTheme.colorScheme.onSurface
        Text(stringResource(R.string.form_transfer_preview_title), style = MaterialTheme.typography.titleSmall, color = content)
        if (state.balanceChanges.isEmpty()) {
            Text(
                stringResource(R.string.form_preview_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.balanceChanges.forEach { change ->
            Column(Modifier.padding(top = 8.dp)) {
                Text(change.account.name, style = MaterialTheme.typography.bodyMedium, color = content)
                Text(
                    stringResource(R.string.form_preview_change, RupiahFormatter.format(change.before), RupiahFormatter.format(change.after)),
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(dateTime: Long, zone: ZoneId, onDate: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Surface(
        onClick = { showPicker = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(stringResource(R.string.form_date_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDateLong(dateTime, zone), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = datePickerInitialMillis(dateTime, zone))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(mergeDatePreservingTime(dateTime, it, zone)) }
                    showPicker = false
                }) { Text(stringResource(R.string.form_date_pick)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun savedMessage(balances: List<AccountBalance>): String = when (balances.size) {
    0 -> stringResource(R.string.form_saved_plain)
    1 -> stringResource(R.string.form_saved_one, balances[0].account.name, RupiahFormatter.format(balances[0].balance))
    else -> stringResource(
        R.string.form_saved_two,
        balances[0].account.name, RupiahFormatter.format(balances[0].balance),
        balances[1].account.name, RupiahFormatter.format(balances[1].balance),
    )
}
