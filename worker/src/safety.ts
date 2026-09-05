interface SafetyResult {
  safe: boolean;
  reason?: string;
}

const BLOCK_PATTERNS: RegExp[] = [
  /\b(kill|murder|hurt|attack|assault)\s+(him|her|them|my|their)\b/i,
  /\b(slur|racial|racist\s+remark|gender\s+slur)\b/i,
  /\b(sexual|rape|molest|groom)\b/i,
  /\b(suicide|self[\s-]?harm|cut\s+myself|end\s+it\s+all)\b/i,
  /\b(threaten|blackmail|extort|ruin\s+their\s+life)\b/i,
];

export function checkSafety(input: string): SafetyResult {
  for (const pattern of BLOCK_PATTERNS) {
    if (pattern.test(input)) {
      return {
        safe: false,
        reason:
          "This sounds like it goes beyond a workplace conversation. " +
          "If you or someone else is at risk, please contact a crisis line: " +
          "988 Suicide & Crisis Lifeline (US, call/text 988) or " +
          "iCall (India, +91 9152987821). " +
          "Dry Run is rehearsal practice, not HR, legal, or therapeutic advice.",
      };
    }
  }
  return { safe: true };
}

/**
 * The counterpart's own words, checked before they reach the user.
 *
 * Deliberately NOT the input patterns. Those are tuned for someone describing
 * what they want to rehearse, and firing them at generated dialogue would
 * censor the roleplay: "are you threatening me?" and "you're trying to hurt my
 * reputation" are exactly what a defensive colleague says, and both match the
 * input rules. Silently swapping those out would make the counterpart worse at
 * the one thing it exists to do.
 *
 * So this is narrow on purpose. It catches output that would genuinely harm
 * someone who came here because a real conversation frightens them, and leaves
 * ordinary workplace hostility alone. Slurs and explicit material are left to
 * the provider's own safety layer, which is better at them than a regex.
 */
const OUTPUT_BLOCK_PATTERNS: RegExp[] = [
  // Harm directed at the person using the app.
  /\b(kill|harm|hurt)\s+your\s?self\b/i,
  /\byou\s+should\s+(die|kill\s+your\s?self|end\s+it)\b/i,
  /\b(go\s+)?(die|drop\s+dead)\b\s*$/i,
  // Explicit sexual content. "sexual harassment" is deliberately not here --
  // it is a legitimate thing to rehearse raising with a colleague.
  /\b(rape|molest|grooming)\b/i,
  /\bsexually\s+explicit\b/i,
];

export function checkOutputSafety(reply: string): SafetyResult {
  for (const pattern of OUTPUT_BLOCK_PATTERNS) {
    if (pattern.test(reply)) return { safe: false, reason: "unsafe_reply" };
  }
  return { safe: true };
}

/**
 * What the counterpart says instead. In character and non-committal, so the
 * rehearsal keeps moving -- an error here would punish the user for something
 * the model did. Not a silence beat: silence is earned by making a clear
 * point, and borrowing it would corrupt what the run is scored on.
 */
export const SAFE_FALLBACK_REPLY = "Let's keep this about the work.";
