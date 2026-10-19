package com.kaskita.lokal

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import com.kaskita.lokal.data.ai.GeminiError
import com.kaskita.lokal.domain.RecapAdvice
import com.kaskita.lokal.domain.RecapPrompt
import com.kaskita.lokal.domain.SavingsStatus
import com.kaskita.lokal.ui.reports.AdviceCard
import com.kaskita.lokal.ui.reports.AiCard
import com.kaskita.lokal.ui.reports.AiPreviewDialog
import com.kaskita.lokal.ui.reports.AiRecapState
import com.kaskita.lokal.ui.reports.ReportsUiState
import com.kaskita.lokal.ui.settings.AiSetupDialog
import com.kaskita.lokal.ui.settings.AiSetupViewModel
import com.kaskita.lokal.ui.theme.KasKitaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** The recap cards and the setup dialog rendered on a device, driven by hand-built state. */
@RunWith(AndroidJUnit4::class)
class AiRecapUiTest {
    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val ready = RecapPrompt.Ready("SISTEM", "Periode: Oktober 2026\nPengeluaran: Rp300.000", "k")

    private fun state(ai: AiRecapState, advice: RecapAdvice? = null) =
        ReportsUiState(month = YearMonth.of(2026, 10), advice = advice, prompt = ready, ai = ai, aiModel = "gemini-3.8-flash", isLoading = false)

    private fun showCard(ai: AiRecapState, onSetup: () -> Unit = {}, onAnalyze: () -> Unit = {}, onAgain: () -> Unit = {}, onShowCode: () -> Unit = {}) {
        rule.setContent {
            KasKitaTheme(ThemeMode.LIGHT) { AiCard(state(ai), "Oktober 2026", onSetup, onAnalyze, onAgain, onPreview = {}, onShowCode = onShowCode) }
        }
    }

    @Test
    fun notReadyOffersSetupOnly() {
        var setup = 0
        showCard(AiRecapState.NotReady, onSetup = { setup++ })
        rule.onNodeWithText(str(R.string.ai_not_ready_body)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_setup)).performClick()
        assertEquals(1, setup)
        assertTrue(rule.onAllNodesWithText(str(R.string.ai_analyze)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun idleShowsAnalyseThePreviewLinkAndTheModel() {
        var analyse = 0
        showCard(AiRecapState.Idle, onAnalyze = { analyse++ })
        rule.onNodeWithText(str(R.string.ai_preview_link)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_model_label, "gemini-3.8-flash")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_analyze)).performClick()
        assertEquals(1, analyse)
    }

    @Test
    fun loadingShowsAProgressMessageAndNoButtons() {
        showCard(AiRecapState.Loading)
        rule.onNodeWithText(str(R.string.ai_loading)).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(str(R.string.ai_analyze)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun doneRendersHeadingsAndBulletsWithoutMarkdownSymbols() {
        var again = 0
        showCard(
            AiRecapState.Done("## Ringkasan bulan ini\n**Pengeluaran** terkendali.\n* Pertahankan\nSaran menabung bulan depan:\n- Sisihkan Rp1.000.000."),
            onAgain = { again++ },
        )
        rule.onNodeWithText("Ringkasan bulan ini").assertIsDisplayed()
        rule.onNodeWithText("Pengeluaran terkendali.").assertIsDisplayed()
        rule.onNodeWithText("Saran menabung bulan depan").assertIsDisplayed()
        rule.onNodeWithText("Sisihkan Rp1.000.000.").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_done_footer)).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("##", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("**", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText(str(R.string.ai_reanalyze)).performClick()
        assertEquals(1, again)
    }

    @Test
    fun aDeniedRequestOffersRetryAndAWrongModelOffersTheSettings() {
        var analyse = 0
        showCard(AiRecapState.Failed(GeminiError.DENIED), onAnalyze = { analyse++ })
        rule.onNodeWithText(str(R.string.ai_err_denied)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_retry)).performClick()
        assertEquals(1, analyse)
        assertTrue(rule.onAllNodesWithText(str(R.string.ai_change_settings)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aDeniedRequestOffersTheDeviceCodeSoTheOwnerCanRegisterThePhone() {
        var shown = 0
        showCard(AiRecapState.Failed(GeminiError.DENIED), onShowCode = { shown++ })
        rule.onNodeWithText(str(R.string.appcheck_show)).performClick()
        assertEquals(1, shown)
    }

    @Test
    fun theCodeDialogShowsTheCodeAndCopiesIt() {
        var copied: String? = null
        rule.setContent { KasKitaTheme(ThemeMode.LIGHT) { com.kaskita.lokal.ui.settings.AppCheckCodeContent("abc-123", onCopy = { copied = it }, onDismiss = {}) } }
        rule.onNodeWithText("abc-123").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.appcheck_copy)).performClick()
        assertEquals("abc-123", copied)
        rule.onNodeWithText(str(R.string.appcheck_copied)).assertIsDisplayed()
    }

    @Test
    fun theCodeDialogSaysSoWhenThereIsNoCodeYet() {
        rule.setContent { KasKitaTheme(ThemeMode.LIGHT) { com.kaskita.lokal.ui.settings.AppCheckCodeContent(null, onCopy = {}, onDismiss = {}) } }
        rule.onNodeWithText(str(R.string.appcheck_missing)).assertIsDisplayed()
    }

    @Test
    fun aMissingModelOffersTheSettings() {
        var setup = 0
        showCard(AiRecapState.Failed(GeminiError.MODEL_NOT_FOUND), onSetup = { setup++ })
        rule.onNodeWithText(str(R.string.ai_err_model)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_change_settings)).performClick()
        assertEquals(1, setup)
    }

    @Test
    fun aNetworkFailureOnlyOffersRetry() {
        showCard(AiRecapState.Failed(GeminiError.NETWORK))
        rule.onNodeWithText(str(R.string.ai_err_network)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.ai_retry)).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText(str(R.string.ai_change_settings)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theAdviceCardStatesTargetLimitAndTheDeficitWording() {
        val advice = RecapAdvice(5_000_000, false, 10, 500_000, 4_500_000, SavingsStatus.DEFICIT, 0, 1_500_000)
        rule.setContent {
            KasKitaTheme(ThemeMode.LIGHT) {
                AdviceCard(state(AiRecapState.NotReady, advice).copy(summary = com.kaskita.lokal.domain.PeriodSummary(5_000_000, 6_000_000, 0, 0)))
            }
        }
        rule.onNodeWithText("Rp500.000").assertIsDisplayed()
        rule.onNodeWithText("Rp4.500.000").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.rp_advice_deficit, "Rp1.000.000", "Rp1.500.000")).assertIsDisplayed()
    }

