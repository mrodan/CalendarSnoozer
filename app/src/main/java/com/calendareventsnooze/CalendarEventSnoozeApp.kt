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

        // Round 27 — the foreground-service notification for a ringing alarm.
        // IMPORTANCE_LOW on purpose: at HIGH, Android drew a heads-up banner
        // across the top of the takeover announcing the alarm the user was
        // already looking at. Importance alone is what produces that banner —
        // the notification carries no sound or vibration and dropping its
        // full-screen intent was not enough, which is why this is a separate
        // channel rather than a flag on the old one.
        val ongoing = NotificationChannel(
            "ces_alarm_ongoing",
            "Ringing Alarm",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        // Still IMPORTANCE_HIGH, and now used only when the takeover could not
        // get on screen by itself: a full-screen intent needs high importance
        // to fire, and in that case a banner is exactly what is wanted.
        val active = NotificationChannel(
            "ces_alarm_active",
            "Alarm Could Not Open",
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
        manager.createNotificationChannel(ongoing)
        manager.createNotificationChannel(active)
        manager.createNotificationChannel(missed)
    }
}
