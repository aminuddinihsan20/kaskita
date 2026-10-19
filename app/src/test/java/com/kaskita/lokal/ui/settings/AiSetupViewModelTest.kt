@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AiSetupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Rig(val settings: FakeSettingsStore, val vm: AiSetupViewModel)

    private fun rig(consent: Boolean = false, model: String = DEFAULT_GEMINI_MODEL): Rig {
        val settings = FakeSettingsStore(aiConsent = consent, geminiModel = model)
        return Rig(settings, AiSetupViewModel(settings))
    }

    @Test
    fun startsFromTheStoredValues() = runTest {
        val r = rig(consent = true, model = "gemini-2.5-flash")
        advanceUntilIdle()
        assertEquals(AiSetupState(consent = true, model = "gemini-2.5-flash"), r.vm.state.value)
    }

    @Test
    fun theFormWaitsForTheStoredValuesInsteadOfShowingPlaceholders() = runTest {
        assertEquals(false, AiSetupViewModel(FakeSettingsStore()).state.value.loaded)
    }

    @Test
    fun savingStoresTheModelAndConsent() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onSave(consent = true, modelInput = " gemini-2.5-flash ")
        advanceUntilIdle()
        assertEquals("gemini-2.5-flash", r.settings.geminiModel.first())
        assertEquals(true, r.settings.aiConsent.first())
        assertEquals(true, r.vm.state.value.saved)
        assertNull(r.vm.state.value.error)
    }

    @Test
    fun consentIsRequiredToTurnTheFeatureOn() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onSave(consent = false, modelInput = "")
        advanceUntilIdle()
        assertEquals(false, r.settings.aiConsent.first())
        assertEquals(AiSetupError.CONSENT_REQUIRED, r.vm.state.value.error)
        assertEquals(false, r.vm.state.value.saved)
    }

    @Test
    fun anInvalidModelIsRejectedAndABlankOneMeansTheDefault() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onSave(consent = true, modelInput = "gemini/../evil")
        advanceUntilIdle()
        assertEquals(AiSetupError.MODEL_INVALID, r.vm.state.value.error)
        assertEquals(false, r.settings.aiConsent.first())

        r.vm.onSave(consent = true, modelInput = "  ")
        advanceUntilIdle()
        assertEquals(DEFAULT_GEMINI_MODEL, r.settings.geminiModel.first())
        assertEquals(true, r.vm.state.value.saved)
    }

    @Test
    fun withdrawingConsentStopsEverything() = runTest {
        val r = rig(consent = true)
        advanceUntilIdle()
        r.vm.onWithdrawConsent()
        advanceUntilIdle()
        assertEquals(false, r.settings.aiConsent.first())
        assertEquals(false, r.vm.state.value.ready)
    }

    @Test
    fun theDialogCanBeReopenedCleanly() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onSave(consent = false, modelInput = "")
        advanceUntilIdle()
        assertEquals(AiSetupError.CONSENT_REQUIRED, r.vm.state.value.error)
        r.vm.onDialogClosed()
        advanceUntilIdle()
        assertNull(r.vm.state.value.error)
        assertEquals(false, r.vm.state.value.saved)
    }
}
