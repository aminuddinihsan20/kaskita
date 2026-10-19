package com.kaskita.lokal.domain

import java.time.LocalDate
import java.time.YearMonth

/** One expense category line in a recap. [percent] is the rounded share of the month's expense. */
data class CategorySpend(val name: String, val total: Long, val count: Int, val percent: Int)

/** Balance of all active accounts of one kind. Deliberately carries the type label, never the name the user chose. */
data class AccountSummary(val typeLabel: String, val balance: Long)

data class MonthFlow(val month: YearMonth, val income: Long, val expense: Long)

data class BusyDay(val date: LocalDate, val total: Long)

/** Savings for the month: transfers into and out of savings accounts, and the savings balance now. */
data class SavingsSummary(val deposits: Long, val withdrawals: Long, val balance: Long)

/** Everything the recap analysis may look at. Aggregates only: no notes, account names or individual transactions. */
data class RecapSnapshot(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val transferTotal: Long,
    val transferCount: Int,
    val previous: MonthFlow?,
    /** Largest first. */
    val categories: List<CategorySpend>,
    /** Up to 12 months ending at [month], oldest first. */
    val trend: List<MonthFlow>,
    val busiestDay: BusyDay?,
    val daysWithSpending: Int,
    val daysInMonth: Int,
    val accounts: List<AccountSummary>,
    val totalBalance: Long,
    /** Null when the user has no savings account. */
    val savings: SavingsSummary? = null,
) {
    /** Selisih arus kas (pemasukan − pengeluaran); not a balance. */
    val net: Long get() = income - expense
}
