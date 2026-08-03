package com.calendareventsnooze.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Source palette (M3.1). These five are the swatches the design is built from;
// every other colour in this file is a tonal step derived from one of them.
// Screens must never reference these directly — use MaterialTheme.colorScheme,
// so light and dark both stay correct.
// ---------------------------------------------------------------------------
val Lavender   = Color(0xFFC5D1EB)
val PowderBlue = Color(0xFF92AFD7)
val BlueSlate  = Color(0xFF5A7684)
val Granite    = Color(0xFF395B50)
val Evergreen  = Color(0xFF1F2F16)

// ---------------------------------------------------------------------------
// Light scheme
//
// Primary   — Blue Slate, the palette's mid tone (4.85:1 against white).
// Secondary — Granite, the green counterweight.
// Tertiary  — Powder Blue's hue pushed dark enough to carry white text.
// Neutrals  — near-white with a blue cast so surfaces sit in the same family.
// ---------------------------------------------------------------------------
val LightPrimary            = BlueSlate
val LightOnPrimary          = Color(0xFFFFFFFF)
val LightPrimaryContainer   = Lavender
val LightOnPrimaryContainer = Color(0xFF16232B)

val LightSecondary            = Granite
val LightOnSecondary          = Color(0xFFFFFFFF)
val LightSecondaryContainer   = Color(0xFFCDE3D9)
val LightOnSecondaryContainer = Color(0xFF12251E)

val LightTertiary            = Color(0xFF3D5F8C)
val LightOnTertiary          = Color(0xFFFFFFFF)
val LightTertiaryContainer   = Color(0xFFD6E2F7)
val LightOnTertiaryContainer = Color(0xFF12243C)

val LightBackground   = Color(0xFFFBFCFE)
val LightOnBackground = Color(0xFF1A1C1E)
val LightSurface      = Color(0xFFFBFCFE)
val LightOnSurface    = Color(0xFF1A1C1E)

val LightSurfaceVariant   = Color(0xFFDFE3EB)
val LightOnSurfaceVariant = Color(0xFF43474E)
val LightOutline          = Color(0xFF73777F)
val LightOutlineVariant   = Color(0xFFC3C7CF)

val LightSurfaceContainerLowest  = Color(0xFFFFFFFF)
val LightSurfaceContainerLow     = Color(0xFFF5F7FA)
val LightSurfaceContainer        = Color(0xFFEFF2F7)
val LightSurfaceContainerHigh    = Color(0xFFE9EDF3)
val LightSurfaceContainerHighest = Color(0xFFE3E8F0)

val LightInverseSurface   = Color(0xFF2F3133)
val LightInverseOnSurface = Color(0xFFF1F1F3)
val LightInversePrimary   = PowderBlue

// ---------------------------------------------------------------------------
// Dark scheme
//
// Evergreen is too dark to be an accent, so it becomes the ground the whole
// dark theme sits on; Powder Blue and Lavender rise to carry the accents.
// ---------------------------------------------------------------------------
val DarkPrimary            = PowderBlue
val DarkOnPrimary          = Color(0xFF17314A)
val DarkPrimaryContainer   = BlueSlate
val DarkOnPrimaryContainer = Lavender

val DarkSecondary            = Color(0xFF8FC3B0)
val DarkOnSecondary          = Color(0xFF143528)
val DarkSecondaryContainer   = Granite
val DarkOnSecondaryContainer = Color(0xFFCDE3D9)

val DarkTertiary            = Lavender
val DarkOnTertiary          = Color(0xFF2A3549)
val DarkTertiaryContainer   = Color(0xFF414F6B)
val DarkOnTertiaryContainer = Color(0xFFE1E7F5)

val DarkBackground   = Color(0xFF12180E)
val DarkOnBackground = Color(0xFFE2E3DD)
val DarkSurface      = Color(0xFF12180E)
val DarkOnSurface    = Color(0xFFE2E3DD)

val DarkSurfaceVariant   = Color(0xFF414B3C)
val DarkOnSurfaceVariant = Color(0xFFC1CBBA)
val DarkOutline          = Color(0xFF8B9585)
val DarkOutlineVariant   = Color(0xFF414B3C)

val DarkSurfaceContainerLowest  = Color(0xFF0D1209)
val DarkSurfaceContainerLow     = Color(0xFF1A2115)
val DarkSurfaceContainer        = Evergreen
val DarkSurfaceContainerHigh    = Color(0xFF293720)
val DarkSurfaceContainerHighest = Color(0xFF34422B)

val DarkInverseSurface   = Color(0xFFE2E3DD)
val DarkInverseOnSurface = Color(0xFF2F3129)
val DarkInversePrimary   = BlueSlate

// ---------------------------------------------------------------------------
// Top app bar (UI.12) — a filled bar rather than a surface one, so it takes a
// palette colour directly and its content colour is the M3 "on" pairing for it.
// Both grounds are dark, so the status-bar icons stay light in either scheme.
// ---------------------------------------------------------------------------
val LightTopBar          = Granite   // #395B50
val LightOnTopBar        = Color(0xFFFFFFFF)
val LightOnTopBarVariant = Color(0xFFCFE0D8)

val DarkTopBar           = BlueSlate // #5A7684
val DarkOnTopBar         = Color(0xFFFFFFFF)
val DarkOnTopBarVariant  = Color(0xFFD8E2F0)

// ---------------------------------------------------------------------------
// Error roles. The palette has no red, and a destructive action must not be
// coloured like an ordinary one, so these are M3's standard error tones.
// ---------------------------------------------------------------------------
val LightError            = Color(0xFFBA1A1A)
val LightOnError          = Color(0xFFFFFFFF)
val LightErrorContainer   = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val DarkError            = Color(0xFFFFB4AB)
val DarkOnError          = Color(0xFF690005)
val DarkErrorContainer   = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// ---------------------------------------------------------------------------
// Alarm takeover (M3.1 — restyled, deliberately higher contrast than the app).
//
// The takeover fires at 3am on a lock screen, so it does not follow the system
// light/dark setting: it is always the darkest ground with the lightest text,
// which is the highest-contrast pairing the palette allows.
// ---------------------------------------------------------------------------
val AlarmBackground     = Color(0xFF0D1209) // darkest evergreen
val AlarmSurface        = Evergreen
val AlarmOnSurface      = Lavender
val AlarmOnSurfaceMuted = Color(0xFF9FB0A6)
val AlarmPrimary        = PowderBlue        // snooze presets
val AlarmOnPrimary      = Color(0xFF11202E)
val AlarmSecondary      = Granite           // specify time / date & time
val AlarmOnSecondary    = Lavender
val AlarmOutline        = Color(0xFF6E7A66)
val AlarmDanger         = Color(0xFFFFB4AB) // dismiss
val AlarmOnDanger       = Color(0xFF690005)
val AlarmCalendar       = Lavender          // open calendar event
val AlarmOnCalendar     = Color(0xFF1B2A33)
