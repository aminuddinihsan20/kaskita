package com.kaskita.lokal.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.R
import com.kaskita.lokal.data.ai.GeminiError
import com.kaskita.lokal.domain.RecapAdvice
import com.kaskita.lokal.domain.RecapPrompt
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.domain.SavingsStatus
import com.kaskita.lokal.ui.common.MorphLoadingIndicator
import com.kaskita.lokal.ui.common.PrimaryButton
import com.kaskita.lokal.ui.common.TonalButton
import com.kaskita.lokal.ui.theme.finance

private fun rp(amount: Long) = RupiahFormatter.format(amount)

/** Next-month saving and spending guidance. Computed on the phone; needs no AI, key or internet. */
@Composable
internal fun AdviceCard(state: ReportsUiState) {
    val advice = state.advice
    val colors = MaterialTheme.finance
    SectionCard(
        title = stringResource(R.string.rp_advice_title),
        subtitle = advice?.let { stringResource(R.string.rp_advice_caption, it.targetPercent) },
    ) {
        if (advice == null) {
            Text(stringResource(R.string.rp_advice_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Line(stringResource(R.string.rp_advice_target), advice.savingsTarget, rp(advice.savingsTarget), MaterialTheme.colorScheme.onSurface, emphasized = true)
        Line(stringResource(R.string.rp_advice_limit), advice.spendingLimit, rp(advice.spendingLimit), MaterialTheme.colorScheme.onSurface, emphasized = true)

        val (container, content) = when (advice.status) {
            SavingsStatus.ON_TRACK -> colors.incomeContainer to colors.onIncomeContainer
            SavingsStatus.BELOW_TARGET -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            SavingsStatus.DEFICIT -> colors.expenseContainer to colors.onExpenseContainer
        }
        Surface(Modifier.padding(top = 8.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container) {
            Text(statusText(advice, deficit = -state.summary.net, expense = state.summary.expense), style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        if (advice.incomeFromAverage) {
            Text(
                stringResource(R.string.rp_advice_basis_average, rp(advice.baseIncome)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            stringResource(R.string.rp_advice_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun statusText(advice: RecapAdvice, deficit: Long, expense: Long): String = if (advice.incomeFromAverage) {
    if (advice.cutNeeded > 0) stringResource(R.string.rp_advice_pending_over, rp(expense), rp(advice.cutNeeded)) else stringResource(R.string.rp_advice_pending_ok, rp(expense))
} else when (advice.status) {
    SavingsStatus.ON_TRACK -> stringResource(R.string.rp_advice_on_track, rp(advice.savedThisMonth))
    SavingsStatus.BELOW_TARGET -> stringResource(R.string.rp_advice_below, rp(advice.savedThisMonth), rp(advice.savingsTarget), rp(advice.cutNeeded))
    SavingsStatus.DEFICIT -> stringResource(R.string.rp_advice_deficit, rp(deficit), rp(advice.cutNeeded))
}

@Composable
internal fun AiCard(
    state: ReportsUiState,
    monthText: String,
    onSetup: () -> Unit,
    onAnalyze: () -> Unit,
    onAnalyzeAgain: () -> Unit,
    onPreview: () -> Unit,
) {
    SectionCard(stringResource(R.string.ai_title), stringResource(R.string.ai_subtitle, monthText)) {
        when (val ai = state.ai) {
            AiRecapState.NoData -> Unit
            AiRecapState.NotReady -> {
                Text(stringResource(R.string.ai_not_ready_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton(stringResource(R.string.ai_setup), onSetup, Modifier.fillMaxWidth().padding(top = 14.dp), icon = Icons.Filled.AutoAwesome)
            }
            AiRecapState.Idle -> {
                PrimaryButton(stringResource(R.string.ai_analyze), onAnalyze, Modifier.fillMaxWidth(), icon = Icons.Filled.AutoAwesome)
                TextButton(onClick = onPreview, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.ai_preview_link)) }
                Text(stringResource(R.string.ai_model_label, state.aiModel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AiRecapState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MorphLoadingIndicator(size = 44.dp)
                Text(stringResource(R.string.ai_loading), style = MaterialTheme.typography.bodyLarge)
            }
            is AiRecapState.Done -> {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { AiTextBlock(ai.text) }
                Text(
                    stringResource(R.string.ai_done_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAnalyzeAgain) { Text(stringResource(R.string.ai_reanalyze)) }
                    TextButton(onClick = onPreview) { Text(stringResource(R.string.ai_preview_link)) }
                }
            }
            is AiRecapState.Failed -> {
                Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(errorText(ai.error)), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val needsSettings = ai.error in setOf(GeminiError.DENIED, GeminiError.MODEL_NOT_FOUND)
                    if (!needsSettings) {
                        PrimaryButton(stringResource(R.string.ai_retry), onAnalyze, Modifier.fillMaxWidth())
                    } else {
                        // Denied by the service: a retry may help; a wrong model name is the only thing the user can change.
                        PrimaryButton(stringResource(R.string.ai_retry), onAnalyze, Modifier.fillMaxWidth())
                        if (ai.error == GeminiError.MODEL_NOT_FOUND) TonalButton(stringResource(R.string.ai_change_settings), onSetup, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun errorText(error: GeminiError): Int = when (error) {
    GeminiError.DENIED -> R.string.ai_err_denied
    GeminiError.MODEL_NOT_FOUND -> R.string.ai_err_model
    GeminiError.RATE_LIMITED -> R.string.ai_err_rate
    GeminiError.SERVER -> R.string.ai_err_server
    GeminiError.TIMEOUT -> R.string.ai_err_timeout
    GeminiError.NETWORK -> R.string.ai_err_network
    GeminiError.BLOCKED -> R.string.ai_err_blocked
    GeminiError.INCOMPLETE -> R.string.ai_err_incomplete
    GeminiError.UNKNOWN -> R.string.ai_err_unknown
}

@Composable
private fun AiTextBlock(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parseAiText(text).forEachIndexed { index, line ->
            when (line) {
                is AiLine.Heading -> Text(
                    line.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp).semantics { heading() },
                )
                is AiLine.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                is AiLine.Body -> Text(line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Shows exactly the text that would be sent, so the user can check it before pressing Analisis. */
@Composable
internal fun AiPreviewDialog(prompt: RecapPrompt.Ready, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ai_preview_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(top = 10.dp))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(prompt.user, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, modifier = Modifier.padding(12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_close)) } },
    )
}
