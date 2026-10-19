package com.kaskita.lokal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kaskita.lokal.data.LedgerRepository
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CategoryMessage { Saved, NameBlank, Rejected, Failed }

data class CategoriesUiState(
    val expense: List<Category> = emptyList(),
    val income: List<Category> = emptyList(),
    val archived: List<Category> = emptyList(),
    val message: CategoryMessage? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
)

class CategoriesViewModel(private val repo: LedgerRepository) : ViewModel() {
    private val message = MutableStateFlow<CategoryMessage?>(null)
    private val saving = MutableStateFlow(false)

    val state: StateFlow<CategoriesUiState> = combine(repo.observeCategories(), message, saving) { categories, msg, isSaving ->
        CategoriesUiState(
            expense = categories.filter { it.type == TransactionType.EXPENSE && !it.isArchived },
            income = categories.filter { it.type == TransactionType.INCOME && !it.isArchived },
            archived = categories.filter { it.isArchived },
            message = msg,
            isSaving = isSaving,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CategoriesUiState())

    fun onSave(id: Long?, name: String, type: TransactionType, iconKey: String, colorKey: String) {
        if (name.isBlank()) {
            message.value = CategoryMessage.NameBlank
            return
        }
        if (!saving.compareAndSet(false, true)) return // a double tap must not create two categories
        viewModelScope.launch {
            try {
                message.value = if (repo.saveCategory(id, name, type, iconKey, colorKey)) CategoryMessage.Saved else CategoryMessage.Rejected
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = CategoryMessage.Failed
            } finally {
                saving.value = false
            }
        }
    }

    fun onArchive(id: Long, archived: Boolean) {
        viewModelScope.launch { repo.setCategoryArchived(id, archived) }
    }

    fun onMessageShown() {
        message.value = null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { CategoriesViewModel(container.ledger) } }
    }
}
