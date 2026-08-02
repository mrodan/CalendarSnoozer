package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.ui.theme.Spacing

@Composable
fun SnoozePresetsScreen() {
    val context = LocalContext.current

    val presets = remember {
        val loaded = AppPrefs.getSnoozePresets(context)
        mutableStateListOf<PresetDraft>().apply {
            for (i in 0 until 4) {
                val p = loaded.getOrNull(i) ?: SnoozePreset("Preset ${i + 1}", 10)
                add(PresetDraft(p.label, p.minutes.toString()))
            }
        }
    }

    // F.8 — changes persist immediately; no Save button. Keyed on the full
    // contents so any edit in any card is captured.
    val saveKey = presets.joinToString("|") { "${it.label}~${it.minutes}" }
    LaunchedEffect(saveKey) {
        AppPrefs.saveSnoozePresets(
            context,
            presets.map {
                SnoozePreset(it.label, it.minutes.toIntOrNull()?.coerceIn(1, 10080) ?: 10)
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // UI.8 — laid out 1 | 2 over 3 | 4, matching the alarm takeover screen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresetCard(0, presets, Modifier.weight(1f))
            PresetCard(1, presets, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresetCard(2, presets, Modifier.weight(1f))
            PresetCard(3, presets, Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            "60 = 1 hour  ·  1440 = 1 day  ·  10080 = 1 week",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

/**
 * M3.2 — each preset is an outlined card, so its heading and the two fields
 * that belong to it read as one bounded group.
 */
@Composable
private fun PresetCard(
    index: Int,
    presets: MutableList<PresetDraft>,
    modifier: Modifier
) {
    val draft = presets[index]
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Button ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { presets[index] = draft.copy(label = it) },
                label = { Text("Label") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = draft.minutes,
                onValueChange = {
                    presets[index] = draft.copy(minutes = it.filter { c -> c.isDigit() })
                },
                label = { Text("Minutes") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class PresetDraft(val label: String, val minutes: String)
