package com.calendareventsnooze.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * UI.4 / UI.11 — a Gmail-style scrollbar: a thumb drawn down the right edge,
 * sized and positioned from the scroll state, shown only when the content
 * overflows. Shared by the Home tab and the Sound & Vibration sub-tabs.
 */
internal fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue <= 0) return@drawWithContent
    val viewport = size.height
    val thumbWidth = 4.dp.toPx()
    val thumbHeight = (viewport / (viewport + state.maxValue)) * viewport
    val travel = viewport - thumbHeight
    val offsetY = (state.value.toFloat() / state.maxValue) * travel
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - thumbWidth, offsetY),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f)
    )
}
