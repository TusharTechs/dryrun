package com.dryrun.app.notifications

expect class LocalNotifier {
    fun scheduleEveningBeforeReminder(scheduleId: String, counterpartRole: String, epochMillis: Long)
}