package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.formatScheduledTime

// F.4 — every action button in the Manage sheet shares one height.
private val MANAGE_BUTTON_HEIGHT = 56.dp

/**
 * UI.4 — the snoozed-alarm list, rendered as plain rows so it can live inside
 * the Home tab's scrolling column (a LazyColumn cannot nest in a parent that
 * scrolls the same direction).
 */
@Composable
fun SnoozedAlarmsSection(
    alarms: List<SnoozedAlarmRecord>,
    onManage: (SnoozedAlarmRecord) -> Unit
) {
    if (alarms.isEmpty()) {
        Text(
            "No snoozed alarms",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    alarms.forEachIndexed { index, record ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.eventTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatScheduledTime(record.scheduledTimeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(Spacing.md))
            FilledTonalButton(onClick = { onManage(record) }) {
                Text("Manage", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (index != alarms.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * M3.1 — the contents of the Manage modal bottom sheet. The sheet itself (and
 * its dismissal) is owned by HomeScreen; this is only the body.
 */
@Composable
fun ManageSnoozeSheet(
    record: SnoozedAlarmRecord,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var showSpecifyTime by remember { mutableStateOf(false) }
    var showReschedule by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val presets = remember { AppPrefs.getSnoozePresets(context).take(4) }

    /** Re-arms this alarm for [newTimeMs] and closes the sheet. */
    fun rescheduleTo(newTimeMs: Long) {
        val event = AlarmEvent(
            alarmId = record.alarmId,
            eventTitle = record.eventTitle,
            eventText = record.eventText,
            eventId = record.eventId,
            eventTimeMs = record.eventTimeMs
        )
        AlarmScheduler.cancelAlarm(context, record.alarmId)
        AlarmScheduler.scheduleAt(context, event, newTimeMs)
        AppPrefs.saveSnoozedAlarm(context, record.copy(scheduledTimeMs = newTimeMs))
        onDone()
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl)
    ) {
        Text(record.eventTitle, style = MaterialTheme.typography.headlineSmall)
        if (record.eventText.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                record.eventText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                "Snoozed until ${formatScheduledTime(record.scheduledTimeMs)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        Text(
            "Snooze again",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))

        // F.4 — the four snooze presets, two per row.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresetSnoozeButton(presets.getOrNull(0), Modifier.weight(1f), ::rescheduleTo)
            PresetSnoozeButton(presets.getOrNull(1), Modifier.weight(1f), ::rescheduleTo)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresetSnoozeButton(presets.getOrNull(2), Modifier.weight(1f), ::rescheduleTo)
            PresetSnoozeButton(presets.getOrNull(3), Modifier.weight(1f), ::rescheduleTo)
        }

        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedButton(
                onClick = { showSpecifyTime = true },
                modifier = Modifier
                    .weight(1f)
                    .height(MANAGE_BUTTON_HEIGHT),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Specify time", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { showReschedule = true },
                modifier = Modifier
                    .weight(1f)
                    .height(MANAGE_BUTTON_HEIGHT),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Date & time", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = { showCancelConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(MANAGE_BUTTON_HEIGHT),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) { Text("Cancel snooze", style = MaterialTheme.typography.labelLarge) }

        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Close", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showSpecifyTime) {
        SpecifyTimeDialog(
            onDismiss = { showSpecifyTime = false },
            onConfirm = { totalMinutes ->
                showSpecifyTime = false
                rescheduleTo(System.currentTimeMillis() + totalMinutes * 60_000L)
            }
        )
    }

    if (showReschedule) {
        // B.4 — shares the takeover screen's picker, so the UTC date fix applies here too.
        TimeAndDateDialog(
            onDismiss = { showReschedule = false },
            confirmLabel = "Reschedule",
            onConfirm = { newTimeMs ->
                showReschedule = false
                rescheduleTo(newTimeMs)
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel snooze") },
            text = { Text("Cancel this alarm? The event will not remind you again.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    AlarmScheduler.cancelAlarm(context, record.alarmId)
                    AppPrefs.removeSnoozedAlarm(context, record.alarmId)
                    onDone()
                }) { Text("Yes, cancel") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun PresetSnoozeButton(
    preset: SnoozePreset?,
    modifier: Modifier,
    onReschedule: (Long) -> Unit
) {
    FilledTonalButton(
        onClick = {
            preset?.let { onReschedule(System.currentTimeMillis() + it.minutes * 60_000L) }
        },
        enabled = preset != null,
        modifier = modifier.height(MANAGE_BUTTON_HEIGHT),
        shape = MaterialTheme.shapes.large
    ) {
        Text(preset?.label ?: "—", style = MaterialTheme.typography.labelLarge)
    }
}
