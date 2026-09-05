package com.dryrun.app.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun formatConversationTime(epochMillis: Long): String {
    val date = dateOf(epochMillis)
    val time = NSDateFormatter().apply { dateFormat = "h:mm a" }.stringFromDate(date)
    return when (daysFromToday(date)) {
        0L -> "Today, $time"
        1L -> "Tomorrow, $time"
        else -> NSDateFormatter().apply { dateFormat = "EEE d MMM" }.stringFromDate(date) + ", $time"
    }
}

actual fun atLocalTimeOfDay(epochMillis: Long, hour: Int, minute: Int): Long {
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = dateOf(epochMillis)
    )
    components.setHour(hour.toLong())
    components.setMinute(minute.toLong())
    components.setSecond(0)
    val date = calendar.dateFromComponents(components) ?: return epochMillis
    return (date.timeIntervalSince1970 * 1000.0).toLong()
}

private fun dateOf(epochMillis: Long): NSDate =
    NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)

private fun daysFromToday(date: NSDate): Long {
    val calendar = NSCalendar.currentCalendar
    val unit = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay
    val startOfTarget = calendar.dateFromComponents(calendar.components(unit, fromDate = date))
    val startOfToday = calendar.dateFromComponents(calendar.components(unit, fromDate = NSDate()))
    if (startOfTarget == null || startOfToday == null) return 0L
    val diffSeconds = startOfTarget.timeIntervalSince1970 - startOfToday.timeIntervalSince1970
    return (diffSeconds / 86400.0).toLong()
}
