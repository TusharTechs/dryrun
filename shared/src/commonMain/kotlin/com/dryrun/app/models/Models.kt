package com.dryrun.app.models

import kotlinx.serialization.Serializable

@Serializable
data class ConversationSchedule(
    val id: String,
    val counterpartRole: String,
    val counterpartPersonality: String,
    val situation: String,
    val scheduledEpochMillis: Long,
    val isCompleted: Boolean = false
)

@Serializable
data class SeedScenario(
    val id: String,
    val title: String,
    val description: String
)

object SeedScenarios {
    val ALL = listOf(
        SeedScenario("seed_1", "First critical feedback", "Giving feedback to someone who doesn't see the problem."),
        SeedScenario("seed_2", "Managing a former peer", "Setting boundaries with a colleague who was your peer last month."),
        SeedScenario("seed_3", "Saying no to former boss", "Pushing back on unrealistic demands from your past manager."),
        SeedScenario("seed_4", "Tears in a 1-on-1", "Navigating an emotional reaction when delivering tough news."),
        SeedScenario("seed_5", "Passed over for promotion", "Managing a report who believes they deserved your job."),
        SeedScenario("seed_6", "Slipping performance", "Addressing declining work quality with someone you personally like.")
    )
}
