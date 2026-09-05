package com.dryrun.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.dryrun.app.platform.atLocalTimeOfDay
import com.dryrun.app.platform.currentTimeMillis

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val HOUR_MS = 60L * 60 * 1000

internal const val CHANNEL_ID = "dryrun_reminders"
internal const val EXTRA_ID = "notification_id"
internal const val EXTRA_TITLE = "notification_title"
internal const val EXTRA_BODY = "notification_body"

actual class LocalNotifier(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createChannel()
    }

    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true)
            return
        }
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onResult(true)
            return
        }
        // MainActivity owns the ActivityResultLauncher; without one we can only report the
        // current state rather than crash.
        val host = NotificationPermissionHost.requester
        if (host == null) onResult(false) else host(onResult)
    }

    actual fun scheduleEveningBeforeReminder(
        scheduleId: String,
        counterpartRole: String,
        epochMillis: Long
    ) {
        // 8pm the night before.
        schedule(
            id = NotificationIds.of(scheduleId, NotificationIds.EVENING_BEFORE),
            title = NotificationCopy.eveningBeforeTitle(),
            body = NotificationCopy.eveningBeforeBody(counterpartRole),
            fireAtMillis = atLocalTimeOfDay(epochMillis - DAY_MS, 20, 0)
        )
    }

    actual fun scheduleMorningOfNudge(scheduleId: String, bestLine: String, epochMillis: Long) {
        // Two hours before, but never before 7am.
        schedule(
            id = NotificationIds.of(scheduleId, NotificationIds.MORNING_OF),
            title = NotificationCopy.morningOfTitle(),
            body = NotificationCopy.morningOfBody(bestLine),
            fireAtMillis = maxOf(epochMillis - 2 * HOUR_MS, atLocalTimeOfDay(epochMillis, 7, 0))
        )
    }

    actual fun scheduleFollowUp(scheduleId: String, epochMillis: Long) {
        schedule(
            id = NotificationIds.of(scheduleId, NotificationIds.FOLLOW_UP),
            title = NotificationCopy.followUpTitle(),
            body = NotificationCopy.followUpBody(),
            fireAtMillis = epochMillis + 3 * HOUR_MS
        )
    }

    actual fun cancelAll(scheduleId: String) {
        NotificationIds.all(scheduleId).forEach { id ->
            alarmManager.cancel(pendingIntent(id, "", ""))
        }
    }

    private fun schedule(id: String, title: String, body: String, fireAtMillis: Long) {
        // An alarm in the past fires immediately, which would be worse than not firing.
        if (fireAtMillis <= currentTimeMillis()) return

        // Inexact on purpose: a reminder does not need SCHEDULE_EXACT_ALARM, and asking
        // for it would put the app in a restricted Play policy bucket for no benefit.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireAtMillis,
            pendingIntent(id, title, body)
        )
    }

    private fun pendingIntent(id: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        // Channels only exist from API 26; minSdk here is 24.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Conversation reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "A nudge before a conversation you've scheduled."
        }
        manager.createNotificationChannel(channel)
    }
}

/**
 * MainActivity registers the permission launcher here. Kept out of [LocalNotifier] so the
 * notifier only ever needs an application Context.
 */
object NotificationPermissionHost {
    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
}
