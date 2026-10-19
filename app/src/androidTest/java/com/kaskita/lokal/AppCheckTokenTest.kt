package com.kaskita.lokal

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCheckTokenTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The SDK's own storage (name and key from firebase-appcheck-debug 20.0.0), read the way the SDK reads it. */
    private fun stored(): String? = context
        .getSharedPreferences("com.google.firebase.appcheck.debug.store.${FirebaseApp.getInstance().persistenceKey}", Context.MODE_PRIVATE)
        .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)

    @Test
    fun aSeededTokenIsTheOneTheSdkWillUse() {
        val before = stored()
        try {
            seedAppCheckDebugToken(context, "11111111-2222-3333-4444-555555555555")
            assertEquals("11111111-2222-3333-4444-555555555555", stored())
        } finally {
            if (before != null) seedAppCheckDebugToken(context, before)
        }
    }

    @Test
    fun aBlankTokenLeavesTheSdksOwnOneUntouched() {
        val before = stored()
        seedAppCheckDebugToken(context, "  ")
        assertEquals(before, stored())
    }
}
