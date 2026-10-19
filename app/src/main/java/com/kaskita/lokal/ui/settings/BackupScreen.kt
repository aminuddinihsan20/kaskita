package com.kaskita.lokal.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.ui.common.PrimaryButton
import com.kaskita.lokal.ui.common.QuietButton
import com.kaskita.lokal.ui.common.ScreenScaffold
import com.kaskita.lokal.ui.common.SoftCard
import com.kaskita.lokal.ui.common.formatDateTime
import java.time.ZoneId

@Composable
fun BackupScreen(viewModel: BackupViewModel, zone: ZoneId, nowMillis: () -> Long, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        viewModel.onBackupTargetChosen(uri?.toString(), uri?.let { displayName(context, it) } ?: "")
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        viewModel.onRestoreFileChosen(uri?.toString())
    }
    val working = state.phase is BackupPhase.Working

    ScreenScaffold(title = stringResource(R.string.bk_title), onBack = onBack) { inner ->
        Column(
            modifier = Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (working) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                Text(stringResource(R.string.bk_working), style = MaterialTheme.typography.bodyMedium)
            }

            SoftCard {
                Text(stringResource(R.string.bk_section_backup), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bk_backup_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                Text(
                    state.lastBackupAt?.let { stringResource(R.string.bk_last, formatDateTime(it, zone)) } ?: stringResource(R.string.bk_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.bk_backup_button),
                    onClick = { createLauncher.launch(suggestedBackupFileName(nowMillis(), zone)) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }

            SoftCard {
                Text(stringResource(R.string.bk_section_restore), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bk_restore_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                QuietButton(
                    text = stringResource(R.string.bk_restore_button),
                    onClick = { openLauncher.launch(arrayOf("*/*")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }

            state.safetyBackup?.let { safety ->
                SoftCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.bk_section_safety), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        stringResource(R.string.bk_safety_desc, formatDateTime(safety.createdAt, zone)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    QuietButton(
                        text = stringResource(R.string.bk_safety_button),
                        onClick = viewModel::onRestoreSafetyBackup,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
            }

            Text(
                stringResource(R.string.bk_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    when (val phase = state.phase) {
        is BackupPhase.BackupDone -> ResultDialog(
            message = stringResource(R.string.bk_done_backup, phase.fileName.ifBlank { "backup.json" }, phase.transactionCount),
            onDismiss = viewModel::onDismissResult,
        )
        is BackupPhase.RestoreDone -> ResultDialog(
            message = stringResource(R.string.bk_done_restore, phase.summary.accountCount, phase.summary.categoryCount, phase.summary.transactionCount),
            onDismiss = viewModel::onDismissResult,
        )
        is BackupPhase.Failed -> ResultDialog(backupFailureText(phase.failure), viewModel::onDismissResult)
        is BackupPhase.RestorePreview -> AlertDialog(
            onDismissRequest = viewModel::onCancelRestore,
            title = { Text(stringResource(R.string.bk_preview_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.bk_preview_body,
                        formatDateTime(phase.summary.backupCreatedAt, zone),
                        phase.summary.accountCount,
                        phase.summary.categoryCount,
                        phase.summary.transactionCount,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::onConfirmRestore) { Text(stringResource(R.string.bk_preview_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::onCancelRestore) { Text(stringResource(R.string.common_cancel)) } },
        )
        BackupPhase.Idle, BackupPhase.Working -> Unit
    }
}

@Composable
private fun ResultDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.bk_dismiss)) } },
    )
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
