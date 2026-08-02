package com.calendareventsnooze.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ScreenLockPortrait
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.TestAlarmHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // Recompute permissions / snoozed alarms whenever the screen resumes.
    var refreshKey by remember { mutableIntStateOf(0) }
    OnResumeRefresh { refreshKey++ }

    var managing by remember { mutableStateOf<SnoozedAlarmRecord?>(null) }
    val alarms = remember(refreshKey) { AppPrefs.getAllSnoozedAlarms(context) }

    val notificationAccess = remember(refreshKey) { hasNotificationAccess(context) }
    val overlay = remember(refreshKey) { Settings.canDrawOverlays(context) }
    val exactAlarm = remember(refreshKey) { canScheduleExactAlarms(context) }
    val readCalendar = remember(refreshKey) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }
    val allGranted = notificationAccess && overlay && exactAlarm && readCalendar

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
            SnoozedAlarmsSection(alarms = alarms, onManage = { managing = it })
        }

        Spacer(Modifier.height(Spacing.xl))

        // ---- Force Stop ----
        // M3: a destructive action takes the error role rather than an arbitrary
        // red, so it stays legible in both schemes.
        Button(
            onClick = {
                // Emergency: silence any alarm, clear notifications, then hard-reset
                // the app process so nothing stuck can keep sounding.
                AlarmService.forceStopEverything(context)
                android.os.Process.killProcess(android.os.Process.myPid())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            // UI.10 — the stop glyph is drawn, not an emoji, so it can be red.
            Box(
                Modifier
                    .size(20.dp)
                    .border(2.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.size(Spacing.md))
            Text("FORCE STOP APP", style = MaterialTheme.typography.titleMedium)
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
                Icon(Icons.Outlined.ScreenLockPortrait, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Test on lock screen (+5 sec)",
                    style = MaterialTheme.typography.labelLarge)
            }
        }

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
                            granted = readCalendar,
                            isLast = true
                        ) {
                            calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
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
