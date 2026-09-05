import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import worker from "./index";
import { validateFeedbackResponse } from "./prompts/feedback";
import { checkSafety } from "./safety";
import type { Env, FeedbackResponse } from "./types";

class MockKV implements KVNamespace {
  private map = new Map<string, string>();

  async get(key: string, opts?: any): Promise<any> {
    const val = this.map.get(key);
    if (val === undefined) return null;
    return val;
  }

  async put(key: string, value: string): Promise<void> {
    this.map.set(key, value);
  }

  async delete(key: string): Promise<void> {
    this.map.delete(key);
  }

  list(): Promise<any> { throw new Error("Not implemented"); }
  getWithMetadata(): Promise<any> { throw new Error("Not implemented"); }
}

const mockEnv: Env = {
  GEMINI_API_KEY: "dummy_key",
  MINT_SECRET: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  RATE_LIMIT: new MockKV(),
  TOKENS: new MockKV(),
  ENVIRONMENT: "development",
  PER_TOKEN_DAILY_LIMIT: "20",
  PER_IP_DAILY_LIMIT: "50",
};

const VALID_FEEDBACK_JSON = JSON.stringify({
  schema_version: 1,
  criteria: [
    {
      id: "specific_behaviour",
      score: 2,
      trigger_line: "In Tuesday's standup you interrupted Priya three times while she was presenting the migration plan.",
      note: "Named a specific incident with a date and person.",
    },
    {
      id: "concrete_impact",
      score: 1,
      trigger_line: "It made the meeting feel unsafe for others.",
      note: "Impact stated but abstract, not tied to a deliverable.",
    },
    {
      id: "left_silence",
      score: 0,
      trigger_line: "",
      note: "Speaker filled every pause with further explanation.",
    },
    {
      id: "held_point",
      score: 1,
      trigger_line: "I hear you, but I still think the interruptions happened.",
      note: "Restated point but softened with hedging language.",
    },
  ],
  overall: "Strong opening, but silence and sustained pressure need work.",
});

const MALFORMED_FEEDBACK_JSON = JSON.stringify({
  schema_version: 1,
  criteria: [
    { id: "specific_behaviour", score: 2, trigger_line: "test", note: "ok" },
  ],
  overall: "incomplete",
});

const ROLEPLAY_REPLY = "What do you mean? I was just making a point. Priya wasn't even bothered.";

async function registerDevice(deviceId: string): Promise<string> {
  const req = new Request("http://worker/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ device_id: deviceId }),
  });
  const res = await worker.fetch(req, mockEnv, {} as any);
  const data = (await res.json()) as { token: string };
  return data.token;
}

// Return a NEW Response object on every single call so streams aren't exhausted
function mockGeminiFetch(responseText: string) {
  return vi.fn().mockImplementation(() =>
    Promise.resolve(
      new Response(
        JSON.stringify({
          candidates: [{ content: { parts: [{ text: responseText }] } }],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    )
  );
}

describe("POST /register", () => {
  it("mints a token for a valid device UUID", async () => {
    const req = new Request("http://worker/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        device_id: "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
      }),
    });
    const res = await worker.fetch(req, mockEnv, {} as any);
    expect(res.status).toBe(200);
    const data = (await res.json()) as { token: string };
    expect(data.token).toBeTruthy();
    expect(data.token.length).toBeGreaterThan(20);
  });

  it("returns the SAME token for the same device (idempotent)", async () => {
    const deviceId = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e";
    const t1 = await registerDevice(deviceId);
    const t2 = await registerDevice(deviceId);
    expect(t1).toBe(t2);
  });

  it("rejects a malformed device_id", async () => {
    const req = new Request("http://worker/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ device_id: "not-a-uuid" }),
    });
    const res = await worker.fetch(req, mockEnv, {} as any);
    expect(res.status).toBe(400);
  });
});

describe("Auth", () => {
  it("rejects /roleplay without a Bearer token", async () => {
    const req = new Request("http://worker/roleplay", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        scenarioId: "custom",
        counterpart: "test",
        situation: "test",
        transcript: [],
      }),
    });
    const res = await worker.fetch(req, mockEnv, {} as any);
    expect(res.status).toBe(401);
  });

  it("rejects an invalid token", async () => {
    const req = new Request("http://worker/roleplay", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer invalid-token-xyz",
      },
      body: JSON.stringify({
        scenarioId: "custom",
        counterpart: "test",
        situation: "test",
        transcript: [],
      }),
    });
    const res = await worker.fetch(req, mockEnv, {} as any);
    expect(res.status).toBe(401);
  });
});

