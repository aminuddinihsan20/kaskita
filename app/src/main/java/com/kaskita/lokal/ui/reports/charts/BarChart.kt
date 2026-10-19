package com.kaskita.lokal.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.MonthTotals
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.ui.common.formatMonthShort
import com.kaskita.lokal.ui.common.formatMonthYearShort
import com.kaskita.lokal.ui.theme.finance
import kotlin.math.max
import kotlin.math.min

/** True when there is nothing to draw (all months zero) — callers show an empty state instead of a fake chart. */
fun List<MonthTotals>.isAllZero(): Boolean = all { it.income == 0L && it.expense == 0L }

/**
 * Grouped bars per month: income on the left, expense on the right. Axis labels are abbreviated (`1,2 jt`)
 * and the chart carries a text description of every value for screen readers.
 */
@Composable
fun IncomeExpenseBarChart(data: List<MonthTotals>, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    val incomeColor = MaterialTheme.finance.incomeBar
    val expenseColor = MaterialTheme.finance.expenseBar
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val maxValue = max(1L, data.maxOfOrNull { max(it.income, it.expense) } ?: 0L)

    val description = stringResource(
        R.string.chart_description,
        data.map { m ->
            stringResource(
                R.string.chart_description_month,
                formatMonthYearShort(m.month),
                RupiahFormatter.format(m.income),
                RupiahFormatter.format(m.expense),
            )
        }.joinToString(" "),
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val left = 48.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 24.dp.toPx()
        val plotWidth = size.width - left
        val plotHeight = size.height - top - bottom

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = top + plotHeight * (1f - fraction)
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(RupiahFormatter.compact((maxValue * fraction).toLong()), labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
        }

        if (data.isEmpty()) return@Canvas
        val groupWidth = plotWidth / data.size
        val barWidth = min(groupWidth * 0.34f, 18.dp.toPx())
        val gap = 1.dp.toPx()
        val radius = CornerRadius(barWidth / 2f)

        fun barHeight(value: Long): Float =
            if (value <= 0L) 0f else max(2.dp.toPx(), plotHeight * (value.toFloat() / maxValue))

        data.forEachIndexed { index, month ->
            val centerX = left + groupWidth * (index + 0.5f)
            val baseY = top + plotHeight
            val incomeHeight = barHeight(month.income)
            val expenseHeight = barHeight(month.expense)
            if (incomeHeight > 0f) {
                drawRoundRect(incomeColor, Offset(centerX - barWidth - gap, baseY - incomeHeight), Size(barWidth, incomeHeight), radius)
            }
            if (expenseHeight > 0f) {
                drawRoundRect(expenseColor, Offset(centerX + gap, baseY - expenseHeight), Size(barWidth, expenseHeight), radius)
            }
            val month3 = measurer.measure(formatMonthShort(month.month), labelStyle)
            drawText(month3, topLeft = Offset(centerX - month3.size.width / 2f, baseY + 4.dp.toPx()))
        }
    }
}

/** Legend with text, so the two series are told apart by position and name, not by color alone. */
@Composable
fun IncomeExpenseLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.finance.incomeBar, stringResource(R.string.chart_legend_income))
        LegendItem(MaterialTheme.finance.expenseBar, stringResource(R.string.chart_legend_expense))
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
