package com.kaskita.lokal.testing

import com.kaskita.lokal.data.backup.BackupCodec
import com.kaskita.lokal.data.backup.BackupFile
import com.kaskita.lokal.data.backup.BackupParseResult
import com.kaskita.lokal.data.backup.BackupService
import com.kaskita.lokal.data.backup.BackupSummary
import com.kaskita.lokal.data.backup.RestoreResult
import com.kaskita.lokal.data.backup.SafetyBackupInfo
import java.io.IOException

/** Programmable [BackupService]; counts calls so tests can prove what did (not) happen. */
class FakeBackupService : BackupService {
    var fileText: String = ""
    var readFailure: IOException? = null
    var exportFailure: IOException? = null
    var restoreResult: RestoreResult? = null
    var exportSummary = BackupSummary(backupCreatedAt = 1L, accountCount = 2, categoryCount = 18, transactionCount = 7)

    var safety: SafetyBackupInfo? = null
    val readLocations = mutableListOf<String>()

    var exportCalls = 0
        private set
    var restoreCalls = 0
        private set
    val restoredFiles = mutableListOf<BackupFile>()

    override suspend fun export(location: String): BackupSummary {
        exportCalls++
        exportFailure?.let { throw it }
        return exportSummary
    }

    override suspend fun latestSafetyBackup(): SafetyBackupInfo? = safety

    override suspend fun read(location: String): BackupParseResult {
        readLocations += location
        readFailure?.let { throw it }
        return BackupCodec.decodeAndValidate(fileText)
    }

    override suspend fun restore(file: BackupFile): RestoreResult {
        restoreCalls++
        restoredFiles += file
        safety = SafetyBackupInfo("file:/safety/new.json", 2_000L) // a restore always leaves a fresh safety copy behind
        return restoreResult ?: RestoreResult.Success(BackupSummary(file.backupCreatedAt, file.accounts.size, file.categories.size, file.transactions.size))
    }
}
