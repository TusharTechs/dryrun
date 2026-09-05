package com.dryrun.app.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HedgeDetectorTest {

    // ---- the probe -------------------------------------------------------

    @Test
    fun `every clean sentence produces no hits`() {
        val failures = HedgeProbeCorpus.CLEAN.mapNotNull { case ->
            val report = HedgeDetector.analyse(case.text)
            if (report.count == 0) null
            else "\"${case.text}\" -> ${report.hits.map { it.phrase }} (${case.note})"
        }
        assertTrue(
            failures.isEmpty(),
            "False positives found:\n" + failures.joinToString("\n")
        )
    }

    @Test
    fun `every hedged sentence produces the expected count`() {
        val failures = (HedgeProbeCorpus.HEDGED + HedgeProbeCorpus.ACCEPTED_FALSE_POSITIVES)
            .mapNotNull { case ->
                val report = HedgeDetector.analyse(case.text)
                if (report.count == case.expected) null
                else "\"${case.text}\" expected ${case.expected}, got ${report.count} " +
                    "${report.hits.map { it.phrase }} (${case.note})"
            }
        assertTrue(
            failures.isEmpty(),
            "Miscounts found:\n" + failures.joinToString("\n")
        )
    }

    // ---- word boundaries, the expensive lesson ---------------------------

    @Test
    fun `substrings never match`() {
        listOf(
            "justify", "justified", "justice", "adjustment", "adjusting",
            "bitter", "bitmap", "arbitration", "littering", "kindly", "sorted"
        ).forEach { word ->
            assertEquals(
                0,
                HedgeDetector.analyse("The $word situation.").count,
                "\"$word\" should not contain a hedge"
            )
        }
    }

    @Test
    fun `punctuation and quotes do not hide a hedge`() {
        assertEquals(1, HedgeDetector.analyse("I said 'just' once.").count)
        assertEquals(1, HedgeDetector.analyse("Maybe.").count)
        assertEquals(1, HedgeDetector.analyse("...maybe?").count)
        assertEquals(1, HedgeDetector.analyse("Does that make sense?").count)
        assertEquals(1, HedgeDetector.analyse("Does that make sense").count)
    }

    @Test
    fun `curly and straight apostrophes both match`() {
        assertEquals(1, HedgeDetector.analyse("Move it, if that's okay.").count)
        assertEquals(1, HedgeDetector.analyse("Move it, if that’s okay.").count)
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(1, HedgeDetector.analyse("JUST checking.").count)
        assertEquals(1, HedgeDetector.analyse("Sort Of a problem.").count)
    }

    // ---- ranges point at the original text -------------------------------

    @Test
    fun `range covers exactly the hedge in the original string`() {
        val text = "I just wanted a bit more time."
        val report = HedgeDetector.analyse(text)
        val quoted = report.hits.map { text.substring(it.range.first, it.range.last + 1) }
        assertEquals(listOf("just", "a bit"), quoted)
    }

    @Test
    fun `ranges survive leading whitespace and newlines`() {
        val text = "\n\n   Maybe we should wait."
        val report = HedgeDetector.analyse(text)
        assertEquals(1, report.count)
        assertEquals("Maybe", text.substring(report.hits[0].range.first, report.hits[0].range.last + 1))
    }

    @Test
    fun `ranges never overlap`() {
        val text = "I just wanted to say I think this is kind of a problem, does that make sense?"
        val hits = HedgeDetector.analyse(text).hits
        hits.zipWithNext { a, b ->
            assertTrue(a.range.last < b.range.first, "ranges overlap: $a and $b")
        }
    }

    // ---- longest match wins ----------------------------------------------

    @Test
    fun `longer phrase beats the shorter one it contains`() {
        val report = HedgeDetector.analyse("I might be wrong but the plan is late.")
        assertEquals(1, report.count)
        assertEquals("I might be wrong but", report.hits.single().phrase)
    }

    // ---- exclusions ------------------------------------------------------

    @Test
    fun `temporal just is not a hedge but softening just is`() {
        assertEquals(0, HedgeDetector.analyse("I just got your email.").count)
        assertEquals(1, HedgeDetector.analyse("I just need your email.").count)
    }

    @Test
    fun `classifying kind of is not a hedge but softening kind of is`() {
        assertEquals(0, HedgeDetector.analyse("What kind of help do you want?").count)
        assertEquals(1, HedgeDetector.analyse("It was kind of rushed.").count)
    }

    @Test
    fun `sorry to hear is sympathy not a pre-apology`() {
        assertEquals(0, HedgeDetector.analyse("Sorry to hear about the outage.").count)
        assertEquals(1, HedgeDetector.analyse("Sorry to drop this on you.").count)
    }

    // ---- report shape ----------------------------------------------------

    @Test
    fun `empty and blank input is safe`() {
        assertEquals(0, HedgeDetector.analyse("").count)
        assertEquals(0, HedgeDetector.analyse("    \n\t ").count)
        assertEquals(0.0, HedgeDetector.analyse("").density)
        assertNull(HedgeDetector.analyse("").mostUsed)
    }

    @Test
    fun `most used names the repeated phrase`() {
        val report = HedgeDetector.analyse(
            "I just wanted to just check, and maybe just confirm the date."
        )
        assertEquals(3, report.countOf("just"))
        assertEquals("just", report.mostUsed)
    }

    @Test
    fun `most used breaks ties alphabetically so results are reproducible`() {
        val report = HedgeDetector.analyse("Maybe I think so.")
        assertEquals(1, report.countOf("maybe"))
        assertEquals(1, report.countOf("I think"))
        assertEquals("I think", report.mostUsed, "ties must resolve the same way every run")
    }

    @Test
    fun `kinds are grouped for the feedback copy`() {
        val report = HedgeDetector.analyse(
            "Sorry to ask, but I think maybe we could just wait, does that make sense?"
        )
        val kinds = report.byKind()
        assertEquals(1, kinds[HedgeKind.PRE_APOLOGY])
        assertEquals(1, kinds[HedgeKind.SELF_DOUBT])
        assertEquals(2, kinds[HedgeKind.SOFTENER])
        assertEquals(1, kinds[HedgeKind.PERMISSION])
    }

    @Test
    fun `density is comparable across different lengths`() {
        val short = HedgeDetector.analyse("I just asked.")
        assertEquals(3, short.wordCount)
        assertTrue(short.density > 30.0)
        assertEquals(0.0, HedgeDetector.analyse("The deadline is Thursday.").density)
    }

    @Test
    fun `analysis is deterministic across repeated runs`() {
        val text = "I just think maybe this is kind of a problem, sorry to bring it up."
        val first = HedgeDetector.analyse(text)
        repeat(5) {
            val again = HedgeDetector.analyse(text)
            assertEquals(first.hits, again.hits)
            assertEquals(first.count, again.count)
        }
    }
}
