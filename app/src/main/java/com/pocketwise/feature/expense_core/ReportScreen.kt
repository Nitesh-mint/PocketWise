package com.pocketwise.feature.expense_core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.CategoryIcon
import com.pocketwise.core.model.Expense
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.icons.imageVector
import com.pocketwise.core.ui.theme.AppColors
import com.pocketwise.core.util.formatAmount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val DayFormat = DateTimeFormatter.ofPattern("MMM d")

private data class CategorySlice(val name: String, val category: Category?, val amount: Double, val color: Color)

// The app bar and month switcher are shared across tabs (see MainActivity);
// this screen is just the page content below them.
@Composable
fun ReportScreen(
    viewModel: ExpenseViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val monthLabel by viewModel.monthLabel.collectAsState()
    val month by viewModel.selectedMonth.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val categories by categoryViewModel.categories.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val symbol = currencySymbolFor(currencyCode)

    val dark = isSystemInDarkTheme()
    val slices = remember(expenses, categories, dark) { buildSlices(expenses, categories, dark) }
    val daily = remember(expenses, month) { dailyTotals(expenses, month) }
    val total = expenses.sumOf { it.amount }
    val today = LocalDate.now()
    val daysElapsed = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "total") { TotalHeader(total = total, comparison = comparison, symbol = symbol) }

        if (expenses.isEmpty()) {
            item(key = "empty") { EmptyReport(monthLabel) }
        } else {
            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Daily avg", "$symbol${compactAmount(total / daysElapsed)}", Modifier.weight(1f))
                    StatTile("Transactions", expenses.size.toString(), Modifier.weight(1f))
                    StatTile("Largest", "$symbol${compactAmount(expenses.maxOf { it.amount })}", Modifier.weight(1f))
                }
            }
            item(key = "daily") { DailySpendingCard(daily = daily, month = month, symbol = symbol) }
            item(key = "categories") { CategoryBreakdownCard(slices = slices, total = total, symbol = symbol) }
        }
    }
}

// Color follows the category, never its rank: slots go by creation order (id), so
// a category keeps the same color in every month. The catch-all "Other" and
// anything past the 8 validated slots share one neutral — a generated 9th hue
// would be indistinguishable under color blindness.
private fun buildSlices(expenses: List<Expense>, categories: List<Category>, dark: Boolean): List<CategorySlice> {
    val palette = if (dark) AppColors.chartCategoricalDark else AppColors.chartCategoricalLight
    val neutral = if (dark) AppColors.chartNeutralDark else AppColors.chartNeutralLight
    val colorByName = categories
        .filter { it.name != "Other" }
        .sortedBy { it.id }
        .zip(palette) { category, color -> category.name to color }
        .toMap()
    val categoryByName = categories.associateBy { it.name }

    return expenses.groupBy { it.category }
        .map { (name, items) -> CategorySlice(name, categoryByName[name], items.sumOf { it.amount }, colorByName[name] ?: neutral) }
        .sortedByDescending { it.amount }
}

private fun dailyTotals(expenses: List<Expense>, month: YearMonth): DoubleArray {
    val totals = DoubleArray(month.lengthOfMonth())
    val zone = ZoneId.systemDefault()
    expenses.forEach {
        val day = Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfMonth
        totals[(day - 1).coerceIn(totals.indices)] += it.amount
    }
    return totals
}

// Tiles are narrow: whole units from 1,000 up and K/M past 10K, so values never truncate.
private fun compactAmount(amount: Double): String = when {
    amount >= 1_000_000 -> "%.1f".format(amount / 1_000_000).removeSuffix(".0") + "M"
    amount >= 10_000 -> "%.1f".format(amount / 1_000).removeSuffix(".0") + "K"
    amount >= 1_000 -> amount.roundToInt().toString()
    else -> formatAmount(amount)
}

private fun sharePercent(share: Float): String = if (share < 0.01f) "<1%" else "${(share * 100).roundToInt()}%"

