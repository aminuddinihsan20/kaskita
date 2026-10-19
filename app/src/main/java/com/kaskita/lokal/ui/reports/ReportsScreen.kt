package com.kaskita.lokal.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.RecapPrompt
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.ui.common.CategoryBadge
import com.kaskita.lokal.ui.common.IconBadge
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.MonthSelector
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.TabScreen
import com.kaskita.lokal.ui.common.formatMonthYear
import com.kaskita.lokal.ui.settings.AiSetupDialog
import com.kaskita.lokal.ui.settings.AppCheckCodeDialog
import com.kaskita.lokal.ui.settings.AiSetupViewModel
import com.kaskita.lokal.ui.reports.charts.DailyExpenseChart
import com.kaskita.lokal.ui.reports.charts.ExpenseDonutChart
import com.kaskita.lokal.ui.reports.charts.IncomeExpenseBarChart
import com.kaskita.lokal.ui.reports.charts.IncomeExpenseLegend
import com.kaskita.lokal.ui.reports.charts.isAllZero
import com.kaskita.lokal.ui.theme.finance
import com.kaskita.lokal.ui.transactions.TransactionRow
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(viewModel: ReportsViewModel, aiSetup: AiSetupViewModel, zone: ZoneId, onOpenTransaction: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSetup by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    val monthText = formatMonthYear(state.month)

    TabScreen(title = stringResource(R.string.rp_title)) { padding ->
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
            item { YearSelector(state.month.year, viewModel::onPreviousYear, viewModel::onNextYear) }
            item { FlowCard(state, monthText) }
            if (state.prompt is RecapPrompt.Ready) {
                item { AdviceCard(state) }
                item {
                    AiCard(
                        state = state,
                        monthText = monthText,
                        onSetup = { showSetup = true },
                        onAnalyze = { viewModel.onAnalyze() },
                        onAnalyzeAgain = { viewModel.onAnalyze(again = true) },
                        onPreview = { showPreview = true },
                        onShowCode = { showCode = true },
                    )
                }
            }
            item { AccountsCard(state) }
            item { YearlyChartCard(state) }
            item { CategoryCard(state, monthText, viewModel::onSelectCategory) }
            if (state.selectedCategoryId != null && state.drillDown.isNotEmpty()) {
                item {
                    SoftCard(padding = 8.dp) {
                        state.drillDown.forEachIndexed { index, row ->
                            TransactionRow(row, zone, onClick = { onOpenTransaction(row.tx.id) }, showDate = true)
                            if (index < state.drillDown.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
            item { DailyChartCard(state, monthText) }
        }
    }

    if (showSetup) AiSetupDialog(aiSetup, onDismiss = { showSetup = false })
    if (showCode) AppCheckCodeDialog(onDismiss = { showCode = false })
    (state.prompt as? RecapPrompt.Ready)?.let { prompt ->
        if (showPreview) AiPreviewDialog(prompt, onDismiss = { showPreview = false })
    }
}

@Composable
private fun YearSelector(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.rp_prev_year))
                }
                Text(stringResource(R.string.rp_year_label, year), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 6.dp))
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.rp_next_year))
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    SoftCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.padding(top = 12.dp))
        content()
    }
}

@Composable
internal fun Line(label: String, amount: Long, text: String, color: Color, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        RupiahText(
            amount,
            text = text,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun FlowCard(state: ReportsUiState, monthText: String) {
    val s = state.summary
    val colors = MaterialTheme.finance
    SectionCard(stringResource(R.string.rp_flow_title, monthText), null) {
        Line(stringResource(R.string.rp_income), s.income, "+" + RupiahFormatter.format(s.income), colors.income)
        Line(stringResource(R.string.rp_expense), s.expense, "−" + RupiahFormatter.format(s.expense), colors.expense)
        Surface(Modifier.padding(top = 8.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rp_net), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                RupiahText(s.net, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (s.transferCount > 0) {
            Text(
                stringResource(R.string.rp_transfer_note, RupiahFormatter.format(s.transferTotal)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.transfer,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun AccountsCard(state: ReportsUiState) {
    SectionCard(stringResource(R.string.rp_accounts_title), stringResource(R.string.rp_accounts_caption)) {
        state.accountBalances.forEachIndexed { index, b ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Filled.AccountBalanceWallet, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, b.account.name, size = 36.dp)
                Text(b.account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                RupiahText(b.balance, style = MaterialTheme.typography.titleSmall)
            }
            if (index < state.accountBalances.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun YearlyChartCard(state: ReportsUiState) {
    val year = state.month.year
    SectionCard(stringResource(R.string.rp_yearly_title), stringResource(R.string.rp_yearly_period, year)) {
        if (state.yearTotals.isAllZero()) {
            Text(stringResource(R.string.rp_yearly_empty, year), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            IncomeExpenseBarChart(state.yearTotals, height = 190.dp)
            Spacer(Modifier.padding(top = 8.dp))
            IncomeExpenseLegend()
        }
    }
}

@Composable
private fun CategoryCard(state: ReportsUiState, monthText: String, onSelect: (Long?) -> Unit) {
    SectionCard(stringResource(R.string.rp_category_title), stringResource(R.string.rp_category_period, monthText)) {
        if (state.categories.isEmpty()) {
            Text(stringResource(R.string.rp_category_empty, monthText), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ExpenseDonutChart(state.categories, state.selectedCategoryId, state.summary.expense)
        }
        Spacer(Modifier.padding(top = 12.dp))
        val unknown = stringResource(R.string.rp_uncategorized)
        state.categories.forEach { row ->
            val id = row.category?.id
            val selected = id != null && id == state.selectedCategoryId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable(enabled = id != null, role = Role.Button) { onSelect(id) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryBadge(row.category?.iconKey.orEmpty(), row.category?.colorKey ?: "gray", size = 40.dp)
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(row.category?.name ?: unknown, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.rp_category_row, row.count, (row.share * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RupiahText(row.total, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.finance.expense)
            }
        }
        val selectedRow = state.categories.firstOrNull { it.category?.id == state.selectedCategoryId }
        Text(
            text = if (selectedRow != null) stringResource(
                R.string.rp_category_selected, selectedRow.category?.name ?: unknown, RupiahFormatter.format(selectedRow.total), selectedRow.count,
            ) else stringResource(R.string.rp_category_hint),
            style = if (selectedRow != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (selectedRow != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DailyChartCard(state: ReportsUiState, monthText: String) {
    SectionCard(stringResource(R.string.rp_daily_title), stringResource(R.string.rp_daily_period, monthText)) {
        if (state.daily.all { it.expense == 0L }) {
            Text(stringResource(R.string.rp_daily_empty, monthText), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DailyExpenseChart(state.daily)
        }
    }
}
