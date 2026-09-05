package com.dryrun.app.platform

import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun formatConversationTime(epochMillis: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    return when (daysFromToday(epochMillis)) {
        0L -> "Today, $time"
        1L -> "Tomorrow, $time"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(epochMillis)) + ", $time"
    }
}

actual fun atLocalTimeOfDay(epochMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun daysFromToday(epochMillis: Long): Long {
    val dayMs = 24L * 60 * 60 * 1000
    return (startOfDay(epochMillis) - startOfDay(System.currentTimeMillis())) / dayMs
}
