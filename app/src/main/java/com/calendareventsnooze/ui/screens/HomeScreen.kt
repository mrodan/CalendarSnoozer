package com.calendareventsnooze.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.ui.components.OnResumeRefresh
import com.calendareventsnooze.ui.components.SectionCard
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.CalendarApps
import com.calendareventsnooze.util.formatScheduledTime

/**
 * Round 21 — the master switch and the two warnings that explain a silent app.
 *
 * The segmented control matches Sequencing's, so a two-way choice looks the same
 * everywhere. Off is styled in the error role rather than the usual selected
 * green: switching the whole app off is the one setting on Home that stops
 * alarms happening, and it should not look like an ordinary preference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozerStatusCard(refreshKey: Int) {
    val context = LocalContext.current
    var enabled by remember(refreshKey) { mutableStateOf(AppPrefs.isSnoozerEnabled(context)) }
    val watched = remember(refreshKey) { AppPrefs.getCalendarPackages(context) }
    val lastSeen = remember(refreshKey) { AppPrefs.getLastInterception(context) }

    SectionCard(title = "Calendar Snoozer") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = enabled,
                onClick = { enabled = true; AppPrefs.setSnoozerEnabled(context, true) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Snoozer is ON", style = MaterialTheme.typography.labelLarge) }
            SegmentedButton(
                selected = !enabled,
                onClick = { enabled = false; AppPrefs.setSnoozerEnabled(context, false) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                // Only the OFF half turns red, and only while it is the one
                // selected — an unselected OFF is just a normal option.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                    activeContentColor = MaterialTheme.colorScheme.error,
                    activeBorderColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Turn OFF Snoozer", style = MaterialTheme.typography.labelLarge) }
        }

        AnimatedVisibility(visible = !enabled) {
            Column {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Calendar notifications will not activate the CalendarSnoozer " +
                        "full-screen alarm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // The other way the app goes quiet: nothing to watch. Worth saying here
        // because the fix is on a different tab entirely.
        AnimatedVisibility(visible = enabled && watched.isEmpty()) {
            Column {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "No calendar app is selected, so no reminder will ever be " +
                        "intercepted. Choose one under Settings → Calendar Apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            lastSeen?.let { (pkg, at) ->
                "Last reminder seen: ${CalendarApps.labelFor(context, pkg)} · " +
                    formatScheduledTime(at)
            } ?: "No calendar reminder intercepted yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Home is now purely the two alarm lists: Permissions moved to the Settings tab
 * and the test buttons became the floating Test cluster, both in round 18.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // Recompute the lists whenever the screen resumes — an alarm can fire, be
    // snoozed or be missed while the user is elsewhere.
    var refreshKey by remember { mutableIntStateOf(0) }
    OnResumeRefresh { refreshKey++ }

    var managing by remember { mutableStateOf<SnoozedAlarmRecord?>(null) }
    // F.15 — the Manage sheet is shared, so a missed alarm is handed to it as a
    // record plus this flag rather than as a second sheet.
    var managingIsMissed by remember { mutableStateOf(false) }
    val alarms = remember(refreshKey) { AppPrefs.getAllSnoozedAlarms(context) }
    val missedAlarms = remember(refreshKey) { AppPrefs.getAllMissedAlarms(context) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
    ) {
        // ---- Status (round 21) ----
        // The master switch, plus the two things that silently stop the app
        // working: nothing selected to watch, and never having seen a reminder.
        // Home is where someone looks to ask "is this on?", so they belong here
        // rather than two taps into Settings.
        SnoozerStatusCard(refreshKey)

        Spacer(Modifier.height(Spacing.xl))

        // ---- Snoozed Alarms (UI.4) ----
        SectionCard(title = "Snoozed Alarms") {
            SnoozedAlarmsSection(
                alarms = alarms,
                onManage = { managing = it; managingIsMissed = false }
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        // ---- Missed Alarms (F.15) ----
        SectionCard(title = "Missed Alarms") {
            MissedAlarmsSection(
                alarms = missedAlarms,
                onManage = { missed ->
                    // A missed alarm has no firing time; the sheet shows the
                    // event's own time instead, so seed scheduledTimeMs with it.
                    managing = SnoozedAlarmRecord(
                        alarmId = missed.alarmId,
                        eventTitle = missed.eventTitle,
                        eventText = missed.eventText,
                        eventId = missed.eventId,
                        eventTimeMs = missed.eventTimeMs,
                        scheduledTimeMs = missed.eventTimeMs
                    )
                    managingIsMissed = true
                }
            )
        }

        Spacer(Modifier.height(Spacing.xl))
    }

    // M3.1 — Manage opens as a modal bottom sheet over Home rather than
    // replacing the whole tab.
    val record = managing
    if (record != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { managing = null; refreshKey++ },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            ManageSnoozeSheet(
                record = record,
                isMissed = managingIsMissed,
                onDone = { managing = null; refreshKey++ }
            )
        }
    }
}
