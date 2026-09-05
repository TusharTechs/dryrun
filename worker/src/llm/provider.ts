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
}

// A phone on a train needs more than three seconds. The old 3s timeout
// failed visibly on anything but wifi.
const DEFAULT_TIMEOUT_MS: Record<ModelTier, number> = {
  quality: 12000,
  fast: 8000,
};

export async function callModel(
  opts: ModelCallOptions,
  env: Env
): Promise<ModelResult> {
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
