package com.kaskita.lokal.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.common.ConfirmDialog
import com.kaskita.lokal.ui.common.DangerButton
import com.kaskita.lokal.ui.common.EmptyState
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.common.ScreenScaffold
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.TonalButton
import com.kaskita.lokal.ui.common.formatDateLong
import com.kaskita.lokal.ui.common.signedAmountText
import com.kaskita.lokal.ui.theme.finance
import java.time.ZoneId

@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    zone: ZoneId,
    onBack: () -> Unit,
    onEdit: (type: TransactionType, id: Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val row = state.row
    ScreenScaffold(
        title = stringResource(R.string.tx_detail_title),
        onBack = onBack,
        bottomBar = {
            if (row != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TonalButton(stringResource(R.string.common_edit), { onEdit(row.tx.type, row.tx.id) }, Modifier.weight(1f))
                        DangerButton(stringResource(R.string.common_delete), viewModel::onRequestDelete, Modifier.weight(1f))
                    }
                }
            }
        },
    ) { inner ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(inner))
            row == null -> if (!state.deleted) {
                EmptyState(
                    message = stringResource(R.string.form_not_found),
                    modifier = Modifier.padding(inner),
                    actionLabel = stringResource(R.string.common_back),
                    onAction = onBack,
                )
            }
            else -> DetailContent(row, zone, Modifier.padding(inner))
        }
    }

    if (state.confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tx_delete_title),
            message = stringResource(R.string.tx_delete_message),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDelete,
        )
    }

    state.archivedBlockedBy?.let { errors ->
        val unknown = stringResource(R.string.tx_unknown_account)
        val message = errors.map { error ->
            val name = listOfNotNull(row?.from, row?.to).firstOrNull { it.id == error.accountId }?.name ?: unknown
            stringResource(R.string.err_delete_archived, name, RupiahFormatter.format(error.resultingBalance))
        }.joinToString("\n\n")
        SimpleMessageDialog(message, viewModel::onDismissBlocked, title = stringResource(R.string.tx_delete_blocked_title))
    }
    if (state.deleteFailed) {
        SimpleMessageDialog(stringResource(R.string.err_delete_failed), viewModel::onDismissBlocked)
    }

    state.blockedBy?.let { errors ->
        val unknown = stringResource(R.string.tx_unknown_account)
        val message = errors.map { error ->
            val name = listOfNotNull(row?.from, row?.to).firstOrNull { it.id == error.accountId }?.name ?: unknown
            stringResource(R.string.err_delete_would_overdraw, name, RupiahFormatter.format(error.shortfall))
        }.joinToString("\n\n")
        AlertDialog(
            onDismissRequest = viewModel::onDismissBlocked,
            title = { Text(stringResource(R.string.tx_delete_blocked_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::onDismissBlocked) { Text(stringResource(R.string.tx_delete_blocked_ok)) } },
        )
    }
}

@Composable
private fun SimpleMessageDialog(message: String, onDismiss: () -> Unit, title: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tx_delete_blocked_ok)) } },
    )
}

@Composable
private fun DetailContent(row: TxRow, zone: ZoneId, modifier: Modifier) {
    val tx = row.tx
    val colors = MaterialTheme.finance
    val (container, onContainer, typeLabel) = when (tx.type) {
        TransactionType.INCOME -> Triple(colors.incomeContainer, colors.onIncomeContainer, R.string.type_income)
        TransactionType.EXPENSE -> Triple(colors.expenseContainer, colors.onExpenseContainer, R.string.type_expense)
        TransactionType.TRANSFER -> Triple(colors.transferContainer, colors.onTransferContainer, R.string.type_transfer)
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SoftCard(containerColor = container, padding = 24.dp, shape = MaterialTheme.shapes.extraLarge) {
            Text(stringResource(typeLabel), style = MaterialTheme.typography.titleSmall, color = onContainer)
            RupiahText(
                amount = tx.amount,
                text = signedAmountText(tx.type, tx.amount),
                style = MaterialTheme.typography.displaySmall,
                color = onContainer,
            )
        }
        SoftCard(padding = 8.dp) {
            val rows: List<Pair<String, String>> = buildList {
                when (tx.type) {
                    TransactionType.INCOME -> {
                        add(stringResource(R.string.tx_detail_category) to row.category?.name.orEmpty())
                        add(stringResource(R.string.tx_detail_account) to row.to?.name.orEmpty())
                    }
                    TransactionType.EXPENSE -> {
                        add(stringResource(R.string.tx_detail_category) to row.category?.name.orEmpty())
                        add(stringResource(R.string.tx_detail_account) to row.from?.name.orEmpty())
                    }
                    TransactionType.TRANSFER -> {
                        add(stringResource(R.string.tx_detail_from) to row.from?.name.orEmpty())
                        add(stringResource(R.string.tx_detail_to) to row.to?.name.orEmpty())
                    }
                }
                add(stringResource(R.string.tx_detail_date) to formatDateLong(tx.dateTime, zone))
                add(stringResource(R.string.tx_detail_note) to (tx.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tx_detail_no_note)))
            }
            rows.forEachIndexed { index, (label, value) ->
                DetailRow(label, value)
                if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        if (tx.type == TransactionType.TRANSFER) {
            Text(
                stringResource(R.string.tx_detail_transfer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
