package com.dryrun.app.coach

/**
 * Finds the words people reach for when they want to sound less demanding.
 *
 * Runs on the device as you type. No model call, no network, no cost, and the
 * same input always gives the same output -- which is what makes the run-to-run
 * comparison worth anything.
 *
 * Two rules govern everything here:
 *
 *  1. Match whole words, never substrings. "just" must not fire inside
 *     "justify"; "a bit" must not fire inside "a bitter argument". Every
 *     match is a run of tokens, so those cannot happen by construction.
 *  2. Observe, do not scold. This never blocks anything. Hedging is a habit
 *     worth noticing, not an error worth preventing.
 */
object HedgeDetector {

    fun analyse(text: String): HedgeReport {
        val tokens = tokenize(text)
        val hits = mutableListOf<HedgeHit>()

        var i = 0
        while (i < tokens.size) {
            // Longest phrase first, so "I might be wrong but" wins over "I think"
            // and nothing is counted twice.
            val match = PHRASES.firstOrNull { phrase ->
                matchesAt(tokens, i, phrase) && !isExcluded(tokens, i, phrase)
            }
            if (match == null) {
                i++
            } else {
                val last = tokens[i + match.tokens.size - 1]
                hits += HedgeHit(
                    phrase = match.canonical,
                    kind = match.kind,
                    range = tokens[i].start until last.endExclusive
                )
                i += match.tokens.size
            }
        }

        return HedgeReport(hits = hits, wordCount = tokens.size)
    }

    // ---- phrase table ----------------------------------------------------

    private class Phrase(val canonical: String, val kind: HedgeKind, val tokens: List<String>)

    private fun phrase(canonical: String, kind: HedgeKind) =
        Phrase(canonical, kind, canonical.split(" ").map { it.normalised() })

    private val PHRASES: List<Phrase> = listOf(
        // Softeners -- shrink the ask.
        phrase("just", HedgeKind.SOFTENER),
        phrase("sort of", HedgeKind.SOFTENER),
        phrase("kind of", HedgeKind.SOFTENER),
        phrase("a little", HedgeKind.SOFTENER),
        phrase("a bit", HedgeKind.SOFTENER),
        phrase("maybe", HedgeKind.SOFTENER),
        phrase("perhaps", HedgeKind.SOFTENER),
        phrase("possibly", HedgeKind.SOFTENER),
        // Self-doubt -- undercut your own judgement before anyone else can.
        phrase("I think", HedgeKind.SELF_DOUBT),
        phrase("I guess", HedgeKind.SELF_DOUBT),
        phrase("I feel like", HedgeKind.SELF_DOUBT),
        phrase("I might be wrong but", HedgeKind.SELF_DOUBT),
        // Pre-emptive self-deprecation: rubbish your own question before
        // anyone else can. Contractions are spelled out both ways because the
        // tokenizer keeps apostrophes rather than folding them.
        phrase("I hope this isn't", HedgeKind.SELF_DOUBT),
        phrase("I hope this is not", HedgeKind.SELF_DOUBT),
        phrase("I'm probably", HedgeKind.SELF_DOUBT),
        phrase("I am probably", HedgeKind.SELF_DOUBT),
        phrase("I might be overthinking", HedgeKind.SELF_DOUBT),
        phrase("not sure if this is", HedgeKind.SELF_DOUBT),
        // Permission -- hand them the decision you already made.
        phrase("does that make sense", HedgeKind.PERMISSION),
        phrase("if that's okay", HedgeKind.PERMISSION),
        phrase("if that is okay", HedgeKind.PERMISSION),
        phrase("I was wondering if", HedgeKind.PERMISSION),
        phrase("any chance", HedgeKind.PERMISSION),
        phrase("would you mind", HedgeKind.PERMISSION),
        // Pre-apology -- apologise for the conversation before having it.
        phrase("sorry to", HedgeKind.PRE_APOLOGY),
        phrase("no worries if not", HedgeKind.PRE_APOLOGY),
        // The general form, so variants like "no worries at all if not" are
        // caught too. Longest-first matching keeps this from double-counting.
        phrase("no worries", HedgeKind.PRE_APOLOGY),
        phrase("not a big deal but", HedgeKind.PRE_APOLOGY),
        phrase("hopefully", HedgeKind.PRE_APOLOGY),
        phrase("apologies for", HedgeKind.PRE_APOLOGY),
        phrase("I hate to ask", HedgeKind.PRE_APOLOGY),
        phrase("leave you alone", HedgeKind.PRE_APOLOGY),
        phrase("if it's not too much trouble", HedgeKind.PRE_APOLOGY),
        phrase("if it is not too much trouble", HedgeKind.PRE_APOLOGY)
    ).sortedByDescending { it.tokens.size }

