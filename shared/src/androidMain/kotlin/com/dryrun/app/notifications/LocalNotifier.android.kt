package com.dryrun.app.notifications

import android.content.Context

actual class LocalNotifier(private val context: Context) {
    actual fun scheduleEveningBeforeReminder(scheduleId: String, counterpartRole: String, epochMillis: Long) {
        // Android AlarmManager / NotificationManager scheduling logic
    }
}