package com.calendareventsnooze.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendareventsnooze.scheduler.AlarmScheduler
import com.calendareventsnooze.ui.screens.HomeScreen
import com.calendareventsnooze.ui.screens.SnoozePresetsScreen

import com.calendareventsnooze.ui.screens.SoundProfileScreen
import com.calendareventsnooze.ui.theme.AppForceStop
import com.calendareventsnooze.ui.theme.AppTopBar
import com.calendareventsnooze.ui.theme.AppTopBarText
import com.calendareventsnooze.ui.theme.CalendarEventSnoozeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B.7 — self-heal any snoozed alarm whose OS-level alarm was lost (reboot,
        // process death). Past-due entries are revived instead of sitting dead.
        AlarmScheduler.rescheduleAllSnoozed(applicationContext)
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
    // UI.11 — the Sound & Vibration sub-tab is owned here, not by the screen, so
    // it survives navigating away to another tab and back.
    var soundSubTab by rememberSaveable { mutableIntStateOf(0) }
    // UI.4 — Snoozed Alarms now lives inside the Home tab.
    val tabs = listOf("Home", "Snooze Buttons", "Sound & Vibration")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // UI.9 — app title bar above the tabs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppForceStop)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "CALENDAR EVENT SNOOZE",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            // UI.9 — same size as the title text (was half).
            Text(
                "  (IFYKYK)",
                color = Color.Gray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

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
            1 -> SnoozePresetsScreen()
            2 -> SoundProfileScreen(
                selectedSubTab = soundSubTab,
                onSubTabChange = { soundSubTab = it }
            )
        }
    }
}
