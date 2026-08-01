package com.calendareventsnooze.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.calendareventsnooze.ui.screens.HomeScreen
import com.calendareventsnooze.ui.screens.SnoozePresetsScreen
import com.calendareventsnooze.ui.screens.SnoozedAlarmsScreen
import com.calendareventsnooze.ui.screens.SoundProfileScreen
import com.calendareventsnooze.ui.theme.AppTopBar
import com.calendareventsnooze.ui.theme.AppTopBarText
import com.calendareventsnooze.ui.theme.CalendarEventSnoozeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalendarEventSnoozeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    // F.1 — tab order: Home, Snoozed Alarms, Snooze Buttons, Sound & Vibration
    val tabs = listOf("Home", "Snoozed Alarms", "Snooze Buttons", "Sound & Vibration")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = AppTopBar,
            contentColor = AppTopBarText
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    selectedContentColor = AppTopBarText,
                    unselectedContentColor = AppTopBarText.copy(alpha = 0.7f),
                    text = { Text(title, color = Color.Unspecified) }
                )
            }
        }
        when (selectedTab) {
            0 -> HomeScreen()
            1 -> SnoozedAlarmsScreen()
            2 -> SnoozePresetsScreen()
            3 -> SoundProfileScreen()
        }
    }
}
