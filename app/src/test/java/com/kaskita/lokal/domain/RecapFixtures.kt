package com.kaskita.lokal.domain

import java.time.LocalDate
import java.time.YearMonth

/** Shared builders for the recap tests. Not production code. */
fun flow(year: Int, month: Int, income: Long, expense: Long) = MonthFlow(YearMonth.of(year, month), income, expense)

fun recapSnapshot(
    month: YearMonth = YearMonth.of(2026, 10),
    income: Long = 100_000,
    expense: Long = 75_000,
    categories: List<CategorySpend> = listOf(CategorySpend("Makanan & minuman", 75_000, 2, 100)),
    accounts: List<AccountSummary> = listOf(AccountSummary("Uang tunai", 525_000), AccountSummary("Rekening bank", 1_500_000)),
    trend: List<MonthFlow> = (1..12).map {
        flow(2026, it, if (it == 10) 100_000 else 0, if (it == 10) 75_000 else if (it == 9) 99_000 else 0)
    },
) = RecapSnapshot(
    month = month,
    income = income,
    expense = expense,
    transferTotal = 500_000,
    transferCount = 1,
    previous = MonthFlow(YearMonth.of(2026, 9), income = 0, expense = 99_000),
    categories = categories,
    trend = trend,
    busiestDay = BusyDay(LocalDate.of(2026, 10, 19), 50_000),
    daysWithSpending = 2,
    daysInMonth = 31,
    accounts = accounts,
    totalBalance = accounts.sumOf { it.balance },
)
