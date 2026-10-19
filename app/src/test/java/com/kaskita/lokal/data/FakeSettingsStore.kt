package com.kaskita.lokal.data

import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SettingsStore] for tests. */
class FakeSettingsStore(
    allowNegative: Boolean = false,
    theme: ThemeMode = ThemeMode.SYSTEM,
    lastBackup: Long? = null,
    aiConsent: Boolean = false,
    geminiModel: String = DEFAULT_GEMINI_MODEL,
) : SettingsStore {
    private val _theme = MutableStateFlow(theme)
    private val _allowNegative = MutableStateFlow(allowNegative)
    private val _lastBackupAt = MutableStateFlow(lastBackup)
    private val _aiConsent = MutableStateFlow(aiConsent)
    private val _geminiModel = MutableStateFlow(geminiModel)

    override val themeMode: Flow<ThemeMode> = _theme
    override val allowNegativeBalance: Flow<Boolean> = _allowNegative
    override val lastBackupAt: Flow<Long?> = _lastBackupAt
    override val aiConsent: Flow<Boolean> = _aiConsent
    override val geminiModel: Flow<String> = _geminiModel

    override suspend fun setThemeMode(mode: ThemeMode) { _theme.value = mode }
    override suspend fun setAllowNegativeBalance(value: Boolean) { _allowNegative.value = value }
    override suspend fun setLastBackupAt(millis: Long) { _lastBackupAt.value = millis }
    override suspend fun setAiConsent(value: Boolean) { _aiConsent.value = value }
    override suspend fun setGeminiModel(model: String) { _geminiModel.value = model.trim().ifEmpty { DEFAULT_GEMINI_MODEL } }
}
