package com.calendareventsnooze.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SoundProfile
import kotlin.math.roundToInt

/**
 * UI.11 — [selectedSubTab] is hoisted into MainActivity so the chosen sub-tab
 * survives leaving the Sound & Vibration tab and coming back.
 */
@Composable
fun SoundProfileScreen(selectedSubTab: Int, onSubTabChange: (Int) -> Unit) {
    val modes = listOf(RingerMode.SOUND_ON, RingerMode.VIBRATE, RingerMode.SILENT)
    val titles = listOf("Sound On", "Vibrate Mode", "Silent")

    // UI.6 — one scroll state per sub-tab, owned here so it survives switching
    // tabs; each mode returns to where it was left.
    val scrollStates = listOf(
        rememberScrollState(), rememberScrollState(), rememberScrollState()
    )
    val subTab = selectedSubTab.coerceIn(0, modes.lastIndex)

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            titles.forEachIndexed { i, t ->
                Tab(selected = subTab == i, onClick = { onSubTabChange(i) },
                    text = { Text(t, fontSize = 13.sp) })
            }
        }
        // key() ensures each sub-tab keeps its own independent state.
        androidx.compose.runtime.key(subTab) {
            ProfileEditor(
                mode = modes[subTab],
                scrollState = scrollStates[subTab]
            )
        }
    }
}

