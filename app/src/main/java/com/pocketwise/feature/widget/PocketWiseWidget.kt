package com.pocketwise.feature.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pocketwise.MainActivity
import com.pocketwise.R
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.ExpenseRepository
import com.pocketwise.core.data.repository.RecurringRepository
import com.pocketwise.core.model.Expense
import com.pocketwise.core.model.currencySymbolFor
import com.pocketwise.core.ui.theme.AppColors
import com.pocketwise.core.util.endMillis
import com.pocketwise.core.util.formatAmount
import com.pocketwise.core.util.startMillis
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.glance.color.ColorProvider as DayNightColorProvider

const val EXTRA_OPEN_ADD_EXPENSE = "open_add_expense"
const val EXTRA_START_VOICE = "start_voice"
const val EXTRA_EDIT_EXPENSE_ID = "edit_expense_id"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun expenseRepository(): ExpenseRepository
    fun userPreferences(): UserPreferences
    fun recurringRepository(): RecurringRepository
}

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM")
private val ShortMonthFormat = DateTimeFormatter.ofPattern("MMM")
private val DayFormat = DateTimeFormatter.ofPattern("MMM d")

// The Home summary card, on the home screen: same color roles, fixed rather than
// wallpaper-derived, so the widget always reads as PocketWise.
private val CardColor = DayNightColorProvider(day = AppColors.nearBlack, night = AppColors.heroSurfaceDark)
private val InkColor = DayNightColorProvider(day = AppColors.nearWhite, night = AppColors.onSurfaceDark)
private val MutedColor = DayNightColorProvider(day = AppColors.nearWhite.copy(alpha = 0.6f), night = AppColors.mutedForegroundDark)
private val HairlineColor = ColorProvider(Color.White.copy(alpha = 0.12f))
private val ButtonColor = ColorProvider(Color.White.copy(alpha = 0.12f))
private val AccentColor = ColorProvider(AppColors.brandDark)
private val OnAccentColor = ColorProvider(Color.White)
private val OverColor = ColorProvider(AppColors.destructiveDark) // the dark-surface red: readable on the card in both modes

// Layout buckets. Responsive mode renders the largest bucket that fits the
// widget, and LocalSize reports that bucket's size.
private val Compact = DpSize(110.dp, 40.dp) // total + mic
private val Wide = DpSize(250.dp, 40.dp) // + add button
private val Tall = DpSize(250.dp, 150.dp) // + budget bar + 2 recent expenses
private val Taller = DpSize(250.dp, 260.dp) // + 4 recent expenses

class PocketWiseWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Wide, Tall, Taller))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        // The widget may render without the app being opened — keep rent & co. current here too.
        deps.recurringRepository().postDue()
        // ponytail: month fixed per widget session; sessions are short-lived, so it rolls over on the next update.
        val month = YearMonth.now()

        // Collected INSIDE provideContent, not read once up here: Glance re-runs
        // the composition on update, not this function, so values read outside
        // it stayed frozen for the whole session — that was the "not realtime" bug.
        val expensesFlow = deps.expenseRepository().expensesForRange(month.startMillis(), month.endMillis())
        val currencyFlow = deps.userPreferences().currencyCode
        val budgetFlow = deps.userPreferences().monthlyBudget
        // First values up front so the very first frame is already correct.
        val initialExpenses = expensesFlow.first()
        val initialCurrency = currencyFlow.first()
        val initialBudget = budgetFlow.first()

        provideContent {
            val expenses by expensesFlow.collectAsState(initialExpenses)
            val currency by currencyFlow.collectAsState(initialCurrency)
            val budget by budgetFlow.collectAsState(initialBudget)
            WidgetContent(
                context = context,
                month = month,
                expenses = expenses,
                symbol = currencySymbolFor(currency),
                budget = budget
            )
        }
    }
}

class PocketWiseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PocketWiseWidget()
}

