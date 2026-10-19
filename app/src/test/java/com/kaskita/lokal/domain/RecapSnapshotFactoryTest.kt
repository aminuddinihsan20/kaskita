package com.kaskita.lokal.domain

import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.FOOD_ID
import com.kaskita.lokal.domain.TestData.bank
import com.kaskita.lokal.domain.TestData.cash
import com.kaskita.lokal.domain.TestData.expense
import com.kaskita.lokal.domain.TestData.foodCategory
import com.kaskita.lokal.domain.TestData.income
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.domain.TestData.salaryCategory
import com.kaskita.lokal.domain.TestData.transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecapSnapshotFactoryTest {
    private val october = YearMonth.of(2026, 10)
    private val categories = listOf(foodCategory(), salaryCategory())
    private val transactions = listOf(
        income(1, 5_000_000, BANK_ID, millis(2026, 10, 1)),
        expense(2, 200_000, BANK_ID, millis(2026, 10, 5), FOOD_ID),
        expense(3, 100_000, BANK_ID, millis(2026, 10, 5, 18), FOOD_ID),
        expense(4, 100_000, CASH_ID, millis(2026, 10, 20), category = null),
        transfer(5, 500_000, BANK_ID, CASH_ID, millis(2026, 10, 3)),
        income(7, 4_000_000, BANK_ID, millis(2026, 9, 1)),
        expense(6, 50_000, BANK_ID, millis(2026, 9, 10), FOOD_ID),
    )

    private fun snapshot(accounts: List<Account> = listOf(bank(), cash()), tx: List<Transaction> = transactions, month: YearMonth = october) =
        RecapSnapshotFactory.create(month, jakarta, tx, accounts, categories)

    @Test
    fun monthTotalsAndTransfersComeFromTheLedger() {
        val s = snapshot()
        assertEquals(5_000_000L, s.income)
        assertEquals(400_000L, s.expense)
        assertEquals(4_600_000L, s.net)
        assertEquals(500_000L, s.transferTotal)
        assertEquals(1, s.transferCount)
    }

    @Test
    fun previousMonthAndTwelveMonthTrendEndAtTheSelectedMonth() {
        val s = snapshot()
        assertEquals(MonthFlow(YearMonth.of(2026, 9), 4_000_000, 50_000), s.previous)
        assertEquals(12, s.trend.size)
        assertEquals(YearMonth.of(2025, 11), s.trend.first().month)
        assertEquals(october, s.trend.last().month)
    }

    @Test
    fun categoriesAreNamedSortedAndGiveShares() {
        val s = snapshot()
        assertEquals(
            listOf(CategorySpend("Makanan & minuman", 300_000, 2, 75), CategorySpend("Tanpa kategori", 100_000, 1, 25)),
            s.categories,
        )
    }

    @Test
    fun anUnresolvedCategoryJoinsTheUncategorisedLine() {
        val tx = transactions + expense(8, 20_000, BANK_ID, millis(2026, 10, 9), category = 999L)
        val s = snapshot(tx = tx)
        assertEquals(CategorySpend("Tanpa kategori", 120_000, 2, 29), s.categories.last())
    }

    @Test
    fun busiestDayAndSpendingDaysAreCounted() {
        val s = snapshot()
        assertEquals(BusyDay(LocalDate.of(2026, 10, 5), 300_000), s.busiestDay)
        assertEquals(2, s.daysWithSpending)
        assertEquals(31, s.daysInMonth)
    }

    @Test
    fun accountsAreGroupedByTypeAndNeverCarryTheirName() {
        val second = Account(30, "BCA tabungan pribadi", AccountType.BANK, 1_000, 0, false)
        val s = snapshot(accounts = listOf(bank(), cash(), second))
        assertEquals(listOf("Uang tunai", "Rekening bank"), s.accounts.map { it.typeLabel })
        assertEquals(400_000L, s.accounts[0].balance)
        assertEquals(10_150_000L + 1_000L, s.accounts[1].balance)
        assertEquals(s.accounts.sumOf { it.balance }, s.totalBalance)
    }

    @Test
    fun archivedAccountsAreLeftOut() {
        val s = snapshot(accounts = listOf(bank(), cash(archived = true)))
        assertEquals(listOf("Rekening bank"), s.accounts.map { it.typeLabel })
    }

    @Test
    fun aMonthWithoutRecordsIsEmptyNotMissing() {
        val s = snapshot(month = YearMonth.of(2026, 12))
        assertEquals(0L, s.income)
        assertEquals(emptyList<CategorySpend>(), s.categories)
        assertNull(s.busiestDay)
        assertEquals(0, s.daysWithSpending)
        assertEquals(31, s.daysInMonth)
    }
}
