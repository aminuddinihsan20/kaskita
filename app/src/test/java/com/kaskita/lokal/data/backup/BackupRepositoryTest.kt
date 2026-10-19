package com.kaskita.lokal.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaskita.lokal.data.FakeSettingsStore
import com.kaskita.lokal.data.RoomLedgerRepository
import com.kaskita.lokal.data.SaveResult
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.data.db.KasKitaDatabase
import com.kaskita.lokal.data.db.SeedCallback
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.TransactionDraft
import com.kaskita.lokal.domain.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: KasKitaDatabase
    private lateinit var settings: FakeSettingsStore
    private lateinit var ledger: RoomLedgerRepository
    private lateinit var backups: BackupRepository
    private lateinit var safetyDir: File

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, KasKitaDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(SeedCallback())
        .build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = FakeSettingsStore()
        db = newDb()
        ledger = RoomLedgerRepository(db, settings) { 1_000L }
        backups = BackupRepository(db, settings, appVersionName = "1.0.0") { 2_000L }
        safetyDir = File(context.cacheDir, "safety-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        db.close()
        safetyDir.deleteRecursively()
    }

    private fun draft(type: TransactionType, amount: Long, category: Long? = null, from: Long? = null, to: Long? = null, note: String? = null) =
        TransactionDraft(null, type, amount, 5_000, category, from, to, note)

    /** Fills [target]'s ledger with accounts, a custom category, an archived account and one of each transaction type. */
    private fun populate(repo: RoomLedgerRepository) = runBlocking {
        val accounts = repo.observeAccounts().first()
        val bank = accounts.first { it.type == AccountType.BANK }
        val cash = accounts.first { it.type == AccountType.CASH }
        repo.saveAccount(bank.id, bank.name, bank.type, 3_000_000)
        repo.saveAccount(null, "GoPay", AccountType.EWALLET, 0)
        repo.saveAccount(null, "Lama", AccountType.OTHER, 0)
        val lama = repo.observeAccounts().first().first { it.name == "Lama" }
        repo.archiveAccount(lama.id)
        repo.saveCategory(null, "Zakat ☕", TransactionType.EXPENSE, "gift", "teal")
        val cats = repo.observeCategories().first()
        val food = cats.first { it.name == "Makanan & minuman" }
        val salary = cats.first { it.name == "Gaji" }
        val zakat = cats.first { it.name.startsWith("Zakat") }
        listOf(
            draft(TransactionType.TRANSFER, 500_000, from = bank.id, to = cash.id),
            draft(TransactionType.EXPENSE, 75_000, food.id, from = cash.id, note = "makan siang"),
            draft(TransactionType.INCOME, 100_000, salary.id, to = cash.id),
            draft(TransactionType.EXPENSE, 10_000, zakat.id, from = bank.id),
        ).forEach { check(repo.saveTransaction(it) is SaveResult.Success) }
        repo.setCategoryArchived(cats.first { it.name == "Hiburan" }.id, true)
    }

    private fun snapshot(): Triple<*, *, *> = runBlocking {
        Triple(ledger.observeAccounts().first(), ledger.observeCategories().first(), ledger.observeTransactions().first())
    }

    // spec G12
    @Test
    fun backupThenRestoreIntoCleanDbIsEquivalent() = runBlocking {
        populate(ledger)
        settings.setThemeMode(ThemeMode.DARK)
        val json = BackupCodec.encode(backups.buildBackup())
        val originalSnapshot = snapshot()
        val originalBalances = Ledger.balances(ledger.observeAccounts().first(), ledger.observeTransactions().first())

        val otherDb = newDb()
        val otherSettings = FakeSettingsStore()
        val otherLedger = RoomLedgerRepository(otherDb, otherSettings) { 1_000L }
        val otherBackups = BackupRepository(otherDb, otherSettings, "1.0.0") { 2_000L }
        populate(otherLedger) // different pre-existing data that must disappear

        val parsed = BackupCodec.decodeAndValidate(json) as BackupParseResult.Valid
        val result = otherBackups.restore(parsed.file, safetyDir)

        assertTrue(result is RestoreResult.Success)
        assertEquals(parsed.summary, (result as RestoreResult.Success).summary)
        assertEquals(
            originalSnapshot,
            Triple(otherLedger.observeAccounts().first(), otherLedger.observeCategories().first(), otherLedger.observeTransactions().first()),
        )
        assertEquals(originalBalances, Ledger.balances(otherLedger.observeAccounts().first(), otherLedger.observeTransactions().first()))
        assertEquals(ThemeMode.DARK, otherSettings.themeMode.first())
        otherDb.close()
    }

    @Test
    fun aSavingsAccountItsTypeAndItsDepositsSurviveBackupAndRestore() = runBlocking {
        val bank = ledger.observeAccounts().first().first { it.type == AccountType.BANK }
        ledger.saveAccount(bank.id, bank.name, bank.type, 3_000_000)
        ledger.saveAccount(null, "Dana darurat", AccountType.SAVINGS, 250_000)
        val savings = ledger.observeAccounts().first().first { it.type == AccountType.SAVINGS }
        check(ledger.saveTransaction(draft(TransactionType.TRANSFER, 800_000, from = bank.id, to = savings.id)) is SaveResult.Success)
        val json = BackupCodec.encode(backups.buildBackup())

        val otherDb = newDb()
        val otherLedger = RoomLedgerRepository(otherDb, FakeSettingsStore()) { 1_000L }
        val otherBackups = BackupRepository(otherDb, FakeSettingsStore(), "1.0.0") { 2_000L }
        val parsed = BackupCodec.decodeAndValidate(json) as BackupParseResult.Valid
        assertTrue(otherBackups.restore(parsed.file, safetyDir) is RestoreResult.Success)

        val accounts = otherLedger.observeAccounts().first()
        val txs = otherLedger.observeTransactions().first()
        assertEquals(AccountType.SAVINGS, accounts.first { it.name == "Dana darurat" }.type)
        val balances = Ledger.balances(accounts, txs)
        assertEquals(1_050_000L, Ledger.savingsBalance(accounts, balances))
        assertEquals(2_200_000L, balances[bank.id])
        assertEquals(3_250_000L, Ledger.totalBalance(accounts, balances))
        otherDb.close()
    }

    @Test
    fun restoreReplacesNotMerges() = runBlocking {
        val emptyBackup = backups.buildBackup() // seeded accounts/categories, no transactions
        populate(ledger)
        assertEquals(4, ledger.observeTransactions().first().size)

        val result = backups.restore(emptyBackup, safetyDir)

        assertTrue(result is RestoreResult.Success)
        assertEquals(0, ledger.observeTransactions().first().size)
        assertEquals(listOf("Cash", "Rekening Bank"), ledger.observeAccounts().first().map { it.name })
        assertEquals(18, ledger.observeCategories().first().size)
    }

    // spec G13
    @Test
    fun corruptFileLeavesActiveDataUntouched() = runBlocking {
        populate(ledger)
        val before = snapshot()

        val garbage = BackupCodec.decodeAndValidate("{ \"schemaVersion\": 1, \"accounts\": [ { ")
        assertTrue(garbage is BackupParseResult.Invalid)
        // The UI only restores a Valid result, so a corrupt file never reaches restore(); active data is unchanged.
        assertEquals(before, snapshot())
        assertTrue(!safetyDir.exists() || safetyDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun restoreRevalidatesAndRefusesInconsistentFile() = runBlocking {
        populate(ledger)
        val before = snapshot()
        val bad = backups.buildBackup().let { it.copy(transactions = it.transactions + it.transactions.first().copy(amount = 0, id = 9_999)) }

        val result = backups.restore(bad, safetyDir)

        assertEquals(RestoreResult.Failed(BackupError.InvalidTransaction(9_999)), result)
        assertEquals(before, snapshot())
    }

    @Test
    fun restoreFailureMidwayRollsBack() = runBlocking {
        populate(ledger)
        val before = snapshot()
        val valid = backups.buildBackup() // a perfectly valid backup...
        // ...but the database rejects the final step (inserting transactions) after everything else was rewritten.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_tx BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'boom'); END;",
        )

        val result = backups.restore(valid, safetyDir)

        assertEquals(RestoreResult.Failed(null), result)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_tx")
        assertEquals(before, snapshot())
    }

    @Test
    fun safetyBackupWrittenBeforeRestore() = runBlocking {
        populate(ledger)
        val before = backups.buildBackup()

        val result = backups.restore(before.copy(transactions = emptyList()), safetyDir)

        assertTrue(result is RestoreResult.Success)
        val written = safetyDir.listFiles().orEmpty().filter { it.name.endsWith(".json") }
        assertEquals(1, written.size)
        val saved = (BackupCodec.decodeAndValidate(written.single().readText()) as BackupParseResult.Valid).file
        assertEquals(before.transactions, saved.transactions)
        assertEquals(before.accounts, saved.accounts)
    }

    @Test
    fun onlyTheNewestSafetyBackupsAreKept() = runBlocking {
        var tick = 10_000L
        val repo = BackupRepository(db, settings, "1.0.0") { tick++ }
        repeat(7) { repo.restore(repo.buildBackup(), safetyDir) }
        assertEquals(5, safetyDir.listFiles().orEmpty().count { it.name.endsWith(".json") })
    }

    @Test
    fun restoredIdsKeepWorkingForNewTransactions() = runBlocking {
        populate(ledger)
        backups.restore(backups.buildBackup(), safetyDir)
        val cash = ledger.observeAccounts().first().first { it.type == AccountType.CASH }
        val salary = ledger.observeCategories().first().first { it.name == "Gaji" }
        val restoredIds = ledger.observeTransactions().first().map { it.id }
        val result = ledger.saveTransaction(draft(TransactionType.INCOME, 1_000, salary.id, to = cash.id))
        val newId = (result as SaveResult.Success).id
        assertTrue(newId > restoredIds.max())
        assertEquals(restoredIds.size + 1, ledger.observeTransactions().first().size)
    }

    @Test
    fun buildBackupRecordsPreferencesAndTimestamp() = runBlocking {
        settings.setAllowNegativeBalance(true)
        settings.setThemeMode(ThemeMode.LIGHT)
        val file = backups.buildBackup()
        assertEquals(2_000L, file.backupCreatedAt)
        assertEquals(BackupCodec.SCHEMA_VERSION, file.schemaVersion)
        assertEquals(BackupPreferences(ThemeMode.LIGHT, allowNegativeBalance = true), file.preferences)
    }

    @Test
    fun fileIoRoundTripsThroughAUri() = runBlocking {
        val target = File(context.cacheDir, "io-${System.nanoTime()}.json")
        val io = BackupFileIo(context)
        val json = BackupCodec.encode(backups.buildBackup())
        io.write(Uri.fromFile(target), json)
        assertEquals(json, io.read(Uri.fromFile(target)))
        check(target.delete())
    }

    @Test
    fun restoreNeverChangesTheAiConsent() = runBlocking {
        val withConsent = FakeSettingsStore(aiConsent = true)
        val repo = BackupRepository(db, withConsent, "1.0.0") { 2_000L }
        repo.restore(repo.buildBackup(), safetyDir)
        assertEquals(true, withConsent.aiConsent.first())
        // and the backup file has nowhere to carry it
        assertTrue(!BackupCodec.encode(repo.buildBackup()).contains("consent", ignoreCase = true))
    }
}
