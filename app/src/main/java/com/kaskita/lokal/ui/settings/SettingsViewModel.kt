package com.kaskita.lokal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.ui.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val themeMode: ThemeMode, val allowNegativeBalance: Boolean)

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(settings.themeMode, settings.allowNegativeBalance, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(ThemeMode.SYSTEM, allowNegativeBalance = false))

    fun onThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun onAllowNegativeBalance(value: Boolean) {
        viewModelScope.launch { settings.setAllowNegativeBalance(value) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { SettingsViewModel(container.settings) } }
    }
}
