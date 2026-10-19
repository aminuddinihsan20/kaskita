package com.kaskita.lokal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.Category
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.common.CategoryBadge
import com.kaskita.lokal.ui.common.CategoryVisuals
import com.kaskita.lokal.ui.common.LoadingState
import com.kaskita.lokal.ui.common.ScreenScaffold
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.theme.ExpressiveShapes
import com.kaskita.lokal.ui.theme.finance

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // null = no dialog; a Category = edit; `adding` = new
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    val dialogOpen = adding || editing != null
    val messageText = state.message?.let { categoryMessageText(it) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        if (message == CategoryMessage.Saved) {
            adding = false
            editing = null
        }
        if (!(message != CategoryMessage.Saved && dialogOpen)) {
            snackbar.showSnackbar(messageText.orEmpty())
            viewModel.onMessageShown()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.cat_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cat_add), style = MaterialTheme.typography.titleSmall) },
                containerColor = MaterialTheme.finance.cta,
                contentColor = MaterialTheme.finance.onCta,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { inner ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(inner))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            section(R.string.cat_expense, state.expense, false, { editing = it }, viewModel::onArchive)
            section(R.string.cat_income, state.income, false, { editing = it }, viewModel::onArchive)
            if (state.archived.isNotEmpty()) section(R.string.cat_archived, state.archived, true, { editing = it }, viewModel::onArchive)
        }
    }

    if (dialogOpen) {
        CategoryEditDialog(
            existing = editing,
            saving = state.isSaving,
            errorText = messageText?.takeIf { state.message != CategoryMessage.Saved },
            onDismiss = { adding = false; editing = null; viewModel.onMessageShown() },
            onSave = { name, type, icon, color -> viewModel.onSave(editing?.id, name, type, icon, color) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    categories: List<Category>,
    archivedSection: Boolean,
    onEdit: (Category) -> Unit,
    onArchive: (Long, Boolean) -> Unit,
) {
    item(key = "title-$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
    }
    item(key = "card-$titleRes") {
        SoftCard(padding = 8.dp) {
            categories.forEachIndexed { index, category ->
                CategoryRowItem(category, archivedSection, onClick = { onEdit(category) }, onToggleArchive = { onArchive(category.id, !archivedSection) })
                if (index < categories.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun CategoryRowItem(category: Category, archived: Boolean, onClick: () -> Unit, onToggleArchive: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = false, onClick = onClick, role = Role.Button)
            .heightIn(min = 68.dp)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryBadge(category.iconKey, category.colorKey, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
            if (category.isDefault) {
                Text(stringResource(R.string.cat_default_badge), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onToggleArchive) {
            Text(stringResource(if (archived) R.string.cat_unarchive else R.string.cat_archive))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditDialog(
    existing: Category?,
    errorText: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, type: TransactionType, iconKey: String, colorKey: String) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var icon by remember(existing?.id) { mutableStateOf(existing?.iconKey ?: CategoryVisuals.iconKeys.first()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorKey ?: CategoryVisuals.colorKeys.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.cat_form_add else R.string.cat_form_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cat_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing == null) {
                    Text(stringResource(R.string.cat_type), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(type == TransactionType.EXPENSE, { type = TransactionType.EXPENSE }, label = { Text(stringResource(R.string.type_expense)) }, shape = MaterialTheme.shapes.large)
                        FilterChip(type == TransactionType.INCOME, { type = TransactionType.INCOME }, label = { Text(stringResource(R.string.type_income)) }, shape = MaterialTheme.shapes.large)
                    }
                }
                Text(stringResource(R.string.cat_icon), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryVisuals.iconKeys.forEach { key ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(ExpressiveShapes.forKey(key))
                                .background(if (selected) CategoryVisuals.container(color) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { icon = key })
                                .semantics { contentDescription = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                CategoryVisuals.icon(key),
                                contentDescription = null,
                                tint = if (selected) CategoryVisuals.tint(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                Text(stringResource(R.string.cat_color), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryVisuals.colorKeys.forEach { key ->
                        val selected = key == color
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CategoryVisuals.container(key))
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { color = key })
                                .semantics { contentDescription = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = CategoryVisuals.tint(key))
                        }
                    }
                }
                if (errorText != null) Text(errorText, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, type, icon, color) }, enabled = !saving) { Text(stringResource(R.string.acc_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
