package com.dryrun.app.billing

/**
 * Where the wall sits.
 *
 * Two full runs are free, which is exactly enough to finish a rehearsal, get
 * the feedback, run it again, and see the before/after card. The whole point
 * is that the value is felt before anyone is asked to pay for it.
 */
object FreeTier {

    const val FREE_RUNS = 2

    /**
     * [completedRuns] is the number of finished runs on this device.
     * [plusActive] should come from [Plus.isActive], which is already true
     * when billing is unavailable -- so a keyless build never blocks anyone.
     */
    fun canStartRun(completedRuns: Int, plusActive: Boolean): Boolean =
        plusActive || completedRuns < FREE_RUNS

    /** Null means unlimited. Used for the "1 run left" line, never a padlock. */
    fun runsLeft(completedRuns: Int, plusActive: Boolean): Int? =
        if (plusActive) null else (FREE_RUNS - completedRuns).coerceAtLeast(0)

    /**
     * True only at the moment the wall is actually reached. The paywall is
     * shown after the before/after card has been seen, never before it.
     */
    fun shouldShowPaywall(completedRuns: Int, plusActive: Boolean): Boolean =
        !plusActive && completedRuns >= FREE_RUNS
}
