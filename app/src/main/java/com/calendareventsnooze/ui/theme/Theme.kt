package com.calendareventsnooze.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Single light scheme — the app uses the mellow palette in both system themes
// so colours stay consistent (UI.1). The alarm takeover styles itself directly.
private val AppColors = lightColorScheme(
    primary = AppAccentBlue,
    onPrimary = AppAccentBlueText,
    secondary = AppButtonRegular,
    onSecondary = AppButtonRegularText,
    error = AppDanger,
    onError = Color.White,
    background = AppBackground,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurface,
    onSurfaceVariant = AppTextSecondary
)

@Composable
fun CalendarEventSnoozeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content
    )
}
