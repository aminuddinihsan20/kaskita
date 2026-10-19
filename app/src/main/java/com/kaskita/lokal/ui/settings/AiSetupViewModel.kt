package com.kaskita.lokal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import com.kaskita.lokal.data.ai.normalizeGeminiModel
import com.kaskita.lokal.ui.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiSetupError { CONSENT_REQUIRED, MODEL_INVALID }

data class AiSetupState(
    val consent: Boolean = false,
    val model: String = DEFAULT_GEMINI_MODEL,
    val error: AiSetupError? = null,
    /** True once a save went through, so the dialog can close. */
    val saved: Boolean = false,
    /** False only for the placeholder before the stored settings are read; the dialog waits for it. */
    val loaded: Boolean = true,
) {
    val ready: Boolean get() = consent
}

/** Agreement and model name for the Gemini recap analysis (through Firebase AI Logic). Shared by Settings and Laporan. */
class AiSetupViewModel(private val settings: SettingsStore) : ViewModel() {

    private val outcome = MutableStateFlow<Pair<AiSetupError?, Boolean>>(null to false)

    val state: StateFlow<AiSetupState> = combine(settings.aiConsent, settings.geminiModel, outcome) { consent, model, (error, saved) ->
        AiSetupState(consent, model, error, saved)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiSetupState(loaded = false))

    /** Turning the feature on needs the user's agreement and a plain model name (blank means the default). */
    fun onSave(consent: Boolean, modelInput: String) {
        viewModelScope.launch {
            val model = if (modelInput.isBlank()) DEFAULT_GEMINI_MODEL else normalizeGeminiModel(modelInput)
            val error = when {
                !consent -> AiSetupError.CONSENT_REQUIRED
                model == null -> AiSetupError.MODEL_INVALID
                else -> null
            }
            if (error != null || model == null) {
                outcome.value = error to false
                return@launch
            }
            settings.setGeminiModel(model)
            settings.setAiConsent(true)
            outcome.value = null to true
        }
    }

    /** Stops all sending until the user agrees again. */
    fun onWithdrawConsent() {
        viewModelScope.launch { settings.setAiConsent(false) }
    }

    fun onDialogClosed() {
        outcome.update { null to false }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { AiSetupViewModel(container.settings) } }
    }
}
