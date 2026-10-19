package com.kaskita.lokal.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.TransactionType
import com.kaskita.lokal.ui.common.CategoryBadge
import com.kaskita.lokal.ui.common.CategoryVisuals
import com.kaskita.lokal.ui.common.IconBadge
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.common.formatDateShort
import com.kaskita.lokal.ui.common.signedAmountText
import com.kaskita.lokal.ui.theme.finance
import java.time.ZoneId

@Composable
fun transactionTitle(row: TxRow): String =
    row.tx.note?.takeIf { it.isNotBlank() } ?: row.category?.name ?: stringResource(R.string.type_transfer)

@Composable
fun typeColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> MaterialTheme.finance.income
    TransactionType.EXPENSE -> MaterialTheme.finance.expense
    TransactionType.TRANSFER -> MaterialTheme.finance.transfer
}

/** One line of history. Direction is shown by sign (+, −, ↔) and text, not by color alone. */
@Composable
fun TransactionRow(
    row: TxRow,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val tx = row.tx
    val detail = when (tx.type) {
        TransactionType.INCOME -> listOfNotNull(row.category?.name.takeIf { tx.note != null }, row.to?.name)
        TransactionType.EXPENSE -> listOfNotNull(row.category?.name.takeIf { tx.note != null }, row.from?.name)
        TransactionType.TRANSFER -> listOf("${row.from?.name.orEmpty()} → ${row.to?.name.orEmpty()}")
    }.let { parts -> if (showDate) listOf(formatDateShort(tx.dateTime, zone)) + parts else parts }.joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val category = row.category
        if (tx.type == TransactionType.TRANSFER || category == null) {
            IconBadge(
                icon = if (tx.type == TransactionType.TRANSFER) Icons.Filled.SwapHoriz else CategoryVisuals.icon(""),
                container = MaterialTheme.finance.transferContainer,
                tint = MaterialTheme.finance.onTransferContainer,
                shapeKey = "transfer",
            )
        } else {
            CategoryBadge(category.iconKey, category.colorKey)
        }
        Column(Modifier.weight(1f)) {
            Text(transactionTitle(row), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            RupiahText(
                amount = tx.amount,
                text = signedAmountText(tx.type, tx.amount),
                style = MaterialTheme.typography.titleSmall,
                color = typeColor(tx.type),
            )
            if (tx.type == TransactionType.TRANSFER) {
                Text(stringResource(R.string.type_transfer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
