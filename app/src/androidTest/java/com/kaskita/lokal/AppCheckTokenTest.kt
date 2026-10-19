package com.kaskita.lokal

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCheckTokenTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aSeededTokenIsTheOneTheSdkAndTheDialogWillUse() {
        val before = appCheckDebugToken(context)
        try {
            seedAppCheckDebugToken(context, "11111111-2222-3333-4444-555555555555")
            assertEquals("11111111-2222-3333-4444-555555555555", appCheckDebugToken(context))
        } finally {
            if (before != null) seedAppCheckDebugToken(context, before)
        }
    }

    @Test
    fun aBlankTokenLeavesTheSdksOwnOneUntouched() {
        val before = appCheckDebugToken(context)
        seedAppCheckDebugToken(context, "  ")
        assertEquals(before, appCheckDebugToken(context))
        assertNotNull(context)
    }
}
