package com.calendareventsnooze.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.ui.components.OnResumeRefresh
import com.calendareventsnooze.ui.components.SectionCard
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.CalendarApps
import kotlin.math.sqrt

/**
 * The five things the app cannot work without. Grouped so the nav bar's badge
 * and the Permissions card can't disagree about how many are outstanding.
 */
data class PermissionsStatus(
    val notificationAccess: Boolean = false,
    val overlay: Boolean = false,
    val exactAlarm: Boolean = false,
    val readCalendar: Boolean = false,
    val fullScreenIntent: Boolean = false,
    /**
     * Round 20 — recommended, not required: the app works without it, it just
     * becomes less reliable on phones that sleep background apps. Deliberately
     * **excluded** from [pendingCount], so leaving it off never puts a red badge
     * on the Settings tab or reports the app as misconfigured.
     */
    val batteryUnrestricted: Boolean = false
) {
    val pendingCount: Int
        get() = listOf(
            notificationAccess, overlay, exactAlarm, readCalendar, fullScreenIntent
        ).count { !it }

    val allGranted: Boolean get() = pendingCount == 0
}

fun readPermissions(context: Context) = PermissionsStatus(
    notificationAccess = hasNotificationAccess(context),
    overlay = Settings.canDrawOverlays(context),
    exactAlarm = canScheduleExactAlarms(context),
    readCalendar = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
    fullScreenIntent = canUseFullScreenIntent(context),
    batteryUnrestricted = isBatteryUnrestricted(context)
)

/**
 * True when Android has been told to stop putting the app to sleep. Optional,
 * but it is the single biggest reliability factor on OEMs that kill background
 * work — without it the notification listener and the alarm service can both be
 * stopped between alarms.
 */
