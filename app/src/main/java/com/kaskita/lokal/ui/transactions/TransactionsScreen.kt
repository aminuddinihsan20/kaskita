package com.kaskita.lokal.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.PeriodSummary
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.common.AddTransactionSheet
import com.kaskita.lokal.ui.common.EmptyCard
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.MonthSelector
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.TabScreen
import com.kaskita.lokal.ui.common.formatDate
import com.kaskita.lokal.ui.theme.finance
import java.time.ZoneId

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    zone: ZoneId,
    onOpen: (Long) -> Unit,
    onAdd: (TransactionType) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    TabScreen(
        title = stringResource(R.string.tx_title),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tx_fab), style = MaterialTheme.typography.titleSmall) },
                containerColor = MaterialTheme.finance.cta,
                contentColor = MaterialTheme.finance.onCta,
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MonthSelector(state.filter.month, viewModel::onPreviousMonth, viewModel::onNextMonth) }
            item { FilterCard(state, viewModel) }
            item { SummaryCard(state.summary) }

            when {
                state.isLoading -> item { LoadingState(Modifier.heightIn(min = 160.dp)) }
                state.isEmpty -> item {
                    if (state.filterActive) {
                        EmptyCard(
                            message = stringResource(R.string.tx_empty_filtered),
                            actionLabel = stringResource(R.string.tx_clear_filters),
                            onAction = {
                                viewModel.onType(null)
                                viewModel.onAccount(null)
                                viewModel.onCategory(null)
                                viewModel.onQuery("")
                            },
                        )
                    } else {
                        EmptyCard(
                            message = stringResource(R.string.tx_empty),
                            actionLabel = stringResource(R.string.tx_empty_action),
                            onAction = { showSheet = true },
                        )
                    }
                }
                else -> state.groups.forEach { group ->
                    item(key = "group-${group.day}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = when (group.relative) {
                                    RelativeDay.TODAY -> stringResource(R.string.tx_today)
                                    RelativeDay.YESTERDAY -> stringResource(R.string.tx_yesterday)
                                    RelativeDay.OTHER -> formatDate(group.day)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            SoftCard(padding = 8.dp) {
                                group.items.forEachIndexed { index, row ->
                                    TransactionRow(row, zone, onClick = { onOpen(row.tx.id) })
                                    if (index < group.items.lastIndex) {
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

    if (showSheet) {
        AddTransactionSheet(onPick = { showSheet = false; onAdd(it) }, onDismiss = { showSheet = false })
    }
}

@Composable
private fun FilterCard(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    val incomeLabel = stringResource(R.string.type_income)
    val expenseLabel = stringResource(R.string.type_expense)
    val allAccounts = stringResource(R.string.tx_filter_account_all)
    val allCategories = stringResource(R.string.tx_filter_category_all)

    val accountOptions = listOf<Pair<Long?, String>>(null to allAccounts) + state.accounts.map { it.id to it.name }
    val categoryOptions = listOf<Pair<Long?, String>>(null to allCategories) + state.categories
        .filter { c -> state.filter.type == null || c.type == state.filter.type }
        .map { c ->
            val suffix = if (state.filter.type == null) " · " + if (c.type == TransactionType.INCOME) incomeLabel else expenseLabel else ""
            c.id to (c.name + suffix)
        }

    SoftCard(padding = 16.dp) {
        TypeChips(state.filter.type, viewModel::onType)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                label = stringResource(R.string.tx_filter_account),
                selectedLabel = accountOptions.firstOrNull { it.first == state.filter.accountId }?.second ?: allAccounts,
                options = accountOptions,
                onSelect = viewModel::onAccount,
                enabled = true,
                modifier = Modifier.weight(1f),
            )
            FilterDropdown(
                label = stringResource(R.string.tx_filter_category),
                selectedLabel = categoryOptions.firstOrNull { it.first == state.filter.categoryId }?.second ?: allCategories,
                options = categoryOptions,
                onSelect = viewModel::onCategory,
                enabled = state.filter.type != TransactionType.TRANSFER,
                modifier = Modifier.weight(1f),
            )
        }
        SearchField(state.filter.query, viewModel::onQuery, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun TypeChips(selected: TransactionType?, onSelect: (TransactionType?) -> Unit) {
    val options = listOf<Pair<TransactionType?, Int>>(
        null to R.string.type_all,
        TransactionType.INCOME to R.string.type_income,
        TransactionType.EXPENSE to R.string.type_expense,
        TransactionType.TRANSFER to R.string.type_transfer,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (type, label) ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(stringResource(label)) },
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<Long?, String>>,
    onSelect: (Long?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$label: $selectedLabel" },
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.medium) {
            options.forEach { (id, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(stringResource(R.string.tx_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.tx_search_clear))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun SummaryCard(summary: PeriodSummary) {
    val colors = MaterialTheme.finance
    SoftCard {
        Text(stringResource(R.string.tx_summary_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryLine(stringResource(R.string.tx_summary_income), "+" + RupiahFormatter.format(summary.income), Icons.Filled.ArrowDownward, colors.incomeContainer, colors.onIncomeContainer, colors.income)
            SummaryLine(stringResource(R.string.tx_summary_expense), "−" + RupiahFormatter.format(summary.expense), Icons.Filled.ArrowUpward, colors.expenseContainer, colors.onExpenseContainer, colors.expense)
            SummaryLine(stringResource(R.string.tx_summary_transfer), RupiahFormatter.format(summary.transferTotal), Icons.Filled.SwapHoriz, colors.transferContainer, colors.onTransferContainer, colors.transfer)
        }
        Surface(
            modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tx_summary_net),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                RupiahText(summary.net, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, text: String, icon: ImageVector, container: Color, onContainer: Color, amountColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        RupiahText(0, text = text, style = MaterialTheme.typography.titleSmall, color = amountColor)
    }
}
