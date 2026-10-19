package com.kaskita.lokal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.ui.common.AmountField
import com.kaskita.lokal.ui.common.ConfirmDialog
import com.kaskita.lokal.ui.common.EmptyState
import com.kaskita.lokal.ui.common.IconBadge
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.common.ScreenScaffold
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.TonalButton
import com.kaskita.lokal.ui.common.groupThousands
import com.kaskita.lokal.ui.theme.finance
import com.kaskita.lokal.ui.transactions.TransactionRow
import java.time.ZoneId

/** Pastel container, tint and icon per account type, so accounts can be told apart at a glance. */
@Composable
private fun accountLook(type: AccountType): Triple<ImageVector, Color, Color> {
    val s = MaterialTheme.colorScheme
    return when (type) {
        AccountType.CASH -> Triple(Icons.Filled.Payments, s.tertiaryContainer, s.onTertiaryContainer)
        AccountType.BANK -> Triple(Icons.Filled.AccountBalance, s.primaryContainer, s.onPrimaryContainer)
        AccountType.EWALLET -> Triple(Icons.Filled.AccountBalanceWallet, s.secondaryContainer, s.onSecondaryContainer)
        AccountType.SAVINGS -> Triple(Icons.Filled.Savings, MaterialTheme.finance.savingsContainer, MaterialTheme.finance.onSavingsContainer)
        AccountType.OTHER -> Triple(Icons.Filled.Widgets, s.surfaceContainerHigh, s.onSurfaceVariant)
    }
}

@Composable
fun AccountsScreen(viewModel: AccountsViewModel, onBack: () -> Unit, onOpenAccount: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    val allAccounts = (state.active + state.archived).map { it.account }
    val messageText = state.message?.let { accountMessageText(it, allAccounts) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        if (message == AccountMessage.Saved) showAdd = false
        // Errors stay inside the open form; anything else is shown once as a snackbar.
        if (!(message.isError() && showAdd)) {
            snackbar.showSnackbar(messageText.orEmpty())
            viewModel.onMessageShown()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.acc_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.acc_add), style = MaterialTheme.typography.titleSmall) },
                containerColor = MaterialTheme.finance.cta,
                contentColor = MaterialTheme.finance.onCta,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { inner ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(inner))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SoftCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 40.dp, topEnd = 16.dp, bottomEnd = 40.dp, bottomStart = 16.dp),
                    padding = 24.dp,
                ) {
                    Text(stringResource(R.string.acc_total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    RupiahText(state.totalBalance, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            item { Text(stringResource(R.string.acc_active), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 6.dp)) }
            items(state.active, key = { it.account.id }) { AccountCard(it) { onOpenAccount(it.account.id) } }
            if (state.archived.isNotEmpty()) {
                item { Text(stringResource(R.string.acc_archived), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 10.dp)) }
                items(state.archived, key = { "a-${it.account.id}" }) { AccountCard(it) { onOpenAccount(it.account.id) } }
            }
        }
    }

    if (showAdd) {
        AccountEditDialog(
            existing = null,
            saving = state.isSaving,
            errorText = messageText?.takeIf { state.message?.isError() == true },
            onDismiss = { showAdd = false; viewModel.onMessageShown() },
            onSave = { name, type, opening -> viewModel.onSave(null, name, type, opening) },
        )
    }
    state.pending?.let { PendingOpeningDialog(it, viewModel::onConfirmOpeningChange, viewModel::onCancelOpeningChange) }
}

