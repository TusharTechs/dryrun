/**
 * The counterpart's hidden state.
 *
 * The roleplay prompt says "soften only gradually, and only if they earn it",
 * but a model has no memory of how far it has already softened, so across a
 * long exchange it drifts -- hostile on turn three, grateful on turn four.
 *
 * Carrying three numbers through every turn and clamping how fast they may
 * move makes the softening governed by state instead of mood. It also makes
 * "held_point" testable: caving has to raise defensiveness, not lower it.
 */
export interface CounterpartState {
  /** 0 open, 5 hostile. */
  defensiveness: number;
  /** 0 dismissed, 5 genuinely heard. */
  feelsHeard: number;
  /** They have accepted the point. One-way: it cannot be taken back. */
  conceded: boolean;
}

export const INITIAL_STATE: CounterpartState = {
  defensiveness: 3,
  feelsHeard: 0,
  conceded: false,
};

/** No value may move more than this in a single turn. */
export const MAX_STEP = 1;

const MIN = 0;
const MAX = 5;

function clampToRange(value: number): number {
  if (!Number.isFinite(value)) return MIN;
  return Math.min(MAX, Math.max(MIN, Math.round(value)));
}

function stepToward(current: number, proposed: number): number {
  const target = clampToRange(proposed);
  const delta = target - current;
  if (Math.abs(delta) <= MAX_STEP) return target;
  return current + Math.sign(delta) * MAX_STEP;
}

/**
 * Applies the model's proposed state on top of the current one, refusing any
 * jump bigger than one step. A counterpart cannot go from hostile to grateful
 * because the model felt like wrapping the scene up.
 */
export function applyStateUpdate(
  current: CounterpartState,
  proposed: Partial<CounterpartState> | undefined | null
): CounterpartState {
  if (!proposed || typeof proposed !== "object") return current;

  // Number.isFinite, not typeof: NaN is a number, and treating it as one
  // let a malformed model value quietly drag the state down a step.
  return {
    defensiveness: stepToward(
      current.defensiveness,
      Number.isFinite(proposed.defensiveness)
        ? (proposed.defensiveness as number)
        : current.defensiveness
    ),
    feelsHeard: stepToward(
      current.feelsHeard,
      Number.isFinite(proposed.feelsHeard)
        ? (proposed.feelsHeard as number)
        : current.feelsHeard
    ),
    // Conceding is one-way. Un-conceding mid-argument reads as a bug.
    conceded: current.conceded || proposed.conceded === true,
  };
}

export function sanitiseIncomingState(
  raw: Partial<CounterpartState> | undefined | null
): CounterpartState {
  if (!raw || typeof raw !== "object") return { ...INITIAL_STATE };
  return {
    defensiveness: Number.isFinite(raw.defensiveness)
      ? clampToRange(raw.defensiveness as number)
      : INITIAL_STATE.defensiveness,
    feelsHeard: Number.isFinite(raw.feelsHeard)
      ? clampToRange(raw.feelsHeard as number)
      : INITIAL_STATE.feelsHeard,
    conceded: raw.conceded === true,
  };
}

/**
 * Whether to hand the user real silence this turn.
 *
 * Text chat has no silence, so "left_silence" was being scored against
 * something that never happened. Here the counterpart genuinely returns
 * nothing and the client shows dead air. Guardrails are deterministic so the
 * beat cannot land twice in a row or dominate a short conversation.
 */
export interface SilenceInput {
  userMadeClearPoint: boolean;
  userTurnCount: number;
  beatsAlreadyOffered: number;
  turnsSinceLastBeat: number;
}

export const MAX_SILENCE_BEATS = 2;
const MIN_USER_TURNS_BEFORE_BEAT = 2;
const MIN_TURNS_BETWEEN_BEATS = 2;

export function shouldOfferSilence(input: SilenceInput): boolean {
  if (!input.userMadeClearPoint) return false;
  if (input.userTurnCount < MIN_USER_TURNS_BEFORE_BEAT) return false;
  if (input.beatsAlreadyOffered >= MAX_SILENCE_BEATS) return false;
  if (
    input.beatsAlreadyOffered > 0 &&
    input.turnsSinceLastBeat < MIN_TURNS_BETWEEN_BEATS
  ) {
    return false;
  }
  return true;
}
