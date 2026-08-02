package com.calendareventsnooze.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3.1 — the Material 3 shape scale. Components pick their corner size from
 * this by role (chips take extraSmall, cards medium, sheets extraLarge), so no
 * screen should hardcode a RoundedCornerShape.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ---------------------------------------------------------------------------
// Spacing scale (M3 uses a 4dp grid). Screens reference these instead of
// scattering magic dp values, so rhythm stays consistent across sections.
// ---------------------------------------------------------------------------
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
