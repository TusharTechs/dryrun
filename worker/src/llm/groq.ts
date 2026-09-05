import { Env } from "../types";
import { ModelCallOptions, ModelResult, ModelTier } from "./provider";

// DEVELOPMENT ONLY. This whole file is deleted before store submission --
// see the removal step in the build plan. Nothing else imports it directly.

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

const MODELS: Record<ModelTier, string> = {
  quality: "openai/gpt-oss-120b",
  fast: "openai/gpt-oss-20b",
};

// These are reasoning models and their reasoning tokens count against
// max_completion_tokens. At 1024 the scorer spent the whole budget thinking
// and returned an empty string, which surfaced as a model_error on every
// single feedback call. Give it room, and keep the reasoning shallow so the
// latency stays where a chat turn needs it.
const MAX_TOKENS: Record<ModelTier, number> = {
  quality: 2048,
  fast: 4096,
};

export async function callGroq(
  opts: ModelCallOptions,
  timeoutMs: number,
  env: Env
): Promise<ModelResult> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(GROQ_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${env.GROQ_API_KEY}`,
      },
      signal: controller.signal,
      body: JSON.stringify({
        model: MODELS[opts.tier],
        temperature: opts.temperature,
        max_completion_tokens: MAX_TOKENS[opts.tier],
        reasoning_effort: "low",
        messages: [
          { role: "system", content: opts.systemPrompt },
          { role: "user", content: opts.userContent },
        ],
        ...(opts.jsonMode ? { response_format: { type: "json_object" } } : {}),
      }),
    });

    if (!res.ok) {
      console.error(`Groq error ${res.status}`);
      return { text: null, timedOut: false };
    }

    const data = (await res.json()) as any;
    const text: string | undefined = data?.choices?.[0]?.message?.content;
    return { text: text ?? null, timedOut: false };
  } catch (err: any) {
    if (err?.name === "AbortError") return { text: null, timedOut: true };
    console.error("Groq fetch error:", err);
    return { text: null, timedOut: false };
  } finally {
    clearTimeout(timer);
  }
}
