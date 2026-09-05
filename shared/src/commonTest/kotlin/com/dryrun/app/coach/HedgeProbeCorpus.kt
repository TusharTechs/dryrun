package com.dryrun.app.coach

/**
 * Ordinary sentences, run through the detector so every hit can be judged by hand.
 *
 * Half of these are clean and half are hedged, and the clean half is deliberately
 * seeded with the traps a substring matcher would fall into -- "justify", "justice",
 * "adjustment", "a bitter argument", "what kind of". A false positive reads to a user
 * as a bug, so this file is the thing that keeps the feature honest.
 *
 * When you add a phrase to the detector, add cases here first.
 */
internal data class ProbeCase(
    val text: String,
    val expected: Int,
    val note: String = ""
)

internal object HedgeProbeCorpus {

    /** Must produce zero hits. Every one of these is a trap. */
    val CLEAN: List<ProbeCase> = listOf(
        ProbeCase("I need to justify the headcount increase to finance.", 0, "justify contains just"),
        ProbeCase("He justified the delay with the vendor timeline.", 0, "justified"),
        ProbeCase("The justice system moves slowly.", 0, "justice"),
        ProbeCase("That adjustment to the schedule was minor.", 0, "adjustment contains just"),
        ProbeCase("We are adjusting the forecast this week.", 0, "adjusting"),
        ProbeCase("Readjust the sprint scope before Friday.", 0, "readjust"),
        ProbeCase("We had a bitter argument about the roadmap.", 0, "bitter contains bit"),
        ProbeCase("The bitmap export is broken.", 0, "bitmap"),
        ProbeCase("Arbitration is the next step.", 0, "arbitration contains bit"),
        ProbeCase("The bit rate on that stream is too low.", 0, "bit without a"),
        ProbeCase("Kindly send the deck by noon.", 0, "kindly is not kind of"),
        ProbeCase("She has a kind manner with new starters.", 0, "kind without of"),
        ProbeCase("Sort the backlog by priority.", 0, "sort without of"),
        ProbeCase("The results were sorted alphabetically.", 0, "sorted"),
        ProbeCase("Littering is not the issue here.", 0, "littering contains little"),
        ProbeCase("She was let go for just cause.", 0, "just cause is the fairness sense"),
        ProbeCase("It was a just outcome for everyone.", 0, "just outcome"),
        ProbeCase("They want a more just society.", 0, "just society"),
        ProbeCase("I just got back from leave.", 0, "temporal just"),
        ProbeCase("I just heard from the client.", 0, "temporal just"),
        ProbeCase("We just finished the migration.", 0, "temporal just"),
        ProbeCase("She just left the building.", 0, "temporal just"),
        ProbeCase("I just now saw your message.", 0, "temporal just"),
        ProbeCase("What kind of support do you need from me?", 0, "classifying kind of"),
        ProbeCase("Some sort of process change is overdue.", 0, "classifying sort of"),
        ProbeCase("Which kind of contract are we on?", 0, "classifying kind of"),
        ProbeCase("That sort of behaviour has to stop.", 0, "classifying sort of"),
        ProbeCase("I am sorry to hear that happened to you.", 0, "sympathy, not pre-apology"),
        ProbeCase("Sorry to learn the deal fell through.", 0, "sympathy"),
        ProbeCase("I hope the review goes well.", 0, "hope is not hopefully"),
        ProbeCase("Your work on the migration was excellent.", 0),
        ProbeCase("The deadline is Thursday and it will not move.", 0),
        ProbeCase("You missed three standups this sprint.", 0),
        ProbeCase("I need the report on my desk by five.", 0),
        ProbeCase("This is the third time it has happened.", 0),
        ProbeCase("Tell me what you need to hit the date.", 0),
        ProbeCase("The client escalated on Tuesday morning.", 0),
        ProbeCase("I am not going to approve that request.", 0),
        ProbeCase("Your review comments made two people quit.", 0),
        ProbeCase("We are changing how the team assigns work.", 0),
        ProbeCase("I decided to give the project to Ravi.", 0),
        ProbeCase("Say more about what got in the way.", 0),
        ProbeCase("What did you want them to do differently?", 0),
        ProbeCase("That does not work for my team.", 0),
        ProbeCase("The answer is no, and here is why.", 0)
    )

    /** Must produce exactly the stated number of hits. */
    val HEDGED: List<ProbeCase> = listOf(
        ProbeCase("I just wanted to check in about the deadline.", 1, "just"),
        ProbeCase("I just need you to hit the date.", 1, "just"),
        ProbeCase("Just a quick thought on the design.", 1, "just"),
        ProbeCase("Maybe we could look at this again.", 1, "maybe"),
        ProbeCase("I think the timeline is too tight.", 1, "I think"),
        ProbeCase("I guess we could push it a week.", 1, "I guess"),
        ProbeCase("Hopefully that works for everyone.", 1, "hopefully"),
        ProbeCase("Does that make sense?", 1, "permission"),
        ProbeCase("No worries if not.", 1, "pre-apology"),
        ProbeCase("Sorry to bother you about this again.", 1, "pre-apology"),
        ProbeCase("I was wondering if you had ten minutes.", 1, "permission"),
        ProbeCase("I might be wrong but the numbers look off.", 1, "self-doubt"),
        ProbeCase("It is not a big deal but the report was late.", 1, "pre-apology"),
        ProbeCase("We need a bit more time on this.", 1, "a bit"),
        ProbeCase("The scope grew a little since kickoff.", 1, "a little"),
        ProbeCase("It was kind of a mess by Friday.", 1, "kind of, not classifying"),
        ProbeCase("I was sort of expecting a heads up.", 1, "sort of, not classifying"),
        ProbeCase("I will move the meeting, if that's okay.", 1, "permission"),
        ProbeCase("I will move the meeting, if that is okay.", 1, "spelled-out variant"),
        ProbeCase("I feel like we are going in circles.", 1, "self-doubt"),

        ProbeCase("I think maybe we should revisit this.", 2, "I think + maybe"),
        ProbeCase("Sorry to bother you, I was wondering if you had a moment.", 2),
        ProbeCase("I feel like the team is a little overwhelmed.", 2),
        ProbeCase("Maybe I guess we could try the other approach.", 2),
        ProbeCase(
            "I just think the deadline is optimistic.",
            1,
            "one construction, not two: 'just' splits 'I think', and counting both " +
                "would double-count the same hedge. Found by the probe."
        ),
        ProbeCase("It is kind of a big deal, does that make sense?", 2),
        ProbeCase("Hopefully we can just move this to Friday.", 2),
        ProbeCase("I might be wrong but maybe we should wait.", 2),

        ProbeCase("Hopefully we can just move this to Friday, if that's okay.", 3),
        ProbeCase("I think we could maybe push it a little.", 3),
        ProbeCase("Sorry to ask, but I just need a bit more time.", 3),
        ProbeCase(
            "I just wanted to say I think your work has maybe slipped a little, " +
                "no worries if not, does that make sense?",
            6,
            "the full catastrophe -- this is the demo sentence"
        )
    )

    /**
     * Known, accepted false positives. Documented rather than hidden: each one is
     * a case where fixing it would cost more precision elsewhere than it buys.
     */
    val ACCEPTED_FALSE_POSITIVES: List<ProbeCase> = listOf(
        ProbeCase(
            "We moved to just-in-time delivery last quarter.",
            1,
            "'just in time' as a term of art; too rare in a difficult conversation to special-case"
        )
    )

    val ALL: List<ProbeCase> get() = CLEAN + HEDGED + ACCEPTED_FALSE_POSITIVES
}
