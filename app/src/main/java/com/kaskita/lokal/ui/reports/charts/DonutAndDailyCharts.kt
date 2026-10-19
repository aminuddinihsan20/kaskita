package com.kaskita.lokal.ui.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.R
import com.kaskita.lokal.domain.DayTotal
import com.kaskita.lokal.domain.RupiahFormatter
import com.kaskita.lokal.ui.common.CategoryVisuals
import com.kaskita.lokal.ui.common.RupiahText
import com.kaskita.lokal.ui.reports.CategoryRow
import com.kaskita.lokal.ui.theme.finance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GAP_DEGREES = 2f

fun CategoryRow.visualColorKey(): String = category?.colorKey ?: "gray"

/**
 * Ring of expense categories. Slices are also listed with name, amount, count and percentage next to the chart,
 * so the information never depends on telling colors apart.
 */
@Composable
fun ExpenseDonutChart(
    rows: List<CategoryRow>,
    selectedCategoryId: Long?,
    totalExpense: Long,
    modifier: Modifier = Modifier,
    diameter: Dp = 190.dp,
) {
    val colors = rows.map { CategoryVisuals.chart(it.visualColorKey()) }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val unknown = stringResource(R.string.rp_uncategorized)
    val description = stringResource(
        R.string.rp_category_description,
        rows.map { row ->
            stringResource(
                R.string.rp_category_description_item,
                row.category?.name ?: unknown,
                RupiahFormatter.format(row.total),
                row.count,
                (row.share * 100).roundToInt(),
            )
        }.joinToString(" "),
    )

    Box(modifier.size(diameter).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val normal = 26.dp.toPx()
            val thick = 34.dp.toPx()
            val inset = thick / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(normal))
            var start = -90f
            rows.forEachIndexed { index, row ->
                val sweep = 360f * row.share
                val selected = row.category?.id != null && row.category.id == selectedCategoryId
                val drawn = if (rows.size == 1) sweep else max(0f, sweep - GAP_DEGREES)
                drawArc(
                    color = colors[index],
                    startAngle = start + if (rows.size == 1) 0f else GAP_DEGREES / 2f,
                    sweepAngle = drawn,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(if (selected) thick else normal),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.rp_category_total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RupiahText(
                amount = totalExpense,
                text = RupiahFormatter.compact(totalExpense),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.finance.expense,
            )
            Text("Rp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Expense per day of the month; x labels every few days, y labels abbreviated. */
@Composable
fun DailyExpenseChart(data: List<DayTotal>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val barColor = MaterialTheme.finance.expenseBar
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val maxValue = max(1L, data.maxOfOrNull { it.expense } ?: 0L)
    val description = stringResource(
        R.string.rp_daily_description,
        data.filter { it.expense > 0 }.map { stringResource(R.string.rp_daily_description_item, it.day.dayOfMonth, RupiahFormatter.format(it.expense)) }
            .joinToString(" "),
    )

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
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

        val slot = plotWidth / data.size
        val barWidth = min(slot * 0.7f, 14.dp.toPx())
        val baseY = top + plotHeight
        data.forEachIndexed { index, day ->
            val centerX = left + slot * (index + 0.5f)
            if (day.expense > 0L) {
                val h = max(2.dp.toPx(), plotHeight * (day.expense.toFloat() / maxValue))
                drawRoundRect(barColor, Offset(centerX - barWidth / 2f, baseY - h), Size(barWidth, h), androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
            }
            val number = day.day.dayOfMonth
            if (number == 1 || number % 5 == 0) {
                val label = measurer.measure(number.toString(), labelStyle)
                drawText(label, topLeft = Offset(centerX - label.size.width / 2f, baseY + 4.dp.toPx()))
            }
        }
    }
}
