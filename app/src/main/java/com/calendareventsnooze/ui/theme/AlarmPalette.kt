package com.calendareventsnooze.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.calendareventsnooze.model.AlarmScreenStyle

/**
 * UI.29 — the takeover's own colour set, now that there is more than one.
 *
 * The takeover has never followed the system light/dark setting and still does
 * not: it fires on a lock screen in the middle of the night, so the choice is
 * the user's explicit one from Alarm Screen → Alarm Screen Styles, not the
 * phone's. This is a plain data class rather than an M3 `ColorScheme` because
 * the roles here are the takeover's own (danger, calendar, the two snooze
 * tiers) and do not map onto the M3 ones.
 */
data class AlarmPalette(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val primary: Color,        // snooze presets
    val onPrimary: Color,
    val secondary: Color,      // specify time / date & time
    val onSecondary: Color,
    val outline: Color,
    val danger: Color,         // the Dismiss button's fill
    val onDanger: Color,
    /**
     * The auto-dismiss countdown, which is loose text on [background] rather
     * than a filled button. It cannot reuse [danger]: that is a *container*
     * colour, and on the light style it is a pale pink that all but disappears
     * against a near-white ground (measured on the phone).
     */
    val dangerText: Color,
    val calendar: Color,       // the "Also Open Calendar Event" row
    val onCalendar: Color
)

/** The original always-dark takeover: darkest ground, lightest text. */
val DarkAlarmPalette = AlarmPalette(
    background     = AlarmBackground,
    surface        = AlarmSurface,
    onSurface      = AlarmOnSurface,
    onSurfaceMuted = AlarmOnSurfaceMuted,
    primary        = AlarmPrimary,
    onPrimary      = AlarmOnPrimary,
    secondary      = AlarmSecondary,
    onSecondary    = AlarmOnSecondary,
    outline        = AlarmOutline,
    danger         = AlarmDanger,
    onDanger       = AlarmOnDanger,
    // Light pink on a near-black ground: already high contrast as text.
    dangerText     = AlarmDanger,
    // UI.17 asked for this row to carry the "Fire test alarm now" colours, and
    // it still does: the palette must not quietly restyle it.
    calendar       = LightSecondaryContainer,
    onCalendar     = LightOnSecondaryContainer
)

/**
 * UI.29 — the light takeover. Built from the light scheme's own constants so it
 * belongs to the same palette, and kept at container-strength fills with dark
 * text: at full brightness on a phone held close, a white-on-colour alarm is
 * harder to read at a glance than dark-on-pale.
 */
val LightAlarmPalette = AlarmPalette(
    background     = LightBackground,
    surface        = LightSurfaceContainer,
    onSurface      = Color(0xFF11202E),
    onSurfaceMuted = LightOnSurfaceVariant,
    primary        = LightPrimaryContainer,      // Lavender tiles
    onPrimary      = LightOnPrimaryContainer,
    secondary      = LightSecondaryContainer,
    onSecondary    = LightOnSecondaryContainer,
    outline        = LightOutline,
    danger         = Color(0xFFFFDAD6),
    onDanger       = Color(0xFF410002),
    dangerText     = Color(0xFFB3261E),          // M3 error, 4.8:1 on this ground
    calendar       = LightSecondaryContainer,
    onCalendar     = LightOnSecondaryContainer
)

fun paletteFor(style: AlarmScreenStyle): AlarmPalette = when (style) {
    AlarmScreenStyle.DARK  -> DarkAlarmPalette
    AlarmScreenStyle.LIGHT -> LightAlarmPalette
}

/**
 * Defaults to dark, so anything that renders the takeover without providing a
 * palette looks exactly as it did before UI.29.
 */
val LocalAlarmPalette = staticCompositionLocalOf { DarkAlarmPalette }
