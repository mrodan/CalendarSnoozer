package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.theme.AppButtonRegular
import com.calendareventsnooze.ui.theme.AppButtonRegularText
import com.calendareventsnooze.ui.theme.AppTopBar
import com.calendareventsnooze.util.formatScheduledTime

// F.4 — every action button in the Manage view shares one height.
private val MANAGE_BUTTON_HEIGHT = 56.dp
private val PRESET_BACKGROUND = Color(0xFFF1F3F4)

@Composable
fun SnoozedAlarmsScreen() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var managing by remember { mutableStateOf<SnoozedAlarmRecord?>(null) }

    val alarms = remember(refreshKey) { AppPrefs.getAllSnoozedAlarms(context) }

    val current = managing
    if (current == null) {
        if (alarms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No snoozed alarms",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alarms, key = { it.alarmId }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(record.eventTitle, fontWeight = FontWeight.Bold)
                                Text(formatScheduledTime(record.scheduledTimeMs),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { managing = record },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppButtonRegular,
                                    contentColor = AppButtonRegularText)
                            ) { Text("Manage") }
                        }
                    }
                }
            }
        }
    } else {
        ManageSnoozeView(
            record = current,
            onDone = { managing = null; refreshKey++ }
        )
    }
}

@Composable
private fun ManageSnoozeView(
    record: SnoozedAlarmRecord,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var showSpecifyTime by remember { mutableStateOf(false) }
    var showReschedule by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val presets = remember { AppPrefs.getSnoozePresets(context).take(4) }

    /** Re-arms this alarm for [newTimeMs] and returns to the list. */
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
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(record.eventTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (record.eventText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(record.eventText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text("Currently snoozed until: ${formatScheduledTime(record.scheduledTimeMs)}")

        Spacer(Modifier.height(24.dp))

        // F.4 — the four snooze presets, two per row.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetSnoozeButton(presets.getOrNull(0), Modifier.weight(1f), ::rescheduleTo)
            PresetSnoozeButton(presets.getOrNull(1), Modifier.weight(1f), ::rescheduleTo)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetSnoozeButton(presets.getOrNull(2), Modifier.weight(1f), ::rescheduleTo)
            PresetSnoozeButton(presets.getOrNull(3), Modifier.weight(1f), ::rescheduleTo)
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showSpecifyTime = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(MANAGE_BUTTON_HEIGHT),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTopBar, contentColor = Color.White)
        ) { Text("⏱ Specify Time", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showReschedule = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(MANAGE_BUTTON_HEIGHT),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppButtonRegular, contentColor = AppButtonRegularText)
        ) { Text("📅 Date & time", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showCancelConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(MANAGE_BUTTON_HEIGHT),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))
        ) { Text("Cancel Snooze", color = Color.White, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(Modifier.height(16.dp))
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
            title = { Text("Cancel Snooze") },
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
    Button(
        onClick = {
            preset?.let { onReschedule(System.currentTimeMillis() + it.minutes * 60_000L) }
        },
        enabled = preset != null,
        modifier = modifier.height(MANAGE_BUTTON_HEIGHT),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PRESET_BACKGROUND, contentColor = Color.Black)
    ) {
        Text(preset?.label ?: "—", fontWeight = FontWeight.Bold)
    }
}
