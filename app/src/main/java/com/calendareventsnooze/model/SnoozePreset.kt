package com.calendareventsnooze.model

data class SnoozePreset(
    val label: String,   // button text shown in UI, e.g. "10 min"
    val minutes: Int     // valid range: 1 to 10080 (1 week)
)
