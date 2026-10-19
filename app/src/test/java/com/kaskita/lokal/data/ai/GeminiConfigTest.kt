package com.kaskita.lokal.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiConfigTest {
    @Test
    fun modelNamesAreNormalisedAndValidated() {
        assertEquals("gemini-3.8-flash", normalizeGeminiModel(" gemini-3.8-flash "))
        assertEquals("gemini-2.5-flash", normalizeGeminiModel("models/gemini-2.5-flash"))
        assertNull(normalizeGeminiModel("gemini/../x"))
        assertNull(normalizeGeminiModel(""))
        assertNull(normalizeGeminiModel("a b"))
        assertNull(normalizeGeminiModel("x".repeat(80)))
    }
}
