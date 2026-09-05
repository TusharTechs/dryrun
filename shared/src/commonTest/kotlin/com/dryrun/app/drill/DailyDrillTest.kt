package com.dryrun.app.drill

import com.dryrun.app.coach.HedgeDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyDrillTest {

    @Test
    fun the_same_day_always_gives_the_same_line() {
        assertEquals(DailyDrill.forDay(19_000L), DailyDrill.forDay(19_000L))
    }

    @Test
    fun consecutive_days_give_different_lines() {
        val run = (0 until DailyDrill.ALL.size).map { DailyDrill.forDay(it.toLong()) }
        assertEquals(DailyDrill.ALL.size, run.toSet().size)
    }

    @Test
    fun a_negative_day_index_still_lands_on_a_real_drill() {
        // localDayIndex is only meaningful as a difference, so guard the sign.
        assertTrue(DailyDrill.forDay(-1L) in DailyDrill.ALL)
        assertTrue(DailyDrill.forDay(-9_999L) in DailyDrill.ALL)
    }

    @Test
    fun every_softened_line_actually_contains_a_hedge() {
        // Otherwise the exercise has nothing in it to find. Reported all at
        // once -- fixing content one failure per run is a waste of a test.
        val bare = DailyDrill.ALL.filter { HedgeDetector.analyse(it.soft).count == 0 }
        assertTrue(bare.isEmpty(), "nothing to remove in:\n" + bare.joinToString("\n") { it.soft })
    }

    @Test
    fun every_straight_answer_is_clean_by_the_app_s_own_detector() {
        // The model answers are held to the standard the app teaches. If the
        // detector and the content ever disagree, one of them is wrong.
        val dirty = DailyDrill.ALL
            .map { it to HedgeDetector.analyse(it.straight).hits }
            .filter { (_, hits) -> hits.isNotEmpty() }
        assertTrue(
            dirty.isEmpty(),
            "still hedging:\n" + dirty.joinToString("\n") { (d, h) ->
                "\"${d.straight}\" -> ${h.map { it.phrase }}"
            }
        )
    }

    @Test
    fun the_straight_version_is_never_longer_than_the_soft_one() {
        DailyDrill.ALL.forEach { drill ->
            assertTrue(
                drill.straight.length < drill.soft.length,
                "not actually tighter: \"${drill.straight}\""
            )
        }
    }

    @Test
    fun nothing_is_left_blank() {
        DailyDrill.ALL.forEach {
            assertTrue(it.soft.isNotBlank() && it.straight.isNotBlank() && it.note.isNotBlank())
        }
    }
}