@Composable
private fun ReportCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun TotalHeader(total: Double, comparison: PeriodComparison?, symbol: String) {
    Column {
        Text("Total spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$symbol${formatAmount(total)}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (comparison != null && comparison.previousTotal > 0) {
            DeltaPill(total = total, comparison = comparison, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// Spending up is bad, down is good. The icon + words carry the meaning; color
// only reinforces it, and the text itself stays in the normal ink.
@Composable
private fun DeltaPill(total: Double, comparison: PeriodComparison, modifier: Modifier = Modifier) {
    val change = (total - comparison.previousTotal) / comparison.previousTotal
    val percent = (abs(change) * 100).roundToInt()
    val (icon, tint, text) = when {
        percent == 0 -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, "Same as ${comparison.label}")
        change > 0 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, MaterialTheme.colorScheme.error, "$percent% more than ${comparison.label}")
        else -> Triple(Icons.AutoMirrored.Filled.TrendingDown, AppColors.success, "$percent% less than ${comparison.label}")
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ReportCard(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// One series, so no categorical color: emphasis instead — the day in focus in
// full ink, every other day a recessive gray. Tap a column to inspect it; tap
// again to return to the highest day.
@Composable
private fun DailySpendingCard(daily: DoubleArray, month: YearMonth, symbol: String) {
    val peak = daily.indices.maxBy { daily[it] }
    val max = daily[peak]
    var selected by remember(month) { mutableIntStateOf(-1) }
    val focus = if (selected >= 0) selected else peak

    val focusColor = MaterialTheme.colorScheme.onSurface
    val restColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val ticks = listOf(1, 8, 15, 22, daily.size)

    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text("Daily spending", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val date = month.atDay(focus + 1).format(DayFormat)
                Text(
                    if (selected >= 0) date else "Highest · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$symbol${formatAmount(daily[focus])}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp) // includes the x-axis label band, so ticks are never clipped
                .pointerInput(month) {
                    detectTapGestures { offset ->
                        val day = (offset.x / size.width * daily.size).toInt().coerceIn(daily.indices)
                        selected = if (day == selected) -1 else day
                    }
                }
                .semantics {
                    contentDescription = "Daily spending chart. Highest day " +
                        "${month.atDay(peak + 1).format(DayFormat)}, $symbol${formatAmount(max)}"
                }
        ) {
            val plotHeight = size.height - 20.dp.toPx()
            val slot = size.width / daily.size
            val barWidth = (slot * 0.62f).coerceAtMost(24.dp.toPx())
            val radius = CornerRadius(2.dp.toPx())

            daily.forEachIndexed { day, value ->
                if (value <= 0) return@forEachIndexed
                val height = (value / max * plotHeight).toFloat().coerceAtLeast(3.dp.toPx())
                val left = day * slot + (slot - barWidth) / 2
                // Rounded data-end, square at the baseline.
                val bar = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left, plotHeight - height, left + barWidth, plotHeight,
                            topLeftCornerRadius = radius, topRightCornerRadius = radius
                        )
                    )
                }
                drawPath(bar, if (day == focus) focusColor else restColor)
            }

            drawLine(axisColor, Offset(0f, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1.dp.toPx())

            ticks.forEach { day ->
                val label = measurer.measure(day.toString(), tickStyle)
                val x = ((day - 0.5f) * slot - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(x, plotHeight + 6.dp.toPx()))
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(slices: List<CategorySlice>, total: Double, symbol: String) {
    val gapColor = MaterialTheme.colorScheme.surfaceVariant

    ReportCard {
        Text("By category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))

        // Part-to-whole as one stacked bar, not a donut — donuts stop reading
        // past ~6 slices, and people routinely keep more categories than that.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
        ) {
            val gap = 2.dp.toPx()
            var x = 0f
            slices.forEach { slice ->
                val width = (slice.amount / total * size.width).toFloat()
                drawRect(slice.color, topLeft = Offset(x, 0f), size = Size(width, size.height))
                x += width
                // Surface-colored gap between segments instead of an outline.
                if (x < size.width - 1f) drawRect(gapColor, topLeft = Offset(x - gap / 2, 0f), size = Size(gap, size.height))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Every slice also listed with its name, amount and share, so no value
        // depends on reading the colors.
        slices.forEach { slice ->
            CategoryBreakdownRow(slice = slice, share = (slice.amount / total).toFloat(), symbol = symbol)
        }
    }
}

@Composable
private fun CategoryBreakdownRow(slice: CategorySlice, share: Float, symbol: String) {
    val limit = slice.category?.monthlyBudget ?: 0.0
    val hasLimit = limit > 0
    val overLimit = hasLimit && slice.amount > limit

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        // Neutral icon avatar; the chart color rides a small ringed dot, linking
        // the row to its segment above without coloring any text.
        Box(modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    (slice.category?.icon ?: CategoryIcon.OTHER).imageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .background(slice.color, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slice.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$symbol${formatAmount(slice.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (hasLimit) {
                LinearProgressIndicator(
                    progress = { (slice.amount / limit).toFloat().coerceIn(0f, 1f) },
                    color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (overLimit) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                    }
                    Text(
                        if (overLimit) {
                            "Over by $symbol${formatAmount(slice.amount - limit)} of $symbol${formatAmount(limit)} limit"
                        } else {
                            "$symbol${formatAmount(limit - slice.amount)} left of $symbol${formatAmount(limit)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        sharePercent(share),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "${sharePercent(share)} of spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyReport(monthLabel: String) {
    ReportCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.PieChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                "No expenses in $monthLabel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "Your daily trend and category breakdown will appear here once you log spending.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
