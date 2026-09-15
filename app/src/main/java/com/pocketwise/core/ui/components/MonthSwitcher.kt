package com.pocketwise.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val LabelFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

// ponytail: 100 years back stands in for "unbounded" history — pages are just
// text labels, so the count costs nothing.
private const val PageCount = 1200

/**
 * The app-wide month picker: swipe the label, or tap the arrows. The last
 * page is the current month, so future months are unreachable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthSwitcher(
    selectedMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMonth = remember { YearMonth.now() }
    val lastPage = PageCount - 1
    fun monthAt(page: Int): YearMonth = currentMonth.minusMonths((lastPage - page).toLong())

    val pagerState = rememberPagerState(
        initialPage = (lastPage - ChronoUnit.MONTHS.between(selectedMonth, currentMonth).toInt()).coerceIn(0, lastPage),
        pageCount = { PageCount }
    )
    val scope = rememberCoroutineScope()
    val onSelect by rememberUpdatedState(onMonthSelected)

    // Commit only once a swipe settles, so a month's data loads once — not on
    // every frame of the drag.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onSelect(monthAt(it)) }
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            enabled = pagerState.currentPage > 0
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    monthAt(page).format(LabelFormat),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            enabled = pagerState.currentPage < lastPage
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}
