import {
  Env,
  RoleplayRequest,
  FeedbackRequest,
  RegisterRequest,
  DeterministicFacts,
  Difficulty,
} from "./types";
import { mintToken, verifyToken } from "./auth";
import { checkRateLimit } from "./ratelimit";
import { checkSafety } from "./safety";
import { callModel, parseJsonLoosely } from "./llm/provider";
import { formatTranscript } from "./transcript";
import {
  applyStateUpdate,
  sanitiseIncomingState,
  shouldOfferSilence,
} from "./counterpart";
import {
  buildRoleplaySystemPrompt,
  validateRoleplayOutput,
} from "./prompts/roleplay";
import {
  FEEDBACK_SYSTEM_PROMPT,
  buildFeedbackUserContent,
  validateFeedbackResponse,
} from "./prompts/feedback";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    }

    const path = new URL(request.url).pathname;

    // Every handler is awaited inside the try. Returning the promise instead
    // would leave the try block before it settles, so a rejection escaped the
    // catch entirely and surfaced as a Cloudflare 1101 page rather than the
    // JSON the app knows how to read.
    try {
      switch (path) {
        case "/health":
          return json({ ok: true, provider: env.LLM_PROVIDER ?? "gemini" });
        case "/register":
          return await handleRegister(request, env);
        case "/roleplay":
          return await handleRoleplay(request, env);
        case "/feedback":
          return await handleFeedback(request, env);
        default:
          return json({ error: "not found" }, 404);
      }
    } catch (err) {
      console.error("Unhandled error:", err);
      return json({ error: "internal error" }, 500);
    }
  },
};

async function handleRegister(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

  const body = (await request.json()) as RegisterRequest;
  if (!body.device_id || typeof body.device_id !== "string") {
    return json({ error: "invalid device_id" }, 400);
  }
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      body.device_id
    )
  ) {
    return json({ error: "device_id must be a UUID v4" }, 400);
  }

  return json({ token: await mintToken(body.device_id, env) });
}

async function handleRoleplay(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

  const deviceId = await authenticate(request, env);
  if (!deviceId) return json({ error: "unauthorized" }, 401);

  const rl = await checkRateLimit(deviceId, clientIp(request), env);
  if (!rl.allowed) {
    return json({ error: "rate limited", retry_after_hours: 24 }, 429);
  }

  const body = (await request.json()) as RoleplayRequest;
  if (!body.counterpart || !body.situation || !Array.isArray(body.transcript)) {
    return json({ error: "missing required fields" }, 400);
  }

  const state = sanitiseIncomingState(body.state);

  const latestUserMsg =
    body.transcript.filter((t) => t.role === "user").slice(-1)[0]?.text ?? "";
  const safety = checkSafety(`${body.situation} ${latestUserMsg}`);
  if (!safety.safe) {
    return json({ error: "content_blocked", message: safety.reason }, 422);
  }

  const difficulty: Difficulty = body.difficulty === "harder" ? "harder" : "normal";
  const systemPrompt = buildRoleplaySystemPrompt(
    body.counterpart,
    body.situation,
    state,
    difficulty
  );

  const userContent =
    body.transcript.length === 0
      ? "(The USER has not spoken yet. Wait for their first line. Respond with a short, " +
        "in-character opening that shows you're in the room — a greeting, a wary " +
        "'what's up?'. One line max.)"
      : `${formatTranscript(body.transcript)}\n\nRespond as the counterpart. One to three sentences.`;

  const result = await callModel(
    { systemPrompt, userContent, temperature: 0.7, tier: "quality", jsonMode: true },
    env
  );

  if (result.timedOut) {
    return json(
      {
        reply: "",
        state,
        silence: false,
        error: "timeout",
        message: "Connection hiccup — your words didn't land. Try again.",
      },
      504
    );
  }

  // Throttled upstream. Clears on its own, so ask for a moment rather than
  // announcing an outage -- a burst of people practising at once causes this.
  if (result.failure === "busy") {
    return json(
      {
        reply: "",
        state,
        silence: false,
        error: "service_busy",
        message:
          "A lot of people are practising right now. Give it a few seconds and say that again.",
      },
      503
    );
  }

  // A dead key or spent credit. Retrying cannot fix it, so say so plainly
  // rather than sending the user round a loop that never succeeds.
  if (result.failure === "outage") {
    return json(
      {
        reply: "",
        state,
        silence: false,
        error: "service_unavailable",
        message:
          "DryRun's practice partner is offline right now. This is our problem, not yours — nothing you wrote is lost.",
      },
      503
    );
  }

  const parsed = result.text ? validateRoleplayOutput(parseJsonLoosely(result.text)) : null;
  if (!parsed) {
    return json(
      {
        reply: "",
        state,
        silence: false,
        error: "model_error",
        message: "Something went wrong on our end. Try again in a few seconds.",
      },
      502
    );
  }

  // The model proposes; the state machine disposes. No jump bigger than one
  // step, so a hostile counterpart cannot turn grateful in a single turn.
  const nextState = applyStateUpdate(state, parsed.state);

  const userTurnCount = body.transcript.filter((t) => t.role === "user").length;
  const silence = shouldOfferSilence({
    userMadeClearPoint: parsed.userMadeClearPoint === true,
    userTurnCount,
    beatsAlreadyOffered: body.beatsOffered ?? 0,
    turnsSinceLastBeat: body.turnsSinceLastBeat ?? 99,
  });

  return json({
    reply: silence ? "" : parsed.reply.trim(),
    state: nextState,
    silence,
  });
}

