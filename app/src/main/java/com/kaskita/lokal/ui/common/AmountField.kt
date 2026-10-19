package com.kaskita.lokal.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.kaskita.lokal.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType

private const val MAX_DIGITS = 13 // MAX_AMOUNT (Rp 1 triliun) has 13 digits

private fun groupDigits(digits: String): String = digits.reversed().chunked(3).joinToString(".").reversed()

/** `1250000` -> `1.250.000` for a number that is already known to be valid. */
fun groupThousands(amount: Long): String = groupDigits(amount.toString())

/**
 * Regroups typed or pasted text (`1250000` -> `1.250.000`, leading zeros dropped). Returns null when the text is not a plain
 * integer (comma, minus, letters, spaces, more than 13 digits) so the caller can reject it; it is never silently rewritten
 * into a different number, e.g. `2.500.000,00` must not turn into Rp250.000.000.
 */
fun formatAmountInput(raw: String): String? {
    if (raw.any { it != '.' && it !in '0'..'9' }) return null
    val digits = raw.filter { it in '0'..'9' }.trimStart('0')
    if (digits.length > MAX_DIGITS) return null
    return groupDigits(digits)
}

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var rejected by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val formatted = formatAmountInput(typed)
            rejected = formatted == null
            if (formatted != null) onValueChange(formatted)
        },
        modifier = modifier,
        label = { Text(label) },
        prefix = { Text("Rp") },
        singleLine = true,
        isError = error != null || rejected,
        supportingText = (error ?: if (rejected) stringResource(R.string.amount_rejected_hint) else null)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.headlineMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
    )
}
