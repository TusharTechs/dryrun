package com.dryrun.app.notifications

import platform.UserNotifications.*
import platform.Foundation.NSDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.experimental.ExperimentalForeignApi

actual class LocalNotifier {
    @OptIn(ExperimentalForeignApi::class)
    actual fun scheduleEveningBeforeReminder(scheduleId: String, counterpartRole: String, epochMillis: Long) {
        val center = UNNotificationCenter.currentNotificationCenter()
        
        val content = UNMutableNotificationContent().apply {
            setTitle("Dry Run")
            setBody("Your conversation with $counterpartRole is tomorrow. One more run?")
            setSound(UNNotificationSound.defaultSound())
        }
        
        val targetSeconds = (epochMillis / 1000) - (24 * 3600)
        val date = NSDate.dateWithTimeIntervalSince1970(targetSeconds.toDouble())
        val calendar = NSCalendar.currentCalendar
        val components = calendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
            fromDate = date
        )
        components.setHour(20)
        components.setMinute(0)
        
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(scheduleId, content, trigger)
        
        center.addNotificationRequest(request) { error ->
            if (error != null) {
                println("Failed to schedule local notification: ${error.localizedDescription}")
            }
        }
    }
}