async function handleFeedback(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return json({ error: "method not allowed" }, 405);

  const deviceId = await authenticate(request, env);
  if (!deviceId) return json({ error: "unauthorized" }, 401);

  const rl = await checkRateLimit(deviceId, clientIp(request), env);
  if (!rl.allowed) {
    return json({ error: "rate limited", retry_after_hours: 24 }, 429);
  }

  const body = (await request.json()) as FeedbackRequest;
  if (!body.counterpart || !body.situation || !Array.isArray(body.transcript)) {
    return json({ error: "missing required fields" }, 400);
  }
  if (body.transcript.length === 0) return json({ error: "empty transcript" }, 400);

  const facts: DeterministicFacts = {
    hedgeCount: body.facts?.hedgeCount ?? 0,
    hedgeTopPhrase: body.facts?.hedgeTopPhrase ?? "",
    hedgeTopCount: body.facts?.hedgeTopCount ?? 0,
    silenceOffered: body.facts?.silenceOffered ?? 0,
    silenceFilled: body.facts?.silenceFilled ?? 0,
  };

  const result = await callModel(
    {
      systemPrompt: FEEDBACK_SYSTEM_PROMPT,
      userContent: buildFeedbackUserContent(
        body.counterpart,
        body.situation,
        formatTranscript(body.transcript),
        facts
      ),
      temperature: 0,
      tier: "fast",
      jsonMode: true,
    },
    env
  );

  if (result.timedOut) return feedbackError("timeout", 504);
  if (result.failure === "busy") return feedbackError("service_busy", 503);
  if (result.failure === "outage") return feedbackError("service_unavailable", 503);
  if (!result.text) return feedbackError("model_error", 502);

  const parsed = parseJsonLoosely(result.text);
  if (parsed === null) return feedbackError("parse_error", 502);

  const validated = validateFeedbackResponse(parsed);
  if (!validated) return feedbackError("schema_violation", 502);

  return json(validated);
}

function feedbackError(error: string, status: number): Response {
  return json(
    { schema_version: 2, criteria: [], overall: "", strongest_line: "", error },
    status
  );
}

async function authenticate(request: Request, env: Env): Promise<string | null> {
  const header = request.headers.get("Authorization");
  if (!header?.startsWith("Bearer ")) return null;
  return verifyToken(header.slice(7), env);
}

function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP") ?? "unknown";
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
