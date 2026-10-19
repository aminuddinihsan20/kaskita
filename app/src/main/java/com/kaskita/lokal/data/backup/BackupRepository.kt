package com.kaskita.lokal.data.backup

import androidx.room.withTransaction
import com.kaskita.lokal.data.SettingsStore
import com.kaskita.lokal.data.db.AccountEntity
import com.kaskita.lokal.data.db.CategoryEntity
import com.kaskita.lokal.data.db.KasKitaDatabase
import com.kaskita.lokal.data.db.TransactionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class BackupRepository(
    private val db: KasKitaDatabase,
    private val settings: SettingsStore,
    private val appVersionName: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Snapshot of all tables taken inside one DB transaction so the file is internally consistent. */
    suspend fun buildBackup(): BackupFile {
        val preferences = BackupPreferences(settings.themeMode.first(), settings.allowNegativeBalance.first())
        return db.withTransaction {
            BackupFile(
                schemaVersion = BackupCodec.SCHEMA_VERSION,
                backupCreatedAt = now(),
                appVersionName = appVersionName,
                accounts = db.accountDao().getAll().map {
                    BackupAccount(it.id, it.name, it.type, it.openingBalance, it.createdAt, it.isArchived)
                },
                categories = db.categoryDao().getAll().map {
                    BackupCategory(it.id, it.name, it.type, it.iconKey, it.colorKey, it.isDefault, it.isArchived)
                },
                transactions = db.transactionDao().getAll().sortedBy { it.id }.map {
                    BackupTransaction(
                        it.id, it.type, it.amount, it.dateTime, it.categoryId,
                        it.fromAccountId, it.toAccountId, it.note, it.createdAt, it.updatedAt,
                    )
                },
                preferences = preferences,
            )
        }
    }

    /**
     * Replaces all local data with [file]. The file is validated again, a safety copy of the current data is written to
     * [safetyBackupDir], and only then are the tables rewritten inside a single DB transaction (all or nothing).
     */
    suspend fun restore(file: BackupFile, safetyBackupDir: File): RestoreResult {
        BackupCodec.validate(file)?.let { return RestoreResult.Failed(it) }

        try {
            writeSafetyBackup(safetyBackupDir)
        } catch (e: IOException) {
            return RestoreResult.Failed(BackupError.SafetyBackupFailed)
        }

        try {
            db.withTransaction {
                db.transactionDao().deleteAll()
                db.categoryDao().deleteAll()
                db.accountDao().deleteAll()
                db.accountDao().insertAll(
                    file.accounts.map { AccountEntity(it.id, it.name, it.type, it.openingBalance, it.createdAt, it.isArchived) },
                )
                db.categoryDao().insertAll(
                    file.categories.map { CategoryEntity(it.id, it.name, it.type, it.iconKey, it.colorKey, it.isDefault, it.isArchived) },
                )
                db.transactionDao().insertAll(
                    file.transactions.map {
                        TransactionEntity(
                            it.id, it.type, it.amount, it.dateTime, it.categoryId,
                            it.fromAccountId, it.toAccountId, it.note, it.createdAt, it.updatedAt,
                        )
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RestoreResult.Failed(null)
        }

        settings.setThemeMode(file.preferences.themeMode)
        settings.setAllowNegativeBalance(file.preferences.allowNegativeBalance)
        return RestoreResult.Success(BackupCodec.summarize(file))
    }

    private suspend fun writeSafetyBackup(dir: File) = withContext(Dispatchers.IO) {
        val snapshot = buildBackup()
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create safety backup directory")
        SafetyBackupStore.fileFor(dir, snapshot.backupCreatedAt).writeText(BackupCodec.encode(snapshot), Charsets.UTF_8)
        SafetyBackupStore.prune(dir)
    }
}
