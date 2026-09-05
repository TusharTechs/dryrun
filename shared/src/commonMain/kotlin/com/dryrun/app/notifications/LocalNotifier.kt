package com.dryrun.app.notifications

/**
 * The reminder loop. Three beats around one conversation:
 *
 *  - the evening before, so there's time for one more run
 *  - the morning of, quoting the best line they actually said
 *  - a few hours after, asking how it went
 *
 * Deliberately finite. No streaks, no daily nagging.
 */
expect class LocalNotifier {

    /** Asks the OS for notification permission. Called when a date is set, never on launch. */
    fun requestPermission(onResult: (Boolean) -> Unit)

    fun scheduleEveningBeforeReminder(scheduleId: String, counterpartRole: String, epochMillis: Long)

    /** [bestLine] is quoted back verbatim — their own words, not encouragement. */
    fun scheduleMorningOfNudge(scheduleId: String, bestLine: String, epochMillis: Long)

    fun scheduleFollowUp(scheduleId: String, epochMillis: Long)

    fun cancelAll(scheduleId: String)
}

/** Suffixes keep the three beats of one rehearsal individually addressable. */
internal object NotificationIds {
    const val EVENING_BEFORE = "evening"
    const val MORNING_OF = "morning"
    const val FOLLOW_UP = "followup"

    fun of(scheduleId: String, beat: String): String = "$scheduleId::$beat"
    fun all(scheduleId: String): List<String> =
        listOf(EVENING_BEFORE, MORNING_OF, FOLLOW_UP).map { of(scheduleId, it) }
}

/**
 * Copy for the three beats, kept in common code so both platforms say the
 * same thing and the tone stays reviewable in one place.
 */
internal object NotificationCopy {
    fun eveningBeforeTitle() = "Tomorrow"
    fun eveningBeforeBody(counterpartRole: String) =
        "Your conversation with $counterpartRole is tomorrow. Time for one more run."

    fun morningOfTitle() = "Today"
    fun morningOfBody(bestLine: String) =
        if (bestLine.isBlank()) "It's today. You've done the run."
        else "Your line: \"$bestLine\""

    fun followUpTitle() = "How did it go?"
    fun followUpBody() = "Log it while it's fresh."
}