@Composable
private fun WidgetContent(context: Context, month: YearMonth, expenses: List<Expense>, symbol: String, budget: Double) {
    val size = LocalSize.current
    val wide = size.width >= Wide.width
    val tall = size.height >= Tall.height
    val total = expenses.sumOf { it.amount }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(CardColor)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = if (tall) 14.dp else 8.dp),
        verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(
                    if (wide) "${month.format(MonthFormat)} · spent" else month.format(ShortMonthFormat),
                    style = TextStyle(color = MutedColor, fontSize = 12.sp),
                    maxLines = 1
                )
                Text(
                    "$symbol${formatAmount(total)}",
                    style = TextStyle(color = InkColor, fontSize = if (tall) 28.sp else 22.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            ActionButton(
                icon = R.drawable.ic_mic,
                description = "Log expense by voice",
                intent = appIntent(context, "voice").putExtra(EXTRA_START_VOICE, true),
                background = ButtonColor,
                tint = InkColor
            )
            if (wide) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                ActionButton(
                    icon = R.drawable.ic_add,
                    description = "Add expense",
                    intent = appIntent(context, "add").putExtra(EXTRA_OPEN_ADD_EXPENSE, true),
                    background = AccentColor,
                    tint = OnAccentColor
                )
            }
        }

        if (tall) {
            if (budget > 0) BudgetStatus(month = month, total = total, budget = budget, symbol = symbol)

            Spacer(modifier = GlanceModifier.height(10.dp))
            Spacer(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(HairlineColor))
            Spacer(modifier = GlanceModifier.height(4.dp))

            if (expenses.isEmpty()) {
                Text(
                    "No expenses yet this month",
                    style = TextStyle(color = MutedColor, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(top = 6.dp)
                )
            } else {
                // Newest first (the DAO orders by timestamp); tap a row to edit it.
                expenses.take(if (size.height >= Taller.height) 4 else 2).forEach { expense ->
                    RecentExpenseRow(context = context, expense = expense, symbol = symbol)
                }
            }
        }
    }
}

// Same pace logic as the Home summary card: what's left, and what that allows per day.
@Composable
private fun BudgetStatus(month: YearMonth, total: Double, budget: Double, symbol: String) {
    val remaining = budget - total
    val over = remaining < 0
    val daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1

    Spacer(modifier = GlanceModifier.height(10.dp))
    LinearProgressIndicator(
        progress = (total / budget).toFloat().coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = if (over) OverColor else InkColor,
        backgroundColor = HairlineColor
    )
    Text(
        if (over) {
            "Over budget by $symbol${formatAmount(-remaining)}"
        } else {
            "$symbol${formatAmount(remaining)} left · ~$symbol${(remaining / daysLeft).roundToInt()}/day"
        },
        style = TextStyle(color = if (over) OverColor else MutedColor, fontSize = 12.sp),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = 6.dp)
    )
}

@Composable
private fun RecentExpenseRow(context: Context, expense: Expense, symbol: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(actionStartActivity(appIntent(context, "expense/${expense.id}").putExtra(EXTRA_EDIT_EXPENSE_ID, expense.id))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            expense.description,
            style = TextStyle(color = InkColor, fontSize = 14.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            dayLabel(expense.timestamp),
            style = TextStyle(color = MutedColor, fontSize = 12.sp),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 8.dp)
        )
        Text(
            "$symbol${formatAmount(expense.amount)}",
            style = TextStyle(color = InkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1
        )
    }
}

@Composable
private fun ActionButton(icon: Int, description: String, intent: Intent, background: ColorProvider, tint: ColorProvider) {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .background(background)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(20.dp)
        )
    }
}

// CLEAR_TASK guarantees a fresh MainActivity, so extras are read in onCreate.
// A distinct data URI per action keeps each button's PendingIntent separate —
// intents differing only in extras can otherwise be treated as the same one.
private fun appIntent(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("pocketwise://widget/$action"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

private fun dayLabel(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DayFormat)
    }
}
