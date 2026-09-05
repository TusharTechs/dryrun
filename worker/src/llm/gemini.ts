import { Env } from "../types";
import { ModelCallOptions, ModelResult, ModelTier } from "./provider";

const BASE = "https://generativelanguage.googleapis.com/v1beta/models";

// The roleplay turn carries "realistic scenarios" and half of "quality of
// feedback", so it does not run on the cheapest model. Scoring does: it runs
// at temperature 0 against a fixed schema, where cheap and deterministic is
// exactly what is wanted.
const MODELS: Record<ModelTier, string> = {
  quality: "gemini-2.5-flash",
  fast: "gemini-2.5-flash-lite",
};

export async function callGemini(
  opts: ModelCallOptions,
  timeoutMs: number,
  env: Env
): Promise<ModelResult> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(
      `${BASE}/${MODELS[opts.tier]}:generateContent?key=${env.GEMINI_API_KEY}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify({
          system_instruction: { parts: [{ text: opts.systemPrompt }] },
          contents: [{ role: "user", parts: [{ text: opts.userContent }] }],
          generationConfig: {
            temperature: opts.temperature,
            maxOutputTokens: 1024,
            ...(opts.jsonMode ? { responseMimeType: "application/json" } : {}),
          },
        }),
      }
    );

    if (!res.ok) {
      console.error(`Gemini error ${res.status}`);
      return { text: null, timedOut: false };
    }

    const data = (await res.json()) as any;
    const text: string | undefined =
      data?.candidates?.[0]?.content?.parts?.[0]?.text;
    return { text: text ?? null, timedOut: false };
  } catch (err: any) {
    if (err?.name === "AbortError") return { text: null, timedOut: true };
    console.error("Gemini fetch error:", err);
    return { text: null, timedOut: false };
  } finally {
    clearTimeout(timer);
  }
}
