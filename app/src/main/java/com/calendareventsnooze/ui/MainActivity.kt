package com.calendareventsnooze.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.screens.HomeScreen
import com.calendareventsnooze.ui.screens.SnoozePresetsScreen
import com.calendareventsnooze.ui.screens.SoundProfileScreen
import com.calendareventsnooze.ui.theme.CalendarEventSnoozeTheme
import com.calendareventsnooze.ui.theme.LocalAppBarColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B.7 — self-heal any snoozed alarm whose OS-level alarm was lost (reboot,
        // process death). Past-due entries are revived instead of sitting dead.
        AlarmScheduler.rescheduleAllSnoozed(applicationContext)
        setContent {
            CalendarEventSnoozeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

private val TAB_TITLES = listOf("Home", "Snooze Buttons", "Sound & Vibration")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainScreen() {
    // M3.1 — one pager drives both the tab row and the swipe gesture, so the
    // two can never disagree about which tab is showing.
    val pagerState = rememberPagerState(pageCount = { TAB_TITLES.size })
    val scope = rememberCoroutineScope()

    // UI.11 — the Sound & Vibration sub-tab is owned here, not by the screen, so
    // it survives navigating away to another tab and back.
    var soundSubTab by rememberSaveable { mutableIntStateOf(0) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedTab = it }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val appBar = LocalAppBarColors.current
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            append("Calendar Snoozer")
                            withStyle(SpanStyle(color = appBar.contentVariant)) {
                                append("  (IYSnoozeYK)")
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = appBar.container,
                    titleContentColor = appBar.content
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            // Two lines allowed: "Sound & Vibration" does not fit
                            // one third of the width on a phone at this size.
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // M3.1 — swiping moves between tabs.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1
            ) { page ->
                when (page) {
                    0 -> HomeScreen()
                    1 -> SnoozePresetsScreen()
                    2 -> SoundProfileScreen(
                        selectedSubTab = soundSubTab,
                        onSubTabChange = { soundSubTab = it }
                    )
                }
            }
        }
    }
}
