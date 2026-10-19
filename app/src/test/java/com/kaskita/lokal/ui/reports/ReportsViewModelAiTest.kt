@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.reports

import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL
import com.kaskita.lokal.data.ai.FakeGeminiClient
import com.kaskita.lokal.data.ai.GeminiError
import com.kaskita.lokal.data.ai.GeminiResult
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.RecapPrompt
import com.kaskita.lokal.domain.SavingsStatus
import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.jakarta
import com.kaskita.lokal.domain.TestData.millis
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class ReportsViewModelAiTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val now = millis(2026, 10, 19, 12)

    private val secretNote = "catatan-rahasia-budi"
    private val accountName = "BCA Pribadi Budi"

    private fun repo() = FakeLedgerRepository(
        accounts = listOf(Account(BANK_ID, accountName, AccountType.BANK, 5_000_000, 0, false), TestData.cash(opening = 0)),
        transactions = listOf(
            TestData.income(1, 5_000_000, BANK_ID, millis(2026, 10, 1)),
            TestData.expense(2, 300_000, CASH_ID, millis(2026, 10, 19, 9)).copy(note = secretNote),
        ),
    )

    private class Rig(val repo: FakeLedgerRepository, val settings: FakeSettingsStore, val gemini: FakeGeminiClient, val vm: ReportsViewModel)

    private fun rig(consent: Boolean = true, gemini: FakeGeminiClient = FakeGeminiClient(), repo: FakeLedgerRepository = repo()): Rig {
        val settings = FakeSettingsStore(aiConsent = consent)
        return Rig(repo, settings, gemini, ReportsViewModel(repo, jakarta, settings, gemini) { now })
    }

    @Test
    fun aiNeedsTheUsersAgreement() = runTest {
        val without = rig(consent = false)
        advanceUntilIdle()
        assertEquals(AiRecapState.NotReady, without.vm.state.value.ai)
        val with = rig(consent = true)
        advanceUntilIdle()
        assertEquals(AiRecapState.Idle, with.vm.state.value.ai)
    }

    @Test
    fun analysingSendsTheModelAndThePromptThenShowsTheText() = runTest {
        val r = rig(gemini = FakeGeminiClient(GeminiResult.Success("Hemat ya")))
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        val call = r.gemini.calls.single()
        assertEquals(DEFAULT_GEMINI_MODEL, call.model)
        assertTrue(call.user.contains("Pengeluaran: Rp300.000"))
        assertEquals(AiRecapState.Done("Hemat ya"), r.vm.state.value.ai)
    }

    @Test
    fun noteAndAccountNamesNeverLeaveThePhone() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        val sent = r.gemini.calls.single().let { it.system + it.user }
        assertFalse(sent.contains(secretNote))
        assertFalse(sent.contains(accountName))
        assertTrue((r.vm.state.value.prompt as RecapPrompt.Ready).user == r.gemini.calls.single().user)
    }

    @Test
    fun nothingIsSentWithoutConsent() = runTest {
        val r = rig(consent = false)
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        assertTrue(r.gemini.calls.isEmpty())
    }

    @Test
    fun doubleTapSendsOnlyOneRequestAndShowsLoadingMeanwhile() = runTest {
        val gate = CompletableDeferred<Unit>()
        val r = rig(gemini = FakeGeminiClient().apply { this.gate = gate })
        advanceUntilIdle()
        r.vm.onAnalyze()
        r.vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(AiRecapState.Loading, r.vm.state.value.ai)
        assertEquals(1, r.gemini.calls.size)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AiRecapState.Done("Analisis"), r.vm.state.value.ai)
        assertEquals(1, r.gemini.calls.size)
    }

    @Test
    fun aFailureIsShownAndRetryingCallsAgain() = runTest {
        val gemini = FakeGeminiClient(GeminiResult.Failure(GeminiError.RATE_LIMITED))
        val r = rig(gemini = gemini)
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(AiRecapState.Failed(GeminiError.RATE_LIMITED), r.vm.state.value.ai)
        gemini.result = GeminiResult.Success("Sekarang berhasil")
        r.vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(2, gemini.calls.size)
        assertEquals(AiRecapState.Done("Sekarang berhasil"), r.vm.state.value.ai)
    }

    @Test
    fun aClientThatCrashesIsAnErrorNotACrashAndNotAStuckSpinner() = runTest {
        val gemini = FakeGeminiClient().apply { failWith = IllegalStateException("boom") }
        val r = rig(gemini = gemini)
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        assertEquals(AiRecapState.Failed(GeminiError.UNKNOWN), r.vm.state.value.ai)
    }

    @Test
    fun aResultStaysWithItsMonthAndNeverLeaksIntoAnother() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        r.vm.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(AiRecapState.NoData, r.vm.state.value.ai) // September has nothing
        r.vm.onNextMonth()
        advanceUntilIdle()
        assertEquals(AiRecapState.Done("Analisis"), r.vm.state.value.ai)
        assertEquals("coming back reuses the answer without another request", 1, r.gemini.calls.size)
    }

    @Test
    fun anyChangeToTheNumbersInvalidatesTheShownAnalysis() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        r.repo.transactions.value = r.repo.transactions.value + TestData.expense(3, 10_000, CASH_ID, millis(2026, 10, 19, 15))
        advanceUntilIdle()
        assertEquals(AiRecapState.Idle, r.vm.state.value.ai)
    }

    @Test
    fun changingTheModelAsksAgainBecauseTheAnswerBelongedToAnotherModel() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        r.settings.setGeminiModel("gemini-2.5-flash")
        advanceUntilIdle()
        assertEquals(AiRecapState.Idle, r.vm.state.value.ai)
        assertEquals("gemini-2.5-flash", r.vm.state.value.aiModel)
    }

    @Test
    fun aMonthWithNothingRecordedCannotBeAnalysed() = runTest {
        val r = rig()
        r.vm.onMonth(YearMonth.of(2026, 3))
        advanceUntilIdle()
        assertEquals(AiRecapState.NoData, r.vm.state.value.ai)
        r.vm.onAnalyze()
        advanceUntilIdle()
        assertTrue(r.gemini.calls.isEmpty())
    }

    @Test
    fun withdrawingConsentHidesTheResult() = runTest {
        val r = rig()
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        r.settings.setAiConsent(false)
        advanceUntilIdle()
        assertEquals(AiRecapState.NotReady, r.vm.state.value.ai)
    }

    @Test
    fun theNextMonthAdviceIsAlwaysAvailableWithoutAnyAiSetup() = runTest {
        val r = rig(consent = false)
        advanceUntilIdle()
        val advice = r.vm.state.value.advice!!
        assertEquals(1_000_000L, advice.savingsTarget)
        assertEquals(4_000_000L, advice.spendingLimit)
        assertEquals(SavingsStatus.ON_TRACK, advice.status)
    }

    @Test
    fun analysingAgainReplacesTheFinishedAnswerOnlyWhenAsked() = runTest {
        val gemini = FakeGeminiClient(GeminiResult.Success("Pertama"))
        val r = rig(gemini = gemini)
        advanceUntilIdle()
        r.vm.onAnalyze()
        advanceUntilIdle()
        gemini.result = GeminiResult.Success("Kedua")
        r.vm.onAnalyze() // a plain tap on a finished answer does nothing
        advanceUntilIdle()
        assertEquals(AiRecapState.Done("Pertama"), r.vm.state.value.ai)
        r.vm.onAnalyze(again = true)
        advanceUntilIdle()
        assertEquals(AiRecapState.Done("Kedua"), r.vm.state.value.ai)
        assertEquals(2, gemini.calls.size)
    }
}
