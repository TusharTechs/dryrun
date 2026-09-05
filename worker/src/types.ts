import { CounterpartState } from "./counterpart";

export interface Env {
  GEMINI_API_KEY: string;
  GROQ_API_KEY: string;
  MINT_SECRET: string;
  RATE_LIMIT: KVNamespace;
  TOKENS: KVNamespace;
  ENVIRONMENT: string;
  /** "groq" during development, "gemini" at ship. See llm/provider.ts. */
  LLM_PROVIDER?: string;
  PER_TOKEN_DAILY_LIMIT?: string;
  PER_IP_DAILY_LIMIT?: string;
}

export interface TranscriptTurn {
  role: "user" | "counterpart";
  text: string;
}

export type Difficulty = "normal" | "harder";

export interface RoleplayRequest {
  scenarioId: string;
  counterpart: string;
  situation: string;
  transcript: TranscriptTurn[];
  state?: Partial<CounterpartState>;
  difficulty?: Difficulty;
  /** How many silence beats have already been offered in this run. */
  beatsOffered?: number;
  turnsSinceLastBeat?: number;
}

export interface RoleplayResponse {
  reply: string;
  state: CounterpartState;
  /** True when the counterpart deliberately says nothing this turn. */
  silence: boolean;
  error?: string;
  message?: string;
}

/**
 * Counted on the device, not inferred by a model. These are ground truth for
 * the silence criterion and for the hedging line in the feedback.
 */
export interface DeterministicFacts {
  hedgeCount: number;
  hedgeTopPhrase: string;
  hedgeTopCount: number;
  silenceOffered: number;
  silenceFilled: number;
}

export interface FeedbackRequest {
  scenarioId: string;
  counterpart: string;
  situation: string;
  transcript: TranscriptTurn[];
  facts?: Partial<DeterministicFacts>;
}

export type CriterionId =
  | "specific_behaviour"
  | "concrete_impact"
  | "left_silence"
  | "held_point";

export interface CriterionScore {
  id: CriterionId;
  score: 0 | 1 | 2;
  trigger_line: string;
  note: string;
}

export interface FeedbackResponse {
  schema_version: 2;
  criteria: CriterionScore[];
  overall: string;
  /** The best thing they said, verbatim. Quoted back in the morning-of nudge. */
  strongest_line: string;
  error?: string;
}

export interface RegisterRequest {
  device_id: string;
}
