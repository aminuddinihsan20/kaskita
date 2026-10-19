package com.kaskita.lokal.domain

enum class SavingsStatus { ON_TRACK, BELOW_TARGET, DEFICIT }

/**
 * Next-month guidance, computed here so the numbers are deterministic and testable. The AI only explains them.
 * A rule of thumb (save 20% of income, 10% after a month that spent more than it earned), not financial advice.
 */
data class RecapAdvice(
    /** The income the percentages are taken from. */
    val baseIncome: Long,
    /** True when the month has no income and [baseIncome] is the average of earlier months. */
    val incomeFromAverage: Boolean,
    val targetPercent: Int,
    val savingsTarget: Long,
    /** [baseIncome] − [savingsTarget]. */
    val spendingLimit: Long,
    val status: SavingsStatus,
    /** What the month left over (never negative); measured against the average income when [incomeFromAverage]. */
    val savedThisMonth: Long,
    /** How much less than this month's expense would fit the limit (0 when it already does). */
    val cutNeeded: Long,
)

object RecapAdvisor {
    const val TARGET_PERCENT = 20
    const val DEFICIT_PERCENT = 10
    private const val AVERAGE_OVER_MONTHS = 3
    private const val ROUND_TO = 1_000L

    fun advise(s: RecapSnapshot): RecapAdvice? {
        val fromAverage = s.income <= 0
        val base = if (fromAverage) {
            val earlier = s.trend.filter { it.month < s.month && it.income > 0 }
                .sortedByDescending { it.month }
                .take(AVERAGE_OVER_MONTHS)
            if (earlier.isEmpty()) return null
            earlier.sumOf { it.income } / earlier.size
        } else {
            s.income
        }
        // With no income recorded yet, what is left is measured against the usual income, not against zero.
        val left = if (fromAverage) base - s.expense else s.income - s.expense
        val percent = if (!fromAverage && left < 0) DEFICIT_PERCENT else TARGET_PERCENT
        val target = (base / 100 * percent) / ROUND_TO * ROUND_TO
        val limit = base - target
        val status = when {
            left < 0 -> SavingsStatus.DEFICIT
            left >= target -> SavingsStatus.ON_TRACK
            else -> SavingsStatus.BELOW_TARGET
        }
        return RecapAdvice(
            baseIncome = base,
            incomeFromAverage = fromAverage,
            targetPercent = percent,
            savingsTarget = target,
            spendingLimit = limit,
            status = status,
            savedThisMonth = maxOf(left, 0),
            cutNeeded = maxOf(s.expense - limit, 0),
        )
    }
}
