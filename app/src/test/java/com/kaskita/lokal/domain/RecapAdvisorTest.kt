package com.kaskita.lokal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecapAdvisorTest {
    private fun advise(income: Long, expense: Long, trend: List<MonthFlow> = emptyList()) =
        RecapAdvisor.advise(recapSnapshot(income = income, expense = expense, categories = emptyList(), trend = trend))

    @Test
    fun savingMoreThanTwentyPercentIsOnTrack() {
        val a = advise(5_000_000, 3_000_000)!!
        assertEquals(SavingsStatus.ON_TRACK, a.status)
        assertEquals(20, a.targetPercent)
        assertEquals(1_000_000L, a.savingsTarget)
        assertEquals(4_000_000L, a.spendingLimit)
        assertEquals(0L, a.cutNeeded)
        assertEquals(2_000_000L, a.savedThisMonth)
    }

    @Test
    fun savingLessThanTheTargetIsBelowTargetAndNamesTheCut() {
        val a = advise(5_000_000, 4_500_000)!!
        assertEquals(SavingsStatus.BELOW_TARGET, a.status)
        assertEquals(1_000_000L, a.savingsTarget)
        assertEquals(500_000L, a.savedThisMonth)
        assertEquals(500_000L, a.cutNeeded)
    }

    @Test
    fun spendingMoreThanEarnedIsADeficitWithAGentlerTenPercentTarget() {
        val a = advise(5_000_000, 6_000_000)!!
        assertEquals(SavingsStatus.DEFICIT, a.status)
        assertEquals(10, a.targetPercent)
        assertEquals(500_000L, a.savingsTarget)
        assertEquals(4_500_000L, a.spendingLimit)
        assertEquals(1_500_000L, a.cutNeeded)
        assertEquals(0L, a.savedThisMonth)
    }

    @Test
    fun spendingExactlyTheIncomeIsBelowTargetNotADeficit() {
        assertEquals(SavingsStatus.BELOW_TARGET, advise(5_000_000, 5_000_000)!!.status)
    }

    @Test
    fun theTargetIsRoundedDownToWholeThousandRupiah() {
        val a = advise(1_234_567, 100_000)!!
        assertEquals(246_000L, a.savingsTarget)
        assertEquals(988_567L, a.spendingLimit)
    }

    @Test
    fun aMonthWithoutIncomeUsesTheAverageOfTheThreeLatestEarlierMonthsThatHaveIncome() {
        val trend = listOf(
            flow(2026, 1, 100_000_000, 0), // too old: the 4th income month going back
            flow(2026, 5, 8_000_000, 0), flow(2026, 7, 4_000_000, 0), flow(2026, 8, 0, 1_000),
            flow(2026, 9, 6_000_000, 0),
            flow(2026, 11, 50_000_000, 0), // after the selected month: ignored
        )
        val a = advise(income = 0, expense = 2_000_000, trend = trend)!!
        assertEquals(6_000_000L, a.baseIncome)
        assertEquals(true, a.incomeFromAverage)
        // spending Rp2.000.000 before the salary arrives is well inside the usual budget: not a deficit
        assertEquals(SavingsStatus.ON_TRACK, a.status)
        assertEquals(20, a.targetPercent)
        assertEquals(1_200_000L, a.savingsTarget)
        assertEquals(4_800_000L, a.spendingLimit)
        assertEquals(0L, a.cutNeeded)
    }

    private val usual = listOf(flow(2026, 9, 6_000_000, 0))

    @Test
    fun beforeTheSalaryArrivesSpendingPastTheLimitIsBelowTargetAndNamesTheCut() {
        val a = advise(income = 0, expense = 5_000_000, trend = usual)!!
        assertEquals(SavingsStatus.BELOW_TARGET, a.status)
        assertEquals(200_000L, a.cutNeeded)
        assertEquals(1_000_000L, a.savedThisMonth)
    }

    @Test
    fun beforeTheSalaryArrivesSpendingMoreThanTheUsualIncomeIsADeficit() {
        val a = advise(income = 0, expense = 7_000_000, trend = usual)!!
        assertEquals(SavingsStatus.DEFICIT, a.status)
        assertEquals(2_200_000L, a.cutNeeded)
        assertEquals(0L, a.savedThisMonth)
    }

    @Test
    fun thisMonthsOwnIncomeIsNeverMixedWithTheAverage() {
        val a = advise(income = 2_000_000, expense = 0, trend = listOf(flow(2026, 9, 9_000_000, 0)))!!
        assertEquals(2_000_000L, a.baseIncome)
        assertEquals(false, a.incomeFromAverage)
    }

    @Test
    fun withoutAnyIncomeAnywhereThereIsNoAdvice() {
        assertNull(advise(0, 80_000))
    }
}
