@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaskita.lokal.ui.settings

import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.TestData
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.testing.FakeLedgerRepository
import com.kaskita.lokal.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoriesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun repo() = FakeLedgerRepository(
        categories = listOf(
            TestData.foodCategory(),
            Category(11, "Lama", TransactionType.EXPENSE, "home", "brown", false, true),
            TestData.salaryCategory(),
        ),
    )

    @Test
    fun listsAreSplitByTypeAndArchivedKeptSeparate() = runTest {
        val vm = CategoriesViewModel(repo())
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(listOf("Makanan & minuman"), s.expense.map { it.name })
        assertEquals(listOf("Gaji"), s.income.map { it.name })
        assertEquals(listOf("Lama"), s.archived.map { it.name })
    }

    @Test
    fun customCategoryIsPassedToTheRepository() = runTest {
        val repo = repo()
        val vm = CategoriesViewModel(repo)
        advanceUntilIdle()
        vm.onSave(null, "Zakat", TransactionType.EXPENSE, "star", "teal")
        advanceUntilIdle()
        assertEquals(listOf("Zakat"), repo.categorySaves)
        assertEquals(CategoryMessage.Saved, vm.state.value.message)
    }

    @Test
    fun blankNameIsRejected() = runTest {
        val vm = CategoriesViewModel(repo())
        advanceUntilIdle()
        vm.onSave(null, "   ", TransactionType.EXPENSE, "star", "teal")
        advanceUntilIdle()
        assertEquals(CategoryMessage.NameBlank, vm.state.value.message)
    }

    @Test
    fun archiveAndUnarchiveMoveACategoryBetweenLists() = runTest {
        val repo = repo()
        val vm = CategoriesViewModel(repo)
        advanceUntilIdle()
        vm.onArchive(TestData.FOOD_ID, archived = true)
        advanceUntilIdle()
        assertTrue(vm.state.value.expense.isEmpty())
        assertEquals(setOf("Lama", "Makanan & minuman"), vm.state.value.archived.map { it.name }.toSet())
        vm.onArchive(TestData.FOOD_ID, archived = false)
        advanceUntilIdle()
        assertEquals(listOf("Makanan & minuman"), vm.state.value.expense.map { it.name })
    }

    @Test
    fun doubleTapSaveCreatesOneCategory() = runTest {
        val repo = repo().apply { categoryGate = CompletableDeferred() }
        val vm = CategoriesViewModel(repo)
        advanceUntilIdle()
        vm.onSave(null, "Zakat", TransactionType.EXPENSE, "star", "teal")
        vm.onSave(null, "Zakat", TransactionType.EXPENSE, "star", "teal")
        advanceUntilIdle()
        assertTrue(vm.state.value.isSaving)
        repo.categoryGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Zakat"), repo.categorySaves)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun anUnexpectedFailureIsReportedAndTheGuardIsReleased() = runTest {
        val repo = repo().apply { failWith = IllegalStateException("disk full") }
        val vm = CategoriesViewModel(repo)
        advanceUntilIdle()
        vm.onSave(null, "Zakat", TransactionType.EXPENSE, "star", "teal")
        advanceUntilIdle()
        assertEquals(CategoryMessage.Failed, vm.state.value.message)
        assertFalse(vm.state.value.isSaving)
    }
}
