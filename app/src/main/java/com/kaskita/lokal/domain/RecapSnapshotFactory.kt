package com.kaskita.lokal.domain

import java.time.YearMonth
import java.time.ZoneId

/** Builds a [RecapSnapshot] from stored data with the same [Ledger] math the Laporan screen uses. */
object RecapSnapshotFactory {
    private const val UNCATEGORISED = "Tanpa kategori"

    fun create(
        month: YearMonth,
        zone: ZoneId,
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
    ): RecapSnapshot {
        val range = MonthRange.of(month, zone)
        val summary = Ledger.summary(transactions, range)
        val categoryById = categories.associateBy { it.id }

        val spend = Ledger.expenseByCategory(transactions, range)
            .groupBy { total -> total.categoryId?.let(categoryById::get)?.name ?: UNCATEGORISED }
            .map { (name, rows) ->
                val total = rows.sumOf { it.total }
                CategorySpend(name, total, rows.sumOf { it.count }, percent(total, summary.expense))
            }
            .sortedWith(compareByDescending<CategorySpend> { it.total }.thenBy { it.name })

        val daily = Ledger.dailyExpense(transactions, month, zone)
        val busiest = daily.filter { it.expense > 0 }.maxByOrNull { it.expense }
        val trend = Ledger.lastMonths(transactions, month, 12, zone).map { MonthFlow(it.month, it.income, it.expense) }

        val balances = Ledger.balances(accounts, transactions)
        val active = accounts.filterNot { it.isArchived }
        val perType = active.groupBy { it.type }
            .toSortedMap()
            .map { (type, group) -> AccountSummary(label(type), group.sumOf { balances[it.id] ?: it.openingBalance }) }

        return RecapSnapshot(
            month = month,
            income = summary.income,
            expense = summary.expense,
            transferTotal = summary.transferTotal,
            transferCount = summary.transferCount,
            previous = trend.getOrNull(trend.lastIndex - 1)?.takeIf { it.month == month.minusMonths(1) },
            categories = spend,
            trend = trend,
            busiestDay = busiest?.let { BusyDay(it.day, it.expense) },
            daysWithSpending = daily.count { it.expense > 0 },
            daysInMonth = month.lengthOfMonth(),
            accounts = perType,
            totalBalance = Ledger.totalBalance(accounts, balances),
        )
    }

    private fun percent(part: Long, whole: Long): Int = if (whole <= 0) 0 else ((part * 100 + whole / 2) / whole).toInt()

    private fun label(type: AccountType) = when (type) {
        AccountType.CASH -> "Uang tunai"
        AccountType.BANK -> "Rekening bank"
        AccountType.EWALLET -> "Dompet digital"
        AccountType.OTHER -> "Akun lainnya"
    }
}
