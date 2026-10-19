package com.kaskita.lokal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kaskita_settings")

class DataStoreSettingsStore(private val store: DataStore<Preferences>) : SettingsStore {

    override val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }
    override val allowNegativeBalance: Flow<Boolean> = store.data.map { it[ALLOW_NEGATIVE] ?: false }
    override val lastBackupAt: Flow<Long?> = store.data.map { it[LAST_BACKUP] }
    override val aiConsent: Flow<Boolean> = store.data.map { it[AI_CONSENT] ?: false }
    override val geminiModel: Flow<String> = store.data.map { it[GEMINI_MODEL]?.ifBlank { null } ?: DEFAULT_GEMINI_MODEL }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    override suspend fun setAllowNegativeBalance(value: Boolean) {
        store.edit { it[ALLOW_NEGATIVE] = value }
    }

    override suspend fun setLastBackupAt(millis: Long) {
        store.edit { it[LAST_BACKUP] = millis }
    }

    override suspend fun setAiConsent(value: Boolean) {
        store.edit { it[AI_CONSENT] = value }
    }

    override suspend fun setGeminiModel(model: String) {
        store.edit { it[GEMINI_MODEL] = model.trim().ifEmpty { DEFAULT_GEMINI_MODEL } }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme_mode")
        private val ALLOW_NEGATIVE = booleanPreferencesKey("allow_negative_balance")
        private val LAST_BACKUP = longPreferencesKey("last_backup_at")
        private val AI_CONSENT = booleanPreferencesKey("ai_consent")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")

        fun create(context: Context) = DataStoreSettingsStore(context.applicationContext.settingsDataStore)

        fun forFile(file: File) = DataStoreSettingsStore(PreferenceDataStoreFactory.create { file })
    }
}
