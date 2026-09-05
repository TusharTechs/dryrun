package com.dryrun.app.drill

/**
 * One line to straighten, offered once a day.
 *
 * The app is for a conversation you have on a specific date, which is not a
 * daily event. This is the part that is: sixty seconds, one softened
 * sentence, say it straight. It runs entirely on the device -- no request,
 * no cost, no network -- and it deliberately never sends a notification,
 * because the app promises one reminder before a conversation and no daily
 * pestering. It is here when you open the app, and silent when you don't.
 */
data class Drill(
    /** The softened line, as people actually write it. */
    val soft: String,
    /** One way to say the same thing straight. Not the only way. */
    val straight: String,
    /** What the rewrite actually changed. One clause, no lecture. */
    val note: String
)

object DailyDrill {

    /**
     * The day's line is a pure function of the date, so it is the same on
     * every device and needs nothing stored to advance. The list is longer
     * than any realistic test window, so nobody sees a repeat.
     */
    fun forDay(dayIndex: Long): Drill {
        val size = ALL.size
        val index = ((dayIndex % size) + size) % size
        return ALL[index.toInt()]
    }

    val ALL: List<Drill> = listOf(
        Drill(
            "I just wanted to check if maybe you'd had a chance to look at that?",
            "Have you had a chance to look at that?",
            "The question was always the question. The run-up added nothing."
        ),
        Drill(
            "Sorry, I know you're busy, but could you possibly send the numbers over?",
            "Can you send me the numbers today?",
            "Apologising for needing something makes it sound optional."
        ),
        Drill(
            "I sort of feel like the deadline might be a bit tight?",
            "The deadline is too tight. I need two more days.",
            "A worry with a question mark is easy to wave off. A number isn't."
        ),
        Drill(
            "No worries at all if not, but would it be okay to move our one-to-one?",
            "Can we move our one-to-one to Thursday?",
            "Offering the no before you've made the ask usually gets you the no."
        ),
        Drill(
            "I might be wrong, but I think there may be an issue with the invoice.",
            "There's an error on the invoice.",
            "Pre-doubting a fact you checked invites someone to check it again."
        ),
        Drill(
            "Just following up on this, whenever you get a sec!",
            "Following up — I need an answer by Friday.",
            "\"Whenever\" is a deadline of never."
        ),
        Drill(
            "I hope this isn't a stupid question, but what does this metric measure?",
            "What does this metric measure?",
            "Nobody was going to think it was stupid until you said so."
        ),
        Drill(
            "It's probably just me, but I found the brief a little unclear.",
            "The brief is unclear. Which of these two things do you want?",
            "\"Probably just me\" hands back the problem you're trying to solve."
        ),
        Drill(
            "I was kind of hoping we might be able to revisit the scope?",
            "The scope needs to change. Here's what I'd cut.",
            "Hoping is not a request. Naming the cut is."
        ),
        Drill(
            "Would it maybe make sense to perhaps loop in legal at some point?",
            "We need legal on this before we ship.",
            "Three hedges in one sentence, and no one knows when to act."
        ),
        Drill(
            "Sorry to be a pain, but I still haven't got access.",
            "I still don't have access. Can you grant it today?",
            "You are not a pain for being blocked."
        ),
        Drill(
            "I don't want to step on toes, but I think this is my area?",
            "This is my area. I'll take it from here.",
            "Asking permission to own the thing you own gives it away."
        ),
        Drill(
            "Just a small thing, feel free to ignore, but the logo looks stretched.",
            "The logo is stretched. It needs fixing before launch.",
            "You told them to ignore it, so they will."
        ),
        Drill(
            "I guess I'm just a bit unsure about whether this is the right call?",
            "I don't think this is the right call. Here's why.",
            "Disagreement disguised as confusion doesn't get answered."
        ),
        Drill(
            "Apologies for the delay, things have been a bit mad!",
            "This is late. It'll be with you by 2pm.",
            "The apology isn't what they need. The new time is."
        ),
        Drill(
            "Do you think there's maybe any chance we could get more budget?",
            "We need two more weeks and another designer.",
            "\"Any chance\" makes it a favour. It's a requirement."
        ),
        Drill(
            "Not sure if this is helpful at all, but I made a doc.",
            "I made a doc that answers this.",
            "You decided it wasn't helpful before they'd opened it."
        ),
        Drill(
            "I feel like it might be worth considering whether we should pause?",
            "We should pause. The data isn't ready.",
            "Four layers between you and a recommendation."
        ),
        Drill(
            "Sorry, just wondering if you'd had any thoughts on my proposal?",
            "What did you think of my proposal?",
            "Wondering out loud is easier to ignore than asking."
        ),
        Drill(
            "It's totally fine either way, but I'd sort of prefer Tuesday.",
            "Tuesday works better for me.",
            "If it were fine either way you wouldn't be writing."
        ),
        Drill(
            "I hate to ask, but any update on the raise conversation?",
            "Where are we on the raise we discussed?",
            "Flinching first makes the question sound unreasonable."
        ),
        Drill(
            "Maybe I've misunderstood, but wasn't this supposed to ship last week?",
            "This was due last week. What happened?",
            "You hadn't misunderstood."
        ),
        Drill(
            "Just checking in, no pressure, whenever suits!",
            "I need this by Thursday to stay on track.",
            "Three softeners, no date, no chance."
        ),
        Drill(
            "I might be overthinking it, but the tone feels slightly off?",
            "The tone is wrong for this audience.",
            "Calling your own judgement overthinking retires it early."
        ),
        Drill(
            "Would you mind terribly if I skipped the Friday sync?",
            "I'm going to skip the Friday sync.",
            "You weren't asking. Don't pretend you were."
        ),
        Drill(
            "I'm probably being dense, but I don't follow the reasoning.",
            "Walk me through the reasoning.",
            "The gap is in the explanation, not in you."
        ),
        Drill(
            "Sorry, one more thing, and then I'll leave you alone!",
            "One more thing.",
            "Apologising for taking up space takes up more of it."
        ),
        Drill(
            "I sort of think we may possibly be over-engineering this a bit.",
            "We're over-engineering this.",
            "Four hedges around a view you actually hold."
        ),
        Drill(
            "If it's not too much trouble, could you review by end of day?",
            "Can you review this by end of day?",
            "It's their job. It isn't trouble."
        ),
        Drill(
            "I just think it might be better if we didn't commit to that date?",
            "I can't commit to that date.",
            "\"Better if we didn't\" hides who is saying no."
        )
    )
}
