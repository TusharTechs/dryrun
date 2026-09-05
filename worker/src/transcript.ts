import { TranscriptTurn } from "./types";

export function formatTranscript(transcript: TranscriptTurn[]): string {
  return transcript
    .map((t) => (t.role === "user" ? `USER: ${t.text}` : `COUNTERPART: ${t.text}`))
    .join("\n\n");
}
