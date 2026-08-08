package com.calendareventsnooze.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.calendareventsnooze.ui.theme.Spacing

/**
 * M3.1 — one card per top-level group of settings, replacing the old emoji
 * headings and full-width dividers.
 *
 * UI.23 — the heading sits on its own tonal band. M3 lifts a header off its
 * card with the next surface-container step rather than a divider or an
 * arbitrary tint, so the card stays `surfaceContainerLow` and the band goes one
 * level up. The band runs to the card's own edges: the card clips its content,
 * so the two top corners follow the card's radius while the bottom edge stays a
 * straight line from side to side. It therefore carries the card's horizontal
 * padding itself, and the body below supplies its own.
 *
 * UI.27 — Home's Snoozed / Missed Alarms cards use this too, so the two screens
 * cannot drift apart.
 *
 * [hint] is an optional note beside the heading, used by Sequencing to explain
 * itself while collapsed (UI.18).
 */
@Composable
fun SectionCard(
    title: String,
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
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
            Column(Modifier.padding(Spacing.lg)) { content() }
        }
    }
}
