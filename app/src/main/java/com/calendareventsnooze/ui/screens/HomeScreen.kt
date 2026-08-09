package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
