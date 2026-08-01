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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozePreset

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Changes are saved automatically.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // UI.8 — laid out 1 | 2 over 3 | 4, matching the alarm takeover screen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetCard(0, presets, Modifier.weight(1f))
            PresetCard(1, presets, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetCard(2, presets, Modifier.weight(1f))
            PresetCard(3, presets, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        Text("60 = 1 hour  |  1440 = 1 day  |  10080 = 1 week",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PresetCard(
    index: Int,
    presets: MutableList<PresetDraft>,
    modifier: Modifier
) {
    val draft = presets[index]
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text("Button ${index + 1}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { presets[index] = draft.copy(label = it) },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.minutes,
                onValueChange = {
                    presets[index] = draft.copy(minutes = it.filter { c -> c.isDigit() })
                },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class PresetDraft(val label: String, val minutes: String)
