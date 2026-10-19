package com.kaskita.lokal.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Contract: valid input is regrouped; anything else is rejected (null) so the field never silently rewrites it. */
class AmountFieldFormatterTest {
    @Test
    fun groupsThousandsWithDots() = assertEquals("1.250.000", formatAmountInput("1250000"))

    @Test
    fun keepsAlreadyGroupedInputStable() = assertEquals("1.250.000", formatAmountInput("1.250.000"))

    @Test
    fun blankStaysBlank() = assertEquals("", formatAmountInput(""))

    @Test
    fun leadingZerosAreDropped() {
        assertEquals("12", formatAmountInput("0012"))
        assertEquals("", formatAmountInput("000"))
    }

    @Test
    fun shortValuesHaveNoSeparator() = assertEquals("999", formatAmountInput("999"))

    @Test
    fun backspaceOnAGroupedValueRegroupsTheRemainingDigits() {
        // "1.250.000" with the last digit deleted arrives as "1.250.00"
        assertEquals("125.000", formatAmountInput("1.250.00"))
    }

    @Test
    fun lettersAreRejectedNotStripped() {
        assertNull(formatAmountInput("abc12x3"))
        assertNull(formatAmountInput("Rp 1.000"))
    }

    // Final review finding 2: "2.500.000,00" used to become Rp250.000.000 (100x), "12,5" became 125, "-5" became 5
    @Test
    fun decimalCommaIsRejectedInsteadOfBecomingAHundredTimesBiggerNumber() {
        assertNull(formatAmountInput("2.500.000,00"))
        assertNull(formatAmountInput("12,5"))
    }

    @Test
    fun minusSignIsRejectedInsteadOfBecomingPositive() = assertNull(formatAmountInput("-5"))

    @Test
    fun whitespaceIsRejected() {
        assertNull(formatAmountInput("1 000"))
        assertNull(formatAmountInput(" "))
    }

    @Test
    fun thirteenDigitsAreAcceptedButMoreAreRejectedNotTruncated() {
        assertEquals("9.999.999.999.999", formatAmountInput("9999999999999"))
        assertNull(formatAmountInput("99999999999999"))
        assertNull(formatAmountInput("99999999999999999999"))
    }

    @Test
    fun groupThousandsFormatsACanonicalNumber() {
        assertEquals("1.250.000", groupThousands(1_250_000))
        assertEquals("0", groupThousands(0))
    }
}
