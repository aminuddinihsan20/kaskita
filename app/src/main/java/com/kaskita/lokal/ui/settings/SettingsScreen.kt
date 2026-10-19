package com.kaskita.lokal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.BuildConfig
import com.kaskita.lokal.R
import com.kaskita.lokal.data.ThemeMode
import com.kaskita.lokal.ui.common.ConnectedToggleGroup
import com.kaskita.lokal.ui.common.IconBadge
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.TabScreen

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    aiSetup: AiSetupViewModel,
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onBackup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ai by aiSetup.state.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }
    var showAiSetup by remember { mutableStateOf(false) }
    val themes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val scheme = MaterialTheme.colorScheme

    TabScreen(title = stringResource(R.string.settings_title)) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_section_data)) }
            item {
                SoftCard(padding = 8.dp) {
                    MenuRow(Icons.Filled.AccountBalanceWallet, scheme.primaryContainer, scheme.onPrimaryContainer, stringResource(R.string.settings_accounts), stringResource(R.string.settings_accounts_desc), onAccounts)
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = scheme.outlineVariant.copy(alpha = 0.5f))
                    MenuRow(Icons.Filled.Category, scheme.tertiaryContainer, scheme.onTertiaryContainer, stringResource(R.string.settings_categories), stringResource(R.string.settings_categories_desc), onCategories)
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = scheme.outlineVariant.copy(alpha = 0.5f))
                    MenuRow(Icons.Filled.Save, scheme.secondaryContainer, scheme.onSecondaryContainer, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_desc), onBackup)
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_ai)) }
            item {
                SoftCard(padding = 8.dp) {
                    val status = if (ai.ready) stringResource(R.string.settings_ai_on, ai.model) else stringResource(R.string.settings_ai_off)
                    MenuRow(Icons.Filled.AutoAwesome, scheme.tertiaryContainer, scheme.onTertiaryContainer, stringResource(R.string.settings_ai), status) { showAiSetup = true }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_display)) }
            item {
                SoftCard {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                    ConnectedToggleGroup(
                        options = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                        selectedIndex = themes.indexOf(state.themeMode),
                        onSelect = { viewModel.onThemeMode(themes[it]) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_rules)) }
            item {
                SoftCard(padding = 16.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .toggleable(
                                value = state.allowNegativeBalance,
                                role = Role.Switch,
                                onValueChange = viewModel::onAllowNegativeBalance,
                            )
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_negative), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_negative_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.allowNegativeBalance, onCheckedChange = null)
                    }
                }
            }
            item {
                SoftCard(padding = 8.dp) {
                    MenuRow(Icons.Filled.Info, scheme.surfaceContainerHigh, scheme.onSurfaceVariant, stringResource(R.string.settings_about), null) { showAbout = true }
                }
            }
        }
    }

    if (showAiSetup) AiSetupDialog(aiSetup, onDismiss = { showAiSetup = false })

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = { Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.about_close)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, container: Color, tint: Color, title: String, description: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, container, tint, title, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
