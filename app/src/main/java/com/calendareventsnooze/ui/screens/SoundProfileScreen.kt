package com.calendareventsnooze.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SoundProfile
import com.calendareventsnooze.model.VibrationPreset
import com.calendareventsnooze.ui.components.SectionCard
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.util.playOnce
import com.calendareventsnooze.util.vibratorOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MODES = listOf(RingerMode.SOUND_ON, RingerMode.VIBRATE, RingerMode.SILENT)
private val MODE_TITLES = listOf("Sound On", "Vibrate", "Silent")

/**
 * UI.11 — [selectedSubTab] is hoisted into MainActivity so the chosen sub-tab
 * survives leaving the Sound & Vibration tab and coming back.
 *
 * M3.1 — the sub-tabs are a secondary tab row (M3 nests secondary under
 * primary) and swiping here moves between sound modes; the outer pager is left
 * via the primary tab row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SoundProfileScreen(selectedSubTab: Int, onSubTabChange: (Int) -> Unit) {
    val subTab = selectedSubTab.coerceIn(0, MODES.lastIndex)
    val pagerState = rememberPagerState(initialPage = subTab, pageCount = { MODES.size })
    val scope = rememberCoroutineScope()

    // UI.6 — one scroll state per sub-tab, owned here so it survives switching
    // tabs; each mode returns to where it was left.
    val scrollStates = listOf(
        rememberScrollState(), rememberScrollState(), rememberScrollState()
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onSubTabChange(it) }
    }
    LaunchedEffect(subTab) {
        if (pagerState.currentPage != subTab) pagerState.animateScrollToPage(subTab)
    }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            MODE_TITLES.forEachIndexed { i, title ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            title,
                            // UI.27 — 15% larger than the M3 titleSmall these
                            // used to be (the primary tabs went up 20%).
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize =
                                    MaterialTheme.typography.titleSmall.fontSize * 1.15f
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ProfileEditor(
                mode = MODES[page],
                scrollState = scrollStates[page]
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(mode: RingerMode, scrollState: ScrollState) {
    val context = LocalContext.current
    val initial = remember(mode) { AppPrefs.getSoundProfile(context, mode) }

    var soundEnabled by remember(mode) { mutableStateOf(initial.soundEnabled) }
    var soundUri by remember(mode) { mutableStateOf(initial.soundUri) }
    // F.10 — the ringtone's display name ("Castle"), resolved from the URI.
    val soundTitle = remember(soundUri) { ringtoneTitle(context, soundUri) }
    var alarmVolume by remember(mode) { mutableIntStateOf(initial.alarmVolumePercent) }
    var soundDelaySeconds by remember(mode) {
        mutableStateOf(initial.soundDelaySeconds.toString())
    }
    var fadeInSeconds by remember(mode) {
        mutableStateOf(initial.fadeInSeconds.toString())
    }
    var stopsAfterSeconds by remember(mode) {
        mutableStateOf(initial.soundStopsAfterSeconds.toString())
    }
    // UI.22 — the vibration counterparts of the two sound timings.
    var vibrationDelaySeconds by remember(mode) {
        mutableStateOf(initial.vibrationDelaySeconds.toString())
    }
    var vibrationStopsAfterSeconds by remember(mode) {
        mutableStateOf(initial.vibrationStopsAfterSeconds.toString())
    }
    var vibrationEnabled by remember(mode) { mutableStateOf(initial.vibrationEnabled) }
    var vibrationPreset by remember(mode) { mutableStateOf(initial.vibrationPreset) }
    var showBuzzDialog by remember(mode) { mutableStateOf(false) }

    // UI.25 — each of the five timings is now behind its own switch. The switch
    // state is held separately rather than derived from "value > 0", so clearing
    // the box while typing cannot make it vanish under the user's finger. Off
    // persists as 0, which is what every consumer already reads as "not set".
    var soundDelayOn by remember(mode) { mutableStateOf(initial.soundDelaySeconds > 0) }
    var fadeInOn by remember(mode) { mutableStateOf(initial.fadeInSeconds > 0) }
    var soundStopsOn by remember(mode) {
        mutableStateOf(initial.soundStopsAfterSeconds > 0)
    }
    var vibrationDelayOn by remember(mode) {
        mutableStateOf(initial.vibrationDelaySeconds > 0)
    }
    var vibrationStopsOn by remember(mode) {
        mutableStateOf(initial.vibrationStopsAfterSeconds > 0)
    }
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
        soundEnabled, soundUri, alarmVolume, soundDelaySeconds, fadeInSeconds,
        stopsAfterSeconds,
        vibrationEnabled, vibrationPreset,
        vibrationDelaySeconds, vibrationStopsAfterSeconds,
        soundDelayOn, fadeInOn, soundStopsOn, vibrationDelayOn, vibrationStopsOn,
        buzzOnIdx, buzzOffIdx, buzzesIdx,
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
                alarmVolumePercent = alarmVolume,
                // UI.25 — a switch that is off writes 0 but leaves whatever the
                // user typed in the box, so turning it back on restores it.
                soundDelaySeconds = seconds(soundDelayOn, soundDelaySeconds),
                fadeInSeconds = seconds(fadeInOn, fadeInSeconds),
                soundStopsAfterSeconds = seconds(soundStopsOn, stopsAfterSeconds),
                vibrationEnabled = vibrationEnabled,
                vibrationPreset = vibrationPreset,
                vibrationDelaySeconds = seconds(vibrationDelayOn, vibrationDelaySeconds),
                vibrationStopsAfterSeconds =
                    seconds(vibrationStopsOn, vibrationStopsAfterSeconds),
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
            .verticalScrollbar(scrollState, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(scrollState)
            .padding(Spacing.lg)
    ) {
        Text(
            "Changes are saved automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))

        // ---- Sound ----
        SectionCard("Sound") {
            SwitchRow("Enable sound alarm", soundEnabled) { soundEnabled = it }
            // UI.16 — the rest of the section only matters once sound is on.
            AnimatedVisibility(visible = soundEnabled) {
            Column {
            Spacer(Modifier.height(Spacing.md))
            // F.10 — the ringtone's own title, at the same weight as the switch
            // label above, with the raw URI kept underneath for reference.
            Text(
                soundTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                soundPath(soundUri),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Change alarm sound")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                            Settings.System.DEFAULT_ALARM_ALERT_URI)
                        val existing = soundUri?.let { Uri.parse(it) }
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                    }
                    ringtoneLauncher.launch(intent)
                },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Change Sound", style = MaterialTheme.typography.labelLarge)
            }

            // F.10 — playback shaping.
            Spacer(Modifier.height(Spacing.lg))
            Text("Alarm Volume", style = MaterialTheme.typography.titleSmall)
            // UI.25 — the caption explains the slider, so it reads before it
            // rather than after.
            Text(
                "Overrides the phone's alarm volume while this alarm rings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = alarmVolume.toFloat(),
                    onValueChange = {
                        alarmVolume = it.roundToInt().coerceIn(
                            SoundProfile.MIN_VOLUME_PERCENT, SoundProfile.MAX_VOLUME_PERCENT)
                    },
                    valueRange = SoundProfile.MIN_VOLUME_PERCENT.toFloat()..
                        SoundProfile.MAX_VOLUME_PERCENT.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(Spacing.md))
                Text(
                    "$alarmVolume%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // UI.25 — each timing is behind its own switch; the box only
            // appears once it is asked for.
            Spacer(Modifier.height(Spacing.md))
            ToggleNumberField(
                switchLabel = "Sound Delay",
                fieldLabel = "Sound Delay (seconds)",
                enabled = soundDelayOn,
                onEnabledChange = { soundDelayOn = it },
                value = soundDelaySeconds,
                onValueChange = { soundDelaySeconds = it }
            )
            ToggleNumberField(
                switchLabel = "Sound Fade In",
                fieldLabel = "Sound Fade In (seconds)",
                enabled = fadeInOn,
                onEnabledChange = { fadeInOn = it },
                value = fadeInSeconds,
                onValueChange = { fadeInSeconds = it }
            )
            ToggleNumberField(
                switchLabel = "Sound Stops After",
                fieldLabel = "Sound Stops After (seconds)",
                enabled = soundStopsOn,
                onEnabledChange = { soundStopsOn = it },
                value = stopsAfterSeconds,
                onValueChange = { stopsAfterSeconds = it }
            )
            }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Vibration ----
        SectionCard("Vibration") {
            SwitchRow("Enable vibration", vibrationEnabled) { vibrationEnabled = it }
            // UI.16 — the five sliders and the test button are irrelevant with
            // vibration off.
            AnimatedVisibility(visible = vibrationEnabled) {
            Column {
            Spacer(Modifier.height(Spacing.lg))

            // UI.25 — the five sliders are no longer the primary control. Four
            // named characters cover what people actually want, and the sliders
            // live behind "Custom Buzz".
            VibrationPresetMenu(vibrationPreset) { vibrationPreset = it }

            AnimatedVisibility(visible = vibrationPreset == VibrationPreset.CUSTOM) {
                Column {
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedButton(
                        onClick = { showBuzzDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Customize your Buzz",
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // F.9 — plays the current buzz once so it can be felt without
            // firing a whole alarm. UI.25 moves it directly above the timing
            // switches, and it now tests whichever character is selected.
            Spacer(Modifier.height(Spacing.lg))
            FilledTonalButton(
                onClick = {
                    vibratorOf(context).playOnce(
                        testWaveform(
                            preset = vibrationPreset,
                            buzzOnIdx = buzzOnIdx,
                            buzzOffIdx = buzzOffIdx,
                            buzzes = buzzesIdx + 1,
                            patternDelayIdx = patternDelayIdx
                        )
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally), // UI.14
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.Vibration, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Test Vibration", style = MaterialTheme.typography.labelLarge)
            }

            // UI.22 / UI.25 — the timings that mirror the sound section's.
            Spacer(Modifier.height(Spacing.lg))
            ToggleNumberField(
                switchLabel = "Vibration Delay",
                fieldLabel = "Vibration Delay (seconds)",
                enabled = vibrationDelayOn,
                onEnabledChange = { vibrationDelayOn = it },
                value = vibrationDelaySeconds,
                onValueChange = { vibrationDelaySeconds = it }
            )
            ToggleNumberField(
                switchLabel = "Vibration Stops After",
                fieldLabel = "Vibration Stops After (seconds)",
                enabled = vibrationStopsOn,
                onEnabledChange = { vibrationStopsOn = it },
                value = vibrationStopsAfterSeconds,
                onValueChange = { vibrationStopsAfterSeconds = it }
            )
            }
            }
        }

        if (showBuzzDialog) {
            CustomBuzzDialog(
                buzzOnIdx = buzzOnIdx, onBuzzOn = { buzzOnIdx = it },
                buzzOffIdx = buzzOffIdx, onBuzzOff = { buzzOffIdx = it },
                buzzesIdx = buzzesIdx, onBuzzes = { buzzesIdx = it },
                patternDelayIdx = patternDelayIdx, onPatternDelay = { patternDelayIdx = it },
                repetitionsIdx = repetitionsIdx, onRepetitions = { repetitionsIdx = it },
                onDismiss = { showBuzzDialog = false }
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Sequencing ----
        val sequencingApplies = soundEnabled && vibrationEnabled
        SectionCard(
            "Sequencing",
            hint = if (sequencingApplies) null else "opens when Sound + Vibration are ON"
        ) {
            // UI.16 — ordering only means anything when there are two things to
            // order, so this collapses unless sound AND vibration are both on.
            AnimatedVisibility(visible = sequencingApplies) {
            Column {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = soundStartsFirst,
                    onClick = { soundStartsFirst = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Sound first", style = MaterialTheme.typography.labelLarge) }
                SegmentedButton(
                    selected = !soundStartsFirst,
                    onClick = { soundStartsFirst = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Vibration first", style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(Spacing.lg))
            NumberField(
                value = secondDelay,
                onValueChange = { secondDelay = it },
                label = "Delay before the second one starts (seconds)"
            )
            }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Auto-snooze / auto-dismiss ----
        SectionCard("Auto-Snooze / Auto-Dismiss") {
            // UI.20 — a two-way choice rather than an on/off switch, matching
            // the Sequencing control. Each side owns its own fields below.
            val autoSnooze = autoDismissAction == AutoDismissAction.SNOOZE
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = autoSnooze,
                    onClick = { autoDismissAction = AutoDismissAction.SNOOZE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Auto-Snooze", style = MaterialTheme.typography.labelLarge) }
                SegmentedButton(
                    selected = !autoSnooze,
                    onClick = { autoDismissAction = AutoDismissAction.DISMISS },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Auto-Dismiss", style = MaterialTheme.typography.labelLarge) }
            }

            AnimatedVisibility(visible = autoSnooze) {
            Column {
            Spacer(Modifier.height(Spacing.lg))
            NumberField(
                value = autoDismissSeconds,
                onValueChange = { autoDismissSeconds = it },
                label = "Trigger Auto-Snooze after (seconds) — 0 to disable"
            )
            Spacer(Modifier.height(Spacing.md))
            NumberField(
                value = autoSnoozeMinutes,
                onValueChange = { autoSnoozeMinutes = it },
                label = "Auto-snooze for (minutes)"
            )
            Spacer(Modifier.height(Spacing.md))
            NumberField(
                value = maxRetries,
                onValueChange = { maxRetries = it },
                label = "Max auto-snooze attempts before auto-dismiss"
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "0 attempts = dismiss immediately. 3 = snooze 3 times, then dismiss.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
            }

            // Auto-Dismiss has a single timing: how long before it gives up.
            // It shares autoDismissSeconds with the snooze branch, so the value
            // carries across when the user flips between the two.
            AnimatedVisibility(visible = !autoSnooze) {
            Column {
            Spacer(Modifier.height(Spacing.lg))
            NumberField(
                value = autoDismissSeconds,
                onValueChange = { autoDismissSeconds = it },
                label = "Trigger Auto-Dismiss after (seconds) — 0 to disable"
            )
            }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
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

/** UI.25 — a switch that is off stores 0, whatever is still typed in its box. */
private fun seconds(enabled: Boolean, text: String): Int =
    if (enabled) text.toIntOrNull()?.coerceAtLeast(0) ?: 0 else 0

