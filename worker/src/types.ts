export interface Env {
  GEMINI_API_KEY: string;
  MINT_SECRET: string;
  RATE_LIMIT: KVNamespace;
  TOKENS: KVNamespace;
  ENVIRONMENT: string;
  PER_TOKEN_DAILY_LIMIT?: string;
  PER_IP_DAILY_LIMIT?: string;
}

export interface RoleplayRequest {
  scenarioId: string;
  counterpart: string;
  situation: string;
  transcript: TranscriptTurn[];
}

export interface TranscriptTurn {
  role: "user" | "counterpart";
  text: string;
}

export interface RoleplayResponse {
  reply: string;
  error?: string;
}

export interface FeedbackRequest {
  scenarioId: string;
  counterpart: string;
  situation: string;
  transcript: TranscriptTurn[];
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
  schema_version: 1;
  criteria: CriterionScore[];
  overall: string;
  error?: string;
}

export interface RegisterRequest {
  device_id: string;
}

export interface RegisterResponse {
  token: string;
}
