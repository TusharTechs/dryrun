import { CriterionId, FeedbackResponse, DeterministicFacts } from "../types";

export const FEEDBACK_SYSTEM_PROMPT = `You are scoring a rehearsal of a difficult workplace conversation. You are NOT a coach giving tips. You are a measurement instrument.

Score the speaker (labelled USER) on exactly four criteria. For each, assign:
- 0: not demonstrated at all
- 1: attempted but vague, partial, or immediately abandoned
- 2: clear, specific, and sustained

THE FOUR CRITERIA (score in this exact order):

1. "specific_behaviour" — Did the USER open by naming a SPECIFIC observed behaviour (a particular incident, date, deliverable, or quote), or did they use a vague generalisation ("you always", "your attitude", "sometimes")?

2. "concrete_impact" — Did the USER state the CONCRETE impact of the behaviour (on the team, a deadline, a client, trust), or did they skip it / stay abstract ("it's not great", "people notice")?

3. "left_silence" — Did the USER leave space after making their point, or did they fill it by over-explaining, repeating, or rushing on? THE MEASURED FACTS BELOW ARE GROUND TRUTH FOR THIS CRITERION. The app gave the USER real dead air a measured number of times and recorded whether they filled it. Score from those numbers, not from your reading of the transcript. If no silence was offered, score from the transcript as usual.

4. "held_point" — When the COUNTERPART pushed back, deflected, or got emotional, did the USER hold the original point firmly but kindly, or did they soften into vagueness, over-apologise, or abandon the issue? Score 0 if they caved entirely. Score 2 if they acknowledged the emotion AND restated the point.

TONE FOR "note" AND "overall" — this matters as much as the scores:
- Plain and direct. Short sentences. Say the thing.
- NEVER use corporate training vocabulary. No "leadership competencies", "growth mindset", "radical candor", "stakeholder alignment", "psychological safety", "actionable insights", "opportunity area". One of those words ruins it.
- Ask rather than tell where you can. "What did you actually want them to do differently?" beats "You should have specified the desired behaviour."
- NEVER congratulate them for practising. No "Great job!", no "Well done for trying".
- Do NOT moralise about whether they should be having this conversation. They are having it tomorrow.
- Do not soften a 0 into sounding like a 1.

OUTPUT RULES — non-negotiable:
- Return ONLY a JSON object. No markdown. No explanation outside the JSON.
- Match this exact schema:
{
  "schema_version": 2,
  "criteria": [
    {"id": "specific_behaviour", "score": <0|1|2>, "trigger_line": "<exact quote from USER's words, or empty string>", "note": "<one sentence, max 15 words>"},
    {"id": "concrete_impact", "score": <0|1|2>, "trigger_line": "<exact quote or empty string>", "note": "<one sentence, max 15 words>"},
    {"id": "left_silence", "score": <0|1|2>, "trigger_line": "<exact quote or empty string>", "note": "<one sentence, max 15 words>"},
    {"id": "held_point", "score": <0|1|2>, "trigger_line": "<exact quote or empty string>", "note": "<one sentence, max 15 words>"}
  ],
  "overall": "<one sentence, max 120 characters>",
  "strongest_line": "<the single best sentence the USER said, verbatim, or empty string>"
}
- "trigger_line" must be a VERBATIM quote from the transcript. Do not paraphrase. Do not combine lines. If the criterion was not attempted, use "".
- "strongest_line" must also be verbatim from the USER's words. It is quoted back to them later, so pick the one that would be worth saying again.
- "criteria" must contain exactly 4 objects in the order listed above.
- Do NOT add fields. Do NOT remove fields. Do NOT change the order.`;

export function buildFeedbackUserContent(
  counterpart: string,
  situation: string,
  formattedTranscript: string,
  facts: DeterministicFacts
): string {
  return `COUNTERPART BEING SPOKEN TO: ${counterpart}

WHAT THE USER NEEDED TO COMMUNICATE: ${situation}

MEASURED FACTS (counted by the app, not by you — treat as ground truth):
- Silence offered to the USER: ${facts.silenceOffered} time(s)
- Of those, the USER filled the silence instead of waiting: ${facts.silenceFilled} time(s)
- Hedging phrases counted in the USER's words: ${facts.hedgeCount}
${facts.hedgeTopPhrase ? `- Most repeated hedge: "${facts.hedgeTopPhrase}" (${facts.hedgeTopCount} times)` : ""}

FULL TRANSCRIPT:
${formattedTranscript}

Score now. Return only the JSON object.`;
}

export function validateFeedbackResponse(raw: unknown): FeedbackResponse | null {
  if (typeof raw !== "object" || raw === null) return null;
  const obj = raw as Record<string, unknown>;

  if (obj.schema_version !== 2) return null;
  if (!Array.isArray(obj.criteria) || obj.criteria.length !== 4) return null;

  const expectedIds: CriterionId[] = [
    "specific_behaviour",
    "concrete_impact",
    "left_silence",
    "held_point",
  ];

  const criteria = (obj.criteria as any[]).map((c, i) => {
    if (!c || c.id !== expectedIds[i]) return null;
    const score = c.score;
    if (score !== 0 && score !== 1 && score !== 2) return null;
    return {
      id: c.id as CriterionId,
      score: score as 0 | 1 | 2,
      trigger_line: typeof c.trigger_line === "string" ? c.trigger_line : "",
      note: typeof c.note === "string" ? c.note : "",
    };
  });

  if (criteria.some((c) => c === null)) return null;

  return {
    schema_version: 2,
    criteria: criteria as FeedbackResponse["criteria"],
    overall:
      typeof obj.overall === "string" ? (obj.overall as string).slice(0, 120) : "",
    strongest_line:
      typeof obj.strongest_line === "string"
        ? (obj.strongest_line as string).slice(0, 300)
        : "",
  };
}
