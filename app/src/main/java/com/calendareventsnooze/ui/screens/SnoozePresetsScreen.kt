package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.ui.theme.AppButtonRegular
import com.calendareventsnooze.ui.theme.AppButtonRegularText
import kotlinx.coroutines.launch

@Composable
fun SnoozePresetsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val presets = remember {
        val loaded = AppPrefs.getSnoozePresets(context)
        mutableStateListOf<PresetDraft>().apply {
            for (i in 0 until 4) {
                val p = loaded.getOrNull(i) ?: SnoozePreset("Preset ${i + 1}", 10)
                add(PresetDraft(p.label, p.minutes.toString()))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        presets.forEachIndexed { index, draft ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Button ${index + 1}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.label,
                        onValueChange = { presets[index] = draft.copy(label = it) },
                        label = { Text("Button label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.minutes,
                        onValueChange = {
                            presets[index] = draft.copy(minutes = it.filter { c -> c.isDigit() })
                        },
                        label = { Text("Minutes (1–10080)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppButtonRegular,
                            contentColor = AppButtonRegularText),
                        onClick = {
                        val minutes = draft.minutes.toIntOrNull()?.coerceIn(1, 10080) ?: 10
                        val updated = draft.copy(minutes = minutes.toString())
                        presets[index] = updated
                        val toSave = presets.map { SnoozePreset(it.label, it.minutes.toIntOrNull()?.coerceIn(1, 10080) ?: 10) }
                        AppPrefs.saveSnoozePresets(context, toSave)
                        scope.launch { snackbarHostState.showSnackbar("Saved") }
                    }) { Text("Save") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("60 = 1 hour  |  1440 = 1 day  |  10080 = 1 week",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SnackbarHost(hostState = snackbarHostState)
    }
}

private data class PresetDraft(val label: String, val minutes: String)
