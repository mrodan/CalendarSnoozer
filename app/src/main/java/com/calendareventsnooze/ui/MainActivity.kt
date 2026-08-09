package com.calendareventsnooze.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.components.OnResumeRefresh
import com.calendareventsnooze.ui.screens.HomeScreen
import com.calendareventsnooze.ui.screens.SettingsScreen
import com.calendareventsnooze.ui.screens.SnoozePresetsScreen
import com.calendareventsnooze.ui.screens.SoundProfileScreen
import com.calendareventsnooze.ui.screens.readPermissions
import com.calendareventsnooze.ui.theme.CalendarEventSnoozeTheme
import com.calendareventsnooze.ui.theme.LocalAppBarColors
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.TestAlarmHelper
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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

/**
 * Round 18 — the four destinations, in bar order. UI.29 named the second after
 * the screen both its halves belong to rather than after one of them.
 */
private data class Destination(val title: String, val icon: ImageVector)

private val DESTINATIONS = listOf(
    Destination("Home", Icons.Outlined.Home),
    Destination("Alarm Screen", Icons.Outlined.Alarm),
    Destination("Sound & Vibration", Icons.AutoMirrored.Outlined.VolumeUp),
    Destination("Settings", Icons.Outlined.Settings)
)

private const val SETTINGS_PAGE = 3

/**
 * UI.27.4 — one scale for the sound-mode sub-tabs in SoundProfileScreen, which
 * reads it from here. The primary tabs it used to share it with became the
 * bottom navigation bar in round 18, where M3 sets the label size itself.
 */
const val TAB_LABEL_SCALE = 1.15f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(openHomeRequest: Int) {
    // M3.1 — one pager drives both the tab row and the swipe gesture, so the
    // two can never disagree about which tab is showing.
    val pagerState = rememberPagerState(pageCount = { DESTINATIONS.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The nav bar's badge has to be as fresh as the Settings card's, so it is
    // re-read on resume too — granting a permission happens outside the app.
    var permissionRefresh by remember { mutableIntStateOf(0) }
    OnResumeRefresh { permissionRefresh++ }
    val permissions = remember(permissionRefresh) { readPermissions(context) }

    // Round 18 — the floating Test cluster, collapsed until asked for.
    var testExpanded by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }

    /** Test alarms need the overlay permission or nothing happens. */
    fun withOverlay(action: () -> Unit) {
        if (Settings.canDrawOverlays(context)) action() else showOverlayDialog = true
        testExpanded = false
    }

    // F.17 — a tap on the missed-alarm notification lands on Home whichever tab
    // was last open. Keyed on the counter, not a flag, so a second tap works too.
    LaunchedEffect(openHomeRequest) {
        if (openHomeRequest > 0) pagerState.scrollToPage(0)
    }

    // UI.28 — back goes to Home first, and only closes the app from Home.
    // Disabled on Home so the system default (finish the activity) applies
    // rather than this swallowing the gesture.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
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
        },
        // Round 18 — destinations moved from a tab row at the top to a
        // navigation bar at the bottom, which is where M3 puts three to five
        // top-level destinations and where a thumb can reach them.
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                DESTINATIONS.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            if (index == SETTINGS_PAGE && !permissions.allGranted) {
                                // The count of what is outstanding, on the icon
                                // itself, so it is visible from any tab.
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) { Text(permissions.pendingCount.toString()) }
                                }) {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            } else {
                                Icon(destination.icon, contentDescription = null)
                            }
                        },
                        label = {
                            Text(
                                destination.title,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            TestCluster(
                expanded = testExpanded,
                onToggle = { testExpanded = !testExpanded },
                onTestNow = { withOverlay { TestAlarmHelper.fireTestAlarmNow(context) } },
                onTestDelayed = {
                    withOverlay { TestAlarmHelper.fireTestAlarmDelayed(context, 5) }
                },
                onForceStop = {
                    testExpanded = false
                    // Emergency: silence any alarm, clear notifications, then
                    // hard-reset the app process so nothing stuck can keep
                    // sounding.
                    AlarmService.forceStopEverything(context)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // M3.1 — swiping moves between destinations as well.
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
                    SETTINGS_PAGE -> SettingsScreen()
                }
            }
        }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Permission needed") },
            text = { Text("The \"Display over other apps\" permission is required to " +
                "show the alarm screen. Please grant it first.") },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Round 18 — the test tools as a speed dial in the bottom-right corner rather
 * than a card on Home. They are occasional, deliberate actions that belong to
 * no one destination, so they float above all of them; the Scaffold keeps the
 * cluster clear of the navigation bar.
 *
 * The three actions open **upwards** in the order asked for, which means the
 * column lists them in reverse: Force Stop is furthest from the thumb because
 * it is the one to hit by accident least.
 */
@Composable
private fun TestCluster(
    expanded: Boolean,
    onToggle: () -> Unit,
    onTestNow: () -> Unit,
    onTestDelayed: () -> Unit,
    onForceStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ClusterAction(
                    label = "Force Stop",
                    onClick = onForceStop,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                ) { StopWithCrossIcon(MaterialTheme.colorScheme.onErrorContainer) }
                ClusterAction(
                    label = "Test Alarm +5",
                    onClick = onTestDelayed,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                }
                ClusterAction(
                    label = "Test Alarm",
                    onClick = onTestNow,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier.height(48.dp)
        ) {
            Text(
                "Test",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
        }
    }
}

/** One action in the cluster: label, then its icon on the right. */
@Composable
private fun ClusterAction(
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(Spacing.sm))
            icon()
        }
    }
}

/**
 * UI.13 — a rounded square with an X across it. Emoji can't be recoloured and
 * no Material icon matches, so it is drawn.
 *
 * The arms stop where the corner arc begins rather than at the square's
 * geometric corner: on a rounded rect the corner point sits *outside* the
 * outline, so running the diagonals all the way would poke past it. For a 45°
 * diagonal that offset is `r * (1 - 1/√2)` on each axis.
 */
@Composable
private fun StopWithCrossIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        val inset = stroke / 2f
        val far = size.width - inset
        val radius = 4.dp.toPx()
        val arc = radius * (1f - 1f / sqrt(2f))

        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke)
        )
        drawLine(color, Offset(inset + arc, inset + arc), Offset(far - arc, far - arc), stroke)
        drawLine(color, Offset(far - arc, inset + arc), Offset(inset + arc, far - arc), stroke)
    }
}
