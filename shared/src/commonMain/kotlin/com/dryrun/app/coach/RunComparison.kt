package com.dryrun.app.coach

import com.dryrun.app.models.Criterion
import com.dryrun.app.models.RunRecord

/**
 * Run 1 against the latest run.
 *
 * Confidence-building is a judged criterion, so this always lands on what
 * improved. Every number here is deterministic: the criteria come from the
 * fixed rubric and the hedge counts from [HedgeDetector], so the same two
 * runs always produce the same card. Nothing is a model's opinion.
 *
 * Private by design. There is no share sheet and no export anywhere in the
 * app -- the user has described a real colleague, possibly by name.
 */
object RunComparison {

    fun compare(first: RunRecord, latest: RunRecord): Comparison {
        val deltas = Criterion.entries.map { criterion ->
            CriterionDelta(
                criterion = criterion,
                before = first.feedback.scoreFor(criterion),
                after = latest.feedback.scoreFor(criterion)
            )
        }

        return Comparison(
            firstRunNumber = first.runNumber,
            latestRunNumber = latest.runNumber,
            deltas = deltas,
            hedgesBefore = first.hedgeCount,
            hedgesAfter = latest.hedgeCount,
            silenceHeldBefore = (first.silenceOffered - first.silenceFilled).coerceAtLeast(0),
            silenceHeldAfter = (latest.silenceOffered - latest.silenceFilled).coerceAtLeast(0),
            mostImproved = mostImproved(first, latest, deltas)
        )
    }

    /**
     * The single line worth showing side by side: the criterion that gained
     * most, quoted verbatim from each run. Ties break on the fixed criterion
     * order so the card never changes between viewings.
     */
    private fun mostImproved(
        first: RunRecord,
        latest: RunRecord,
        deltas: List<CriterionDelta>
    ): ImprovedLine? {
        val best = deltas
            .filter { it.gain > 0 }
            .filter { latest.feedback.lineFor(it.criterion).isNotBlank() }
            .maxByOrNull { it.gain }
            ?: return null

        return ImprovedLine(
            criterion = best.criterion,
            before = first.feedback.lineFor(best.criterion),
            after = latest.feedback.lineFor(best.criterion)
        )
    }
}

data class CriterionDelta(
    val criterion: Criterion,
    val before: Int,
    val after: Int
) {
    val gain: Int get() = after - before
    val improved: Boolean get() = gain > 0
}

data class ImprovedLine(
    val criterion: Criterion,
    /** Empty when they did not attempt this at all the first time. */
    val before: String,
    val after: String
)

data class Comparison(
    val firstRunNumber: Int,
    val latestRunNumber: Int,
    val deltas: List<CriterionDelta>,
    val hedgesBefore: Int,
    val hedgesAfter: Int,
    val silenceHeldBefore: Int,
    val silenceHeldAfter: Int,
    val mostImproved: ImprovedLine?
) {
    val hedgeDrop: Int get() = hedgesBefore - hedgesAfter
    val scoreBefore: Int get() = deltas.sumOf { it.before }
    val scoreAfter: Int get() = deltas.sumOf { it.after }
    val improvedCount: Int get() = deltas.count { it.improved }

    /** True when there is something honest to celebrate. */
    val anyImprovement: Boolean
        get() = improvedCount > 0 || hedgeDrop > 0 || silenceHeldAfter > silenceHeldBefore
}
