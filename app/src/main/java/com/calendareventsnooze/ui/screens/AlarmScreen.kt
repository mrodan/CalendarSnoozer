package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calendareventsnooze.R
import com.calendareventsnooze.model.AlarmEvent
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.ui.theme.AlarmBackground
import com.calendareventsnooze.ui.theme.AlarmCalendar
import com.calendareventsnooze.ui.theme.AlarmDanger
import com.calendareventsnooze.ui.theme.AlarmOnCalendar
import com.calendareventsnooze.ui.theme.AlarmOnDanger
import com.calendareventsnooze.ui.theme.AlarmOnPrimary
import com.calendareventsnooze.ui.theme.AlarmOnSecondary
import com.calendareventsnooze.ui.theme.AlarmOnSurface
import com.calendareventsnooze.ui.theme.AlarmOnSurfaceMuted
import com.calendareventsnooze.ui.theme.AlarmOutline
import com.calendareventsnooze.ui.theme.AlarmPrimary
import com.calendareventsnooze.ui.theme.AlarmSecondary
import com.calendareventsnooze.ui.theme.AlarmSurface
import com.calendareventsnooze.ui.theme.LightOnSecondaryContainer
import com.calendareventsnooze.ui.theme.LightSecondaryContainer
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.combineDateAndTime
import com.calendareventsnooze.util.formatEventTime
import kotlinx.coroutines.delay
import java.util.Calendar

// UI.2 — buttons are 1.5x taller (was 64dp) with 2x thicker borders (was 1dp).
private val ACTION_BUTTON_HEIGHT = 96.dp
private val ACTION_BUTTON_BORDER = 2.dp

/**
 * M3.1 — the takeover follows Material 3 shape, spacing and type, but keeps its
 * own always-dark colours rather than the app's light/dark scheme. It fires on
 * a lock screen in the middle of the night, so it uses the highest-contrast
 * pairing in the palette regardless of the system setting.
 */
