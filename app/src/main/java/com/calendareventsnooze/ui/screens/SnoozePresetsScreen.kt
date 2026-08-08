package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.calendareventsnooze.data.AppPrefs
import com.calendareventsnooze.model.AlarmScreenStyle
import com.calendareventsnooze.model.SnoozePreset
import com.calendareventsnooze.ui.components.SectionCard
import com.calendareventsnooze.ui.theme.AlarmPalette
import com.calendareventsnooze.ui.theme.Spacing
import com.calendareventsnooze.ui.theme.paletteFor

/**
 * UI.29 — the sub-cards are shorter than they were: the text fields lose 15% of
 * their height and the gaps either side of the divider lose 40%. Both are
 * expressed as the arithmetic rather than as new round numbers, so the
 * relationship to the original values stays visible.
 */
private val PRESET_FIELD_HEIGHT = 47.6.dp   // 56dp - 15%
private val DIVIDER_GAP_ABOVE = 4.8.dp      // Spacing.sm (8dp) - 40%
private val DIVIDER_GAP_BELOW = 9.6.dp      // Spacing.md (16dp) - 40%

/** Border weights that tell a chosen style from an unchosen one (UI.29). */
private val STYLE_BORDER_SELECTED = 3.dp
private val STYLE_BORDER_UNSELECTED = 1.dp

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

    var alarmStyle by remember { mutableStateOf(AppPrefs.getAlarmScreenStyle(context)) }
    LaunchedEffect(alarmStyle) { AppPrefs.setAlarmScreenStyle(context, alarmStyle) }

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

        // ---- Snooze buttons (UI.29) ----
        SectionCard("Snooze Buttons X4") {
            // UI.8 — laid out 1 | 2 over 3 | 4, matching the alarm takeover screen.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                PresetCard(0, presets, Modifier.weight(1f))
                PresetCard(1, presets, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                PresetCard(2, presets, Modifier.weight(1f))
                PresetCard(3, presets, Modifier.weight(1f))
            }

            // UI.29 — the conversion key belongs with the fields it explains,
            // so it moved inside the card.
            Spacer(Modifier.height(Spacing.md))
            Text(
                "60 = 1 hour  ·  1440 = 1 day  ·  10080 = 1 week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        // ---- Takeover styles (UI.29) ----
        SectionCard("Alarm Screen Styles") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                AlarmScreenStyle.entries.forEach { style ->
                    StyleCard(
                        style = style,
                        selected = alarmStyle == style,
                        onSelect = { alarmStyle = style },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

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
            Spacer(Modifier.height(DIVIDER_GAP_ABOVE))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(DIVIDER_GAP_BELOW))
            CompactTextField(
                value = draft.label,
                onValueChange = { presets[index] = draft.copy(label = it) },
                label = "Label"
            )
            Spacer(Modifier.height(Spacing.sm))
            CompactTextField(
                value = draft.minutes,
                onValueChange = {
                    presets[index] = draft.copy(minutes = it.filter { c -> c.isDigit() })
                },
                label = "Minutes",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

/**
 * UI.29 — an outlined text field 15% shorter than the stock one.
 *
 * `OutlinedTextField` cannot simply be given a smaller height: its decoration
 * box reserves a fixed slice for the floating label and centres the input in
 * what is left, so at 47.6dp the value itself is cut in half (seen on the
 * phone). Shrinking it means driving the decoration box directly and taking the
 * height out of the content padding instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(PRESET_FIELD_HEIGHT),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            visualTransformation = VisualTransformation.None,
            innerTextField = innerTextField,
            placeholder = null,
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            enabled = true,
            isError = false,
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                top = 0.dp, bottom = 0.dp),
            container = {
                OutlinedTextFieldDefaults.ContainerBox(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = MaterialTheme.shapes.small
                )
            }
        )
    }
}

/**
 * UI.29 — one takeover style, shown the same way a snooze button is: name,
 * rule, then the thing itself. The chosen one is marked by a thicker border in
 * the primary colour, which is the M3 way to show selection on a card without
 * adding a radio button that would compete with the preview.
 */
@Composable
private fun StyleCard(
    style: AlarmScreenStyle,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier
) {
    OutlinedCard(
        modifier = modifier.clickable { onSelect() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            if (selected) STYLE_BORDER_SELECTED else STYLE_BORDER_UNSELECTED,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                style.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(DIVIDER_GAP_ABOVE))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(DIVIDER_GAP_BELOW))
            TakeoverPreview(paletteFor(style))
        }
    }
}

/**
 * UI.29 — a miniature of the takeover, drawn from the same palette the real
 * screen uses rather than bundled as a screenshot: a captured image would go
 * stale the next time the takeover changes, and would have to be re-shot for
 * every style added later.
 */
@Composable
private fun TakeoverPreview(palette: AlarmPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.small)
            .background(palette.background)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Event title and time.
        PreviewBar(palette.onSurface, 0.7f, 7.dp)
        PreviewBar(palette.onSurfaceMuted, 0.45f, 4.dp)
        Spacer(Modifier.height(2.dp))
        // Four snooze presets, 2 x 2.
        repeat(2) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                PreviewTile(palette.primary, Modifier.weight(1f))
                PreviewTile(palette.primary, Modifier.weight(1f))
            }
        }
        // Specify time / date & time.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            PreviewTile(palette.secondary, Modifier.weight(1f))
            PreviewTile(palette.secondary, Modifier.weight(1f))
        }
        // Dismiss, then the calendar row.
        PreviewBar(palette.danger, 1f, 12.dp)
        PreviewBar(palette.calendar, 1f, 9.dp)
    }
}

@Composable
private fun PreviewBar(color: Color, widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun PreviewTile(color: Color, modifier: Modifier) {
    Box(
        modifier
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

private data class PresetDraft(val label: String, val minutes: String)
