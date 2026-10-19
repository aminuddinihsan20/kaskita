package com.kaskita.lokal.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import com.kaskita.lokal.data.ai.GeminiClient
import com.kaskita.lokal.data.ai.GeminiError
import com.kaskita.lokal.data.ai.GeminiResult
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountBalance
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.DayTotal
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.MonthRange
import com.kaskita.lokal.domain.MonthSavings
import com.kaskita.lokal.domain.MonthTotals
import com.kaskita.lokal.domain.SavingsFlow
import com.kaskita.lokal.domain.PeriodSummary
import com.kaskita.lokal.domain.RecapAdvice
import com.kaskita.lokal.domain.RecapAdvisor
import com.kaskita.lokal.domain.RecapPrompt
import com.kaskita.lokal.domain.RecapPromptBuilder
import com.kaskita.lokal.domain.RecapSnapshotFactory
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.AppContainer
import com.kaskita.lokal.ui.transactions.TxRow
import com.kaskita.lokal.ui.transactions.buildRows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One slice of the expense-by-category chart; [category] is null only if the category row no longer resolves. */
data class CategoryRow(val category: Category?, val total: Long, val count: Int, val share: Float)

data class ReportsUiState(
    val month: YearMonth,
    val summary: PeriodSummary = PeriodSummary(0, 0, 0, 0),
    /** 12 entries for [month]'s year. */
    val yearTotals: List<MonthTotals> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val daily: List<DayTotal> = emptyList(),
    /** Current balances of active accounts (all time) — deliberately separate from the month's cash flow. */
    val accountBalances: List<AccountBalance> = emptyList(),
    val selectedCategoryId: Long? = null,
    /** The expense transactions behind the selected category slice. */
    val drillDown: List<TxRow> = emptyList(),
    /** Next-month saving and spending guidance for [month]; null when there is no income to base it on. Needs no AI and no network. */
    val advice: RecapAdvice? = null,
    val savings: SavingsUi? = null,
    /** Exactly what an analysis would send; [RecapPrompt.NoData] for an empty month. */
    val prompt: RecapPrompt = RecapPrompt.NoData,
    val ai: AiRecapState = AiRecapState.NotReady,
    val aiModel: String = DEFAULT_GEMINI_MODEL,
    val isLoading: Boolean = true,
)

data class SavingsAccountRow(val name: String, val balance: Long)

/** The Tabungan card: only exists when the user has at least one savings account. */
data class SavingsUi(
    val balance: Long,
    val accounts: List<SavingsAccountRow>,
    /** Deposits and withdrawals (transfers) in the selected month. */
    val monthFlow: SavingsFlow,
    /** The last six months ending at the selected month, oldest first. */
    val recent: List<MonthSavings>,
)

sealed interface AiRecapState {
    /** The user has not agreed to sending data yet. */
    data object NotReady : AiRecapState
    data object NoData : AiRecapState
    data object Idle : AiRecapState
    data object Loading : AiRecapState
    data class Done(val text: String) : AiRecapState
    data class Failed(val error: GeminiError) : AiRecapState
}

private data class Computed(val state: ReportsUiState, val advice: RecapAdvice?, val prompt: RecapPrompt)
private data class AiConfig(val consent: Boolean, val model: String)
private data class AiRuntime(val results: Map<String, GeminiResult>, val inFlight: Set<String>)

