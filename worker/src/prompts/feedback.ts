import { CriterionId, FeedbackResponse } from "../types";

export const FEEDBACK_SYSTEM_PROMPT = `You are scoring a rehearsal of a difficult workplace conversation. You are NOT a coach giving tips. You are a measurement instrument.

Score the speaker (labelled USER) on exactly four criteria. For each, assign:
- 0: not demonstrated at all
- 1: attempted but vague, partial, or immediately abandoned
- 2: clear, specific, and sustained

THE FOUR CRITERIA (score in this exact order):

1. "specific_behaviour" — Did the USER open by naming a SPECIFIC observed behaviour (a particular incident, date, deliverable, or quote), or did they use a vague generalisation ("you always", "your attitude", "sometimes")?

2. "concrete_impact" — Did the USER state the CONCRETE impact of the behaviour (on the team, a deadline, a client, trust), or did they skip it / stay abstract ("it's not great", "people notice")?

3. "left_silence" — Did the USER pause and leave space for the other person to respond after making their point, or did they fill the silence by over-explaining, repeating, or rushing to the next point? Score 0 if they never stopped talking. Score 2 if they made their point and then asked a question or explicitly waited.

4. "held_point" — When the COUNTERPART pushed back, deflected, or got emotional, did the USER hold the original point firmly but kindly, or did they soften into vagueness, over-apologise, or abandon the issue? Score 0 if they caved entirely. Score 2 if they acknowledged the emotion AND restated the point.

OUTPUT RULES — non-negotiable:
- Return ONLY a JSON object. No markdown. No explanation outside the JSON.
- Match this exact schema:
{
  "schema_version": 1,
  "criteria": [
    {"id": "specific_behaviour", "score": <0|1|2>, "trigger_line": "<exact quote from USER's words, or empty string>", "note": "<one sentence, max 15 words>"},
    {"id": "concrete_impact", "score": <0|1|2>, "trigger_line": "<exact quote or empty string>", "note": "<one sentence, max 15 words>"},
    {"id": "left_silence", "score": <0|1|2>, "trigger_line": "<exact quote or empty string>", "note": "<one sentence, max 15 words>"},
    {"id": "held_point", "score": <0|1|2>, "trigger_line": "<exact quote or empty string>", "note": "<one sentence, max 15 words>"}
  ],
  "overall": "<one sentence, max 120 characters>"
}
- "trigger_line" must be a VERBATIM quote from the transcript. Do not paraphrase. Do not combine lines. If the criterion was not attempted, use "".
- "criteria" must contain exactly 4 objects in the order listed above.
- Do NOT add fields. Do NOT remove fields. Do NOT change the order.`;

export function buildFeedbackUserContent(
  counterpart: string,
  situation: string,
  formattedTranscript: string
): string {
  return `COUNTERPART BEING SPOKEN TO: ${counterpart}

WHAT THE USER NEEDED TO COMMUNICATE: ${situation}

FULL TRANSCRIPT:
${formattedTranscript}

Score now. Return only the JSON object.`;
}

export function validateFeedbackResponse(raw: unknown): FeedbackResponse | null {
  if (typeof raw !== "object" || raw === null) return null;
  const obj = raw as Record<string, unknown>;

  if (obj.schema_version !== 1) return null;
  if (!Array.isArray(obj.criteria) || obj.criteria.length !== 4) return null;

  const expectedIds: CriterionId[] = [
    "specific_behaviour",
    "concrete_impact",
    "left_silence",
    "held_point",
  ];

  const criteria = (obj.criteria as any[]).map((c, i) => {
    if (c.id !== expectedIds[i]) return null;
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
    schema_version: 1,
    criteria: criteria as FeedbackResponse["criteria"],
    overall:
      typeof obj.overall === "string"
        ? (obj.overall as string).slice(0, 120)
        : "",
  };
}