/**
 * UI.25 — what "Test Vibration" plays.
 *
 * A custom buzz plays exactly one pattern, repetitions ignored, as it always
 * has. A preset buzzes forever during a real alarm, so the test plays a few
 * cycles of it — long enough to feel the character, short enough to end on its
 * own.
 */
private const val PRESET_TEST_CYCLES = 3

private fun testWaveform(
    preset: VibrationPreset,
    buzzOnIdx: Int,
    buzzOffIdx: Int,
    buzzes: Int,
    patternDelayIdx: Int
): LongArray {
    val profile = { p: VibrationPreset, reps: Int ->
        SoundProfile(
            ringerMode = RingerMode.SOUND_ON,
            soundEnabled = false,
            soundUri = null,
            alarmVolumePercent = AppPrefs.DEFAULT_ALARM_VOLUME_PERCENT,
            soundDelaySeconds = 0,
            fadeInSeconds = 0,
            soundStopsAfterSeconds = 0,
            vibrationEnabled = true,
            vibrationPreset = p,
            vibrationDelaySeconds = 0,
            vibrationStopsAfterSeconds = 0,
            buzzOnMs = SoundProfile.BUZZ_LENGTH_OPTIONS[buzzOnIdx],
            buzzOffMs = SoundProfile.BUZZ_LENGTH_OPTIONS[buzzOffIdx],
            buzzesPerPattern = buzzes,
            delayBetweenPatternsMs = SoundProfile.PATTERN_DELAY_OPTIONS[patternDelayIdx],
            vibrationRepetitions = reps,
            soundStartsFirst = true,
            secondStartDelaySeconds = 0,
            autoDismissSeconds = 0,
            autoDismissAction = AutoDismissAction.DISMISS,
            autoDismissSnoozeMinutes = 0,
            autoDismissMaxRetries = 0
        )
    }
    if (preset == VibrationPreset.CUSTOM) {
        return profile(VibrationPreset.CUSTOM, 1).buildVibrationWaveform()
    }
    // The preset waveform is [0, on, off]; repeat its on/off pair by hand
    // rather than looping the vibrator, so the test always stops itself.
    val single = profile(preset, 1).buildVibrationWaveform()
    val out = ArrayList<Long>(1 + PRESET_TEST_CYCLES * 2)
    out.add(0L)
    repeat(PRESET_TEST_CYCLES) { i ->
        out.add(single[1])
        if (i < PRESET_TEST_CYCLES - 1) out.add(single[2])
    }
    return out.toLongArray()
}