    @Test
    fun theAdviceCardSaysSoWhenThereIsNoIncomeToBaseItOn() {
        rule.setContent { KasKitaTheme(ThemeMode.LIGHT) { AdviceCard(state(AiRecapState.NotReady, advice = null)) } }
        rule.onNodeWithText(str(R.string.rp_advice_none)).assertIsDisplayed()
    }

    @Test
    fun thePreviewShowsExactlyTheTextThatIsSent() {
        rule.setContent { KasKitaTheme(ThemeMode.LIGHT) { AiPreviewDialog(ready, onDismiss = {}) } }
        rule.onNodeWithText(str(R.string.ai_preview_title)).assertIsDisplayed()
        rule.onNodeWithText(ready.user).assertIsDisplayed()
    }

    private fun dump(node: androidx.compose.ui.semantics.SemanticsNode): String {
        val text = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }
        val toggle = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState)
        return "[" + listOfNotNull(text, toggle?.let { "toggle=$it" }).joinToString(" ") + node.children.joinToString("") { dump(it) } + "]"
    }

    // --- setup dialog

    private class Settings(consent: Boolean = false) : SettingsStore {
        val consentFlow = MutableStateFlow(consent)
        val modelFlow = MutableStateFlow(DEFAULT_GEMINI_MODEL)
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.LIGHT)
        override val allowNegativeBalance: Flow<Boolean> = MutableStateFlow(false)
        override val lastBackupAt: Flow<Long?> = MutableStateFlow(null)
        override val aiConsent: Flow<Boolean> = consentFlow
        override val geminiModel: Flow<String> = modelFlow
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setAllowNegativeBalance(value: Boolean) = Unit
        override suspend fun setLastBackupAt(millis: Long) = Unit
        override suspend fun setAiConsent(value: Boolean) { consentFlow.value = value }
        override suspend fun setGeminiModel(model: String) { modelFlow.value = model }
    }

    @Test
    fun theSetupDialogRejectsASaveWithoutConsentThenStoresItOnceAgreed() {
        val settings = Settings()
        val vm = AiSetupViewModel(settings)
        var closed = 0
        rule.setContent { KasKitaTheme(ThemeMode.LIGHT) { AiSetupDialog(vm, onDismiss = { closed++ }) } }

        rule.onNodeWithText(str(R.string.ai_setup_save)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.ai_setup_err_consent)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(false, settings.consentFlow.value)
        assertEquals(0, closed)

        rule.onNodeWithText(str(R.string.ai_setup_consent)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.ai_setup_save)).performClick()
        rule.waitUntil(5_000) { closed > 0 }
        assertEquals(true, settings.consentFlow.value)
    }
}
