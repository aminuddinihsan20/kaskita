@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.data.backup.BackupAccount
import com.kaskita.lokal.data.backup.BackupCategory
import com.kaskita.lokal.data.backup.BackupCodec
import com.kaskita.lokal.data.backup.BackupError
import com.kaskita.lokal.data.backup.BackupFile
import com.kaskita.lokal.data.backup.BackupPreferences
import com.kaskita.lokal.data.backup.BackupTransaction
import com.kaskita.lokal.data.backup.RestoreResult
import com.kaskita.lokal.data.backup.SafetyBackupInfo
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.testing.FakeBackupService
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.ZoneId

class BackupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val validFile = BackupFile(
        schemaVersion = 1,
        backupCreatedAt = 1_700_000_000_000,
        appVersionName = "1.0.0",
        accounts = listOf(BackupAccount(1, "Cash", AccountType.CASH, 0, 1, false)),
        categories = listOf(BackupCategory(1, "Gaji", TransactionType.INCOME, "payments", "green", true, false)),
        transactions = listOf(BackupTransaction(1, TransactionType.INCOME, 1_000, 5_000, 1, null, 1, null, 1, 1)),
        preferences = BackupPreferences(ThemeMode.SYSTEM, false),
    )
    private val validJson = BackupCodec.encode(validFile)

    private fun vm(service: FakeBackupService, settings: FakeSettingsStore = FakeSettingsStore()) =
        BackupViewModel(service, settings)

    @Test
    fun invalidFileShowsErrorAndNeverCallsRestore() = runTest {
        val service = FakeBackupService().apply { fileText = "ini bukan backup" }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()

        assertEquals(BackupPhase.Failed(BackupFailure.Invalid(BackupError.Malformed)), vm.state.value.phase)
        vm.onConfirmRestore() // even a stray confirm cannot restore a file that never validated
        advanceUntilIdle()
        assertEquals(0, service.restoreCalls)
    }

    @Test
    fun emptyFileIsReportedAsEmpty() = runTest {
        val service = FakeBackupService().apply { fileText = "" }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        assertEquals(BackupPhase.Failed(BackupFailure.Invalid(BackupError.Empty)), vm.state.value.phase)
    }

    @Test
    fun newerVersionIsReportedAsUnsupported() = runTest {
        val service = FakeBackupService().apply { fileText = BackupCodec.encode(validFile.copy(schemaVersion = 9)) }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        assertEquals(BackupPhase.Failed(BackupFailure.Invalid(BackupError.UnsupportedVersion(9, 1))), vm.state.value.phase)
    }

    @Test
    fun unreadableFileShowsErrorAndNeverCallsRestore() = runTest {
        val service = FakeBackupService().apply { readFailure = IOException("permission denied") }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        assertEquals(BackupPhase.Failed(BackupFailure.Unreadable), vm.state.value.phase)
        assertEquals(0, service.restoreCalls)
    }

    @Test
    fun restoreOnlyAfterExplicitConfirm() = runTest {
        val service = FakeBackupService().apply { fileText = validJson }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()

        val preview = vm.state.value.phase as BackupPhase.RestorePreview
        assertEquals(1, preview.summary.transactionCount)
        assertEquals(0, service.restoreCalls) // previewing changes nothing

        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(1, service.restoreCalls)
        assertEquals(validFile, service.restoredFiles.single())
    }

    @Test
    fun cancelPreviewLeavesDataUntouched() = runTest {
        val service = FakeBackupService().apply { fileText = validJson }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        vm.onCancelRestore()
        advanceUntilIdle()
        assertEquals(BackupPhase.Idle, vm.state.value.phase)
        assertEquals(0, service.restoreCalls)
        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(0, service.restoreCalls)
    }

    @Test
    fun confirmWithoutPreviewIsIgnored() = runTest {
        val service = FakeBackupService()
        val vm = vm(service)
        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(0, service.restoreCalls)
        assertEquals(BackupPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun successShowsRestoredCounts() = runTest {
        val service = FakeBackupService().apply { fileText = validJson }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        vm.onConfirmRestore()
        advanceUntilIdle()
        val done = vm.state.value.phase as BackupPhase.RestoreDone
        assertEquals(1, done.summary.accountCount)
        assertEquals(1, done.summary.transactionCount)
    }

    @Test
    fun doubleConfirmRestoresOnce() = runTest {
        val service = FakeBackupService().apply { fileText = validJson }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        vm.onConfirmRestore()
        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(1, service.restoreCalls)
    }

    @Test
    fun databaseRejectionIsReportedAsFailure() = runTest {
        val service = FakeBackupService().apply {
            fileText = validJson
            restoreResult = RestoreResult.Failed(null)
        }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(BackupPhase.Failed(BackupFailure.RestoreRejected(null)), vm.state.value.phase)
    }

    @Test
    fun pickerCancelledDoesNothing() = runTest {
        val service = FakeBackupService()
        val vm = vm(service)
        vm.onRestoreFileChosen(null)
        vm.onBackupTargetChosen(null, "x.json")
        advanceUntilIdle()
        assertEquals(BackupPhase.Idle, vm.state.value.phase)
        assertEquals(0, service.exportCalls)
    }

    @Test
    fun backupReportsFileNameAndTransactionCount() = runTest {
        val service = FakeBackupService()
        val vm = vm(service)
        vm.onBackupTargetChosen("content://target", "kaskita-backup-20261019.json")
        advanceUntilIdle()
        assertEquals(BackupPhase.BackupDone("kaskita-backup-20261019.json", 7), vm.state.value.phase)
        assertEquals(1, service.exportCalls)
    }

    @Test
    fun backupWriteFailureIsReported() = runTest {
        val service = FakeBackupService().apply { exportFailure = IOException("disk full") }
        val vm = vm(service)
        vm.onBackupTargetChosen("content://target", "x.json")
        advanceUntilIdle()
        assertEquals(BackupPhase.Failed(BackupFailure.WriteFailed), vm.state.value.phase)
    }

    @Test
    fun lastBackupTimeComesFromSettings() = runTest {
        val vm = vm(FakeBackupService(), FakeSettingsStore(lastBackup = 123L))
        advanceUntilIdle()
        assertEquals(123L, vm.state.value.lastBackupAt)
    }

    @Test
    fun dismissingAResultReturnsToIdle() = runTest {
        val service = FakeBackupService().apply { fileText = "x" }
        val vm = vm(service)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is BackupPhase.Failed)
        vm.onDismissResult()
        advanceUntilIdle()
        assertEquals(BackupPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun suggestedFileNameUsesLocalDate() {
        val millis = java.time.LocalDateTime.of(2026, 10, 19, 23, 30).atZone(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
        assertEquals("kaskita-backup-20261019.json", suggestedBackupFileName(millis, ZoneId.of("Asia/Jakarta")))
    }

    // Final review finding 3: the safety copy written before every restore must be usable by the person
    @Test
    fun noSafetyCopyMeansNothingIsOffered() = runTest {
        val vm = vm(FakeBackupService())
        advanceUntilIdle()
        assertNull(vm.state.value.safetyBackup)
    }

    @Test
    fun anExistingSafetyCopyIsOffered() = runTest {
        val service = FakeBackupService().apply { safety = SafetyBackupInfo("file:/safety/a.json", 111L) }
        val vm = vm(service)
        advanceUntilIdle()
        assertEquals(SafetyBackupInfo("file:/safety/a.json", 111L), vm.state.value.safetyBackup)
    }

    @Test
    fun restoringTheSafetyCopyGoesThroughTheSameValidateAndConfirmFlow() = runTest {
        val service = FakeBackupService().apply {
            safety = SafetyBackupInfo("file:/safety/a.json", 111L)
            fileText = validJson
        }
        val vm = vm(service)
        advanceUntilIdle()

        vm.onRestoreSafetyBackup()
        advanceUntilIdle()
        assertEquals(listOf("file:/safety/a.json"), service.readLocations)
        assertTrue(vm.state.value.phase is BackupPhase.RestorePreview)
        assertEquals(0, service.restoreCalls) // nothing happens before the explicit confirmation

        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(1, service.restoreCalls)
    }

    @Test
    fun aCorruptSafetyCopyIsReportedLikeAnyOtherBadFile() = runTest {
        val service = FakeBackupService().apply {
            safety = SafetyBackupInfo("file:/safety/a.json", 111L)
            fileText = "rusak"
        }
        val vm = vm(service)
        advanceUntilIdle()
        vm.onRestoreSafetyBackup()
        advanceUntilIdle()
        assertEquals(BackupPhase.Failed(BackupFailure.Invalid(BackupError.Malformed)), vm.state.value.phase)
        assertEquals(0, service.restoreCalls)
    }

    @Test
    fun theOfferedSafetyCopyIsRefreshedAfterARestore() = runTest {
        val service = FakeBackupService().apply { fileText = validJson }
        val vm = vm(service)
        advanceUntilIdle()
        assertNull(vm.state.value.safetyBackup)
        vm.onRestoreFileChosen("content://picked")
        advanceUntilIdle()
        vm.onConfirmRestore()
        advanceUntilIdle()
        assertEquals(SafetyBackupInfo("file:/safety/new.json", 2_000L), vm.state.value.safetyBackup)
    }

    @Test
    fun withoutASafetyCopyTheRestoreActionDoesNothing() = runTest {
        val service = FakeBackupService()
        val vm = vm(service)
        advanceUntilIdle()
        vm.onRestoreSafetyBackup()
        advanceUntilIdle()
        assertEquals(BackupPhase.Idle, vm.state.value.phase)
        assertTrue(service.readLocations.isEmpty())
    }
}
