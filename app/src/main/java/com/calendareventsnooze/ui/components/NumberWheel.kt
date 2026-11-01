package com.calendareventsnooze.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** One row of the wheel. Three are visible at a time. */
private val ITEM_HEIGHT = 48.dp

/**
 * Round 26 — a scrolling number wheel, the control Android's own duration
 * pickers use: the chosen value sits between two rules with its neighbours
 * greyed above and below.
 *
 * It replaces the pair of typed fields in "Specify snooze time", which opened
 * the keypad over the alarm and accepted anything the user typed.
 *
 * **The blank rows at each end are load-bearing.** They are what lets the first
 * and last values reach the middle of a three-row window, and they also make
 * the arithmetic exact: `scrollToItem(i)` puts list item `i` flush at the top of
 * the window, so the row in the middle is list item `i + 1` — value `i`. Doing
 * the same with `contentPadding` works visually but leaves the index the list
 * reports ambiguous, because the padding counts as content in some of
 * LazyColumn's measurements and not others.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberWheel(
    range: IntRange,
    value: Int,
    unit: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)

    // Centre the starting value once. Afterwards the list is the source of
    // truth, so re-running this on every change would fight the user's finger.
    LaunchedEffect(Unit) {
        listState.scrollToItem((value - range.first).coerceIn(0, range.count() - 1))
    }

    // Which value is in the middle of the window, computed from the measured
    // layout rather than from firstVisibleItemIndex, so it stays right mid-fling.
    val centredValue by remember(range) {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val listIndex = info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - middle) }
                ?.index
                ?: return@derivedStateOf null
            // List index 0 is the leading blank row.
            (range.first + listIndex - 1).coerceIn(range.first, range.last)
        }
    }

    LaunchedEffect(centredValue) {
        centredValue?.let { if (it != value) onValueChange(it) }
    }

    Box(
        modifier = modifier.height(ITEM_HEIGHT * 3),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize()
        ) {
            // Leading blank row — see the note above.
            item { Box(Modifier.height(ITEM_HEIGHT)) }
            items(range.count()) { index ->
                val itemValue = range.first + index
                val selected = itemValue == (centredValue ?: value)
                Box(
                    modifier = Modifier
                        .height(ITEM_HEIGHT)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$itemValue $unit",
                        style = if (selected) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item { Box(Modifier.height(ITEM_HEIGHT)) }
        }

        // The two rules marking the selection. Drawn over the list, but with no
        // input modifier of their own, so a drag that starts on one still
        // scrolls the wheel.
        Column(
            modifier = Modifier
                .height(ITEM_HEIGHT)
                .fillMaxWidth()
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