    // ---- exclusions ------------------------------------------------------
    // Each of these is a real false positive found by running the detector over
    // ordinary sentences. See HedgeProbeTest.

    /** "I just got back", "just now" -- temporal, not a hedge. */
    private val JUST_TEMPORAL = setOf(
        "now", "then", "got", "gotten", "finished", "arrived", "came", "left",
        "started", "saw", "heard", "yesterday", "today", "landed", "sent", "read"
    )

    /** "a just cause", "a just outcome" -- the fairness adjective, not a hedge. */
    private val JUST_FAIRNESS = setOf(
        "cause", "causes", "world", "society", "outcome", "outcomes", "war", "peace"
    )

    /** "what kind of", "some sort of" -- classifying, not softening. */
    private val CLASSIFYING_DETERMINERS = setOf(
        "what", "which", "any", "some", "that", "this", "every", "no",
        "other", "another", "all", "both", "each", "the"
    )

    /** "sorry to hear that" -- sympathy, not a pre-apology. */
    private val SORRY_SYMPATHY = setOf("hear", "learn", "see")

    private fun isExcluded(tokens: List<Token>, i: Int, phrase: Phrase): Boolean {
        val previous = tokens.getOrNull(i - 1)?.text
        val following = tokens.getOrNull(i + phrase.tokens.size)?.text
        return when (phrase.canonical) {
            "just" -> following in JUST_TEMPORAL || following in JUST_FAIRNESS
            "kind of", "sort of" -> previous in CLASSIFYING_DETERMINERS
            "sorry to" -> following in SORRY_SYMPATHY
            else -> false
        }
    }

    private fun matchesAt(tokens: List<Token>, i: Int, phrase: Phrase): Boolean {
        if (i + phrase.tokens.size > tokens.size) return false
        return phrase.tokens.indices.all { k -> tokens[i + k].text == phrase.tokens[k] }
    }

    // ---- tokenizer -------------------------------------------------------

    /** A word, and where it sits in the original untouched string. */
    internal data class Token(val text: String, val start: Int, val endExclusive: Int)

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    internal fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            if (!isWordChar(text[i])) {
                i++
                continue
            }
            val runStart = i
            while (i < text.length && isWordChar(text[i])) i++
            // Quote marks around a word get swept up by the run; drop them so
            // 'just' in quotes still matches, and keep offsets honest.
            var start = runStart
            var end = i
            while (start < end && !text[start].isLetterOrDigit()) start++
            while (end > start && !text[end - 1].isLetterOrDigit()) end--
            if (start < end) {
                tokens += Token(text.substring(start, end).normalised(), start, end)
            }
        }
        return tokens
    }

    /** Case-folds and flattens the curly apostrophe so "that's" == "that's". */
    private fun String.normalised(): String = lowercase().replace('’', '\'')
}

/** What the hedge is doing, which is more useful to say back than a raw count. */
enum class HedgeKind(val label: String) {
    SOFTENER("Softener"),
    SELF_DOUBT("Self-doubt"),
    PERMISSION("Asking permission"),
    PRE_APOLOGY("Pre-apology")
}

/** One hedge, and exactly where it sits so the UI can underline it in place. */
data class HedgeHit(
    val phrase: String,
    val kind: HedgeKind,
    val range: IntRange
)

data class HedgeReport(
    val hits: List<HedgeHit>,
    val wordCount: Int
) {
    val count: Int get() = hits.size

    /** Hedges per hundred words. Comparable across runs of different lengths. */
    val density: Double
        get() = if (wordCount == 0) 0.0 else hits.size * 100.0 / wordCount

    fun countOf(phrase: String): Int = hits.count { it.phrase == phrase }

    fun byKind(): Map<HedgeKind, Int> =
        hits.groupingBy { it.kind }.eachCount()

    /**
     * The phrase to name back to them. Ties break alphabetically so the same
     * transcript always produces the same sentence.
     */
    val mostUsed: String?
        get() = hits.groupingBy { it.phrase }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

    companion object {
        val EMPTY = HedgeReport(emptyList(), 0)
    }
}
