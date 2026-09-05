import { Env, TranscriptTurn } from "./types";

const GEMINI_URL =
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

const TIMEOUT_MS = 3000;

interface GeminiCallOptions {
  systemPrompt: string;
  userContent: string;
  temperature: number;
  timeoutMs?: number;
}

interface GeminiResult {
  text: string | null;
  timedOut: boolean;
}

export async function callGemini(
  opts: GeminiCallOptions,
  env: Env
): Promise<GeminiResult> {
  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    opts.timeoutMs ?? TIMEOUT_MS
  );

  try {
    const res = await fetch(`${GEMINI_URL}?key=${env.GEMINI_API_KEY}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal: controller.signal,
      body: JSON.stringify({
        system_instruction: { parts: [{ text: opts.systemPrompt }] },
        contents: [{ role: "user", parts: [{ text: opts.userContent }] }],
        generationConfig: {
          temperature: opts.temperature,
          maxOutputTokens: 1024,
          ...(opts.temperature === 0
            ? { responseMimeType: "application/json" }
            : {}),
        },
      }),
    });

    if (!res.ok) {
      const body = await res.text();
      console.error(`Gemini API error ${res.status}: ${body}`);
      return { text: null, timedOut: false };
    }

    const data = (await res.json()) as any;
    const text: string | undefined =
      data?.candidates?.[0]?.content?.parts?.[0]?.text;

    return { text: text ?? null, timedOut: false };
  } catch (err: any) {
    if (err?.name === "AbortError") {
      return { text: null, timedOut: true };
    }
    console.error("Gemini fetch error:", err);
    return { text: null, timedOut: false };
  } finally {
    clearTimeout(timer);
  }
}

export function formatTranscript(transcript: TranscriptTurn[]): string {
  return transcript
    .map((t) =>
      t.role === "user" ? `USER: ${t.text}` : `COUNTERPART: ${t.text}`
    )
    .join("\n\n");
}