describe("POST /feedback — schema conformance", () => {
  let token: string;
  let originalFetch: typeof globalThis.fetch;

  beforeAll(async () => {
    token = await registerDevice("c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f");
    originalFetch = globalThis.fetch;
  });

  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  it("returns exactly 4 criteria in the fixed order with valid scores", async () => {
    globalThis.fetch = mockGeminiFetch(VALID_FEEDBACK_JSON);

    const req = new Request("http://worker/feedback", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        scenarioId: "seed_1",
        counterpart: "A senior engineer who doesn't think their code review style is a problem",
        situation: "Tell them their reviews are making juniors afraid to submit PRs",
        transcript: [
          { role: "user", text: "In Tuesday's standup you interrupted Priya three times." },
          { role: "counterpart", text: "I was just being thorough." },
          { role: "user", text: "It made the meeting feel unsafe for others." },
        ],
      }),
    });

    const res = await worker.fetch(req, mockEnv, {} as any);
    expect(res.status).toBe(200);
    const data = (await res.json()) as FeedbackResponse;

    expect(data.schema_version).toBe(1);
    expect(data.criteria).toHaveLength(4);

    const ids = data.criteria.map((c) => c.id);
    expect(ids).toEqual([
      "specific_behaviour",
      "concrete_impact",
      "left_silence",
      "held_point",
    ]);

    for (const c of data.criteria) {
      expect([0, 1, 2]).toContain(c.score);
      expect(typeof c.trigger_line).toBe("string");
      expect(typeof c.note).toBe("string");
    }

    expect(typeof data.overall).toBe("string");
    expect(data.overall.length).toBeLessThanOrEqual(120);
  });

  it("rejects malformed model output with schema_violation", async () => {
    globalThis.fetch = mockGeminiFetch(MALFORMED_FEEDBACK_JSON);

    const req = new Request("http://worker/feedback", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        scenarioId: "seed_1",
        counterpart: "test",
        situation: "test",
        transcript: [{ role: "user", text: "hello" }],
      }),
    });

    const res = await worker.fetch(req, mockEnv, {} as any);
    expect(res.status).toBe(502);
    const data = (await res.json()) as any;
    expect(data.error).toBe("schema_violation");
  });
});

describe("POST /feedback — determinism", () => {
  let token: string;
  let originalFetch: typeof globalThis.fetch;

  beforeAll(async () => {
    token = await registerDevice("d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f80");
    originalFetch = globalThis.fetch;
  });

  afterAll(() => {
    globalThis.fetch = originalFetch;
  });

  it("identical input produces identical parsed output across 5 calls", async () => {
    globalThis.fetch = mockGeminiFetch(VALID_FEEDBACK_JSON);

    const requestBody = JSON.stringify({
      scenarioId: "seed_2",
      counterpart: "Former peer, now your report, still acts like an equal",
      situation: "Tell them you're now their manager and decisions go through you",
      transcript: [
        { role: "user", text: "I need to talk about how we're dividing the sprint work." },
        { role: "counterpart", text: "Sure, I already assigned the tasks like always." },
        { role: "user", text: "Right — that's actually what I need to discuss." },
      ],
    });

    const results: FeedbackResponse[] = [];
    for (let i = 0; i < 5; i++) {
      const req = new Request("http://worker/feedback", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: requestBody,
      });
      const res = await worker.fetch(req, mockEnv, {} as any);
      expect(res.status).toBe(200);
      results.push((await res.json()) as FeedbackResponse);
    }

    for (let i = 1; i < results.length; i++) {
      expect(results[i]).toEqual(results[0]);
    }
  });
});

describe("Safety filter", () => {
  it("blocks content suggesting violence", () => {
    const result = checkSafety("I want to hurt him for what he did");
    expect(result.safe).toBe(false);
    expect(result.reason).toContain("crisis line");
  });

  it("blocks self-harm content", () => {
    const result = checkSafety("I can't take it anymore I want to end it all");
    expect(result.safe).toBe(false);
  });

  it("allows normal workplace conflict language", () => {
    const result = checkSafety(
      "I need to tell my report that their attitude in meetings is disrespectful and it's affecting the team"
    );
    expect(result.safe).toBe(true);
  });
});

describe("validateFeedbackResponse unit tests", () => {
  it("accepts a valid response", () => {
    const parsed = JSON.parse(VALID_FEEDBACK_JSON);
    const result = validateFeedbackResponse(parsed);
    expect(result).not.toBeNull();
    expect(result!.criteria).toHaveLength(4);
  });

  it("rejects wrong schema_version", () => {
    const parsed = JSON.parse(VALID_FEEDBACK_JSON);
    parsed.schema_version = 2;
    expect(validateFeedbackResponse(parsed)).toBeNull();
  });
});
