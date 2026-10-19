package com.kaskita.lokal.domain

import java.time.LocalDateTime
import java.time.ZoneId

/** Shared builders for domain tests. Not production code. */
object TestData {
    val jakarta: ZoneId = ZoneId.of("Asia/Jakarta")

    const val BANK_ID = 1L
    const val CASH_ID = 2L
    const val FOOD_ID = 10L
    const val SALARY_ID = 20L

    fun bank(opening: Long = 2_000_000, archived: Boolean = false) =
        Account(BANK_ID, "Rekening Bank", AccountType.BANK, opening, 0, archived)

    fun cash(opening: Long = 0, archived: Boolean = false) =
        Account(CASH_ID, "Cash", AccountType.CASH, opening, 0, archived)

    fun foodCategory() = Category(FOOD_ID, "Makanan & minuman", TransactionType.EXPENSE, "food", "red", true, false)
    fun salaryCategory() = Category(SALARY_ID, "Gaji", TransactionType.INCOME, "work", "green", true, false)

    fun millis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: ZoneId = jakarta): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val defaultDate = millis(2026, 10, 15)

    fun income(id: Long, amount: Long, to: Long, date: Long = defaultDate, category: Long? = SALARY_ID) =
        Transaction(id, TransactionType.INCOME, amount, date, category, null, to, null, 0, 0)

    fun expense(id: Long, amount: Long, from: Long, date: Long = defaultDate, category: Long? = FOOD_ID) =
        Transaction(id, TransactionType.EXPENSE, amount, date, category, from, null, null, 0, 0)

    fun transfer(id: Long, amount: Long, from: Long, to: Long, date: Long = defaultDate) =
        Transaction(id, TransactionType.TRANSFER, amount, date, null, from, to, null, 0, 0)
}
