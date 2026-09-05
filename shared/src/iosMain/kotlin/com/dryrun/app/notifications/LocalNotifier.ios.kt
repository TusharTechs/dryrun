package com.dryrun.app.notifications

import com.dryrun.app.platform.atLocalTimeOfDay
import com.dryrun.app.platform.currentTimeMillis
import platform.Foundation.NSDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val HOUR_MS = 60L * 60 * 1000

actual class LocalNotifier {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    actual fun requestPermission(onResult: (Boolean) -> Unit) {
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionSound or
            UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ -> onResult(granted) }
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
            fireAtMillis = atTimeOfDay(epochMillis - DAY_MS, hour = 20, minute = 0)
        )
    }

    actual fun scheduleMorningOfNudge(scheduleId: String, bestLine: String, epochMillis: Long) {
        // Two hours before, but never before 7am.
        val twoHoursBefore = epochMillis - 2 * HOUR_MS
        val sevenAm = atTimeOfDay(epochMillis, hour = 7, minute = 0)
        schedule(
            id = NotificationIds.of(scheduleId, NotificationIds.MORNING_OF),
            title = NotificationCopy.morningOfTitle(),
            body = NotificationCopy.morningOfBody(bestLine),
            fireAtMillis = maxOf(twoHoursBefore, sevenAm)
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
        center.removePendingNotificationRequestsWithIdentifiers(NotificationIds.all(scheduleId))
    }

    private fun schedule(id: String, title: String, body: String, fireAtMillis: Long) {
        // A trigger in the past never fires, so drop it rather than queue a stale alert.
        if (fireAtMillis <= nowMillis()) return

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
        }

        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = NSDate.dateWithTimeIntervalSince1970(fireAtMillis / 1000.0)
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id,
            content = content,
            trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = components,
                repeats = false
            )
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) println("DryRun: could not schedule $id")
        }
    }

    private fun nowMillis(): Long = currentTimeMillis()

    private fun atTimeOfDay(epochMillis: Long, hour: Int, minute: Int): Long =
        atLocalTimeOfDay(epochMillis, hour, minute)
}