@Composable
private fun ProfileEditor(mode: RingerMode, scrollState: ScrollState) {
    val context = LocalContext.current
    val initial = remember(mode) { AppPrefs.getSoundProfile(context, mode) }

    var soundEnabled by remember(mode) { mutableStateOf(initial.soundEnabled) }
    var soundUri by remember(mode) { mutableStateOf(initial.soundUri) }
    var vibrationEnabled by remember(mode) { mutableStateOf(initial.vibrationEnabled) }
    // F.7 — the five vibration sliders. Each holds the *index* of its stop, so a
    // stored value that isn't exactly on a stop snaps to the nearest one.
    var buzzOnIdx by remember(mode) {
        mutableIntStateOf(nearestIndex(SoundProfile.BUZZ_LENGTH_OPTIONS, initial.buzzOnMs))
    }
    var buzzOffIdx by remember(mode) {
        mutableIntStateOf(nearestIndex(SoundProfile.BUZZ_LENGTH_OPTIONS, initial.buzzOffMs))
    }
    var buzzesIdx by remember(mode) {
        mutableIntStateOf(initial.buzzesPerPattern.coerceIn(1, SoundProfile.MAX_COUNT) - 1)
    }
    var patternDelayIdx by remember(mode) {
        mutableIntStateOf(
            nearestIndex(SoundProfile.PATTERN_DELAY_OPTIONS, initial.delayBetweenPatternsMs))
    }
    var repetitionsIdx by remember(mode) {
        mutableIntStateOf(initial.vibrationRepetitions.coerceIn(1, SoundProfile.MAX_COUNT) - 1)
    }
    var soundStartsFirst by remember(mode) { mutableStateOf(initial.soundStartsFirst) }
    var secondDelay by remember(mode) {
        mutableStateOf(initial.secondStartDelaySeconds.toString())
    }
    var autoDismissSeconds by remember(mode) {
        mutableStateOf(initial.autoDismissSeconds.toString())
    }
    var autoSnoozeMinutes by remember(mode) {
        mutableStateOf(initial.autoDismissSnoozeMinutes.toString())
    }
    var maxRetries by remember(mode) {
        mutableStateOf(initial.autoDismissMaxRetries.toString())
    }
    var autoDismissAction by remember(mode) { mutableStateOf(initial.autoDismissAction) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            soundUri = uri?.toString()
        }
    }

    // F.5 — every setting persists as soon as it changes, so there is no Save
    // button. Keying the effect on all editable state means no change can be
    // missed, whichever control produced it.
    LaunchedEffect(
        soundEnabled, soundUri, vibrationEnabled, buzzOnIdx, buzzOffIdx, buzzesIdx,
        patternDelayIdx, repetitionsIdx,
        soundStartsFirst, secondDelay, autoDismissSeconds, autoSnoozeMinutes, maxRetries,
        autoDismissAction
    ) {
        AppPrefs.saveSoundProfile(
            context,
            SoundProfile(
                ringerMode = mode,
                soundEnabled = soundEnabled,
                soundUri = soundUri,
                vibrationEnabled = vibrationEnabled,
                buzzOnMs = SoundProfile.BUZZ_LENGTH_OPTIONS[buzzOnIdx],
                buzzOffMs = SoundProfile.BUZZ_LENGTH_OPTIONS[buzzOffIdx],
                buzzesPerPattern = buzzesIdx + 1,
                delayBetweenPatternsMs = SoundProfile.PATTERN_DELAY_OPTIONS[patternDelayIdx],
                vibrationRepetitions = repetitionsIdx + 1,
                soundStartsFirst = soundStartsFirst,
                secondStartDelaySeconds = secondDelay.toIntOrNull() ?: 0,
                autoDismissSeconds = autoDismissSeconds.toIntOrNull() ?: 0,
                autoDismissAction = autoDismissAction,
                autoDismissSnoozeMinutes = autoSnoozeMinutes.toIntOrNull() ?: 0,
                autoDismissMaxRetries = maxRetries.toIntOrNull() ?: 0
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // UI.11 — same overflow-only scrollbar the Home tab uses.
            .verticalScrollbar(
                scrollState,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Changes are saved automatically.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // 🔊 SOUND
        SectionHeader("🔊 SOUND")
        SwitchRow("Enable sound alarm", soundEnabled) { soundEnabled = it }
        Text("Current sound: ${soundName(soundUri)}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose alarm sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    Settings.System.DEFAULT_ALARM_ALERT_URI)
                val existing = soundUri?.let { Uri.parse(it) }
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
            ringtoneLauncher.launch(intent)
        }) { Text("Choose") }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // 📳 VIBRATION
        SectionHeader("📳 VIBRATION")
        SwitchRow("Enable vibration", vibrationEnabled) { vibrationEnabled = it }
        Spacer(Modifier.height(12.dp))

        // F.7 — five sliders replace the raw comma-separated pattern field.
        OptionSlider(
            title = "Buzz-On Length",
            labels = SoundProfile.SIZE_LABELS,
            index = buzzOnIdx
        ) { buzzOnIdx = it }
        Spacer(Modifier.height(12.dp))
        OptionSlider(
            title = "Buzz-Off Length",
            labels = SoundProfile.SIZE_LABELS,
            index = buzzOffIdx
        ) { buzzOffIdx = it }
        Spacer(Modifier.height(12.dp))
        OptionSlider(
            title = "Number of Buzzes Pattern",
            labels = COUNT_LABELS,
            index = buzzesIdx
        ) { buzzesIdx = it }
        Spacer(Modifier.height(12.dp))
        OptionSlider(
            title = "Delay Between Patterns",
            labels = SoundProfile.SIZE_LABELS,
            index = patternDelayIdx
        ) { patternDelayIdx = it }
        Spacer(Modifier.height(12.dp))
        OptionSlider(
            title = "Number of Pattern Repetitions",
            labels = COUNT_LABELS,
            index = repetitionsIdx
        ) { repetitionsIdx = it }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // ⏱ SEQUENCING
        SectionHeader("⏱ SEQUENCING")
        RadioRow("Sound starts first", soundStartsFirst) { soundStartsFirst = true }
        RadioRow("Vibration starts first", !soundStartsFirst) { soundStartsFirst = false }
        OutlinedTextField(
            value = secondDelay,
            onValueChange = { secondDelay = it.filter { c -> c.isDigit() } },
            label = { Text("Delay seconds") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // ⏰ AUTO-SNOOZE / AUTO-DISMISS
        SectionHeader("AUTO-SNOOZE / AUTO-DISMISS")
        // UI.7 — one switch replaces the "Auto-snooze and retry" /
        // "Dismiss immediately (no retry)" radio pair.
        SwitchRow("Auto-Snooze ON", autoDismissAction == AutoDismissAction.SNOOZE) { on ->
            autoDismissAction =
                if (on) AutoDismissAction.SNOOZE else AutoDismissAction.DISMISS
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = autoDismissSeconds,
            onValueChange = { autoDismissSeconds = it.filter { c -> c.isDigit() } },
            label = { Text("Trigger Auto-Snooze after (seconds) — 0 to disable") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = autoSnoozeMinutes,
            onValueChange = { autoSnoozeMinutes = it.filter { c -> c.isDigit() } },
            label = { Text("Auto-snooze for (minutes)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = maxRetries,
            onValueChange = { maxRetries = it.filter { c -> c.isDigit() } },
            label = { Text("Max auto-snooze attempts before auto-dismiss — 0 to skip snoozing") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Text("0 attempts = dismiss immediately. 3 = snooze 3 times, then dismiss.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))
    }
}

/** Slider labels for the two 1..10 counts. */
private val COUNT_LABELS = (1..SoundProfile.MAX_COUNT).map { it.toString() }

/** Index of the stop closest to [value], so off-preset stored values snap. */
private fun nearestIndex(options: List<Int>, value: Int): Int {
    var best = 0
    options.forEachIndexed { i, option ->
        if (kotlin.math.abs(option - value) < kotlin.math.abs(options[best] - value)) best = i
    }
    return best
}

/**
 * F.7 — a discrete slider: title above, one label per stop below. The slider
 * value *is* the stop index, so it can never land between options.
 */
@Composable
private fun OptionSlider(
    title: String,
    labels: List<String>,
    index: Int,
    onIndexChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = index.toFloat(),
            onValueChange = { onIndexChange(it.roundToInt().coerceIn(0, labels.lastIndex)) },
            valueRange = 0f..labels.lastIndex.toFloat(),
            steps = (labels.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
        // SpaceBetween lines the labels up with the tick marks; the padding
        // compensates for the thumb's radius at either end.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun soundName(uri: String?): String {
    return if (uri.isNullOrEmpty()) "Default alarm sound" else uri
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
