import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import worker from "./index";
import { validateFeedbackResponse } from "./prompts/feedback";
import { validateRoleplayOutput } from "./prompts/roleplay";
import { checkSafety } from "./safety";
import type { Env, FeedbackResponse, RoleplayResponse } from "./types";

class MockKV {
  private map = new Map<string, string>();
  async get(key: string): Promise<any> {
    return this.map.get(key) ?? null;
  }
  async put(key: string, value: string): Promise<void> {
    this.map.set(key, value);
  }
  async delete(key: string): Promise<void> {
    this.map.delete(key);
  }
  list(): Promise<any> {
    throw new Error("Not implemented");
  }
  getWithMetadata(): Promise<any> {
    throw new Error("Not implemented");
  }
}

const mockEnv: Env = {
  GEMINI_API_KEY: "dummy_key",
  GROQ_API_KEY: "dummy_key",
  MINT_SECRET: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  RATE_LIMIT: new MockKV() as unknown as KVNamespace,
  TOKENS: new MockKV() as unknown as KVNamespace,
  ENVIRONMENT: "test",
  LLM_PROVIDER: "groq",
  PER_TOKEN_DAILY_LIMIT: "200",
  PER_IP_DAILY_LIMIT: "500",
};

const VALID_FEEDBACK_JSON = JSON.stringify({
  schema_version: 2,
  criteria: [
    {
      id: "specific_behaviour",
      score: 2,
      trigger_line: "In Tuesday's standup you cut across Priya three times.",
      note: "Named a specific incident with a date and person.",
    },
    {
      id: "concrete_impact",
      score: 1,
      trigger_line: "It made the meeting harder for everyone else.",
      note: "Impact stated but abstract, not tied to a deliverable.",
    },
    {
      id: "left_silence",
      score: 0,
      trigger_line: "",
      note: "Filled both pauses instead of waiting.",
    },
    {
      id: "held_point",
      score: 1,
      trigger_line: "I hear you, but the interruptions still happened.",
      note: "Restated the point, then softened it away.",
    },
  ],
  overall: "Strong open. You talked through both silences.",
  strongest_line: "In Tuesday's standup you cut across Priya three times.",
});

const VALID_ROLEPLAY_JSON = JSON.stringify({
  reply: "I was being thorough. Priya didn't seem bothered.",
  state: { defensiveness: 4, feelsHeard: 0, conceded: false },
  userMadeClearPoint: true,
});

async function registerDevice(deviceId: string): Promise<string> {
  const res = await worker.fetch(
    new Request("http://worker/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ device_id: deviceId }),
    }),
    mockEnv
  );
  return ((await res.json()) as { token: string }).token;
}

