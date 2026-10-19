package com.kaskita.lokal.data.backup

import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.TransactionType
import kotlinx.serialization.Serializable

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val type: AccountType,
    val openingBalance: Long,
    val createdAt: Long,
    val isArchived: Boolean,
)

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorKey: String,
    val isDefault: Boolean,
    val isArchived: Boolean,
)

@Serializable
data class BackupTransaction(
    val id: Long,
    val type: TransactionType,
    val amount: Long,
    val dateTime: Long,
    val categoryId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupPreferences(val themeMode: ThemeMode, val allowNegativeBalance: Boolean)

/** Everything needed to rebuild the app's data on a clean install. */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val backupCreatedAt: Long,
    val appVersionName: String,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val preferences: BackupPreferences,
)

data class BackupSummary(
    val backupCreatedAt: Long,
    val accountCount: Int,
    val categoryCount: Int,
    val transactionCount: Int,
)

sealed interface BackupError {
    data object Empty : BackupError
    data object Malformed : BackupError
    data class UnsupportedVersion(val found: Int, val supported: Int) : BackupError
    data class DuplicateId(val entity: String, val id: Long) : BackupError
    data class MissingAccount(val transactionId: Long, val accountId: Long) : BackupError
    data class MissingCategory(val transactionId: Long, val categoryId: Long) : BackupError
    data class InvalidTransaction(val transactionId: Long) : BackupError
    data object SafetyBackupFailed : BackupError
}

sealed interface BackupParseResult {
    data class Valid(val file: BackupFile, val summary: BackupSummary) : BackupParseResult
    data class Invalid(val error: BackupError) : BackupParseResult
}

sealed interface RestoreResult {
    data class Success(val summary: BackupSummary) : RestoreResult

    /** [error] is null when the database itself rejected the write (nothing was changed). */
    data class Failed(val error: BackupError?) : RestoreResult
}