private fun isBatteryUnrestricted(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(false)

/**
 * Opens the system's own confirm dialog where possible; some OEMs remove that
 * activity, so fall back to the full battery-optimisation list.
 */
private fun openBatterySettings(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    OnResumeRefresh { refreshKey++ }
    val status = remember(refreshKey) { readPermissions(context) }

    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    var permissionsExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(scrollState)
            .padding(Spacing.lg)
    ) {
        // UI.30 — the same shape as the two cards below it: heading band with
        // the status icon beside the title, and a body whose first row
        // summarises what the section contains and doubles as the expander.
        SectionCard(
            title = "Permissions",
            headerExtra = {
                Spacer(Modifier.size(Spacing.sm))
                PermissionsBadge(status)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { permissionsExpanded = !permissionsExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        status.allGranted -> "All permissions are granted"
                        status.pendingCount == 1 -> "1 permission pending"
                        else -> "${status.pendingCount} permissions pending"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.allGranted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                )
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
                    Spacer(Modifier.height(Spacing.sm))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(
                        name = "Notification Access",
                        description = "Allows intercepting calendar notifications",
                        granted = status.notificationAccess
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    PermissionRow(
                        name = "Display over other apps",
                        description = "Required to show the alarm over the lock screen",
                        granted = status.overlay
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    PermissionRow(
                        name = "Schedule Exact Alarms",
                        description = "Required for precise snooze timing",
                        granted = status.exactAlarm
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
                        granted = status.readCalendar
                    ) {
                        calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                    // Android 14+ — revocable, and without it the takeover
                    // silently degrades to an ordinary heads-up notification.
                    PermissionRow(
                        name = "Full-screen notifications",
                        description = "Required for the alarm to take over the screen",
                        granted = status.fullScreenIntent
                    ) {
                        openFullScreenIntentSettings(context)
                    }
                    // Optional — see PermissionsStatus.batteryUnrestricted. It
                    // never counts towards the badge or the pending total.
                    PermissionRow(
                        name = "Unrestricted background battery usage (OPTIONAL)",
                        description = "Recommended: stops Android sleeping the app " +
                            "between alarms",
                        granted = status.batteryUnrestricted,
                        isLast = true
                    ) {
                        openBatterySettings(context)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        CalendarAppsCard(refreshKey) { refreshKey++ }

        Spacer(Modifier.height(Spacing.lg))
        PendingCard("Silent Hours/Days")

        Spacer(Modifier.height(Spacing.lg))
        PendingCard("Ignore These Calendars")

        Spacer(Modifier.height(Spacing.xl))
    }
}

/**
 * Round 20 — which apps' reminders the takeover reacts to.
 *
 * The watched set was hardcoded to three packages and had no UI at all, so on a
 * phone whose calendar was not one of them the app silently did nothing while
 * reporting every permission granted. The list offers every known calendar app
 * that is installed, plus anything the system reports as a calendar, and it
 * warns rather than silently doing nothing when the selection is empty.
 */
@Composable
private fun CalendarAppsCard(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(refreshKey) { CalendarApps.installedKnown(context) }
    var selected by remember(refreshKey) {
        mutableStateOf(AppPrefs.getCalendarPackages(context))
    }
    var expanded by remember { mutableStateOf(false) }

    // Packages the user chose that are no longer installed stay selected but are
    // shown separately, so removing an app does not silently drop the setting.
    val missing = selected - installed.map { it.packageName }.toSet()

    SectionCard("Calendar Apps") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    selected.isEmpty() -> "No calendar app selected"
                    selected.size == 1 -> "1 calendar app watched"
                    else -> "${selected.size} calendar apps watched"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected.isEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Reminders from the apps you tick here are replaced by the " +
                        "full-screen alarm. Everything else is left alone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))

                if (installed.isEmpty() && missing.isEmpty()) {
                    Text(
                        "No calendar app found on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                }

                installed.forEach { entry ->
                    CalendarAppRow(
                        label = entry.label,
                        packageName = entry.packageName,
                        checked = entry.packageName in selected,
                        installed = true,
                        mixedUse = !CalendarApps.isDedicatedCalendar(entry.packageName)
                    ) { checked ->
                        selected = if (checked) selected + entry.packageName
                                   else selected - entry.packageName
                        AppPrefs.saveCalendarPackages(context, selected)
                        onChanged()
                    }
                }

                missing.forEach { pkg ->
                    CalendarAppRow(
                        label = CalendarApps.labelFor(context, pkg),
                        packageName = pkg,
                        checked = true,
                        installed = false
                    ) { checked ->
                        selected = if (checked) selected + pkg else selected - pkg
                        AppPrefs.saveCalendarPackages(context, selected)
                        onChanged()
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarAppRow(
    label: String,
    packageName: String,
    checked: Boolean,
    installed: Boolean,
    mixedUse: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    !installed -> "$packageName · not installed"
                    // The warning that stops someone turning every incoming
                    // email into a 3am full-screen alarm.
                    mixedUse -> "Also posts non-calendar notifications"
                    else -> packageName
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mixedUse && installed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A section that exists so its place in the order is settled; no controls yet.
 * UI.32 — the marker rides in the heading at 75% of its size.
 */
@Composable
private fun PendingCard(title: String) {
    SectionCard(title, titleSuffix = "[Coming soon]") {
        Text(
            "Not built yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The permissions status as one small circle: a count of what is outstanding on
 * the error colour, or a check once nothing is. The nav bar shows the same
 * thing beside the Settings icon, from the same [PermissionsStatus], so the two
 * cannot disagree.
 */
@Composable
fun PermissionsBadge(status: PermissionsStatus, size: androidx.compose.ui.unit.Dp = 20.dp) {
    if (status.allGranted) {
        OutlinedCheckIcon(size)
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    status.pendingCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * UI.27 — the "all granted" mark: a hollow circle with a check inside, both at
 * the same weight, the long arm ending exactly **on** the circle at 45° up-right.
 * Material's `CheckCircle` is a solid disc and its outlined variant leaves the
 * check floating well inside the ring.
 */
@Composable
fun OutlinedCheckIcon(size: androidx.compose.ui.unit.Dp = 20.dp) {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val radius = (this.size.minDimension - stroke) / 2f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = stroke)
        )

        // On a 45° diagonal the circle is radius/√2 away on each axis.
        val diagonal = radius / sqrt(2f)
        val vertex = Offset(cx - 0.09f * this.size.width, cy + 0.09f * this.size.height)
        val path = Path().apply {
            moveTo(vertex.x - 0.175f * this@Canvas.size.width,
                   vertex.y - 0.175f * this@Canvas.size.height)
            lineTo(vertex.x, vertex.y)
            lineTo(cx + diagonal, cy - diagonal)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
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
 * a normal heads-up notification with no indication why. Always true below API
 * 34, where the permission is granted at install and cannot be withdrawn.
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