/** A fresh Response every call, so streams are never exhausted. */
function mockModelFetch(responseText: string) {
  return vi.fn().mockImplementation(() =>
    Promise.resolve(
      new Response(
        JSON.stringify({ choices: [{ message: { content: responseText } }] }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    )
  );
}

function post(path: string, token: string, body: unknown): Request {
  return new Request(`http://worker${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
}

describe("POST /register", () => {
  it("mints a token for a valid device UUID", async () => {
    const res = await worker.fetch(
      new Request("http://worker/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ device_id: "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d" }),
      }),
      mockEnv
    );
    expect(res.status).toBe(200);
    const data = (await res.json()) as { token: string };
    expect(data.token.length).toBeGreaterThan(20);
  });

  it("returns the same token for the same device", async () => {
    const id = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e";
    expect(await registerDevice(id)).toBe(await registerDevice(id));
  });

  it("rejects a malformed device_id", async () => {
    const res = await worker.fetch(
      new Request("http://worker/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ device_id: "not-a-uuid" }),
      }),
      mockEnv
    );
    expect(res.status).toBe(400);
  });
});

describe("Auth", () => {
  it("rejects /roleplay without a Bearer token", async () => {
    const res = await worker.fetch(
      new Request("http://worker/roleplay", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ counterpart: "x", situation: "y", transcript: [] }),
      }),
      mockEnv
    );
    expect(res.status).toBe(401);
  });

  it("rejects an invalid token", async () => {
    const res = await worker.fetch(
      post("/roleplay", "invalid-token-xyz", {
        counterpart: "x",
        situation: "y",
        transcript: [],
      }),
      mockEnv
    );
    expect(res.status).toBe(401);
  });
});

describe("POST /roleplay", () => {
  let token: string;
  let originalFetch: typeof globalThis.fetch;

  beforeAll(async () => {
    token = await registerDevice("c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f");
    originalFetch = globalThis.fetch;
  });
  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  const body = (extra: Record<string, unknown> = {}) => ({
    scenarioId: "seed_1",
    counterpart: "A senior engineer who talks over people in reviews",
    situation: "Tell them their review style is making juniors afraid to post PRs",
    transcript: [
      { role: "user", text: "In Tuesday's standup you cut across Priya three times." },
      { role: "counterpart", text: "I was being thorough." },
      { role: "user", text: "It made the meeting harder for everyone else." },
    ],
    state: { defensiveness: 3, feelsHeard: 0, conceded: false },
    ...extra,
  });

  it("returns a reply and the updated state", async () => {
    globalThis.fetch = mockModelFetch(VALID_ROLEPLAY_JSON);
    const res = await worker.fetch(post("/roleplay", token, body()), mockEnv);
    expect(res.status).toBe(200);
    const data = (await res.json()) as RoleplayResponse;
    expect(data.state.defensiveness).toBe(4);
    expect(data.state.feelsHeard).toBe(0);
  });

  it("clamps a state jump the model tries to make", async () => {
    globalThis.fetch = mockModelFetch(
      JSON.stringify({
        reply: "You know what, you're completely right, I'm so sorry.",
        state: { defensiveness: 0, feelsHeard: 5, conceded: true },
        userMadeClearPoint: false,
      })
    );
    const res = await worker.fetch(
      post("/roleplay", token, body({ state: { defensiveness: 5, feelsHeard: 0, conceded: false } })),
      mockEnv
    );
    const data = (await res.json()) as RoleplayResponse;
    // Hostile to grateful in one turn is exactly what the state machine exists
    // to prevent, no matter what the model returns.
    expect(data.state.defensiveness).toBe(4);
    expect(data.state.feelsHeard).toBe(1);
  });

  it("returns real silence with an empty reply after a clear point", async () => {
    globalThis.fetch = mockModelFetch(VALID_ROLEPLAY_JSON);
    const res = await worker.fetch(
      post("/roleplay", token, body({ beatsOffered: 0, turnsSinceLastBeat: 99 })),
      mockEnv
    );
    const data = (await res.json()) as RoleplayResponse;
    expect(data.silence).toBe(true);
    expect(data.reply).toBe("");
  });

  it("does not offer silence once the cap is reached", async () => {
    globalThis.fetch = mockModelFetch(VALID_ROLEPLAY_JSON);
    const res = await worker.fetch(
      post("/roleplay", token, body({ beatsOffered: 2, turnsSinceLastBeat: 5 })),
      mockEnv
    );
    const data = (await res.json()) as RoleplayResponse;
    expect(data.silence).toBe(false);
    expect(data.reply.length).toBeGreaterThan(0);
  });

  it("blocks content that is no longer a workplace conversation", async () => {
    globalThis.fetch = mockModelFetch(VALID_ROLEPLAY_JSON);
    const res = await worker.fetch(
      post("/roleplay", token, body({ situation: "I want to hurt him for this" })),
      mockEnv
    );
    expect(res.status).toBe(422);
    const data = (await res.json()) as any;
    expect(data.message).toContain("crisis line");
  });

  it("survives model output that is not JSON", async () => {
    globalThis.fetch = mockModelFetch("I'm sorry, I can't help with that.");
    const res = await worker.fetch(post("/roleplay", token, body()), mockEnv);
    expect(res.status).toBe(502);
    const data = (await res.json()) as RoleplayResponse;
    // The caller still gets a usable state back rather than undefined.
    expect(data.state).toBeDefined();
  });
});

describe("POST /feedback", () => {
  let token: string;
  let originalFetch: typeof globalThis.fetch;

  beforeAll(async () => {
    token = await registerDevice("d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f80");
    originalFetch = globalThis.fetch;
  });
  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  const body = {
    scenarioId: "seed_1",
    counterpart: "A senior engineer who talks over people",
    situation: "Tell them their review style is a problem",
    transcript: [
      { role: "user", text: "In Tuesday's standup you cut across Priya three times." },
      { role: "counterpart", text: "I was being thorough." },
    ],
    facts: {
      hedgeCount: 4,
      hedgeTopPhrase: "just",
      hedgeTopCount: 3,
      silenceOffered: 2,
      silenceFilled: 2,
    },
  };

  it("returns exactly four criteria in the fixed order", async () => {
    globalThis.fetch = mockModelFetch(VALID_FEEDBACK_JSON);
    const res = await worker.fetch(post("/feedback", token, body), mockEnv);
    expect(res.status).toBe(200);
    const data = (await res.json()) as FeedbackResponse;

    expect(data.schema_version).toBe(2);
    expect(data.criteria.map((c) => c.id)).toEqual([
      "specific_behaviour",
      "concrete_impact",
      "left_silence",
      "held_point",
    ]);
    for (const c of data.criteria) expect([0, 1, 2]).toContain(c.score);
    expect(data.overall.length).toBeLessThanOrEqual(120);
    expect(data.strongest_line.length).toBeGreaterThan(0);
  });

  it("passes the measured facts to the model as ground truth", async () => {
    const spy = mockModelFetch(VALID_FEEDBACK_JSON);
    globalThis.fetch = spy;
    await worker.fetch(post("/feedback", token, body), mockEnv);

    const sent = JSON.parse((spy.mock.calls[0][1] as RequestInit).body as string);
    const prompt = sent.messages.map((m: any) => m.content).join("\n");
    expect(prompt).toContain("Silence offered to the USER: 2");
    expect(prompt).toContain("the USER filled the silence instead of waiting: 2");
    expect(prompt).toContain('Most repeated hedge: "just" (3 times)');
  });

  it("rejects malformed model output rather than passing it through", async () => {
    globalThis.fetch = mockModelFetch(
      JSON.stringify({ schema_version: 2, criteria: [{ id: "specific_behaviour" }], overall: "x" })
    );
    const res = await worker.fetch(post("/feedback", token, body), mockEnv);
    expect(res.status).toBe(502);
    expect(((await res.json()) as any).error).toBe("schema_violation");
  });

  it("rejects the old schema version", async () => {
    globalThis.fetch = mockModelFetch(
      VALID_FEEDBACK_JSON.replace('"schema_version":2', '"schema_version":1')
    );
    const res = await worker.fetch(post("/feedback", token, body), mockEnv);
    expect(res.status).toBe(502);
  });

  it("identical input produces identical output across five calls", async () => {
    globalThis.fetch = mockModelFetch(VALID_FEEDBACK_JSON);
    const results: FeedbackResponse[] = [];
    for (let i = 0; i < 5; i++) {
      const res = await worker.fetch(post("/feedback", token, body), mockEnv);
      results.push((await res.json()) as FeedbackResponse);
    }
    for (const r of results) expect(r).toEqual(results[0]);
  });
});

describe("Safety filter", () => {
  it("blocks content suggesting violence", () => {
    const result = checkSafety("I want to hurt him for what he did");
    expect(result.safe).toBe(false);
    expect(result.reason).toContain("crisis line");
  });

  it("blocks self-harm content", () => {
    expect(checkSafety("I can't take it anymore I want to end it all").safe).toBe(false);
  });

  it("allows ordinary workplace conflict", () => {
    expect(
      checkSafety(
        "I need to tell my report their attitude in meetings is disrespectful"
      ).safe
    ).toBe(true);
  });
});

describe("validators", () => {
  it("accepts a valid feedback response", () => {
    const result = validateFeedbackResponse(JSON.parse(VALID_FEEDBACK_JSON));
    expect(result?.criteria).toHaveLength(4);
  });

  it("rejects a wrong schema version", () => {
    const parsed = JSON.parse(VALID_FEEDBACK_JSON);
    parsed.schema_version = 1;
    expect(validateFeedbackResponse(parsed)).toBeNull();
  });

  it("rejects roleplay output with no reply", () => {
    expect(validateRoleplayOutput({ state: {} })).toBeNull();
    expect(validateRoleplayOutput(null)).toBeNull();
    expect(validateRoleplayOutput("nope")).toBeNull();
  });
});
