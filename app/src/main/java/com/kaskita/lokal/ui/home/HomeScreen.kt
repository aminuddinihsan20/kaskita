package com.kaskita.lokal.ui.home

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.common.ConnectedButtonGroup
import com.kaskita.lokal.ui.common.GroupButton
import com.kaskita.lokal.ui.common.IconBadge
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.MonthSelector
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.TabScreen
import com.kaskita.lokal.ui.common.TonalButton
import com.kaskita.lokal.ui.common.formatMonthYearShort
import com.kaskita.lokal.ui.reports.charts.IncomeExpenseBarChart
import com.kaskita.lokal.ui.reports.charts.IncomeExpenseLegend
import com.kaskita.lokal.ui.reports.charts.isAllZero
import com.kaskita.lokal.ui.theme.finance
import com.kaskita.lokal.ui.transactions.TransactionRow
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    zone: ZoneId,
    onAdd: (TransactionType) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAllTransactions: () -> Unit,
    onCashHistory: (accountId: Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TabScreen(
        title = stringResource(R.string.home_title),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_open_settings))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@TabScreen
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MonthSelector(state.month, viewModel::onPreviousMonth, viewModel::onNextMonth) }
            item { HeroTotal(state.totalBalance) }
            item { Bento(state, onWithdraw = { onAdd(TransactionType.TRANSFER) }, onCashHistory = onCashHistory) }
            item { QuickActions(onAdd) }
            item { MiniChartCard(state) }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onAllTransactions) { Text(stringResource(R.string.home_recent_all) + " ›") }
                }
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_recent_empty),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    SoftCard(padding = 8.dp) {
                        state.recent.forEachIndexed { index, row ->
                            TransactionRow(row, zone, onClick = { onOpenTransaction(row.tx.id) }, showDate = true)
                            if (index < state.recent.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The one bold shape on the screen: an asymmetric container (two generous corners, two tight ones) with soft pastel glows
 * behind the figure. The glow needs the blur effect of Android 12+, so older devices simply get the flat container.
 */
@Composable
private fun HeroTotal(total: Long) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 48.dp, topEnd = 16.dp, bottomEnd = 48.dp, bottomStart = 16.dp)
    val glowA = MaterialTheme.finance.cta
    val glowB = scheme.tertiaryContainer
    Box(Modifier.fillMaxWidth().clip(shape).background(scheme.primaryContainer)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(Modifier.matchParentSize().blur(36.dp)) {
                drawCircle(glowA, radius = size.height * 0.55f, center = Offset(size.width * 0.96f, size.height * 0.05f), alpha = 0.55f)
                drawCircle(glowB, radius = size.height * 0.4f, center = Offset(size.width * 0.12f, size.height * 1.0f), alpha = 0.75f)
            }
        }
        Column(Modifier.padding(horizontal = 28.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_total_label), style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer.copy(alpha = 0.85f))
            RupiahText(total, style = MaterialTheme.typography.displaySmall, color = scheme.onPrimaryContainer)
            Text(stringResource(R.string.home_total_caption), style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun Bento(state: HomeUiState, onWithdraw: () -> Unit, onCashHistory: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.cash?.let { cash ->
            CashTile(cash, onWithdraw, { onCashHistory(cash.account.id) }, Modifier.weight(1.1f).fillMaxHeight())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val month = formatMonthYearShort(state.month)
            FlowTile(
                label = stringResource(R.string.home_income_named, month),
                amount = state.income,
                text = "+" + RupiahFormatter.format(state.income),
                container = MaterialTheme.finance.incomeContainer,
                content = MaterialTheme.finance.onIncomeContainer,
                direction = Icons.Filled.ArrowDownward,
                modifier = Modifier.weight(1f),
            )
            FlowTile(
                label = stringResource(R.string.home_expense_named, month),
                amount = state.expense,
                text = "−" + RupiahFormatter.format(state.expense),
                container = MaterialTheme.finance.expenseContainer,
                content = MaterialTheme.finance.onExpenseContainer,
                direction = Icons.Filled.ArrowUpward,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CashTile(cash: AccountBalance, onWithdraw: () -> Unit, onHistory: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    SoftCard(
        modifier = modifier,
        containerColor = scheme.tertiaryContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 40.dp, bottomEnd = 16.dp, bottomStart = 40.dp),
        padding = 18.dp,
    ) {
        IconBadge(Icons.Filled.Payments, scheme.surfaceContainerLowest.copy(alpha = 0.65f), scheme.onTertiaryContainer, "cash", size = 44.dp)
        Spacer(Modifier.height(12.dp))
        Text(cash.account.name, style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer)
        RupiahText(cash.balance, style = MaterialTheme.typography.headlineSmall, color = scheme.onTertiaryContainer)
        Text(stringResource(R.string.home_cash_caption), style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer.copy(alpha = 0.85f))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))
        TonalButton(
            text = stringResource(R.string.home_withdraw),
            onClick = onWithdraw,
            modifier = Modifier.fillMaxWidth(),
            container = scheme.surfaceContainerLowest.copy(alpha = 0.7f),
            content = scheme.onTertiaryContainer,
        )
        TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_history), color = scheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun FlowTile(
    label: String,
    amount: Long,
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    direction: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    SoftCard(modifier = modifier, containerColor = container, shape = RoundedCornerShape(28.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(content.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(direction, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = content)
        }
        Spacer(Modifier.height(6.dp))
        RupiahText(amount, text = text, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

@Composable
private fun QuickActions(onAdd: (TransactionType) -> Unit) {
    val colors = MaterialTheme.finance
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectedButtonGroup(
            listOf(
                GroupButton(stringResource(R.string.add_income), colors.incomeContainer, colors.onIncomeContainer) { onAdd(TransactionType.INCOME) },
                GroupButton(stringResource(R.string.add_expense), colors.expenseContainer, colors.onExpenseContainer) { onAdd(TransactionType.EXPENSE) },
            ),
        )
        TonalButton(
            text = stringResource(R.string.add_transfer),
            onClick = { onAdd(TransactionType.TRANSFER) },
            modifier = Modifier.fillMaxWidth(),
            container = colors.transferContainer,
            content = colors.onTransferContainer,
        )
    }
}

@Composable
private fun MiniChartCard(state: HomeUiState) {
    SoftCard {
        Text(stringResource(R.string.home_chart_title), style = MaterialTheme.typography.titleMedium)
        val first = state.miniChart.firstOrNull()
        val last = state.miniChart.lastOrNull()
        if (first != null && last != null) {
            Text(
                stringResource(R.string.home_chart_period, formatMonthYearShort(first.month), formatMonthYearShort(last.month)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
        }
        if (state.miniChart.isAllZero()) {
            Text(stringResource(R.string.home_chart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IncomeExpenseBarChart(state.miniChart, height = 150.dp)
            Spacer(Modifier.height(8.dp))
            IncomeExpenseLegend()
        }
    }
}
