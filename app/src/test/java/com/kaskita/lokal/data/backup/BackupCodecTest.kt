package com.kaskita.lokal.data.backup

import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.MAX_AMOUNT
import com.kaskita.lokal.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun account(id: Long, type: AccountType = AccountType.BANK, opening: Long = 0) =
        BackupAccount(id, "Akun $id", type, opening, createdAt = 1, isArchived = false)

    private fun category(id: Long, type: TransactionType) =
        BackupCategory(id, "Kategori $id", type, "icon", "color", isDefault = false, isArchived = false)

    private fun income(id: Long, to: Long, category: Long = 100, amount: Long = 1_000) =
        BackupTransaction(id, TransactionType.INCOME, amount, 5_000, category, null, to, null, 1, 1)

    private fun expense(id: Long, from: Long, category: Long = 200, amount: Long = 1_000) =
        BackupTransaction(id, TransactionType.EXPENSE, amount, 5_000, category, from, null, "beli ☕ kopi", 1, 1)

    private fun transfer(id: Long, from: Long, to: Long, amount: Long = 1_000) =
        BackupTransaction(id, TransactionType.TRANSFER, amount, 5_000, null, from, to, null, 1, 1)

    private fun file(
        accounts: List<BackupAccount> = listOf(account(1), account(2, AccountType.CASH)),
        categories: List<BackupCategory> = listOf(category(100, TransactionType.INCOME), category(200, TransactionType.EXPENSE)),
        transactions: List<BackupTransaction> = listOf(income(1, 1), expense(2, 2), transfer(3, 1, 2)),
        schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
        prefs: BackupPreferences = BackupPreferences(ThemeMode.DARK, allowNegativeBalance = false),
    ) = BackupFile(schemaVersion, backupCreatedAt = 1_700_000_000_000, appVersionName = "1.0.0", accounts, categories, transactions, prefs)

    private fun decode(f: BackupFile) = BackupCodec.decodeAndValidate(BackupCodec.encode(f))
    private fun invalid(result: BackupParseResult) = (result as BackupParseResult.Invalid).error

    @Test
    fun roundTripEncodeDecodeEqual() {
        val original = file()
        val result = decode(original) as BackupParseResult.Valid
        assertEquals(original, result.file)
        assertEquals(BackupSummary(1_700_000_000_000, accountCount = 2, categoryCount = 2, transactionCount = 3), result.summary)
    }

    @Test
    fun encodedFileCarriesVersionAndCreationTime() {
        val json = BackupCodec.encode(file())
        assertTrue(json.contains("\"schemaVersion\":1"))
        assertTrue(json.contains("\"backupCreatedAt\":1700000000000"))
    }

    @Test
    fun emptyStringRejected() {
        assertEquals(BackupError.Empty, invalid(BackupCodec.decodeAndValidate("")))
        assertEquals(BackupError.Empty, invalid(BackupCodec.decodeAndValidate("   \n")))
    }

    @Test
    fun garbageRejectedAsMalformed() {
        assertEquals(BackupError.Malformed, invalid(BackupCodec.decodeAndValidate("ini bukan json")))
        assertEquals(BackupError.Malformed, invalid(BackupCodec.decodeAndValidate("[1,2,3]")))
        assertEquals(BackupError.Malformed, invalid(BackupCodec.decodeAndValidate("{}")))
    }

    @Test
    fun truncatedJsonRejected() {
        val json = BackupCodec.encode(file())
        assertEquals(BackupError.Malformed, invalid(BackupCodec.decodeAndValidate(json.take(json.length / 2))))
    }

    @Test
    fun newerSchemaVersionRejectedEvenWhenStructureDiffers() {
        assertEquals(BackupError.UnsupportedVersion(2, 1), invalid(decode(file(schemaVersion = 2))))
        assertEquals(
            BackupError.UnsupportedVersion(5, 1),
            invalid(BackupCodec.decodeAndValidate("""{"schemaVersion":5,"somethingNew":[]}""")),
        )
    }

    @Test
    fun versionZeroRejected() {
        assertEquals(BackupError.Malformed, invalid(decode(file(schemaVersion = 0))))
    }

    @Test
    fun missingAccountReferenceRejected() {
        val result = decode(file(transactions = listOf(income(7, to = 99))))
        assertEquals(BackupError.MissingAccount(transactionId = 7, accountId = 99), invalid(result))
    }

    @Test
    fun missingCategoryReferenceRejected() {
        val result = decode(file(transactions = listOf(expense(8, from = 1, category = 555))))
        assertEquals(BackupError.MissingCategory(transactionId = 8, categoryId = 555), invalid(result))
    }

    @Test
    fun duplicateIdsRejected() {
        assertEquals(
            BackupError.DuplicateId("account", 1),
            invalid(decode(file(accounts = listOf(account(1), account(1))))),
        )
        assertEquals(
            BackupError.DuplicateId("category", 100),
            invalid(decode(file(categories = listOf(category(100, TransactionType.INCOME), category(100, TransactionType.INCOME))))),
        )
        assertEquals(
            BackupError.DuplicateId("transaction", 1),
            invalid(decode(file(transactions = listOf(income(1, 1), income(1, 1))))),
        )
    }

    @Test
    fun zeroOrNegativeOrHugeAmountRejected() {
        for (bad in listOf(0L, -5L, MAX_AMOUNT + 1)) {
            assertEquals(
                "amount=$bad",
                BackupError.InvalidTransaction(4),
                invalid(decode(file(transactions = listOf(income(4, 1, amount = bad))))),
            )
        }
    }

    @Test
    fun transferToSameAccountRejected() {
        assertEquals(BackupError.InvalidTransaction(9), invalid(decode(file(transactions = listOf(transfer(9, 1, 1))))))
    }

    @Test
    fun typeFieldMismatchRejected() {
        val expenseWithoutSource = expense(5, from = 1).copy(fromAccountId = null)
        assertEquals(BackupError.InvalidTransaction(5), invalid(decode(file(transactions = listOf(expenseWithoutSource)))))
        val incomeWithWrongCategoryType = income(6, to = 1, category = 200)
        assertEquals(BackupError.InvalidTransaction(6), invalid(decode(file(transactions = listOf(incomeWithWrongCategoryType)))))
        val transferWithCategory = transfer(10, 1, 2).copy(categoryId = 100)
        assertEquals(BackupError.InvalidTransaction(10), invalid(decode(file(transactions = listOf(transferWithCategory)))))
    }

    @Test
    fun unknownEnumValueRejected() {
        val json = BackupCodec.encode(file()).replace("\"type\":\"BANK\"", "\"type\":\"CRYPTO\"")
        assertEquals(BackupError.Malformed, invalid(BackupCodec.decodeAndValidate(json)))
    }

    @Test
    fun wrongTypedFieldRejected() {
        val json = BackupCodec.encode(file()).replace("\"openingBalance\":0", "\"openingBalance\":\"banyak\"")
        assertEquals(BackupError.Malformed, invalid(BackupCodec.decodeAndValidate(json)))
    }

    @Test
    fun manyTransactionsValid() {
        val many = (1L..20_000L).map { id ->
            when (id % 3) {
                0L -> income(id, to = 1)
                1L -> expense(id, from = 2)
                else -> transfer(id, 1, 2)
            }
        }
        val result = decode(file(transactions = many)) as BackupParseResult.Valid
        assertEquals(20_000, result.summary.transactionCount)
    }

    @Test
    fun negativeBalanceBackupStillValidWhenAllowed() {
        val overspent = listOf(expense(1, from = 2, amount = 5_000_000)) // cash opening 0 -> negative
        val result = decode(
            file(transactions = overspent, prefs = BackupPreferences(ThemeMode.SYSTEM, allowNegativeBalance = true)),
        )
        assertTrue(result is BackupParseResult.Valid)
    }

    @Test
    fun unicodeNotesSurviveRoundTrip() {
        val original = file()
        val decoded = (decode(original) as BackupParseResult.Valid).file
        assertEquals("beli ☕ kopi", decoded.transactions.first { it.id == 2L }.note)
    }
}
