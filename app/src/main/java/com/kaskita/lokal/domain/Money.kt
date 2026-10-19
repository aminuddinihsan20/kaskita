package com.kaskita.lokal.domain

/** Upper bound for a single transaction/opening balance (Rp 1 triliun); keeps all sums far from Long overflow. */
const val MAX_AMOUNT = 1_000_000_000_000L

object RupiahFormatter {
    private const val MINUS = '−'

    /** `1250000` -> `Rp1.250.000`; negative values are `−Rp75.000`. */
    fun format(amount: Long): String {
        val digits = amount.toString().removePrefix("-")
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (amount < 0) "${MINUS}Rp$grouped" else "Rp$grouped"
    }

    /** Short form for chart axes: `75 rb`, `1,2 jt`, `1,5 M`, `1 T` (truncated to one decimal, comma as separator). */
    fun compact(amount: Long): String {
        val magnitude = kotlin.math.abs(amount)
        fun scaled(unit: Long, suffix: String): String {
            val tenths = magnitude * 10 / unit
            val whole = tenths / 10
            val fraction = tenths % 10
            return if (fraction == 0L) "$whole $suffix" else "$whole,$fraction $suffix"
        }
        val text = when {
            magnitude >= 1_000_000_000_000L -> scaled(1_000_000_000_000L, "T")
            magnitude >= 1_000_000_000L -> scaled(1_000_000_000L, "M")
            magnitude >= 1_000_000L -> scaled(1_000_000L, "jt")
            magnitude >= 1_000L -> scaled(1_000L, "rb")
            else -> magnitude.toString()
        }
        return if (amount < 0) "$MINUS$text" else text
    }
}

object AmountParser {
    private val plainDigits = Regex("""\d+""")
    private val dotGrouped = Regex("""\d{1,3}(\.\d{3})+""")

    /** Accepts plain digits or `.`-grouped thousands; returns null for anything else or a value outside `1..MAX_AMOUNT`. */
    fun parse(text: String): Long? {
        val s = text.trim()
        if (!plainDigits.matches(s) && !dotGrouped.matches(s)) return null
        val value = s.replace(".", "").toLongOrNull() ?: return null
        return value.takeIf { it in 1..MAX_AMOUNT }
    }
}
