package com.dryrun.app.coach

import com.dryrun.app.models.Criterion
import com.dryrun.app.models.CriterionScore
import com.dryrun.app.models.FeedbackReport
import com.dryrun.app.models.RunRecord
import com.dryrun.app.models.SilenceLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun report(
    specific: Int = 0,
    impact: Int = 0,
    silence: Int = 0,
    held: Int = 0,
    lines: Map<Criterion, String> = emptyMap(),
    overall: String = "",
    strongest: String = ""
) = FeedbackReport(
    criteria = listOf(
        CriterionScore(Criterion.SPECIFIC_BEHAVIOUR.id, specific, lines[Criterion.SPECIFIC_BEHAVIOUR].orEmpty(), ""),
        CriterionScore(Criterion.CONCRETE_IMPACT.id, impact, lines[Criterion.CONCRETE_IMPACT].orEmpty(), ""),
        CriterionScore(Criterion.LEFT_SILENCE.id, silence, lines[Criterion.LEFT_SILENCE].orEmpty(), ""),
        CriterionScore(Criterion.HELD_POINT.id, held, lines[Criterion.HELD_POINT].orEmpty(), "")
    ),
    overall = overall,
    strongestLine = strongest
)

private fun run(
    number: Int,
    feedback: FeedbackReport,
    hedges: Int = 0,
    silenceOffered: Int = 0,
    silenceFilled: Int = 0
) = RunRecord(
    runNumber = number,
    transcript = emptyList(),
    feedback = feedback,
    hedgeCount = hedges,
    hedgeTopPhrase = "",
    silenceOffered = silenceOffered,
    silenceFilled = silenceFilled,
    completedAtMillis = 0L
)

class RunComparisonTest {

    @Test
    fun `bars move with the scores`() {
        val c = RunComparison.compare(
            run(1, report(specific = 0, impact = 1, silence = 0, held = 0)),
            run(4, report(specific = 2, impact = 2, silence = 1, held = 1))
        )
        assertEquals(1, c.scoreBefore)
        assertEquals(6, c.scoreAfter)
        assertEquals(4, c.improvedCount)
    }

    @Test
    fun `hedge drop is reported`() {
        val c = RunComparison.compare(
            run(1, report(), hedges = 6),
            run(4, report(), hedges = 1)
        )
        assertEquals(6, c.hedgesBefore)
        assertEquals(1, c.hedgesAfter)
        assertEquals(5, c.hedgeDrop)
    }

    @Test
    fun `most improved quotes both runs verbatim`() {
        val improved = RunComparison.compare(
            run(1, report(held = 0, lines = mapOf(Criterion.HELD_POINT to "Sorry, forget I said anything."))),
            run(4, report(held = 2, lines = mapOf(Criterion.HELD_POINT to "I hear that. The deadline still moved.")))
        ).mostImproved

        assertNotNull(improved)
        assertEquals(Criterion.HELD_POINT, improved.criterion)
        assertEquals("Sorry, forget I said anything.", improved.before)
        assertEquals("I hear that. The deadline still moved.", improved.after)
    }

    @Test
    fun `most improved picks the biggest gain, not the highest score`() {
        val improved = RunComparison.compare(
            run(1, report(specific = 2, held = 0, lines = mapOf(
                Criterion.SPECIFIC_BEHAVIOUR to "a", Criterion.HELD_POINT to "b"
            ))),
            // specific gains 0 (already maxed), held gains 2.
            run(2, report(specific = 2, held = 2, lines = mapOf(
                Criterion.SPECIFIC_BEHAVIOUR to "a2", Criterion.HELD_POINT to "b2"
            )))
        ).mostImproved

        assertEquals(Criterion.HELD_POINT, improved?.criterion)
    }

    @Test
    fun `most improved is null when nothing improved`() {
        assertNull(
            RunComparison.compare(
                run(1, report(specific = 2)),
                run(2, report(specific = 2))
            ).mostImproved
        )
    }

    @Test
    fun `most improved needs a quotable line in the later run`() {
        // Gained, but there is nothing verbatim to show. Better to show
        // nothing than to invent a quote.
        assertNull(
            RunComparison.compare(
                run(1, report(held = 0)),
                run(2, report(held = 2, lines = emptyMap()))
            ).mostImproved
        )
    }

    @Test
    fun `ties resolve the same way every time`() {
        val first = run(1, report(specific = 0, impact = 0, lines = mapOf(
            Criterion.SPECIFIC_BEHAVIOUR to "x", Criterion.CONCRETE_IMPACT to "y"
        )))
        val latest = run(2, report(specific = 1, impact = 1, lines = mapOf(
            Criterion.SPECIFIC_BEHAVIOUR to "x2", Criterion.CONCRETE_IMPACT to "y2"
        )))
        val picks = (1..10).map { RunComparison.compare(first, latest).mostImproved?.criterion }
        assertEquals(1, picks.toSet().size, "the card must not change between viewings")
    }

    @Test
    fun `silence held is counted, and never negative`() {
        val c = RunComparison.compare(
            run(1, report(), silenceOffered = 2, silenceFilled = 2),
            run(4, report(), silenceOffered = 2, silenceFilled = 0)
        )
        assertEquals(0, c.silenceHeldBefore)
        assertEquals(2, c.silenceHeldAfter)
    }

    @Test
    fun `a run with a worse score but fewer hedges still has something honest to show`() {
        val c = RunComparison.compare(
            run(1, report(specific = 2), hedges = 8),
            run(2, report(specific = 1), hedges = 2)
        )
        assertTrue(c.anyImprovement)
        assertEquals(6, c.hedgeDrop)
    }

    @Test
    fun `a genuinely flat run does not pretend otherwise`() {
        val flat = report(specific = 1)
        assertFalse(RunComparison.compare(run(1, flat, hedges = 3), run(2, flat, hedges = 3)).anyImprovement)
    }

    @Test
    fun `best line falls back when the model declined to pick one`() {
        val r = report(
            specific = 2,
            held = 1,
            lines = mapOf(
                Criterion.SPECIFIC_BEHAVIOUR to "You missed Monday's handoff.",
                Criterion.HELD_POINT to "I still need it Monday."
            ),
            strongest = ""
        )
        assertEquals("You missed Monday's handoff.", r.bestLine())
    }

    @Test
    fun `best line prefers the model's own pick`() {
        val r = report(specific = 2, lines = mapOf(Criterion.SPECIFIC_BEHAVIOUR to "a"), strongest = "b")
        assertEquals("b", r.bestLine())
    }
}

class SilenceLogTest {

    @Test
    fun `beats are counted as they happen`() {
        var log = SilenceLog()
        log = log.beatOffered()
        log = log.beatFilled()
        log = log.beatOffered()
        assertEquals(2, log.offered)
        assertEquals(1, log.filled)
        assertEquals(1, log.held)
    }

    @Test
    fun `held never goes negative`() {
        assertEquals(0, SilenceLog(offered = 1, filled = 3).held)
    }

    @Test
    fun `the window is long enough to actually feel`() {
        assertTrue(SilenceLog.WINDOW_MILLIS >= 3000L)
    }
}
