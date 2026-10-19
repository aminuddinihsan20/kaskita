package com.kaskita.lokal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RupiahFormatterTest {
    @Test
    fun zero() = assertEquals("Rp0", RupiahFormatter.format(0))

    @Test
    fun thousandsGroupedWithDots() = assertEquals("Rp1.250.000", RupiahFormatter.format(1_250_000))

    @Test
    fun belowThousandHasNoSeparator() = assertEquals("Rp999", RupiahFormatter.format(999))

    @Test
    fun negativeUsesMinusSignBeforeRp() = assertEquals("−Rp75.000", RupiahFormatter.format(-75_000))

    @Test
    fun largeValueKeepsAllGroups() = assertEquals("Rp1.000.000.000.000", RupiahFormatter.format(MAX_AMOUNT))
}

class AmountParserTest {
    @Test
    fun plainDigitsParse() = assertEquals(1_250_000L, AmountParser.parse("1250000"))

    @Test
    fun dotGroupedDigitsParse() = assertEquals(1_250_000L, AmountParser.parse("1.250.000"))

    @Test
    fun maxAmountAccepted() = assertEquals(MAX_AMOUNT, AmountParser.parse("1000000000000"))

    @Test
    fun commaDecimalRejected() = assertNull(AmountParser.parse("12,5"))

    @Test
    fun lettersRejected() = assertNull(AmountParser.parse("abc"))

    @Test
    fun negativeRejected() = assertNull(AmountParser.parse("-5"))

    @Test
    fun blankRejected() {
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("   "))
    }

    @Test
    fun zeroRejected() = assertNull(AmountParser.parse("0"))

    @Test
    fun overflowRejectedNotTruncated() = assertNull(AmountParser.parse("99999999999999999999"))

    @Test
    fun aboveMaxRejected() = assertNull(AmountParser.parse("1000000000001"))

    @Test
    fun mixedGarbageRejected() = assertNull(AmountParser.parse("1.2a5"))
}

class CompactRupiahTest {
    @Test
    fun smallValuesAreShownAsIs() {
        assertEquals("0", RupiahFormatter.compact(0))
        assertEquals("999", RupiahFormatter.compact(999))
    }

    @Test
    fun thousandsUseRb() {
        assertEquals("1 rb", RupiahFormatter.compact(1_000))
        assertEquals("1,5 rb", RupiahFormatter.compact(1_500))
        assertEquals("75 rb", RupiahFormatter.compact(75_000))
    }

    @Test
    fun millionsUseJtAndTruncateToOneDecimal() {
        assertEquals("1,2 jt", RupiahFormatter.compact(1_250_000))
        assertEquals("2 jt", RupiahFormatter.compact(2_000_000))
        assertEquals("5 jt", RupiahFormatter.compact(5_000_000))
    }

    @Test
    fun billionsAndTrillions() {
        assertEquals("1,5 M", RupiahFormatter.compact(1_500_000_000))
        assertEquals("1 T", RupiahFormatter.compact(MAX_AMOUNT))
    }

    @Test
    fun negativeKeepsTheMinusSign() = assertEquals("\u221275 rb", RupiahFormatter.compact(-75_000))
}
