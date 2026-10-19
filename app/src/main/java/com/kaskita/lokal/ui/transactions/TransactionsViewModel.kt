package com.kaskita.lokal.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.MonthRange
import com.kaskita.lokal.domain.PeriodSummary
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

data class DayGroup(val day: LocalDate, val relative: RelativeDay, val items: List<TxRow>)

data class TxFilter(
    val month: YearMonth,
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val query: String = "",
)

data class TransactionsUiState(
    val filter: TxFilter,
    val groups: List<DayGroup> = emptyList(),
    /** Totals of exactly the rows listed (same month and filters); transfers never count as income/expense. */
    val summary: PeriodSummary = PeriodSummary(0, 0, 0, 0),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
    val filterActive: Boolean
        get() = filter.type != null || filter.accountId != null || filter.categoryId != null || filter.query.isNotBlank()
}

class TransactionsViewModel(
    repo: LedgerRepository,
    private val zone: ZoneId,
    private val clock: () -> Long,
) : ViewModel() {

    private val filter = MutableStateFlow(TxFilter(month = YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone))))

    val state: StateFlow<TransactionsUiState> = combine(
        filter,
        repo.observeAccounts(),
        repo.observeCategories(),
        repo.observeTransactions(),
    ) { f, accounts, categories, transactions ->
        val range = MonthRange.of(f.month, zone)
        val query = f.query.trim()
        val matching = transactions.filter { tx ->
            tx.dateTime in range &&
                (f.type == null || tx.type == f.type) &&
                (f.accountId == null || tx.fromAccountId == f.accountId || tx.toAccountId == f.accountId) &&
                (f.categoryId == null || tx.categoryId == f.categoryId) &&
                (query.isEmpty() || tx.note?.contains(query, ignoreCase = true) == true)
        }
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        val groups = buildRows(matching, accounts, categories)
            .groupBy { Instant.ofEpochMilli(it.tx.dateTime).atZone(zone).toLocalDate() }
            .map { (day, rows) ->
                val relative = when (day) {
                    today -> RelativeDay.TODAY
                    today.minusDays(1) -> RelativeDay.YESTERDAY
                    else -> RelativeDay.OTHER
                }
                DayGroup(day, relative, rows)
            }
        TransactionsUiState(
            filter = f,
            groups = groups,
            summary = Ledger.summary(matching, range),
            accounts = accounts,
            categories = categories,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TransactionsUiState(filter = filter.value))

    fun onMonth(month: YearMonth) = filter.update { it.copy(month = month) }
    fun onPreviousMonth() = filter.update { it.copy(month = it.month.minusMonths(1)) }
    fun onNextMonth() = filter.update { it.copy(month = it.month.plusMonths(1)) }

    fun onType(type: TransactionType?) = filter.update { f ->
        val category = f.categoryId?.let { id -> state.value.categories.firstOrNull { it.id == id } }
        val categoryStillFits = category != null && type != null && type != TransactionType.TRANSFER && category.type == type
        f.copy(type = type, categoryId = if (type == null || categoryStillFits) f.categoryId else null)
    }

    fun onAccount(id: Long?) = filter.update { it.copy(accountId = id) }
    fun onCategory(id: Long?) = filter.update { it.copy(categoryId = id) }
    fun onQuery(query: String) = filter.update { it.copy(query = query) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { TransactionsViewModel(container.ledger, container.zone, container::now) }
        }
    }
}
