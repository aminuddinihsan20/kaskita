@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun defaultsAreSystemThemeAndNegativeBalanceOff() = runTest {
        val vm = SettingsViewModel(FakeSettingsStore())
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.SYSTEM, allowNegativeBalance = false), vm.state.value)
    }

    @Test
    fun changesAreWrittenToTheStoreAndReflectedInState() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(store)
        advanceUntilIdle()
        vm.onThemeMode(ThemeMode.DARK)
        vm.onAllowNegativeBalance(true)
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.DARK, allowNegativeBalance = true), vm.state.value)
    }
}
