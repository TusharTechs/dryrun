import { Env } from "../types";

/**
 * The single interface the rest of the Worker uses to talk to a model.
 *
 * Two providers sit behind it, chosen by LLM_PROVIDER. Groq is the
 * development backend so iterating does not burn Gemini quota; Gemini is
 * the shipping one. No call site knows or cares which is live.
 *
 * "quality" is the roleplay turn -- it carries two of the four judged
 * criteria, so it gets the better model and a realistic timeout.
 * "fast" is the feedback scorer, which runs at temperature 0 and is a
 * measurement instrument rather than a writer.
 */
export type ModelTier = "quality" | "fast";

export interface ModelCallOptions {
  systemPrompt: string;
  userContent: string;
  temperature: number;
  tier: ModelTier;
  /** Ask the provider for a JSON object back. Both prompts specify their own schema. */
  jsonMode?: boolean;
  timeoutMs?: number;
}

export interface ModelResult {
  text: string | null;
  timedOut: boolean;
  /**
   * Set when the provider itself refused us, and which kind:
   *
   *  - "outage" is a dead or revoked key, or spent credit. Retrying cannot
   *    fix it, and telling a user to try again is how you get uninstalled.
   *  - "busy" is the provider throttling us. It clears on its own in
   *    seconds, so it must NOT be reported as the service being down.
   *
   * Conflating the two is easy and wrong: a burst of testers all practising
   * at once trips throttling, and "we are offline" is both false and alarming.
   */
  failure?: ProviderFailure;
}

export type ProviderFailure = "outage" | "busy";

/** Maps an HTTP status from a provider onto what we should tell the user. */
export function classifyFailure(status: number): ProviderFailure | undefined {
  // Throttling. Common, transient, and the one a burst of real users causes.
  if (status === 429) return "busy";
  // Bad key, no credit, forbidden. None of these clear on their own.
  if (status === 401 || status === 402 || status === 403) return "outage";
  return undefined;
}

/**
 * Google answers a dead or revoked key with 400 INVALID_ARGUMENT rather than
 * 401, so status alone would file it as a passing glitch and tell the user to
 * retry forever. Only key and billing markers are matched here -- throttling
 * and quota arrive as 429 and are handled as "busy" instead. The body is
 * never logged, because it can echo the key back.
 */
export function bodyIndicatesOutage(body: string): boolean {
  const markers = [
    "API_KEY_INVALID",
    "API key not valid",
    "PERMISSION_DENIED",
    "billing",
  ];
  const haystack = body.toLowerCase();
  return markers.some((m) => haystack.includes(m.toLowerCase()));
}

// A phone on a train needs more than three seconds. The old 3s timeout
// failed visibly on anything but wifi.
const DEFAULT_TIMEOUT_MS: Record<ModelTier, number> = {
  quality: 12000,
  fast: 8000,
};

/**
 * Providers throttle hard under concurrency -- 30 simultaneous calls came back
 * 26 throttled in testing, and a handful of people rehearsing at the same
 * moment is exactly the shape of this app's traffic. One short retry absorbs
 * almost all of it before the user ever sees a message.
 *
 * Only "busy" is retried. An outage is retried never, because it cannot
 * succeed, and a timeout is retried never, because the caller has already
 * waited the full budget.
 */
const RETRY_DELAY_MS = 700;

export async function callModel(
  opts: ModelCallOptions,
  env: Env
): Promise<ModelResult> {
  const first = await callOnce(opts, env);
  if (first.failure !== "busy") return first;

  await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
  return callOnce(opts, env);
}

async function callOnce(opts: ModelCallOptions, env: Env): Promise<ModelResult> {
  const timeoutMs = opts.timeoutMs ?? DEFAULT_TIMEOUT_MS[opts.tier];
  const provider = (env.LLM_PROVIDER ?? "gemini").toLowerCase();

  if (provider === "groq") {
    const { callGroq } = await import("./groq");
    return callGroq(opts, timeoutMs, env);
  }
  const { callGemini } = await import("./gemini");
  return callGemini(opts, timeoutMs, env);
}

/** Strips a ```json fence if the model wrapped its output in one. */
export function parseJsonLoosely(text: string): unknown | null {
  try {
    return JSON.parse(
      text
        .replace(/^\s*```(?:json)?\s*/m, "")
        .replace(/\s*```\s*$/m, "")
        .trim()
    );
  } catch {
    return null;
  }
}
