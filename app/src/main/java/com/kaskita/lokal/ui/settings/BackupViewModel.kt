package com.kaskita.lokal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.backup.BackupError
import com.kaskita.lokal.data.backup.BackupFile
import com.kaskita.lokal.data.backup.BackupParseResult
import com.kaskita.lokal.data.backup.BackupService
import com.kaskita.lokal.data.backup.BackupSummary
import com.kaskita.lokal.data.backup.RestoreResult
import com.kaskita.lokal.data.backup.SafetyBackupInfo
import com.kaskita.lokal.ui.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface BackupFailure {
    /** The chosen file is not a usable backup (empty, corrupt, unsupported version, inconsistent). */
    data class Invalid(val error: BackupError) : BackupFailure
    data object Unreadable : BackupFailure
    data object WriteFailed : BackupFailure
    data class RestoreRejected(val error: BackupError?) : BackupFailure
}

sealed interface BackupPhase {
    data object Idle : BackupPhase
    data object Working : BackupPhase
    data class BackupDone(val fileName: String, val transactionCount: Int) : BackupPhase

    /** A validated file waiting for the user to choose "Ganti data lokal" or "Batalkan". */
    data class RestorePreview(val summary: BackupSummary, val file: BackupFile) : BackupPhase
    data class RestoreDone(val summary: BackupSummary) : BackupPhase
    data class Failed(val failure: BackupFailure) : BackupPhase
}

data class BackupUiState(
    val phase: BackupPhase = BackupPhase.Idle,
    val lastBackupAt: Long? = null,
    val safetyBackup: SafetyBackupInfo? = null,
)

private val fileDate = DateTimeFormatter.ofPattern("yyyyMMdd") // not BASIC_ISO_DATE: that one appends the zone offset

fun suggestedBackupFileName(millis: Long, zone: ZoneId): String =
    "kaskita-backup-" + fileDate.format(Instant.ofEpochMilli(millis).atZone(zone)) + ".json"

class BackupViewModel(
    private val service: BackupService,
    settings: SettingsStore,
) : ViewModel() {

    private val phase = MutableStateFlow<BackupPhase>(BackupPhase.Idle)

    private val safety = MutableStateFlow<SafetyBackupInfo?>(null)

    val state: StateFlow<BackupUiState> = combine(phase, settings.lastBackupAt, safety, ::BackupUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    init {
        refreshSafetyBackup()
    }

    private fun refreshSafetyBackup() {
        viewModelScope.launch {
            safety.value = try {
                service.latestSafetyBackup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Restores the copy saved before the last restore; same validate → preview → confirm flow as any file. */
    fun onRestoreSafetyBackup() {
        val info = safety.value ?: return
        onRestoreFileChosen(info.location)
    }

    /** [location] is null when the user dismissed the system file picker. */
    fun onBackupTargetChosen(location: String?, fileName: String) {
        if (location == null || !startWork()) return
        viewModelScope.launch {
            phase.value = try {
                BackupPhase.BackupDone(fileName, service.export(location).transactionCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                BackupPhase.Failed(BackupFailure.WriteFailed)
            }
        }
    }

    /** Reads and validates only; nothing is changed until [onConfirmRestore]. */
    fun onRestoreFileChosen(location: String?) {
        if (location == null || !startWork()) return
        viewModelScope.launch {
            phase.value = try {
                when (val parsed = service.read(location)) {
                    is BackupParseResult.Valid -> BackupPhase.RestorePreview(parsed.summary, parsed.file)
                    is BackupParseResult.Invalid -> BackupPhase.Failed(BackupFailure.Invalid(parsed.error))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                BackupPhase.Failed(BackupFailure.Unreadable)
            }
        }
    }

    /** Only acts on a previewed, validated file and only once. */
    fun onConfirmRestore() {
        val preview = phase.value as? BackupPhase.RestorePreview ?: return
        if (!phase.compareAndSet(preview, BackupPhase.Working)) return
        viewModelScope.launch {
            phase.value = try {
                when (val result = service.restore(preview.file)) {
                    is RestoreResult.Success -> BackupPhase.RestoreDone(result.summary)
                    is RestoreResult.Failed -> BackupPhase.Failed(BackupFailure.RestoreRejected(result.error))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupPhase.Failed(BackupFailure.RestoreRejected(null))
            }
            refreshSafetyBackup()
        }
    }

    fun onCancelRestore() {
        if (phase.value is BackupPhase.RestorePreview) phase.value = BackupPhase.Idle
    }

    fun onDismissResult() {
        val current = phase.value
        if (current is BackupPhase.BackupDone || current is BackupPhase.RestoreDone || current is BackupPhase.Failed) {
            phase.value = BackupPhase.Idle
        }
    }

    private fun startWork(): Boolean {
        val current = phase.value
        if (current is BackupPhase.Working) return false
        return phase.compareAndSet(current, BackupPhase.Working)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { BackupViewModel(container.backupService, container.settings) }
        }
    }
}
