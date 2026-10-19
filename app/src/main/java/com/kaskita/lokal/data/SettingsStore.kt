package com.kaskita.lokal.data

import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * UI preferences plus the two settings that backups carry. Never holds transactions.
 * The AI consent and model are per install: they are not part of a backup, and a restore never touches them.
 */
interface SettingsStore {
    val themeMode: Flow<ThemeMode>
    val allowNegativeBalance: Flow<Boolean>
    val lastBackupAt: Flow<Long?>
    val aiConsent: Flow<Boolean>
    val geminiModel: Flow<String>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAllowNegativeBalance(value: Boolean)
    suspend fun setLastBackupAt(millis: Long)
    suspend fun setAiConsent(value: Boolean)

    /** A blank [model] falls back to [DEFAULT_GEMINI_MODEL]. */
    suspend fun setGeminiModel(model: String)
}
