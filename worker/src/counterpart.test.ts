import { describe, it, expect } from "vitest";
import {
  applyStateUpdate,
  sanitiseIncomingState,
  shouldOfferSilence,
  INITIAL_STATE,
  MAX_SILENCE_BEATS,
} from "./counterpart";

describe("counterpart state clamping", () => {
  it("refuses a jump from hostile to grateful in one turn", () => {
    const hostile = { defensiveness: 5, feelsHeard: 0, conceded: false };
    const next = applyStateUpdate(hostile, {
      defensiveness: 0,
      feelsHeard: 5,
      conceded: true,
    });
    expect(next.defensiveness).toBe(4);
    expect(next.feelsHeard).toBe(1);
  });

  it("allows a single step in either direction", () => {
    const state = { defensiveness: 3, feelsHeard: 2, conceded: false };
    expect(applyStateUpdate(state, { defensiveness: 4 }).defensiveness).toBe(4);
    expect(applyStateUpdate(state, { defensiveness: 2 }).defensiveness).toBe(2);
  });

  it("caving raises defensiveness rather than lowering it", () => {
    // This is the whole point of the state machine: the model reporting that
    // the counterpart calmed down after the user backed off must still be
    // rate-limited, and the prompt instructs a rise. Here we assert the
    // machine faithfully carries a rise through.
    const state = { defensiveness: 2, feelsHeard: 1, conceded: false };
    const afterCaving = applyStateUpdate(state, { defensiveness: 3 });
    expect(afterCaving.defensiveness).toBeGreaterThan(state.defensiveness);
  });

  it("clamps to the 0..5 range", () => {
    const state = { defensiveness: 5, feelsHeard: 0, conceded: false };
    expect(applyStateUpdate(state, { defensiveness: 99 }).defensiveness).toBe(5);
    expect(applyStateUpdate(state, { feelsHeard: -99 }).feelsHeard).toBe(0);
  });

  it("conceding is one-way", () => {
    const conceded = { defensiveness: 1, feelsHeard: 4, conceded: true };
    expect(applyStateUpdate(conceded, { conceded: false }).conceded).toBe(true);
  });

  it("ignores missing or malformed model state", () => {
    const state = { defensiveness: 3, feelsHeard: 2, conceded: false };
    expect(applyStateUpdate(state, undefined)).toEqual(state);
    expect(applyStateUpdate(state, null)).toEqual(state);
    expect(applyStateUpdate(state, {} as any)).toEqual(state);
    expect(
      applyStateUpdate(state, { defensiveness: NaN as any }).defensiveness
    ).toBe(3);
  });

  it("sanitises whatever the client sends", () => {
    expect(sanitiseIncomingState(undefined)).toEqual(INITIAL_STATE);
    expect(sanitiseIncomingState({ defensiveness: 42 }).defensiveness).toBe(5);
    expect(sanitiseIncomingState({ defensiveness: -3 }).defensiveness).toBe(0);
    expect(sanitiseIncomingState({ conceded: "yes" } as any).conceded).toBe(false);
  });

  it("state never drifts outside the range over a long exchange", () => {
    let state = { ...INITIAL_STATE };
    for (let turn = 0; turn < 50; turn++) {
      state = applyStateUpdate(state, {
        defensiveness: turn % 2 === 0 ? 99 : -99,
        feelsHeard: turn % 3 === 0 ? 99 : -99,
        conceded: false,
      });
      expect(state.defensiveness).toBeGreaterThanOrEqual(0);
      expect(state.defensiveness).toBeLessThanOrEqual(5);
      expect(state.feelsHeard).toBeGreaterThanOrEqual(0);
      expect(state.feelsHeard).toBeLessThanOrEqual(5);
    }
  });
});

describe("the silence beat", () => {
  const base = {
    userMadeClearPoint: true,
    userTurnCount: 3,
    beatsAlreadyOffered: 0,
    turnsSinceLastBeat: 99,
  };

  it("offers silence after a clear point", () => {
    expect(shouldOfferSilence(base)).toBe(true);
  });

  it("never offers silence for a vague turn", () => {
    expect(shouldOfferSilence({ ...base, userMadeClearPoint: false })).toBe(false);
  });

  it("does not open with silence", () => {
    expect(shouldOfferSilence({ ...base, userTurnCount: 1 })).toBe(false);
  });

  it("never lands two beats back to back", () => {
    expect(
      shouldOfferSilence({ ...base, beatsAlreadyOffered: 1, turnsSinceLastBeat: 1 })
    ).toBe(false);
    expect(
      shouldOfferSilence({ ...base, beatsAlreadyOffered: 1, turnsSinceLastBeat: 2 })
    ).toBe(true);
  });

  it("caps the beats per run so it stays a moment, not a gimmick", () => {
    expect(
      shouldOfferSilence({ ...base, beatsAlreadyOffered: MAX_SILENCE_BEATS })
    ).toBe(false);
  });
});
