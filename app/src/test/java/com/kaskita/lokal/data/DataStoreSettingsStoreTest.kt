package com.kaskita.lokal.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsStoreTest {
    private fun store(): Pair<DataStoreSettingsStore, File> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb")
        return DataStoreSettingsStore.forFile(file) to file
    }

    @Test
    fun defaultsAreSystemThemeNegativeBalanceOffAndNoBackup() = runBlocking {
        val (s, file) = store()
        assertEquals(ThemeMode.SYSTEM, s.themeMode.first())
        assertEquals(false, s.allowNegativeBalance.first())
        assertNull(s.lastBackupAt.first())
        file.delete()
        Unit
    }

    @Test
    fun valuesRoundTrip() = runBlocking {
        val (s, file) = store()
        s.setThemeMode(ThemeMode.DARK)
        s.setAllowNegativeBalance(true)
        s.setLastBackupAt(1_700_000_000_000)
        assertEquals(ThemeMode.DARK, s.themeMode.first())
        assertEquals(true, s.allowNegativeBalance.first())
        assertEquals(1_700_000_000_000, s.lastBackupAt.first())
        file.delete()
        Unit
    }

    @Test
    fun aiDefaultsAreNoConsentAndTheDefaultModel() = runBlocking {
        val (s, file) = store()
        assertEquals(false, s.aiConsent.first())
        assertEquals(DEFAULT_GEMINI_MODEL, s.geminiModel.first())
        file.delete()
        Unit
    }

    @Test
    fun aiSettingsRoundTripAndABlankModelFallsBackToTheDefault() = runBlocking {
        val (s, file) = store()
        s.setAiConsent(true)
        s.setGeminiModel("gemini-2.5-flash")
        assertEquals(true, s.aiConsent.first())
        assertEquals("gemini-2.5-flash", s.geminiModel.first())
        s.setGeminiModel("   ")
        assertEquals(DEFAULT_GEMINI_MODEL, s.geminiModel.first())
        file.delete()
        Unit
    }
}
