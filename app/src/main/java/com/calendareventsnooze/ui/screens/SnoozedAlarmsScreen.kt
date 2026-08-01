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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.theme.AppButtonRegular
import com.calendareventsnooze.ui.theme.AppButtonRegularText
import com.calendareventsnooze.util.formatScheduledTime
import java.util.Calendar

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSnoozeView(
    record: SnoozedAlarmRecord,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var showReschedule by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Button(onClick = { showReschedule = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Reschedule")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showCancelConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))
        ) { Text("Cancel Snooze", color = Color.White) }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }

    if (showReschedule) {
        RescheduleDialog(
            onDismiss = { showReschedule = false },
            onConfirm = { newTimeMs ->
                showReschedule = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (ms: Long) -> Unit
) {
    val defaultCal = remember {
        Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var pickingDate by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour = defaultCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = defaultCal.get(Calendar.MINUTE),
        is24Hour = false
    )
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    if (!pickingDate) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = { TextButton(onClick = { pickingDate = true }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val dateMs = dateState.selectedDateMillis
                    if (dateMs != null) {
                        val dateCal = Calendar.getInstance().apply { timeInMillis = dateMs }
                        val result = Calendar.getInstance().apply {
                            set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        if (result > System.currentTimeMillis()) onConfirm(result)
                    }
                }) { Text("Reschedule") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    }
}
