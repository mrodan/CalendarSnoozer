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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozedAlarmRecord
import com.calendareventsnooze.service.AlarmService
import com.calendareventsnooze.ui.theme.AppButtonRegular
import com.calendareventsnooze.ui.theme.AppButtonRegularText
import com.calendareventsnooze.ui.theme.AppForceStop
import com.calendareventsnooze.ui.theme.AppForceStopText
import com.calendareventsnooze.ui.theme.AppGreenCheck
import com.calendareventsnooze.ui.theme.AppWarningYellow
import com.calendareventsnooze.util.TestAlarmHelper

/**
 * UI.4 — a Gmail-style scrollbar: a thumb drawn down the right edge, sized and
 * positioned from the scroll state, shown only when the content overflows.
 */
private fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent
    val viewport = size.height
    val thumbWidth = 4.dp.toPx()
    val thumbHeight = (viewport / (viewport + state.maxValue)) * viewport
    val travel = viewport - thumbHeight
    val offsetY = (state.value.toFloat() / state.maxValue) * travel
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - thumbWidth, offsetY),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f)
    )
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // Recompute permissions / snoozed alarms whenever the screen resumes.
    var refreshKey by remember { mutableIntStateOf(0) }
    OnResumeRefresh { refreshKey++ }

    var managing by remember { mutableStateOf<SnoozedAlarmRecord?>(null) }
    val alarms = remember(refreshKey) { AppPrefs.getAllSnoozedAlarms(context) }

    val current = managing
    if (current != null) {
        ManageSnoozeView(
            record = current,
            onDone = { managing = null; refreshKey++ }
        )
        return
    }

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
            .verticalScrollbar(scrollState, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ---- Snoozed Alarms (UI.4) ----
        SnoozedAlarmsSection(alarms = alarms, onManage = { managing = it })

        Spacer(Modifier.height(24.dp))

        // ---- Force Stop ----
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
            colors = ButtonDefaults.buttonColors(
                containerColor = AppForceStop, contentColor = AppForceStopText)
        ) {
            Text("⏹  FORCE STOP", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        // ---- Test Alarm ----
        Text("Test Alarm", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                // UI.5 — matches the lock-screen test button.
                Button(
                    onClick = {
                        if (Settings.canDrawOverlays(context)) {
                            TestAlarmHelper.fireTestAlarmNow(context)
                        } else showOverlayDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppButtonRegular, contentColor = AppButtonRegularText)
                ) { Text("🔔  FIRE TEST ALARM NOW", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (Settings.canDrawOverlays(context)) {
                            TestAlarmHelper.fireTestAlarmDelayed(context, 5)
                        } else showOverlayDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppButtonRegular, contentColor = AppButtonRegularText)
                ) { Text("🔒  TEST ON LOCK SCREEN (+5 sec)", fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Permissions (collapsible) ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { permissionsExpanded = !permissionsExpanded }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (allGranted) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "All granted",
                            tint = AppGreenCheck, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Filled.Warning, contentDescription = "Permissions pending",
                            tint = AppWarningYellow, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text("Permissions", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(
                        if (permissionsExpanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (permissionsExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (permissionsExpanded) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
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
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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

@Composable
private fun PermissionRow(
    name: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (!granted) Modifier.clickable { onGrant() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Text("✓ Granted", color = AppGreenCheck, fontWeight = FontWeight.Bold)
            } else {
                Text("✗ Required", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold)
            }
        }
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