/**
 * UI.25 — the vibration character menu. Four named presets buzz until something
 * stops the alarm; "Custom Buzz" is the only one that uses the pattern sliders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VibrationPresetMenu(
    selected: VibrationPreset,
    onSelect: (VibrationPreset) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Buzz style", style = MaterialTheme.typography.bodySmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VibrationPreset.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

/**
 * UI.25 — the five pattern sliders, moved out of the section and behind
 * "Customize your Buzz". Every change still auto-saves as it happens, so the
 * dialog needs no confirm button — only a way out.
 */
@Composable
private fun CustomBuzzDialog(
    buzzOnIdx: Int, onBuzzOn: (Int) -> Unit,
    buzzOffIdx: Int, onBuzzOff: (Int) -> Unit,
    buzzesIdx: Int, onBuzzes: (Int) -> Unit,
    patternDelayIdx: Int, onPatternDelay: (Int) -> Unit,
    repetitionsIdx: Int, onRepetitions: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize your Buzz", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OptionSlider("Buzz-On Length", SoundProfile.SIZE_LABELS, buzzOnIdx, onBuzzOn)
                Spacer(Modifier.height(Spacing.lg))
                OptionSlider("Buzz-Off Length", SoundProfile.SIZE_LABELS, buzzOffIdx, onBuzzOff)
                Spacer(Modifier.height(Spacing.lg))
                OptionSlider("Buzzes per Pattern", COUNT_LABELS, buzzesIdx, onBuzzes)
                Spacer(Modifier.height(Spacing.lg))
                OptionSlider("Delay Between Patterns", SoundProfile.SIZE_LABELS,
                    patternDelayIdx, onPatternDelay)
                Spacer(Modifier.height(Spacing.lg))
                OptionSlider("Number of Pattern Repetitions", COUNT_LABELS,
                    repetitionsIdx, onRepetitions)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/**
 * UI.25 — a switch and the number field it governs. Off hides the field
 * entirely; the value it wrote is 0, which every consumer already reads as
 * "not set".
 */
@Composable
private fun ToggleNumberField(
    switchLabel: String,
    fieldLabel: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit
) {
    SwitchRow(switchLabel, enabled, onEnabledChange)
    AnimatedVisibility(visible = enabled) {
        Column {
            Spacer(Modifier.height(Spacing.sm))
            NumberField(value = value, onValueChange = onValueChange, label = fieldLabel)
        }
    }
    Spacer(Modifier.height(Spacing.md))
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
        Text(title, style = MaterialTheme.typography.titleSmall)
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
                .padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = if (i == index) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.labelMedium,
                    color = if (i == index) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun soundPath(uri: String?): String =
    if (uri.isNullOrEmpty()) "System default" else uri

/**
 * F.10 — the ringtone's own display name, e.g. "Castle". Falls back to the
 * default alarm sound's title when nothing is chosen, and to a plain label if
 * the URI no longer resolves (a deleted file, or a media store the app can't
 * read).
 */
private fun ringtoneTitle(context: Context, uri: String?): String {
    val target = if (uri.isNullOrEmpty()) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    } else {
        Uri.parse(uri)
    } ?: return "Default alarm sound"
    val title = runCatching {
        RingtoneManager.getRingtone(context, target)?.getTitle(context)
    }.getOrNull()
    return if (title.isNullOrBlank()) "Default alarm sound" else title
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
