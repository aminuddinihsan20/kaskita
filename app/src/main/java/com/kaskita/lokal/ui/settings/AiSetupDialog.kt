package com.kaskita.lokal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaskita.lokal.R
import com.kaskita.lokal.data.ai.DEFAULT_GEMINI_MODEL

/** The user's agreement (read first) and the model name for the Gemini recap analysis. */
@Composable
fun AiSetupDialog(viewModel: AiSetupViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return // the stored values are still being read; do not seed the form with placeholders
    var consent by remember { mutableStateOf(state.consent) }
    var modelInput by remember { mutableStateOf(state.model) }
    var showCode by remember { mutableStateOf(false) }

    fun close() {
        viewModel.onDialogClosed()
        onDismiss()
    }
    LaunchedEffect(state.saved) { if (state.saved) close() }

    AlertDialog(
        onDismissRequest = ::close,
        // The dialog is its own window: without these the keyboard covers the checkbox and the Simpan button.
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.ai_setup_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ai_setup_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ai_setup_sent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.ai_setup_not_sent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.ai_setup_via), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = consent, role = Role.Checkbox, onValueChange = { consent = it }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = consent, onCheckedChange = null)
                    Text(stringResource(R.string.ai_setup_consent), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_setup_model_label)) },
                    supportingText = { Text(stringResource(R.string.ai_setup_model_help, DEFAULT_GEMINI_MODEL)) },
                    singleLine = true,
                    isError = state.error == AiSetupError.MODEL_INVALID,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                state.error?.let { error ->
                    Text(
                        stringResource(if (error == AiSetupError.CONSENT_REQUIRED) R.string.ai_setup_err_consent else R.string.ai_setup_err_model),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { showCode = true }) { Text(stringResource(R.string.appcheck_show)) }
                if (state.consent) {
                    TextButton(onClick = { viewModel.onWithdrawConsent(); close() }, modifier = Modifier.padding(top = 2.dp)) {
                        Text(stringResource(R.string.ai_setup_withdraw))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.onSave(consent, modelInput) }) { Text(stringResource(R.string.ai_setup_save)) } },
        dismissButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.ai_setup_cancel)) } },
    )
    if (showCode) AppCheckCodeDialog(onDismiss = { showCode = false })
}