@Composable
fun AlarmScreen(
    alarmEvent: AlarmEvent,
    presets: List<SnoozePreset>,
    autoDismissSeconds: Int,
    onSnooze: (scheduledTimeMs: Long, openCalendar: Boolean) -> Unit,
    onDismiss: (openCalendar: Boolean) -> Unit,
    onAutoDismissTimeout: () -> Unit
) {
    var showSpecifyTime by remember { mutableStateOf(false) }
    var showTimeAndDate by remember { mutableStateOf(false) }
    // F.14 — replaces the old "Open Calendar Event" button. Ticking it makes the
    // next snooze or dismiss also open the event; it never resolves the alarm on
    // its own. Resets whenever a different alarm takes over the screen.
    var openCalendarAfter by remember(alarmEvent.alarmId) { mutableStateOf(false) }

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
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // UI.2 / UI.15 — content sits lower on the screen. The extra 44dp shifts
        // the title down by exactly one row: headlineMedium's 36sp line height
        // plus the 8dp gap under it, so the title now starts where the event
        // time used to.
        Spacer(Modifier.height(124.dp))

        Text(
            alarmEvent.eventTitle,
            color = AlarmOnSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        formatEventTime(alarmEvent.eventTimeMs)?.let { timeStr ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                timeStr,
                color = AlarmOnSurfaceMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }

        if (alarmEvent.eventText.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                alarmEvent.eventText,
                color = AlarmOnSurfaceMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        if (autoDismissSeconds > 0) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Auto-dismiss in ${secondsLeft}s",
                color = AlarmDanger,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionDivider("SNOOZE")
        Spacer(Modifier.height(Spacing.lg))

        // Preset buttons in a 2-column grid
        val safePresets = presets.take(4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresetButton(safePresets.getOrNull(0), Modifier.weight(1f)) { onSnooze(it, openCalendarAfter) }
            PresetButton(safePresets.getOrNull(1), Modifier.weight(1f)) { onSnooze(it, openCalendarAfter) }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresetButton(safePresets.getOrNull(2), Modifier.weight(1f)) { onSnooze(it, openCalendarAfter) }
            PresetButton(safePresets.getOrNull(3), Modifier.weight(1f)) { onSnooze(it, openCalendarAfter) }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Button(
                onClick = { showSpecifyTime = true },
                modifier = Modifier
                    .weight(1f)
                    .height(ACTION_BUTTON_HEIGHT),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(ACTION_BUTTON_BORDER, AlarmOutline),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmSecondary, contentColor = AlarmOnSecondary)
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Specify Time", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { showTimeAndDate = true },
                modifier = Modifier
                    .weight(1f)
                    .height(ACTION_BUTTON_HEIGHT),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(ACTION_BUTTON_BORDER, AlarmOutline),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmSecondary, contentColor = AlarmOnSecondary)
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Time & Date", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionDivider("")
        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = { onDismiss(openCalendarAfter) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlarmDanger, contentColor = AlarmOnDanger)
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text("DISMISS", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }

        // F.14 — replaces the old "Open Calendar Event" button, which resolved
        // the alarm as a side effect. This only arms the follow-up: whichever
        // action the user then takes, snooze or dismiss, also opens the event.
        //
        // UI.17 — carries the same colours as the "Fire test alarm now" button.
        // Those are taken from the *light* scheme rather than the live one
        // because the takeover keeps a fixed palette whatever the system theme
        // is doing, so this must not flip with it.
        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(LightSecondaryContainer)
                .clickable { openCalendarAfter = !openCalendarAfter }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Checkbox(
                checked = openCalendarAfter,
                onCheckedChange = { openCalendarAfter = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = LightOnSecondaryContainer,
                    checkmarkColor = LightSecondaryContainer,
                    uncheckedColor = LightOnSecondaryContainer
                )
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(
                "Also Open Calendar Event",
                color = LightOnSecondaryContainer,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(Spacing.sm))
            Icon(
                painter = painterResource(R.drawable.ic_calendar_snooze),
                contentDescription = null,
                tint = LightOnSecondaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    if (showSpecifyTime) {
        SpecifyTimeDialog(
            onDismiss = { showSpecifyTime = false },
            onConfirm = { totalMinutes ->
                showSpecifyTime = false
                onSnooze(
                    System.currentTimeMillis() + totalMinutes * 60_000L,
                    openCalendarAfter
                )
            }
        )
    }

    if (showTimeAndDate) {
        TimeAndDateDialog(
            onDismiss = { showTimeAndDate = false },
            onConfirm = { ms ->
                showTimeAndDate = false
                onSnooze(ms, openCalendarAfter)
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
    Button(
        onClick = { preset?.let { onSnooze(System.currentTimeMillis() + it.minutes * 60_000L) } },
        enabled = preset != null,
        modifier = modifier.height(ACTION_BUTTON_HEIGHT),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(ACTION_BUTTON_BORDER, AlarmOutline),
        colors = ButtonDefaults.buttonColors(
            containerColor = AlarmPrimary,
            contentColor = AlarmOnPrimary,
            disabledContainerColor = AlarmSecondary,
            disabledContentColor = AlarmOnSurfaceMuted
        )
    ) {
        Text(
            preset?.label ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
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
                .background(AlarmOutline)
        )
        if (label.isNotEmpty()) {
            Text(
                "  $label  ",
                color = AlarmOnSurfaceMuted,
                style = MaterialTheme.typography.labelLarge
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(AlarmOutline)
            )
        }
    }
}

/** Shared with the Snoozed Alarms "Manage" sheet (F.4). */
@Composable
internal fun SpecifyTimeDialog(
    onDismiss: () -> Unit,
    onConfirm: (totalMinutes: Int) -> Unit
) {
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Specify snooze time") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // F.6 — digits only, so open the numeric keypad.
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() } },
                    label = { Text("Hours") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Minutes") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
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
 * Time-then-date snooze picker. Shared with the Snoozed Alarms "Manage" sheet so
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
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Pick time") },
            text = { TimePicker(state = timeState) },
            confirmButton = { TextButton(onClick = { pickingDate = true }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            shape = MaterialTheme.shapes.extraLarge,
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
