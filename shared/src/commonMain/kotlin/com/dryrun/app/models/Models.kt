package com.dryrun.app.models

import kotlinx.serialization.Serializable

/**
 * One conversation the user has to have. Device-local only — the free-text
 * situation never persists server-side beyond the request that uses it.
 */
@Serializable
data class Rehearsal(
    val id: String = "rehearsal_1",
    val counterpartRole: String,
    val counterpartPersonality: String = "",
    val situation: String,
    val scheduledEpochMillis: Long,
    val isCompleted: Boolean = false,
    /** Orders the list when two conversations fall on the same day. */
    val createdAtMillis: Long = 0L
)

/** The three activity types the category names. Shown as labels in the picker. */
enum class ActivityType(val label: String) {
    DELIVERING_FEEDBACK("Delivering feedback"),
    BOUNDARY_SETTING("Boundary-setting"),
    DECLINING_REQUESTS("Declining requests")
}

@Serializable
data class SeedScenario(
    val id: String,
    val title: String,
    val description: String,
    val counterpartRole: String,
    val counterpartPersonality: String,
    val activity: ActivityType
)

/**
 * Seven scenarios, covering all three activity types the category names.
 * Balance is deliberate: three feedback, two boundary, two declining.
 */
object SeedScenarios {
    val ALL: List<SeedScenario> = listOf(
        SeedScenario(
            id = "feedback_first",
            title = "Their first piece of hard feedback",
            description = "They think the work was fine. You don't.",
            counterpartRole = "An engineer on your team, two years in, first time hearing this",
            counterpartPersonality = "Takes it personally. Goes quiet, then argues the details.",
            activity = ActivityType.DELIVERING_FEEDBACK
        ),
        SeedScenario(
            id = "feedback_slipping",
            title = "Work that has slipped",
            description = "Two months of decline, and you like them.",
            counterpartRole = "A designer you've worked with for years and genuinely like",
            counterpartPersonality = "Warm, apologetic, deflects with self-deprecation. Never commits to a change.",
            activity = ActivityType.DELIVERING_FEEDBACK
        ),
        SeedScenario(
            id = "feedback_tears",
            title = "Tears in a one-on-one",
            description = "You're three sentences in and they start crying.",
            counterpartRole = "A junior analyst who cries when criticised and is embarrassed about it",
            counterpartPersonality = "Emotional, then mortified. Apologises for crying. Tries to end the meeting early.",
            activity = ActivityType.DELIVERING_FEEDBACK
        ),
        SeedScenario(
            id = "boundary_former_peer",
            title = "The peer you now manage",
            description = "Last month you sat beside them. Today you decide.",
            counterpartRole = "A former peer, now your report, who still assigns work without asking",
            counterpartPersonality = "Breezy and familiar. Treats your authority as a joke between friends.",
            activity = ActivityType.BOUNDARY_SETTING
        ),
        SeedScenario(
            id = "boundary_wanted_your_job",
            title = "The report who wanted your job",
            description = "They applied. You got it. Now you run their one-on-ones.",
            counterpartRole = "A senior report who was passed over for the role you're now in",
            counterpartPersonality = "Coolly professional. Undermines in meetings, denies it in private.",
            activity = ActivityType.BOUNDARY_SETTING
        ),
        SeedScenario(
            id = "declining_former_boss",
            title = "Saying no to your old boss",
            description = "They still ask like you report to them.",
            counterpartRole = "Your previous manager, now a peer, who keeps handing you their work",
            counterpartPersonality = "Charming and senior. Assumes yes. Reacts to no as if it's a misunderstanding.",
            activity = ActivityType.DECLINING_REQUESTS
        ),
        SeedScenario(
            id = "declining_weekend_ask",
            title = "The weekend ask",
            description = "A peer wants your team on Saturday. Again.",
            counterpartRole = "A peer manager who needs your team to cover their slipped deadline",
            counterpartPersonality = "Urgent and guilt-leaning. Frames it as letting the company down.",
            activity = ActivityType.DECLINING_REQUESTS
        )
    )

    fun byActivity(activity: ActivityType): List<SeedScenario> = ALL.filter { it.activity == activity }
}
