package com.kaskita.lokal.domain

import com.kaskita.lokal.domain.TestData.BANK_ID
import com.kaskita.lokal.domain.TestData.CASH_ID
import com.kaskita.lokal.domain.TestData.FOOD_ID
import com.kaskita.lokal.domain.TestData.SALARY_ID
import com.kaskita.lokal.domain.TestData.bank
import com.kaskita.lokal.domain.TestData.cash
import com.kaskita.lokal.domain.TestData.expense
import com.kaskita.lokal.domain.TestData.foodCategory
import com.kaskita.lokal.domain.TestData.income
import com.kaskita.lokal.domain.TestData.salaryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionValidatorTest {
    private val categories = listOf(foodCategory(), salaryCategory())

    private fun draft(
        type: TransactionType,
        amount: Long? = 10_000,
        category: Long? = null,
        from: Long? = null,
        to: Long? = null,
        id: Long? = null,
    ) = TransactionDraft(id, type, amount, 0L, category, from, to, null)

    private fun validate(
        draft: TransactionDraft,
        accounts: List<Account> = listOf(bank(opening = 2_000_000), cash(opening = 0)),
        existing: List<Transaction> = emptyList(),
        allowNegative: Boolean = false,
        cats: List<Category> = categories,
    ) = TransactionValidator.validate(draft, accounts, cats, existing, allowNegative)

    @Test
    fun incomeRequiresDestinationAndCategory() {
        val errors = validate(draft(TransactionType.INCOME))
        assertTrue(ValidationError.DestinationAccountRequired in errors)
        assertTrue(ValidationError.CategoryRequired in errors)
    }

    @Test
    fun expenseRequiresSourceAndCategory() {
        val errors = validate(draft(TransactionType.EXPENSE))
        assertTrue(ValidationError.SourceAccountRequired in errors)
        assertTrue(ValidationError.CategoryRequired in errors)
    }

    @Test
    fun transferRequiresBothAccounts() {
        val errors = validate(draft(TransactionType.TRANSFER))
        assertTrue(ValidationError.SourceAccountRequired in errors)
        assertTrue(ValidationError.DestinationAccountRequired in errors)
    }

    @Test
    fun transferToSameAccountRejected() {
        val errors = validate(draft(TransactionType.TRANSFER, from = BANK_ID, to = BANK_ID))
        assertEquals(listOf<ValidationError>(ValidationError.SameAccount), errors)
    }

    @Test
    fun zeroOrNegativeAmountRejected() {
        for (bad in listOf(0L, -1L, null)) {
            val errors = validate(draft(TransactionType.EXPENSE, amount = bad, category = FOOD_ID, from = CASH_ID))
            assertTrue("amount=$bad", ValidationError.AmountRequired in errors)
        }
    }

    @Test
    fun amountAboveMaxRejected() {
        val errors = validate(
            draft(TransactionType.INCOME, amount = MAX_AMOUNT + 1, category = SALARY_ID, to = BANK_ID),
        )
        assertEquals(listOf<ValidationError>(ValidationError.AmountTooLarge), errors)
    }

    @Test
    fun categoryTypeMustMatch() {
        val errors = validate(draft(TransactionType.EXPENSE, category = SALARY_ID, from = BANK_ID))
        assertEquals(listOf<ValidationError>(ValidationError.CategoryTypeMismatch), errors)
    }

    @Test
    fun transferIgnoresCategory() {
        val errors = validate(draft(TransactionType.TRANSFER, category = SALARY_ID, from = BANK_ID, to = CASH_ID))
        assertEquals(emptyList<ValidationError>(), errors)
    }

    @Test
    fun validIncomeAndExpensePass() {
        assertEquals(emptyList<ValidationError>(), validate(draft(TransactionType.INCOME, category = SALARY_ID, to = CASH_ID)))
        assertEquals(emptyList<ValidationError>(), validate(draft(TransactionType.EXPENSE, category = FOOD_ID, from = BANK_ID)))
    }

    @Test
    fun insufficientBalanceReportsShortfall() {
        val accounts = listOf(bank(opening = 0), cash(opening = 100_000))
        val errors = validate(
            draft(TransactionType.EXPENSE, amount = 150_000, category = FOOD_ID, from = CASH_ID),
            accounts = accounts,
        )
        assertEquals(listOf<ValidationError>(ValidationError.InsufficientBalance(CASH_ID, 50_000)), errors)
    }

    @Test
    fun transferOutOfSourceChecksSourceBalance() {
        val accounts = listOf(bank(opening = 300_000), cash(opening = 0))
        val errors = validate(draft(TransactionType.TRANSFER, amount = 500_000, from = BANK_ID, to = CASH_ID), accounts = accounts)
        assertEquals(listOf<ValidationError>(ValidationError.InsufficientBalance(BANK_ID, 200_000)), errors)
    }

    @Test
    fun allowNegativeSkipsBalanceCheck() {
        val accounts = listOf(bank(opening = 0), cash(opening = 100_000))
        val errors = validate(
            draft(TransactionType.EXPENSE, amount = 150_000, category = FOOD_ID, from = CASH_ID),
            accounts = accounts,
            allowNegative = true,
        )
        assertEquals(emptyList<ValidationError>(), errors)
    }

    // Review Focus 2
    @Test
    fun editExcludesOwnOldEffect() {
        val accounts = listOf(bank(opening = 0), cash(opening = 500_000))
        val existing = listOf(expense(id = 1, amount = 400_000, from = CASH_ID)) // cash balance now 100.000
        val ok = validate(
            draft(TransactionType.EXPENSE, amount = 450_000, category = FOOD_ID, from = CASH_ID, id = 1),
            accounts = accounts, existing = existing,
        )
        assertEquals(emptyList<ValidationError>(), ok)

        val tooMuch = validate(
            draft(TransactionType.EXPENSE, amount = 550_000, category = FOOD_ID, from = CASH_ID, id = 1),
            accounts = accounts, existing = existing,
        )
        assertEquals(listOf<ValidationError>(ValidationError.InsufficientBalance(CASH_ID, 50_000)), tooMuch)
    }

    @Test
    fun preExistingNegativeNotBlamedOnUnrelatedEdit() {
        val accounts = listOf(bank(opening = -10_000), cash(opening = 0)) // bank already negative (allowed earlier)
        val existing = listOf(income(id = 1, amount = 50_000, to = CASH_ID))
        val errors = validate(
            draft(TransactionType.INCOME, amount = 50_000, category = SALARY_ID, to = CASH_ID, id = 1),
            accounts = accounts, existing = existing,
        )
        assertEquals(emptyList<ValidationError>(), errors)
    }

    @Test
    fun archivedAccountRejected() {
        val accounts = listOf(bank(), cash(archived = true))
        val errors = validate(draft(TransactionType.INCOME, category = SALARY_ID, to = CASH_ID), accounts = accounts)
        assertEquals(listOf<ValidationError>(ValidationError.AccountUnavailable(CASH_ID)), errors)
    }

    @Test
    fun unknownAccountRejected() {
        val errors = validate(draft(TransactionType.INCOME, category = SALARY_ID, to = 999L))
        assertEquals(listOf<ValidationError>(ValidationError.AccountUnavailable(999L)), errors)
    }

    // Review Focus 4: history on an archived account stays editable
    @Test
    fun editingOldTransactionOnArchivedAccountIsAllowed() {
        val accounts = listOf(bank(), cash(archived = true))
        val existing = listOf(income(id = 1, amount = 5_000, to = CASH_ID))
        val errors = validate(
            // same amount, other note: the archived account's balance does not move, so the correction is allowed
            draft(TransactionType.INCOME, amount = 5_000, category = SALARY_ID, to = CASH_ID, id = 1).copy(note = "dikoreksi"),
            accounts = accounts, existing = existing,
        )
        assertEquals(emptyList<ValidationError>(), errors)
    }

    @Test
    fun archivedCategoryRejectedForNewTransaction() {
        val cats = listOf(foodCategory().copy(isArchived = true), salaryCategory())
        val errors = validate(draft(TransactionType.EXPENSE, category = FOOD_ID, from = BANK_ID), cats = cats)
        assertEquals(listOf<ValidationError>(ValidationError.CategoryUnavailable(FOOD_ID)), errors)
    }

    @Test
    fun shapeErrorsSkipBalanceCheck() {
        val accounts = listOf(bank(opening = 0), cash(opening = 0))
        val errors = validate(draft(TransactionType.EXPENSE, amount = 999, from = CASH_ID), accounts = accounts) // no category
        assertEquals(listOf<ValidationError>(ValidationError.CategoryRequired), errors)
    }

    @Test
    fun normalizedClearsFieldsThatDoNotApplyToType() {
        val d = draft(TransactionType.TRANSFER, category = FOOD_ID, from = BANK_ID, to = CASH_ID)
        assertEquals(null, d.normalized().categoryId)
        val e = draft(TransactionType.EXPENSE, category = FOOD_ID, from = BANK_ID, to = CASH_ID).normalized()
        assertEquals(null, e.toAccountId)
        assertEquals(BANK_ID, e.fromAccountId)
        val i = draft(TransactionType.INCOME, category = SALARY_ID, from = BANK_ID, to = CASH_ID).normalized()
        assertEquals(null, i.fromAccountId)
        assertEquals(CASH_ID, i.toAccountId)
    }

    // Final review finding 1: an archived account must stay at Rp0 or its money silently leaves "Total saldo"
    @Test
    fun archivedAccountMustStayAtZero() {
        val accounts = listOf(bank(), cash(opening = 0, archived = true))
        val existing = listOf(income(id = 1, amount = 100_000, to = CASH_ID), expense(id = 2, amount = 100_000, from = CASH_ID))

        val loweringTheExpense = validate(
            draft(TransactionType.EXPENSE, amount = 60_000, category = FOOD_ID, from = CASH_ID, id = 2),
            accounts = accounts, existing = existing,
        )
        assertEquals(listOf<ValidationError>(ValidationError.ArchivedAccountNotZero(CASH_ID, 40_000)), loweringTheExpense)

        val movingTheExpenseAway = validate(
            draft(TransactionType.EXPENSE, amount = 100_000, category = FOOD_ID, from = BANK_ID, id = 2),
            accounts = accounts, existing = existing,
        )
        assertEquals(listOf<ValidationError>(ValidationError.ArchivedAccountNotZero(CASH_ID, 100_000)), movingTheExpenseAway)
    }

    @Test
    fun editingOnlyTheNoteOfAnArchivedAccountsTransactionIsStillAllowed() {
        val accounts = listOf(bank(), cash(opening = 0, archived = true))
        val existing = listOf(income(id = 1, amount = 100_000, to = CASH_ID), expense(id = 2, amount = 100_000, from = CASH_ID))
        val errors = validate(
            draft(TransactionType.EXPENSE, amount = 100_000, category = FOOD_ID, from = CASH_ID, id = 2).copy(note = "revisi catatan"),
            accounts = accounts, existing = existing,
        )
        assertEquals(emptyList<ValidationError>(), errors)
    }

    @Test
    fun archivedDriftIsRejectedEvenWhenNegativeBalancesAreAllowed() {
        val accounts = listOf(bank(), cash(opening = 0, archived = true))
        val existing = listOf(income(id = 1, amount = 100_000, to = CASH_ID), expense(id = 2, amount = 100_000, from = CASH_ID))
        val errors = validate(
            draft(TransactionType.EXPENSE, amount = 60_000, category = FOOD_ID, from = CASH_ID, id = 2),
            accounts = accounts, existing = existing, allowNegative = true,
        )
        assertEquals(listOf<ValidationError>(ValidationError.ArchivedAccountNotZero(CASH_ID, 40_000)), errors)
    }

    @Test
    fun archivedDriftHelperIgnoresAccountsThatDidNotChange() {
        val accounts = listOf(bank(), cash(opening = 5_000, archived = true)) // already non-zero (e.g. restored data)
        val before = mapOf(BANK_ID to 10L, CASH_ID to 5_000L)
        assertEquals(emptyList<ValidationError.ArchivedAccountNotZero>(), TransactionValidator.archivedAccountDrift(accounts, before, before))
        assertEquals(
            listOf(ValidationError.ArchivedAccountNotZero(CASH_ID, 4_000)),
            TransactionValidator.archivedAccountDrift(accounts, before, mapOf(BANK_ID to 10L, CASH_ID to 4_000L)),
        )
    }
}