@Composable
private fun AccountCard(item: AccountBalance, onClick: () -> Unit) {
    val (icon, container, tint) = accountLook(item.account.type)
    SoftCard(padding = 16.dp, onClick = onClick, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon, container, tint, item.account.name, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(item.account.name, style = MaterialTheme.typography.titleSmall)
                Text(accountTypeLabel(item.account.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RupiahText(item.balance, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PendingOpeningDialog(change: PendingOpeningChange, onConfirm: () -> Unit, onCancel: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.acc_confirm_opening_title),
        message = stringResource(
            R.string.acc_confirm_opening_message,
            RupiahFormatter.format(change.oldOpening),
            RupiahFormatter.format(change.newOpening),
        ),
        confirmLabel = stringResource(R.string.acc_confirm_opening_ok),
        onConfirm = onConfirm,
        onDismiss = onCancel,
    )
}

/** Add or edit form. It stays open (values kept) until the save succeeds; errors are shown inside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditDialog(
    existing: Account?,
    errorText: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, openingText: String) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: AccountType.BANK) }
    var opening by remember(existing?.id) {
        mutableStateOf(existing?.openingBalance?.takeIf { it > 0 }?.let { groupThousands(it) }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.acc_form_add else R.string.acc_form_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.acc_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.acc_type), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(accountTypeLabel(option)) },
                            shape = MaterialTheme.shapes.large,
                        )
                    }
                }
                AmountField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = stringResource(R.string.acc_opening),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.acc_opening_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (errorText != null) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, type, opening) }, enabled = !saving) { Text(stringResource(R.string.acc_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun AccountDetailScreen(
    viewModel: AccountDetailViewModel,
    zone: ZoneId,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val account = state.account
    val messageText = state.message?.let { accountMessageText(it, listOfNotNull(account)) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        if (message == AccountMessage.Archived) {
            onBack()
            return@LaunchedEffect
        }
        if (message == AccountMessage.Saved) editing = false
        if (!(message.isError() && editing)) {
            snackbar.showSnackbar(messageText.orEmpty())
            viewModel.onMessageShown()
        }
    }

    ScreenScaffold(
        title = account?.name ?: stringResource(R.string.acc_detail_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (account != null) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.acc_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = MaterialTheme.shapes.medium) {
                    if (account.isArchived) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.acc_unarchive)) },
                            onClick = { menuOpen = false; viewModel.onUnarchive() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.acc_edit)) },
                            onClick = { menuOpen = false; editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.acc_archive), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmArchive = true },
                        )
                    }
                }
            }
        },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(inner))
            account == null -> EmptyState(
                message = stringResource(R.string.acc_msg_not_found),
                modifier = Modifier.padding(inner),
                actionLabel = stringResource(R.string.common_back),
                onAction = onBack,
            )
            else -> {
                val (icon, container, tint) = accountLook(account.type)
                LazyColumn(
                    modifier = Modifier.padding(inner),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        SoftCard(
                            containerColor = container,
                            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 16.dp, bottomEnd = 40.dp, bottomStart = 16.dp),
                            padding = 24.dp,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(icon, MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f), tint, account.name, size = 44.dp)
                                Text(accountTypeLabel(account.type), style = MaterialTheme.typography.titleSmall, color = tint)
                            }
                            Text(stringResource(R.string.acc_current_balance), style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.padding(top = 14.dp))
                            RupiahText(state.balance, style = MaterialTheme.typography.displaySmall, color = tint)
                            Text(
                                stringResource(R.string.acc_opening_balance, RupiahFormatter.format(account.openingBalance)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tint.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (account.isArchived) {
                        item {
                            SoftCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, padding = 16.dp) {
                                Text(stringResource(R.string.acc_is_archived), style = MaterialTheme.typography.bodyMedium)
                                TonalButton(stringResource(R.string.acc_unarchive), viewModel::onUnarchive, Modifier.fillMaxWidth().padding(top = 12.dp))
                            }
                        }
                    }
                    item { Text(stringResource(R.string.acc_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 6.dp)) }
                    if (state.rows.isEmpty()) {
                        item {
                            SoftCard(padding = 20.dp) {
                                Text(stringResource(R.string.acc_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        item {
                            SoftCard(padding = 8.dp) {
                                state.rows.forEachIndexed { index, row ->
                                    TransactionRow(row, zone, onClick = { onOpenTransaction(row.tx.id) }, showDate = true)
                                    if (index < state.rows.lastIndex) {
                                        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing && account != null) {
        AccountEditDialog(
            existing = account,
            saving = state.isSaving,
            errorText = messageText?.takeIf { state.message?.isError() == true },
            onDismiss = { editing = false; viewModel.onMessageShown() },
            onSave = viewModel::onSave,
        )
    }
    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.acc_archive_title),
            message = stringResource(R.string.acc_archive_message),
            confirmLabel = stringResource(R.string.acc_archive),
            onConfirm = { confirmArchive = false; viewModel.onArchive() },
            onDismiss = { confirmArchive = false },
        )
    }
    state.pending?.let { PendingOpeningDialog(it, viewModel::onConfirmOpeningChange, viewModel::onCancelOpeningChange) }
}