class ReportsViewModel(
    repo: LedgerRepository,
    private val zone: ZoneId,
    settings: SettingsStore,
    private val gemini: GeminiClient,
    clock: () -> Long,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(Instant.ofEpochMilli(clock()).atZone(zone)))
    private val selected = MutableStateFlow<Long?>(null)

    /** Answers by `model|cacheKey`, so a result only ever shows for the exact numbers and model it was asked with. Memory only. */
    private val results = MutableStateFlow<Map<String, GeminiResult>>(emptyMap())
    private val inFlight = MutableStateFlow<Set<String>>(emptySet())

    private val computed = combine(
        month,
        selected,
        repo.observeAccounts(),
        repo.observeCategories(),
        repo.observeTransactions(),
    ) { m, selectedId, accounts, categories, transactions ->
        val range = MonthRange.of(m, zone)
        val summary = Ledger.summary(transactions, range)
        val categoryById = categories.associateBy { it.id }

        val rows = Ledger.expenseByCategory(transactions, range).map { total ->
            CategoryRow(
                category = total.categoryId?.let(categoryById::get),
                total = total.total,
                count = total.count,
                share = if (summary.expense > 0) total.total.toFloat() / summary.expense else 0f,
            )
        }
        val effectiveSelection = selectedId?.takeIf { id -> rows.any { it.category?.id == id } }
        val drillDown = if (effectiveSelection == null) {
            emptyList()
        } else {
            buildRows(
                transactions.filter {
                    it.type == TransactionType.EXPENSE && it.dateTime in range && it.categoryId == effectiveSelection
                },
                accounts,
                categories,
            )
        }
        val balances = Ledger.balances(accounts, transactions)
        val snapshot = RecapSnapshotFactory.create(m, zone, transactions, accounts, categories)

        Computed(ReportsUiState(
            month = m,
            summary = summary,
            yearTotals = Ledger.monthlyTotals(transactions, m.year, zone),
            categories = rows,
            daily = Ledger.dailyExpense(transactions, m, zone),
            accountBalances = accounts.filterNot { it.isArchived }.map { AccountBalance(it, balances[it.id] ?: it.openingBalance) },
            selectedCategoryId = effectiveSelection,
            drillDown = drillDown,
            savings = savingsUi(accounts, transactions, balances, m, range),
            isLoading = false,
        ), RecapAdvisor.advise(snapshot), RecapPromptBuilder.build(snapshot))
    }

    private fun savingsUi(
        accounts: List<Account>,
        transactions: List<Transaction>,
        balances: Map<Long, Long>,
        month: YearMonth,
        range: MonthRange,
    ): SavingsUi? {
        if (accounts.none { it.type == AccountType.SAVINGS }) return null
        return SavingsUi(
            balance = Ledger.savingsBalance(accounts, balances),
            accounts = accounts.filter { it.type == AccountType.SAVINGS && !it.isArchived }
                .map { SavingsAccountRow(it.name, balances[it.id] ?: it.openingBalance) },
            monthFlow = Ledger.savingsFlow(transactions, accounts, range),
            recent = Ledger.savingsByMonth(transactions, accounts, month, 6, zone),
        )
    }

    private val config = combine(settings.aiConsent, settings.geminiModel, ::AiConfig)
    private val runtime = combine(results, inFlight, ::AiRuntime)

    val state: StateFlow<ReportsUiState> = combine(computed, config, runtime) { c, cfg, rt ->
        c.state.copy(advice = c.advice, prompt = c.prompt, aiModel = cfg.model, ai = aiState(c.prompt, cfg, rt))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportsUiState(month = month.value))

    private fun aiState(prompt: RecapPrompt, cfg: AiConfig, rt: AiRuntime): AiRecapState {
        if (prompt !is RecapPrompt.Ready) return AiRecapState.NoData
        if (!cfg.consent) return AiRecapState.NotReady
        val id = requestId(cfg.model, prompt)
        if (id in rt.inFlight) return AiRecapState.Loading
        return when (val result = rt.results[id]) {
            null -> AiRecapState.Idle
            is GeminiResult.Success -> AiRecapState.Done(result.text)
            is GeminiResult.Failure -> AiRecapState.Failed(result.error)
        }
    }

    private fun requestId(model: String, prompt: RecapPrompt.Ready) = "$model|${prompt.cacheKey}"

    /**
     * Sends the aggregates shown in [ReportsUiState.prompt] to Gemini. Does nothing unless the user has agreed,
     * and not while a request is running. [again] also allows replacing a finished answer.
     */
    fun onAnalyze(again: Boolean = false) {
        val s = state.value
        val prompt = s.prompt as? RecapPrompt.Ready ?: return
        val allowed = when (s.ai) {
            AiRecapState.Idle, is AiRecapState.Failed -> true
            is AiRecapState.Done -> again
            else -> false
        }
        if (!allowed) return
        val id = requestId(s.aiModel, prompt)
        if (!markInFlight(id)) return
        results.update { it - id }
        viewModelScope.launch {
            try {
                val result = try {
                    gemini.analyze(s.aiModel, prompt.system, prompt.user)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    GeminiResult.Failure(GeminiError.UNKNOWN)
                }
                results.update { (it + (id to result)).entries.toList().takeLast(MAX_CACHED).associate { e -> e.toPair() } }
            } finally {
                inFlight.update { it - id }
            }
        }
    }

    private fun markInFlight(id: String): Boolean {
        while (true) {
            val current = inFlight.value
            if (id in current) return false
            if (inFlight.compareAndSet(current, current + id)) return true
        }
    }

    fun onMonth(value: YearMonth) = moveTo(value)
    fun onPreviousMonth() = moveTo(month.value.minusMonths(1))
    fun onNextMonth() = moveTo(month.value.plusMonths(1))
    fun onPreviousYear() = moveTo(month.value.minusYears(1))
    fun onNextYear() = moveTo(month.value.plusYears(1))

    /** Tapping the selected slice again clears the selection. */
    fun onSelectCategory(id: Long?) {
        selected.value = if (id == selected.value) null else id
    }

    private fun moveTo(value: YearMonth) {
        selected.value = null
        month.value = value
    }

    companion object {
        private const val MAX_CACHED = 24

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ReportsViewModel(container.ledger, container.zone, container.settings, container.gemini, container::now)
            }
        }
    }
}
