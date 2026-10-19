package com.kaskita.lokal.data.backup

import com.kaskita.lokal.domain.MAX_AMOUNT
import com.kaskita.lokal.domain.TransactionType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object BackupCodec {
    const val SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Parses and fully validates [text]. Nothing outside this function should trust a backup it has not returned as Valid. */
    fun decodeAndValidate(text: String): BackupParseResult {
        if (text.isBlank()) return BackupParseResult.Invalid(BackupError.Empty)

        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return BackupParseResult.Invalid(BackupError.Malformed)
        } catch (_: IllegalArgumentException) {
            return BackupParseResult.Invalid(BackupError.Malformed)
        }
        val obj = root as? JsonObject ?: return BackupParseResult.Invalid(BackupError.Malformed)

        // Read the version before the rest so a newer file is reported as "unsupported", not "corrupt".
        val version = (obj["schemaVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            ?: return BackupParseResult.Invalid(BackupError.Malformed)
        if (version > SCHEMA_VERSION) {
            return BackupParseResult.Invalid(BackupError.UnsupportedVersion(version, SCHEMA_VERSION))
        }

        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), obj)
        } catch (_: SerializationException) {
            return BackupParseResult.Invalid(BackupError.Malformed)
        } catch (_: IllegalArgumentException) {
            return BackupParseResult.Invalid(BackupError.Malformed)
        }

        validate(file)?.let { return BackupParseResult.Invalid(it) }
        return BackupParseResult.Valid(file, summarize(file))
    }

    fun summarize(file: BackupFile) = BackupSummary(
        backupCreatedAt = file.backupCreatedAt,
        accountCount = file.accounts.size,
        categoryCount = file.categories.size,
        transactionCount = file.transactions.size,
    )

    /** Returns the first problem found, or null when [file] is safe to write to the database. */
    fun validate(file: BackupFile): BackupError? {
        if (file.schemaVersion > SCHEMA_VERSION) return BackupError.UnsupportedVersion(file.schemaVersion, SCHEMA_VERSION)
        if (file.schemaVersion < 1) return BackupError.Malformed

        duplicate("account", file.accounts.map { it.id })?.let { return it }
        duplicate("category", file.categories.map { it.id })?.let { return it }
        duplicate("transaction", file.transactions.map { it.id })?.let { return it }

        val accountIds = file.accounts.mapTo(HashSet()) { it.id }
        val categories = file.categories.associateBy { it.id }

        for (tx in file.transactions) {
            if (!hasValidShape(tx)) return BackupError.InvalidTransaction(tx.id)
            listOfNotNull(tx.fromAccountId, tx.toAccountId).firstOrNull { it !in accountIds }?.let {
                return BackupError.MissingAccount(tx.id, it)
            }
            tx.categoryId?.let { categoryId ->
                val category = categories[categoryId] ?: return BackupError.MissingCategory(tx.id, categoryId)
                if (category.type != tx.type) return BackupError.InvalidTransaction(tx.id)
            }
        }
        return null
    }

    private fun hasValidShape(tx: BackupTransaction): Boolean {
        if (tx.amount !in 1..MAX_AMOUNT) return false
        return when (tx.type) {
            TransactionType.INCOME -> tx.toAccountId != null && tx.fromAccountId == null && tx.categoryId != null
            TransactionType.EXPENSE -> tx.fromAccountId != null && tx.toAccountId == null && tx.categoryId != null
            TransactionType.TRANSFER ->
                tx.fromAccountId != null && tx.toAccountId != null && tx.fromAccountId != tx.toAccountId && tx.categoryId == null
        }
    }

    private fun duplicate(entity: String, ids: List<Long>): BackupError.DuplicateId? {
        val seen = HashSet<Long>(ids.size)
        ids.forEach { if (!seen.add(it)) return BackupError.DuplicateId(entity, it) }
        return null
    }
}
