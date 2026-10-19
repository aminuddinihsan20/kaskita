package com.kaskita.lokal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.MonthRange
import com.kaskita.lokal.domain.MonthTotals
import com.kaskita.lokal.ui.AppContainer
import com.kaskita.lokal.ui.transactions.TxRow
import com.kaskita.lokal.ui.transactions.buildRows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private const val RECENT_COUNT = 5
private const val MINI_CHART_MONTHS = 6

data class HomeUiState(
    val month: YearMonth,
    /** Sum of all active accounts, over all time — independent of [month]. */
    val totalBalance: Long = 0,
    val cash: AccountBalance? = null,
    /** Income/expense of [month] only; transfers excluded. */
    val income: Long = 0,
    val expense: Long = 0,
    val recent: List<TxRow> = emptyList(),
    val miniChart: List<MonthTotals> = emptyList(),
    val isLoading: Boolean = true,
)

class HomeViewModel(
    repo: LedgerRepository,
    private val zone: ZoneId,
    clock: () -> Long,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone)))

    val state: StateFlow<HomeUiState> = combine(
        month,
        repo.observeAccounts(),
        repo.observeCategories(),
        repo.observeTransactions(),
    ) { selected, accounts, categories, transactions ->
        val balances = Ledger.balances(accounts, transactions)
        val summary = Ledger.summary(transactions, MonthRange.of(selected, zone))
        val cashAccount = accounts.firstOrNull { it.type == AccountType.CASH && !it.isArchived }
        HomeUiState(
            month = selected,
            totalBalance = Ledger.totalBalance(accounts, balances),
            cash = cashAccount?.let { AccountBalance(it, balances[it.id] ?: it.openingBalance) },
            income = summary.income,
            expense = summary.expense,
            recent = buildRows(transactions.take(RECENT_COUNT), accounts, categories),
            miniChart = Ledger.lastMonths(transactions, selected, MINI_CHART_MONTHS, zone),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(month = month.value))

    fun onPreviousMonth() = month.update { it.minusMonths(1) }
    fun onNextMonth() = month.update { it.plusMonths(1) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(container.ledger, container.zone, container::now) }
        }
    }
}
