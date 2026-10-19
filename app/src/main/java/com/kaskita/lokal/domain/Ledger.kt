package com.kaskita.lokal.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class PeriodSummary(val income: Long, val expense: Long, val transferTotal: Long, val transferCount: Int) {
    /** Selisih arus kas (pemasukan − pengeluaran); not a balance. */
    val net: Long get() = income - expense
}

data class CategoryTotal(val categoryId: Long?, val total: Long, val count: Int)

data class MonthTotals(val month: YearMonth, val income: Long, val expense: Long)

data class DayTotal(val day: LocalDate, val expense: Long)

data class AccountBalance(val account: Account, val balance: Long)

/** Pure money math. Everything is recomputed from stored accounts and transactions; nothing is cached. */
object Ledger {

    /** Balance per account id (archived accounts included): `opening + incoming − outgoing`. */
    fun balances(accounts: List<Account>, transactions: List<Transaction>): Map<Long, Long> {
        val result = HashMap<Long, Long>(accounts.size)
        accounts.forEach { result[it.id] = it.openingBalance }
        for (tx in transactions) {
            val credit = tx.type == TransactionType.INCOME || tx.type == TransactionType.TRANSFER
            val debit = tx.type == TransactionType.EXPENSE || tx.type == TransactionType.TRANSFER
            if (credit) tx.toAccountId?.let { id -> result[id]?.let { result[id] = it + tx.amount } }
            if (debit) tx.fromAccountId?.let { id -> result[id]?.let { result[id] = it - tx.amount } }
        }
        return result
    }

    /** Sum of balances of active (non-archived) accounts. */
    fun totalBalance(accounts: List<Account>, balances: Map<Long, Long>): Long =
        accounts.filterNot { it.isArchived }.sumOf { balances[it.id] ?: it.openingBalance }

    fun summary(transactions: List<Transaction>, range: MonthRange): PeriodSummary {
        var income = 0L
        var expense = 0L
        var transferTotal = 0L
        var transferCount = 0
        for (tx in transactions) {
            if (tx.dateTime !in range) continue
            when (tx.type) {
                TransactionType.INCOME -> income += tx.amount
                TransactionType.EXPENSE -> expense += tx.amount
                TransactionType.TRANSFER -> {
                    transferTotal += tx.amount
                    transferCount++
                }
            }
        }
        return PeriodSummary(income, expense, transferTotal, transferCount)
    }

    /** EXPENSE rows only, largest total first. */
    fun expenseByCategory(transactions: List<Transaction>, range: MonthRange): List<CategoryTotal> =
        transactions
            .filter { it.type == TransactionType.EXPENSE && it.dateTime in range }
            .groupBy { it.categoryId }
            .map { (categoryId, rows) -> CategoryTotal(categoryId, rows.sumOf { it.amount }, rows.size) }
            .sortedWith(compareByDescending<CategoryTotal> { it.total }.thenBy { it.categoryId ?: Long.MAX_VALUE })

    /** Exactly 12 entries (Jan..Dec of [year]), zero-filled. */
    fun monthlyTotals(transactions: List<Transaction>, year: Int, zone: ZoneId): List<MonthTotals> {
        val income = LongArray(12)
        val expense = LongArray(12)
        for (tx in transactions) {
            if (tx.type == TransactionType.TRANSFER) continue
            val local = Instant.ofEpochMilli(tx.dateTime).atZone(zone)
            if (local.year != year) continue
            val idx = local.monthValue - 1
            if (tx.type == TransactionType.INCOME) income[idx] += tx.amount else expense[idx] += tx.amount
        }
        return (1..12).map { m -> MonthTotals(YearMonth.of(year, m), income[m - 1], expense[m - 1]) }
    }

    /** [count] consecutive months ending at [endMonth] (oldest first), zero-filled; transfers are ignored. */
    fun lastMonths(transactions: List<Transaction>, endMonth: YearMonth, count: Int, zone: ZoneId): List<MonthTotals> {
        val months = (count - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }
        val wanted = months.toHashSet()
        val income = HashMap<YearMonth, Long>()
        val expense = HashMap<YearMonth, Long>()
        for (tx in transactions) {
            if (tx.type == TransactionType.TRANSFER) continue
            val month = YearMonth.from(Instant.ofEpochMilli(tx.dateTime).atZone(zone))
            if (month !in wanted) continue
            val target = if (tx.type == TransactionType.INCOME) income else expense
            target[month] = (target[month] ?: 0L) + tx.amount
        }
        return months.map { MonthTotals(it, income[it] ?: 0L, expense[it] ?: 0L) }
    }

    /** One entry per calendar day of [month], zero-filled; EXPENSE rows only. */
    fun dailyExpense(transactions: List<Transaction>, month: YearMonth, zone: ZoneId): List<DayTotal> {
        val perDay = HashMap<LocalDate, Long>()
        for (tx in transactions) {
            if (tx.type != TransactionType.EXPENSE) continue
            val day = Instant.ofEpochMilli(tx.dateTime).atZone(zone).toLocalDate()
            if (YearMonth.from(day) != month) continue
            perDay[day] = (perDay[day] ?: 0L) + tx.amount
        }
        return (1..month.lengthOfMonth()).map { d ->
            val day = month.atDay(d)
            DayTotal(day, perDay[day] ?: 0L)
        }
    }
}
