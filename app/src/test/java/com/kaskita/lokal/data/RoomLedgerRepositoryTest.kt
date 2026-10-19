package com.kaskita.lokal.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaskita.lokal.data.db.KasKitaDatabase
import com.kaskita.lokal.data.db.SeedCallback
import com.kaskita.lokal.domain.Account
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.Ledger
import com.kaskita.lokal.domain.Transaction
import com.kaskita.lokal.domain.TransactionDraft
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.domain.ValidationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLedgerRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: KasKitaDatabase
    private lateinit var settings: FakeSettingsStore
    private lateinit var repo: RoomLedgerRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = FakeSettingsStore()
        db = Room.inMemoryDatabaseBuilder(context, KasKitaDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(SeedCallback())
            .build()
        repo = RoomLedgerRepository(db, settings) { clock }
    }

    @After
    fun tearDown() = db.close()

    // --- helpers
    private fun accounts(): List<Account> = runBlocking { repo.observeAccounts().first() }
    private fun categories(): List<Category> = runBlocking { repo.observeCategories().first() }
    private fun transactions(): List<Transaction> = runBlocking { repo.observeTransactions().first() }
    private fun cash() = accounts().first { it.type == AccountType.CASH }
    private fun bank() = accounts().first { it.type == AccountType.BANK }
    private fun category(name: String) = categories().first { it.name == name }
    private fun balance(id: Long) = Ledger.balances(accounts(), transactions())[id]!!
    private fun total() = Ledger.totalBalance(accounts(), Ledger.balances(accounts(), transactions()))

    private fun setBankOpening(amount: Long) = runBlocking {
        val b = bank()
        assertEquals(AccountResult.Success, repo.saveAccount(b.id, b.name, b.type, amount))
    }

    private fun draft(
        type: TransactionType,
        amount: Long?,
        category: Long? = null,
        from: Long? = null,
        to: Long? = null,
        id: Long? = null,
        date: Long = 5_000L,
    ) = TransactionDraft(id, type, amount, date, category, from, to, null)

    private fun save(d: TransactionDraft) = runBlocking { repo.saveTransaction(d) }
    private fun saveOk(d: TransactionDraft): Long =
        (save(d) as SaveResult.Success).id

    // --- seed
    @Test
    fun seedCreatesCashAndBankAndNoTransactions() {
        assertEquals(listOf("Cash", "Rekening Bank"), accounts().map { it.name })
        assertEquals(listOf(AccountType.CASH, AccountType.BANK), accounts().map { it.type })
        assertTrue(accounts().all { it.openingBalance == 0L && !it.isArchived })
        assertEquals(emptyList<Transaction>(), transactions())
        assertEquals(11, categories().count { it.type == TransactionType.EXPENSE })
        assertEquals(7, categories().count { it.type == TransactionType.INCOME })
        assertFalse(categories().any { it.name.contains("Tarik tunai", ignoreCase = true) })
    }

    // --- transfer storage + AI_RULES scenario
    @Test
    fun transferStoredAsSingleRow() {
        setBankOpening(2_000_000)
        saveOk(draft(TransactionType.TRANSFER, 500_000, from = bank().id, to = cash().id))
        val rows = transactions()
        assertEquals(1, rows.size)
        assertEquals(bank().id, rows[0].fromAccountId)
        assertEquals(cash().id, rows[0].toAccountId)
        assertNull(rows[0].categoryId)
    }

    @Test
    fun transferBankToCashViaRepositoryMatchesAiRules() {
        setBankOpening(2_000_000)
        saveOk(draft(TransactionType.TRANSFER, 500_000, from = bank().id, to = cash().id))
        assertEquals(1_500_000L, balance(bank().id))
        assertEquals(500_000L, balance(cash().id))
        assertEquals(2_000_000L, total())

        saveOk(draft(TransactionType.EXPENSE, 75_000, category("Makanan & minuman").id, from = cash().id))
        assertEquals(425_000L, balance(cash().id))
        assertEquals(1_925_000L, total())

        saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = cash().id))
        assertEquals(525_000L, balance(cash().id))
        assertEquals(2_025_000L, total())
    }

    @Test
    fun invalidDraftWritesNothing() {
        val result = save(draft(TransactionType.EXPENSE, 0, category("Gaji").id, from = cash().id))
        assertTrue(result is SaveResult.Invalid)
        assertEquals(emptyList<Transaction>(), transactions())
    }

    // Review Focus 1
    @Test
    fun editChangingTypeNormalizesFields() {
        setBankOpening(1_000_000)
        val id = saveOk(draft(TransactionType.EXPENSE, 100_000, category("Makanan & minuman").id, from = bank().id))
        assertTrue(save(draft(TransactionType.TRANSFER, 100_000, category("Makanan & minuman").id, from = bank().id, to = cash().id, id = id)) is SaveResult.Success)
        val row = transactions().single()
        assertEquals(TransactionType.TRANSFER, row.type)
        assertNull(row.categoryId)
        assertEquals(bank().id, row.fromAccountId)
        assertEquals(cash().id, row.toAccountId)
    }

    @Test
    fun editKeepsCreatedAtAndBumpsUpdatedAt() {
        setBankOpening(1_000_000)
        clock = 10_000L
        val id = saveOk(draft(TransactionType.EXPENSE, 100, category("Makanan & minuman").id, from = bank().id))
        clock = 20_000L
        saveOk(draft(TransactionType.EXPENSE, 200, category("Makanan & minuman").id, from = bank().id, id = id))
        val row = transactions().single()
        assertEquals(10_000L, row.createdAt)
        assertEquals(20_000L, row.updatedAt)
        assertEquals(200L, row.amount)
    }

    @Test
    fun editOfMissingRowReportsNotFound() {
        val result = save(draft(TransactionType.INCOME, 100, category("Gaji").id, to = cash().id, id = 4242))
        assertEquals(SaveResult.NotFound, result)
        assertEquals(emptyList<Transaction>(), transactions())
    }

    @Test
    fun deleteRecomputesBalances() {
        val incomeId = saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = cash().id))
        val expenseId = saveOk(draft(TransactionType.EXPENSE, 30_000, category("Makanan & minuman").id, from = cash().id))
        assertEquals(70_000L, balance(cash().id))
        assertEquals(DeleteResult.Success, runBlocking { repo.deleteTransaction(expenseId) })
        assertEquals(100_000L, balance(cash().id))
        assertEquals(listOf(incomeId), transactions().map { it.id })
    }

    @Test
    fun deleteOfMissingRowReportsNotFound() {
        assertEquals(DeleteResult.NotFound, runBlocking { repo.deleteTransaction(999) })
    }

    // Review Focus 3
    @Test
    fun deleteBlockedWhenItWouldGoNegative() {
        val incomeId = saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = cash().id))
        saveOk(draft(TransactionType.EXPENSE, 80_000, category("Makanan & minuman").id, from = cash().id))
        val result = runBlocking { repo.deleteTransaction(incomeId) }
        assertEquals(
            DeleteResult.WouldGoNegative(listOf(ValidationError.InsufficientBalance(cash().id, 80_000))),
            result,
        )
        assertEquals(2, transactions().size)
        assertEquals(20_000L, balance(cash().id))
    }

    @Test
    fun deleteAllowedWhenNegativeBalanceIsEnabled() {
        val incomeId = saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = cash().id))
        saveOk(draft(TransactionType.EXPENSE, 80_000, category("Makanan & minuman").id, from = cash().id))
        runBlocking { settings.setAllowNegativeBalance(true) }
        assertEquals(DeleteResult.Success, runBlocking { repo.deleteTransaction(incomeId) })
        assertEquals(-80_000L, balance(cash().id))
    }

    // --- accounts
    @Test
    fun expenseBeyondBalanceIsRejectedWithShortfall() {
        saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = cash().id))
        val result = save(draft(TransactionType.EXPENSE, 150_000, category("Makanan & minuman").id, from = cash().id))
        assertEquals(SaveResult.Invalid(listOf(ValidationError.InsufficientBalance(cash().id, 50_000))), result)
        assertEquals(1, transactions().size)
    }

    // Review Focus 4
    @Test
    fun archiveRequiresZeroBalance() {
        saveOk(draft(TransactionType.INCOME, 5_000, category("Gaji").id, to = bank().id))
        assertEquals(AccountResult.NonZeroBalance, runBlocking { repo.archiveAccount(bank().id) })
        assertFalse(bank().isArchived)

        // empty it, then archiving works
        saveOk(draft(TransactionType.EXPENSE, 5_000, category("Makanan & minuman").id, from = bank().id))
        assertEquals(AccountResult.Success, runBlocking { repo.archiveAccount(bank().id) })
        assertTrue(accounts().first { it.type == AccountType.BANK }.isArchived)
    }

    @Test
    fun archivedAccountStillShownOnHistory() {
        val bankId = bank().id
        saveOk(draft(TransactionType.INCOME, 5_000, category("Gaji").id, to = bankId))
        saveOk(draft(TransactionType.EXPENSE, 5_000, category("Makanan & minuman").id, from = bankId))
        runBlocking { repo.archiveAccount(bankId) }
        assertTrue(accounts().any { it.id == bankId && it.isArchived })
        assertEquals(2, transactions().count { it.fromAccountId == bankId || it.toAccountId == bankId })
        assertEquals(0L, balance(bankId))
    }

    @Test
    fun onlyOneActiveCashAccount() {
        val result = runBlocking { repo.saveAccount(null, "Dompet kedua", AccountType.CASH, 0) }
        assertEquals(AccountResult.CashAlreadyExists, result)
        assertEquals(2, accounts().size)
    }

    @Test
    fun cashCanBeRecreatedAfterArchivingTheOldOne() {
        assertEquals(AccountResult.Success, runBlocking { repo.archiveAccount(cash().id) })
        val result = runBlocking { repo.saveAccount(null, "Cash baru", AccountType.CASH, 0) }
        assertEquals(AccountResult.Success, result)
    }

    @Test
    fun blankAccountNameRejected() {
        assertEquals(AccountResult.NameBlank, runBlocking { repo.saveAccount(null, "   ", AccountType.EWALLET, 0) })
    }

    @Test
    fun negativeOpeningBalanceRejectedUnlessAllowed() {
        assertEquals(AccountResult.InvalidOpeningBalance, runBlocking { repo.saveAccount(null, "GoPay", AccountType.EWALLET, -1) })
        runBlocking { settings.setAllowNegativeBalance(true) }
        assertEquals(AccountResult.Success, runBlocking { repo.saveAccount(null, "GoPay", AccountType.EWALLET, -1) })
    }

    @Test
    fun loweringOpeningBalanceBelowSpentAmountIsRejected() {
        setBankOpening(100_000)
        saveOk(draft(TransactionType.EXPENSE, 80_000, category("Makanan & minuman").id, from = bank().id))
        val b = bank()
        val result = runBlocking { repo.saveAccount(b.id, b.name, b.type, 50_000) }
        assertEquals(AccountResult.WouldGoNegative(listOf(ValidationError.InsufficientBalance(b.id, 30_000))), result)
        assertEquals(100_000L, bank().openingBalance)
    }

    // --- categories
    @Test
    fun customCategoryCanBeAddedRenamedAndArchived() {
        assertTrue(runBlocking { repo.saveCategory(null, "Zakat", TransactionType.EXPENSE, "gift", "teal") })
        val c = category("Zakat")
        assertFalse(c.isDefault)
        assertTrue(runBlocking { repo.saveCategory(c.id, "Zakat & infak", TransactionType.EXPENSE, "gift", "teal") })
        assertEquals("Zakat & infak", category("Zakat & infak").name)
        runBlocking { repo.setCategoryArchived(c.id, true) }
        assertTrue(category("Zakat & infak").isArchived)
    }

    @Test
    fun categoryTypeCannotChangeAfterCreation() {
        val c = category("Gaji")
        assertFalse(runBlocking { repo.saveCategory(c.id, "Gaji", TransactionType.EXPENSE, c.iconKey, c.colorKey) })
        assertFalse(runBlocking { repo.saveCategory(null, "X", TransactionType.TRANSFER, "i", "c") })
        assertFalse(runBlocking { repo.saveCategory(null, " ", TransactionType.EXPENSE, "i", "c") })
    }

    // --- persistence
    @Test
    fun dataSurvivesReopeningDatabase() {
        val name = "reopen-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KasKitaDatabase::class.java, name)
            .allowMainThreadQueries()
            .addCallback(SeedCallback())
            .build()

        val first = open()
        val firstRepo = RoomLedgerRepository(first, settings) { clock }
        runBlocking {
            val cashId = firstRepo.observeAccounts().first().first { it.type == AccountType.CASH }.id
            val gaji = firstRepo.observeCategories().first().first { it.name == "Gaji" }.id
            firstRepo.saveTransaction(draft(TransactionType.INCOME, 123_456, gaji, to = cashId))
        }
        first.close()

        val second = open()
        val rows = runBlocking { RoomLedgerRepository(second, settings) { clock }.observeTransactions().first() }
        assertEquals(listOf(123_456L), rows.map { it.amount })
        second.close()
        context.deleteDatabase(name)
    }

    // Final review finding 1
    @Test
    fun deleteBlockedWhenItWouldUnbalanceAnArchivedAccount() {
        val bankId = bank().id
        saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = bankId))
        val expenseId = saveOk(draft(TransactionType.EXPENSE, 100_000, category("Makanan & minuman").id, from = bankId))
        assertEquals(AccountResult.Success, runBlocking { repo.archiveAccount(bankId) })

        val result = runBlocking { repo.deleteTransaction(expenseId) }

        assertEquals(DeleteResult.ArchivedAccountAffected(listOf(ValidationError.ArchivedAccountNotZero(bankId, 100_000))), result)
        assertEquals(2, transactions().size)
        assertEquals(0L, balance(bankId))
    }

    @Test
    fun editThatWouldLeaveAnArchivedAccountNonZeroIsRejectedAndNothingChanges() {
        val bankId = bank().id
        saveOk(draft(TransactionType.INCOME, 100_000, category("Gaji").id, to = bankId))
        val expenseId = saveOk(draft(TransactionType.EXPENSE, 100_000, category("Makanan & minuman").id, from = bankId))
        runBlocking { repo.archiveAccount(bankId) }

        val result = save(draft(TransactionType.EXPENSE, 60_000, category("Makanan & minuman").id, from = bankId, id = expenseId))

        assertEquals(SaveResult.Invalid(listOf(ValidationError.ArchivedAccountNotZero(bankId, 40_000))), result)
        assertEquals(100_000L, transactions().first { it.id == expenseId }.amount)
    }

    @Test
    fun openingBalanceOfAnArchivedAccountCannotChange() {
        val b = bank()
        runBlocking { repo.archiveAccount(b.id) }
        assertEquals(AccountResult.ArchivedBalanceLocked, runBlocking { repo.saveAccount(b.id, b.name, b.type, 50_000) })
        assertEquals(0L, accounts().first { it.id == b.id }.openingBalance)
        // renaming is harmless and still allowed
        assertEquals(AccountResult.Success, runBlocking { repo.saveAccount(b.id, "BCA lama", b.type, 0) })
    }

    @Test
    fun unarchiveBringsAnAccountBackAndRespectsASingleActiveCash() {
        val bankId = bank().id
        runBlocking { repo.archiveAccount(bankId) }
        assertEquals(AccountResult.Success, runBlocking { repo.unarchiveAccount(bankId) })
        assertFalse(accounts().first { it.id == bankId }.isArchived)

        // Cash is archived, a new active Cash is created, then the old one cannot come back
        val oldCash = cash()
        runBlocking { repo.archiveAccount(oldCash.id) }
        assertEquals(AccountResult.Success, runBlocking { repo.saveAccount(null, "Cash baru", AccountType.CASH, 0) })
        assertEquals(AccountResult.CashAlreadyExists, runBlocking { repo.unarchiveAccount(oldCash.id) })
        assertTrue(accounts().first { it.id == oldCash.id }.isArchived)
        assertEquals(AccountResult.NotFound, runBlocking { repo.unarchiveAccount(9_999) })
    }
}
