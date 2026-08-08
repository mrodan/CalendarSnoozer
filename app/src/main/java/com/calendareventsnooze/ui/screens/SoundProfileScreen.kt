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
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AutoDismissAction
import com.calendareventsnooze.model.RingerMode
import com.calendareventsnooze.model.SoundProfile
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
                            style = MaterialTheme.typography.titleSmall,
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
        vibrationEnabled, vibrationDelaySeconds, vibrationStopsAfterSeconds,
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
                soundDelaySeconds = soundDelaySeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                fadeInSeconds = fadeInSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                soundStopsAfterSeconds = stopsAfterSeconds.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0,
                vibrationEnabled = vibrationEnabled,
                vibrationDelaySeconds = vibrationDelaySeconds.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0,
                vibrationStopsAfterSeconds = vibrationStopsAfterSeconds.toIntOrNull()
                    ?.coerceAtLeast(0) ?: 0,
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
        SettingsSection("Sound") {
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
            Text(
                "Overrides the phone's alarm volume while this alarm rings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.md))
            NumberField(
                value = soundDelaySeconds,
                onValueChange = { soundDelaySeconds = it },
                label = "Sound Delay (seconds) — 0 for none"
            )
            Spacer(Modifier.height(Spacing.md))
            NumberField(
                value = fadeInSeconds,
                onValueChange = { fadeInSeconds = it },
                label = "Sound Fade In (seconds) — 0 for none"
            )
            Spacer(Modifier.height(Spacing.md))
            NumberField(
                value = stopsAfterSeconds,
                onValueChange = { stopsAfterSeconds = it },
                label = "Sound Stops After (seconds) — 0 to keep sounding"
            )
            }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Vibration ----
        SettingsSection("Vibration") {
            SwitchRow("Enable vibration", vibrationEnabled) { vibrationEnabled = it }
            // UI.16 — the five sliders and the test button are irrelevant with
            // vibration off.
            AnimatedVisibility(visible = vibrationEnabled) {
            Column {
            Spacer(Modifier.height(Spacing.lg))

            // F.7 — five sliders replace the raw comma-separated pattern field.
            OptionSlider("Buzz-On Length", SoundProfile.SIZE_LABELS, buzzOnIdx) {
                buzzOnIdx = it
            }
            Spacer(Modifier.height(Spacing.lg))
            OptionSlider("Buzz-Off Length", SoundProfile.SIZE_LABELS, buzzOffIdx) {
                buzzOffIdx = it
            }
            Spacer(Modifier.height(Spacing.lg))
            OptionSlider("Number of Buzzes Pattern", COUNT_LABELS, buzzesIdx) {
                buzzesIdx = it
            }
            // F.9 — plays exactly one pattern (repetitions ignored) so the buzz
            // settings above can be felt without firing a whole alarm. F.11 puts
            // it directly under the sliders that shape a single pattern, above
            // the two that only govern how patterns repeat.
            Spacer(Modifier.height(Spacing.md))
            FilledTonalButton(
                onClick = {
                    val onePattern = SoundProfile(
                        ringerMode = mode,
                        soundEnabled = false,
                        soundUri = null,
                        alarmVolumePercent = AppPrefs.DEFAULT_ALARM_VOLUME_PERCENT,
                        soundDelaySeconds = 0,
                        fadeInSeconds = 0,
                        soundStopsAfterSeconds = 0,
                        vibrationEnabled = true,
                        vibrationDelaySeconds = 0,
                        vibrationStopsAfterSeconds = 0,
                        buzzOnMs = SoundProfile.BUZZ_LENGTH_OPTIONS[buzzOnIdx],
                        buzzOffMs = SoundProfile.BUZZ_LENGTH_OPTIONS[buzzOffIdx],
                        buzzesPerPattern = buzzesIdx + 1,
                        delayBetweenPatternsMs =
                            SoundProfile.PATTERN_DELAY_OPTIONS[patternDelayIdx],
                        vibrationRepetitions = 1,
                        soundStartsFirst = true,
                        secondStartDelaySeconds = 0,
                        autoDismissSeconds = 0,
                        autoDismissAction = AutoDismissAction.DISMISS,
                        autoDismissSnoozeMinutes = 0,
                        autoDismissMaxRetries = 0
                    )
                    vibratorOf(context).playOnce(onePattern.buildVibrationWaveform())
                },
                modifier = Modifier.align(Alignment.CenterHorizontally), // UI.14
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.Vibration, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Test Vibration", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(Spacing.lg))
            OptionSlider("Delay Between Patterns", SoundProfile.SIZE_LABELS, patternDelayIdx) {
                patternDelayIdx = it
            }
            Spacer(Modifier.height(Spacing.lg))
            OptionSlider("Number of Pattern Repetitions", COUNT_LABELS, repetitionsIdx) {
                repetitionsIdx = it
            }

            // UI.22 — the timings that mirror the sound section's.
            Spacer(Modifier.height(Spacing.lg))
            NumberField(
                value = vibrationDelaySeconds,
                onValueChange = { vibrationDelaySeconds = it },
                label = "Vibration Delay (seconds) — 0 for none"
            )
            Spacer(Modifier.height(Spacing.md))
            NumberField(
                value = vibrationStopsAfterSeconds,
                onValueChange = { vibrationStopsAfterSeconds = it },
                label = "Vibration Stops After (seconds) — 0 to keep vibrating"
            )
            }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Sequencing ----
        val sequencingApplies = soundEnabled && vibrationEnabled
        SettingsSection(
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
        SettingsSection("Auto-Snooze / Auto-Dismiss") {
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

/**
 * M3.1 — one card per group of settings, replacing the old emoji headings and
 * full-width dividers.
 */
@Composable
private fun SettingsSection(
    title: String,
    // UI.18 — optional note beside the heading, used by Sequencing to explain
    // itself while collapsed.
    hint: String? = null,
    // ColumnScope so section contents can use Modifier.align (UI.14).
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            // UI.23 — the heading sits on its own tonal band. M3 says to lift a
            // header off its card with the next surface-container step rather
            // than a divider or an arbitrary tint, so the card stays
            // surfaceContainerLow and this goes one level up.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    // UI.18 — 25% larger than the M3 titleMedium these used to be.
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.25f
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                if (hint != null) {
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        hint,
                        // Matches the delay field's label beneath it.
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
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
