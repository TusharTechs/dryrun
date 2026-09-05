package com.dryrun.app.platform

/** Milliseconds since the Unix epoch. */
expect fun currentTimeMillis(): Long

/**
 * Formats an instant the way a person would say it out loud:
 * "Tomorrow, 9:00 AM", "Thu 18 Sep, 2:30 PM".
 */
expect fun formatConversationTime(epochMillis: Long): String

/** Epoch millis for [epochMillis]'s calendar day at [hour]:[minute] local time. */
expect fun atLocalTimeOfDay(epochMillis: Long, hour: Int, minute: Int): Long
