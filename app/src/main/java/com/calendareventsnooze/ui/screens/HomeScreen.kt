package com.calendareventsnooze.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.TestAlarmHelper
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // Recompute permissions / snoozed alarms whenever the screen resumes.
    var refreshKey by remember { mutableIntStateOf(0) }
    OnResumeRefresh { refreshKey++ }

    var managing by remember { mutableStateOf<SnoozedAlarmRecord?>(null) }
    // F.15 — the Manage sheet is shared, so a missed alarm is handed to it as a
    // record plus this flag rather than as a second sheet.
    var managingIsMissed by remember { mutableStateOf(false) }
    val alarms = remember(refreshKey) { AppPrefs.getAllSnoozedAlarms(context) }
    val missedAlarms = remember(refreshKey) { AppPrefs.getAllMissedAlarms(context) }

    val notificationAccess = remember(refreshKey) { hasNotificationAccess(context) }
    val overlay = remember(refreshKey) { Settings.canDrawOverlays(context) }
    val exactAlarm = remember(refreshKey) { canScheduleExactAlarms(context) }
    val readCalendar = remember(refreshKey) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }
    val fullScreenIntent = remember(refreshKey) { canUseFullScreenIntent(context) }
    val allGranted =
        notificationAccess && overlay && exactAlarm && readCalendar && fullScreenIntent

    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    var showOverlayDialog by remember { mutableStateOf(false) }
    var permissionsExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
    ) {
        // ---- Section 1: Snoozed Alarms (UI.4) ----
        SectionCard(title = "Snoozed Alarms") {
            SnoozedAlarmsSection(
                alarms = alarms,
                onManage = { managing = it; managingIsMissed = false }
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        // ---- Section 2: Missed Alarms (F.15) ----
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

        // ---- Section 2: Test Alarm ----
        SectionCard(title = "Test Alarm") {
            FilledTonalButton(
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        TestAlarmHelper.fireTestAlarmNow(context)
                    } else showOverlayDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Fire test alarm now", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(Spacing.md))

            FilledTonalButton(
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        TestAlarmHelper.fireTestAlarmDelayed(context, 5)
                    } else showOverlayDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                // UI.12 — same alarm glyph as the button above, then "+5", so the
                // two test buttons read as a pair that differ only by the delay.
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Text("+5", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(Spacing.sm))
                Text("Test on lock screen (+5 sec)",
                    style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        // ---- Force Stop (UI.13 — below Test Alarm, half width, centred) ----
        ForceStopButton(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onForceStop = {
                // Emergency: silence any alarm, clear notifications, then hard-reset
                // the app process so nothing stuck can keep sounding.
                AlarmService.forceStopEverything(context)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        )

        Spacer(Modifier.height(Spacing.xl))

        // ---- Section 3: Permissions (collapsible) ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { permissionsExpanded = !permissionsExpanded }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // UI.10 — status symbol sits to the right of the heading.
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(Spacing.sm))
                if (allGranted) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "All granted",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Filled.Warning, contentDescription = "Permissions pending",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (permissionsExpanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (permissionsExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = permissionsExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(Spacing.lg)) {
                        PermissionRow(
                            name = "Notification Access",
                            description = "Allows intercepting calendar notifications",
                            granted = notificationAccess
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        PermissionRow(
                            name = "Display over other apps",
                            description = "Required to show the alarm over the lock screen",
                            granted = overlay
                        ) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        PermissionRow(
                            name = "Schedule Exact Alarms",
                            description = "Required for precise snooze timing",
                            granted = exactAlarm
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        PermissionRow(
                            name = "Read Calendar",
                            description = "Lets the app open the correct calendar event",
                            granted = readCalendar
                        ) {
                            calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                        // Android 14+ — revocable, and without it the takeover
                        // silently degrades to an ordinary heads-up notification.
                        PermissionRow(
                            name = "Full-screen notifications",
                            description = "Required for the alarm to take over the screen",
                            granted = fullScreenIntent,
                            isLast = true
                        ) {
                            openFullScreenIntentSettings(context)
                        }
                    }
                }
            }
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
 * UI.13 — outline only, no fill, so the page background shows through.
 *
 * Losing the fill also forced the colour off UI.12's fixed light-scheme pin:
 * that content colour is near-black (#410002), which was only legible because
 * it sat on a pale pink container. Against the dark scheme's background it
 * measures about 1.06:1 — invisible. Border and label therefore take the M3
 * error role, which adapts per scheme.
 */
@Composable
private fun ForceStopButton(
    modifier: Modifier = Modifier,
    onForceStop: () -> Unit
) {
    OutlinedButton(
        onClick = onForceStop,
        modifier = modifier
            .fillMaxWidth(0.5f)
            .height(52.dp), // 80% of the old 64dp
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = Spacing.md),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        StopWithCrossIcon()
        Spacer(Modifier.size(Spacing.sm))
        Text(
            "FORCE STOP APP",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
private fun StopWithCrossIcon() {
    val color = MaterialTheme.colorScheme.error
    Canvas(Modifier.size(16.dp)) { // 80% of the old 20dp
        val stroke = 1.5.dp.toPx() // 25% thinner than the old 2dp
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

/**
 * M3.1 — one container per top-level section of the Home tab, so Snoozed
 * Alarms / Test Alarm / Permissions read as three distinct blocks instead of
 * one continuous column.
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    name: String,
    description: String,
    granted: Boolean,
    isLast: Boolean = false,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!granted) Modifier.clickable { onGrant() } else Modifier)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(Spacing.sm))
        if (granted) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Granted", style = MaterialTheme.typography.labelMedium) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                border = null
            )
        } else {
            AssistChip(
                onClick = onGrant,
                label = { Text("Grant", style = MaterialTheme.typography.labelMedium) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                border = null
            )
        }
    }
    if (!isLast) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OnResumeRefresh(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun hasNotificationAccess(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.contains(context.packageName)
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    } else true
}

/**
 * Android 14 made USE_FULL_SCREEN_INTENT revocable per app. It is what lets the
 * alarm take over a locked screen, so if it is off the takeover quietly becomes
 * a normal heads-up notification with no indication why — hence surfacing it
 * alongside the other permissions. Always true below API 34, where the
 * permission is granted at install and cannot be withdrawn.
 */
private fun canUseFullScreenIntent(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    } else true
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Fall back to the app's notification settings if the OEM has no such screen.
    if (!runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
