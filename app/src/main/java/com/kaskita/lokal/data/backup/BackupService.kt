package com.kaskita.lokal.data.backup

import android.net.Uri
import com.kaskita.lokal.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What the backup screen needs; locations are `Uri.toString()` values from the Storage Access Framework. */
interface BackupService {
    /** Builds a backup, writes it to [location] and records the time. Throws [IOException] if it cannot be written. */
    suspend fun export(location: String): BackupSummary

    /** Reads and fully validates the file at [location]; throws [IOException] if it cannot be read. */
    suspend fun read(location: String): BackupParseResult

    /** The newest copy of the data that was saved automatically right before a restore, if any. */
    suspend fun latestSafetyBackup(): SafetyBackupInfo?

    /** Replaces local data with [file] (which must have come out of [read] as Valid). */
    suspend fun restore(file: BackupFile): RestoreResult
}

class DefaultBackupService(
    private val backups: BackupRepository,
    private val io: BackupFileIo,
    private val settings: SettingsStore,
    private val safetyBackupDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : BackupService {

    override suspend fun export(location: String): BackupSummary {
        val file = backups.buildBackup()
        val json = withContext(Dispatchers.Default) { BackupCodec.encode(file) }
        io.write(Uri.parse(location), json)
        settings.setLastBackupAt(now())
        return BackupCodec.summarize(file)
    }

    override suspend fun read(location: String): BackupParseResult {
        val text = io.read(Uri.parse(location))
        return withContext(Dispatchers.Default) { BackupCodec.decodeAndValidate(text) }
    }

    override suspend fun latestSafetyBackup(): SafetyBackupInfo? =
        withContext(Dispatchers.IO) { SafetyBackupStore.latest(safetyBackupDir) }

    override suspend fun restore(file: BackupFile): RestoreResult = backups.restore(file, safetyBackupDir)
}
