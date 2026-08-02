package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.ui.theme.AlarmBackground
import com.calendareventsnooze.ui.theme.AlarmOpenCalendar
import com.calendareventsnooze.ui.theme.AlarmDanger
import com.calendareventsnooze.ui.theme.AlarmDateAndTime
import com.calendareventsnooze.ui.theme.AlarmSnoozeButton
import com.calendareventsnooze.ui.theme.AlarmSpecifyTime
import com.calendareventsnooze.ui.theme.AlarmTextPrimary
import com.calendareventsnooze.ui.theme.AlarmTextSecondary
import com.calendareventsnooze.util.combineDateAndTime
import com.calendareventsnooze.util.formatEventTime
import kotlinx.coroutines.delay
import java.util.Calendar

// UI.2 — buttons are 1.5x taller (was 64dp) with 2x thicker borders (was 1dp).
private val ACTION_BUTTON_HEIGHT = 96.dp
private val ACTION_BUTTON_BORDER = 2.dp
// UI.3 — Specify Time / Time & Date match the old snooze font; presets are 1.5x that.
private val SECONDARY_FONT = 18.sp
private val PRESET_FONT = 27.sp

@Composable
fun AlarmScreen(
    alarmEvent: AlarmEvent,
    presets: List<SnoozePreset>,
    autoDismissSeconds: Int,
    onOpenCalendar: () -> Unit,
    onSnooze: (scheduledTimeMs: Long) -> Unit,
    onDismiss: () -> Unit,
    onAutoDismissTimeout: () -> Unit
) {
    var showSpecifyTime by remember { mutableStateOf(false) }
    var showTimeAndDate by remember { mutableStateOf(false) }

    // Countdown restarts whenever a different alarm takes over the screen.
    var secondsLeft by remember(alarmEvent.alarmId) { mutableIntStateOf(autoDismissSeconds) }
    LaunchedEffect(alarmEvent.alarmId) {
        if (autoDismissSeconds <= 0) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onAutoDismissTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // UI.2 — content sits lower on the screen.
        Spacer(Modifier.height(80.dp))

        Text(
            "⚠  ${alarmEvent.eventTitle}",
            color = AlarmTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        formatEventTime(alarmEvent.eventTimeMs)?.let { timeStr ->
            Spacer(Modifier.height(6.dp))
            Text(timeStr, color = AlarmTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        if (alarmEvent.eventText.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                alarmEvent.eventText,
                color = AlarmTextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        if (autoDismissSeconds > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Auto-dismiss in ${secondsLeft}s",
                color = AlarmDanger,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionDivider("SNOOZE")
        Spacer(Modifier.height(16.dp))

        // Preset buttons in a 2-column grid
        val safePresets = presets.take(4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetButton(safePresets.getOrNull(0), Modifier.weight(1f), onSnooze)
            PresetButton(safePresets.getOrNull(1), Modifier.weight(1f), onSnooze)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetButton(safePresets.getOrNull(2), Modifier.weight(1f), onSnooze)
            PresetButton(safePresets.getOrNull(3), Modifier.weight(1f), onSnooze)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { showSpecifyTime = true },
                modifier = Modifier
                    .weight(1f)
                    .height(ACTION_BUTTON_HEIGHT),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(ACTION_BUTTON_BORDER, AlarmTextSecondary),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmSpecifyTime, contentColor = Color.White)
            ) { Text("⏱ Specify Time", fontSize = SECONDARY_FONT, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { showTimeAndDate = true },
                modifier = Modifier
                    .weight(1f)
                    .height(ACTION_BUTTON_HEIGHT),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(ACTION_BUTTON_BORDER, AlarmTextSecondary),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmDateAndTime, contentColor = Color.White)
            ) { Text("📅 Time & Date", fontSize = SECONDARY_FONT, fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(20.dp))
        SectionDivider("")
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlarmDanger)
        ) {
            Text("✕  DISMISS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // UI.3 — Open Calendar Event sits below Dismiss, with the "(DISMISSES
        // ALARM)" caveat centred on its own line beneath the label.
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenCalendar,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AlarmOpenCalendar)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📅  OPEN CALENDAR EVENT",
                    color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("(DISMISSES ALARM)", color = Color.Black, fontSize = 11.sp,
                    textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showSpecifyTime) {
        SpecifyTimeDialog(
            onDismiss = { showSpecifyTime = false },
            onConfirm = { totalMinutes ->
                showSpecifyTime = false
                onSnooze(System.currentTimeMillis() + totalMinutes * 60_000L)
            }
        )
    }

    if (showTimeAndDate) {
        TimeAndDateDialog(
            onDismiss = { showTimeAndDate = false },
            onConfirm = { ms ->
                showTimeAndDate = false
                onSnooze(ms)
            }
        )
    }
}

@Composable
private fun PresetButton(
    preset: SnoozePreset?,
    modifier: Modifier,
    onSnooze: (Long) -> Unit
) {
    // UI.2 — light background with black text.
    Button(
        onClick = { preset?.let { onSnooze(System.currentTimeMillis() + it.minutes * 60_000L) } },
        enabled = preset != null,
        modifier = modifier.height(ACTION_BUTTON_HEIGHT),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(ACTION_BUTTON_BORDER, AlarmTextSecondary),
        colors = ButtonDefaults.buttonColors(
            containerColor = AlarmSnoozeButton, contentColor = Color.Black)
    ) {
        Text(preset?.label ?: "—", fontSize = PRESET_FONT, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(AlarmTextSecondary)
        )
        if (label.isNotEmpty()) {
            Text(
                "  $label  ",
                color = AlarmTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(AlarmTextSecondary)
            )
        }
    }
}

/** Shared with the Snoozed Alarms "Manage" view (F.4). */
@Composable
internal fun SpecifyTimeDialog(
    onDismiss: () -> Unit,
    onConfirm: (totalMinutes: Int) -> Unit
) {
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Specify Snooze Time") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // F.6 — digits only, so open the numeric keypad.
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hours.toIntOrNull() ?: 0
                val m = minutes.toIntOrNull() ?: 0
                val total = h * 60 + m
                if (total > 0) onConfirm(total)
            }) { Text("Snooze") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Time-then-date snooze picker. Shared with the Snoozed Alarms "Manage" view so
 * the UTC date handling (B.3) has a single implementation (B.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeAndDateDialog(
    onDismiss: () -> Unit,
    confirmLabel: String = "Snooze",
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
                        // B.3 — combineDateAndTime reads the picker's UTC date correctly.
                        val result = combineDateAndTime(dateMs, timeState.hour, timeState.minute)
                        if (result > System.currentTimeMillis()) onConfirm(result)
                    }
                }) { Text(confirmLabel) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    }
}
