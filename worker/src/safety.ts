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
