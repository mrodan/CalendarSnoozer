package com.calendareventsnooze.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    companion object {
        /** F.17 — set by the missed-alarm notification: show the Home tab. */
        const val EXTRA_OPEN_HOME = "ces_open_home"
    }

    /**
     * F.17 — bumped whenever an intent asks for Home, so a tap on the missed
     * alarm notification moves the pager even when the activity was already
     * running on another tab.
     */
    private var openHomeRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_HOME, false) == true) openHomeRequest++
        // Android 15+ forces edge-to-edge for targetSdk 35 anyway; opting in
        // explicitly means Android 14 and newer releases lay out identically
        // instead of the bars changing appearance across versions.
        enableEdgeToEdge()
        // B.7 — self-heal any snoozed alarm whose OS-level alarm was lost (reboot,
        // process death). Past-due entries are revived instead of sitting dead.
        AlarmScheduler.rescheduleAllSnoozed(applicationContext)
        setContent {
            CalendarEventSnoozeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(openHomeRequest = openHomeRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) openHomeRequest++
    }
}

private val TAB_TITLES = listOf("Home", "Snooze Buttons", "Sound & Vibration")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(openHomeRequest: Int) {
    // M3.1 — one pager drives both the tab row and the swipe gesture, so the
    // two can never disagree about which tab is showing.
    val pagerState = rememberPagerState(pageCount = { TAB_TITLES.size })
    val scope = rememberCoroutineScope()

    // F.17 — a tap on the missed-alarm notification lands on Home whichever tab
    // was last open. Keyed on the counter, not a flag, so a second tap works too.
    LaunchedEffect(openHomeRequest) {
        if (openHomeRequest > 0) pagerState.scrollToPage(0)
    }

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
                                // UI.27 — 20% larger than the M3 titleSmall
                                // these used to be.
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.titleSmall.fontSize * 1.2f
                                ),
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
