package com.calendareventsnooze

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CalendarEventSnoozeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val foreground = NotificationChannel(
            "ces_foreground",
            "Alarm Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        val active = NotificationChannel(
            "ces_alarm_active",
            "Active Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            // No sound on the channel; alarm audio is handled by MediaPlayer.
            setSound(null, null)
            enableVibration(false)
        }

        val missed = NotificationChannel(
            "ces_missed",
            "Missed Alarms",
            NotificationManager.IMPORTANCE_HIGH
        )

        manager.createNotificationChannel(foreground)
        manager.createNotificationChannel(active)
        manager.createNotificationChannel(missed)
    }
}
