package com.kaskita.lokal.ui.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.R
import com.kaskita.lokal.appCheckDebugToken

/** Reads this installation's App Check code from the SDK and shows it. */
@Composable
fun AppCheckCodeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val token = remember { appCheckDebugToken(context) }
    AppCheckCodeContent(token = token, onCopy = { code ->
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("kode", code))
    }, onDismiss = onDismiss)
}

/** The dialog itself, with the code passed in so it can be tested without Firebase. */
@Composable
fun AppCheckCodeContent(token: String?, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appcheck_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.appcheck_body), style = MaterialTheme.typography.bodyMedium)
                if (token != null) {
                    Surface(Modifier.padding(top = 12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        SelectionContainer {
                            Text(token, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp))
                        }
                    }
                    Text(stringResource(R.string.appcheck_secret), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(R.string.appcheck_missing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            if (token != null) {
                TextButton(onClick = { onCopy(token); copied = true }) {
                    Text(stringResource(if (copied) R.string.appcheck_copied else R.string.appcheck_copy))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_close)) } },
    )
}
