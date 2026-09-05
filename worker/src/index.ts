import { Env, RoleplayRequest, FeedbackRequest, RegisterRequest } from "./types";
import { mintToken, verifyToken } from "./auth";
import { checkRateLimit } from "./ratelimit";
import { checkSafety } from "./safety";
import { callGemini, formatTranscript } from "./gemini";
import { buildRoleplaySystemPrompt } from "./prompts/roleplay";
import {
  FEEDBACK_SYSTEM_PROMPT,
  buildFeedbackUserContent,
  validateFeedbackResponse,
} from "./prompts/feedback";

export default {
  async fetch(
    request: Request,
    env: Env,
    ctx: ExecutionContext
  ): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      switch (path) {
        case "/register":
          return handleRegister(request, env);
        case "/roleplay":
          return handleRoleplay(request, env);
        case "/feedback":
          return handleFeedback(request, env);
        default:
          return json({ error: "not found" }, 404);
      }
    } catch (err: any) {
      console.error("Unhandled error:", err);
      return json({ error: "internal error" }, 500);
    }
  },
};

async function handleRegister(
  request: Request,
  env: Env
): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

  const body = (await request.json()) as RegisterRequest;
  if (!body.device_id || typeof body.device_id !== "string" || body.device_id.length < 8) {
    return json({ error: "invalid device_id" }, 400);
  }

  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.device_id)) {
    return json({ error: "device_id must be a UUID v4" }, 400);
  }

  const token = await mintToken(body.device_id, env);
  return json({ token });
}

async function handleRoleplay(
  request: Request,
  env: Env
): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

  const deviceId = await authenticate(request, env);
  if (!deviceId) return json({ error: "unauthorized" }, 401);

  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const rl = await checkRateLimit(deviceId, ip, env);
  if (!rl.allowed) {
    return json(
      { error: "rate limited", retry_after_hours: 24 },
      429,
      { "X-RateLimit-Remaining": "0" }
    );
  }

  const body = (await request.json()) as RoleplayRequest;
  if (!body.counterpart || !body.situation || !Array.isArray(body.transcript)) {
    return json({ error: "missing required fields" }, 400);
  }

  const latestUserMsg =
    body.transcript.filter((t) => t.role === "user").slice(-1)[0]?.text ?? "";
  const safetyInput = `${body.situation} ${latestUserMsg}`;
  const safety = checkSafety(safetyInput);
  if (!safety.safe) {
    return json({ error: "content_blocked", message: safety.reason }, 422);
  }

  const systemPrompt = buildRoleplaySystemPrompt(body.counterpart, body.situation);
  const userContent = formatTranscript(body.transcript) +
    (body.transcript.length === 0
      ? "\n\n(The USER has not spoken yet. Wait for their first line. Respond with a short, in-character opening that shows you're in the room — a greeting, a wary 'what's up?', silence. One line max.)"
      : "\n\nRespond as the counterpart. One to three sentences.");

  const result = await callGemini(
    { systemPrompt, userContent, temperature: 0.7 },
    env
  );

  if (result.timedOut) {
    return json({
      reply: "",
      error: "timeout",
      message: "Connection hiccup — your words didn't land. Try again.",
    }, 504);
  }

  if (!result.text) {
    return json({
      reply: "",
      error: "model_error",
      message: "Something went wrong on our end. Try again in a few seconds.",
    }, 502);
  }

  return json({ reply: result.text.trim() });
}

async function handleFeedback(
  request: Request,
  env: Env
): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

  const deviceId = await authenticate(request, env);
  if (!deviceId) return json({ error: "unauthorized" }, 401);

  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const rl = await checkRateLimit(deviceId, ip, env);
  if (!rl.allowed) {
    return json({ error: "rate limited", retry_after_hours: 24 }, 429);
  }

  const body = (await request.json()) as FeedbackRequest;
  if (!body.counterpart || !body.situation || !Array.isArray(body.transcript)) {
    return json({ error: "missing required fields" }, 400);
  }

  if (body.transcript.length === 0) {
    return json({ error: "empty transcript" }, 400);
  }

  const formatted = formatTranscript(body.transcript);
  const userContent = buildFeedbackUserContent(
    body.counterpart,
    body.situation,
    formatted
  );

  const result = await callGemini(
    {
      systemPrompt: FEEDBACK_SYSTEM_PROMPT,
      userContent,
      temperature: 0,
      timeoutMs: 6000,
    },
    env
  );

  if (result.timedOut) {
    return json({
      schema_version: 1,
      criteria: [],
      overall: "",
      error: "timeout",
    }, 504);
  }

  if (!result.text) {
    return json({
      schema_version: 1,
      criteria: [],
      overall: "",
      error: "model_error",
    }, 502);
  }

  let parsed: unknown;
  try {
    const cleaned = result.text
      .replace(/^```(?:json)?\s*/m, "")
      .replace(/\s*```$/m, "")
      .trim();
    parsed = JSON.parse(cleaned);
  } catch {
    return json({
      schema_version: 1,
      criteria: [],
      overall: "",
      error: "parse_error",
    }, 502);
  }

  const validated = validateFeedbackResponse(parsed);
  if (!validated) {
    return json({
      schema_version: 1,
      criteria: [],
      overall: "",
      error: "schema_violation",
    }, 502);
  }

  return json(validated);
}

async function authenticate(
  request: Request,
  env: Env
): Promise<string | null> {
  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return null;
  const token = authHeader.slice(7);
  return verifyToken(token, env);
}

function json(
  body: unknown,
  status = 200,
  extraHeaders: Record<string, string> = {}
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      ...extraHeaders,
    },
  });
}
