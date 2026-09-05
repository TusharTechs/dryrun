package com.dryrun.app.models

import kotlinx.serialization.Serializable

enum class Speaker { YOU, THEM }

@Serializable
data class Turn(
    val speaker: Speaker,
    val text: String,
    /** True when the counterpart deliberately said nothing. Rendered as dead air. */
    val isSilence: Boolean = false
)

/** The counterpart's hidden state. Mirrors the Worker's, clamped there. */
@Serializable
data class CounterpartState(
    val defensiveness: Int = 3,
    val feelsHeard: Int = 0,
    val conceded: Boolean = false
)

/**
 * The four things a run is scored on. Labels are deliberately plain -- a new
 * manager should recognise them, not have to decode them.
 */
enum class Criterion(val id: String, val label: String) {
    SPECIFIC_BEHAVIOUR("specific_behaviour", "Named the specific thing"),
    CONCRETE_IMPACT("concrete_impact", "Said what it cost"),
    LEFT_SILENCE("left_silence", "Left them room to answer"),
    HELD_POINT("held_point", "Held the point under pushback");

    companion object {
        fun fromId(id: String): Criterion? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class CriterionScore(
    val criterionId: String,
    val score: Int,
    /** Verbatim from the transcript. Never paraphrased. */
    val triggerLine: String,
    val note: String
) {
    val criterion: Criterion? get() = Criterion.fromId(criterionId)
}

@Serializable
data class FeedbackReport(
    val criteria: List<CriterionScore>,
    val overall: String,
    val strongestLine: String
) {
    fun scoreFor(criterion: Criterion): Int =
        criteria.firstOrNull { it.criterionId == criterion.id }?.score ?: 0

    fun lineFor(criterion: Criterion): String =
        criteria.firstOrNull { it.criterionId == criterion.id }?.triggerLine.orEmpty()

    val total: Int get() = criteria.sumOf { it.score }

    /**
     * The best thing they said. Falls back to the highest-scoring criterion's
     * quote when the model declined to pick one, so the morning-of reminder
     * always has their own words to hand back.
     */
    fun bestLine(): String {
        if (strongestLine.isNotBlank()) return strongestLine
        return criteria
            .filter { it.triggerLine.isNotBlank() }
            .maxByOrNull { it.score }
            ?.triggerLine
            .orEmpty()
    }
}

/** One completed run. Device-local, always. */
@Serializable
data class RunRecord(
    val runNumber: Int,
    val transcript: List<Turn>,
    val feedback: FeedbackReport,
    val hedgeCount: Int,
    val hedgeTopPhrase: String,
    val silenceOffered: Int,
    val silenceFilled: Int,
    val completedAtMillis: Long
)

/**
 * How many silence beats were offered in a run and how many the user talked
 * over. Counted on the device, so it is fact rather than a model's guess.
 */
@Serializable
data class SilenceLog(
    val offered: Int = 0,
    val filled: Int = 0
) {
    fun beatOffered(): SilenceLog = copy(offered = offered + 1)
    fun beatFilled(): SilenceLog = copy(filled = filled + 1)

    /** Beats they actually sat through. */
    val held: Int get() = (offered - filled).coerceAtLeast(0)

    companion object {
        /** Real dead air, long enough to feel. */
        const val WINDOW_MILLIS = 3000L
    }
}
