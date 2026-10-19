package com.kaskita.lokal.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.theme.finance

/** The "Catat transaksi" chooser: Pemasukan, Pengeluaran, or Transfer / Tarik ke Cash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(onPick: (TransactionType) -> Unit, onSavings: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.finance
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.add_sheet_title), style = MaterialTheme.typography.titleMedium)
            TonalButton(stringResource(R.string.add_income), { onPick(TransactionType.INCOME) }, Modifier.fillMaxWidth(), container = colors.incomeContainer, content = colors.onIncomeContainer)
            TonalButton(stringResource(R.string.add_expense), { onPick(TransactionType.EXPENSE) }, Modifier.fillMaxWidth(), container = colors.expenseContainer, content = colors.onExpenseContainer)
            TonalButton(stringResource(R.string.add_transfer), { onPick(TransactionType.TRANSFER) }, Modifier.fillMaxWidth(), container = colors.transferContainer, content = colors.onTransferContainer)
            TonalButton(stringResource(R.string.add_savings), onSavings, Modifier.fillMaxWidth(), container = colors.savingsContainer, content = colors.onSavingsContainer)
        }
    }
}
