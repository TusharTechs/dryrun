import { Env } from "./types";

const TOKEN_TTL_SECONDS = 60 * 60 * 24 * 180;

export async function mintToken(
  deviceId: string,
  env: Env
): Promise<string> {
  const existing = await env.TOKENS.get(`device:${deviceId}`);
  if (existing) return existing;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(env.MINT_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(deviceId)
  );
  const token =
    Array.from(new Uint8Array(sig))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("") + deviceId.replace(/-/g, "");

  await env.TOKENS.put(`device:${deviceId}`, token, {
    expirationTtl: TOKEN_TTL_SECONDS,
  });
  await env.TOKENS.put(`token:${token}`, deviceId, {
    expirationTtl: TOKEN_TTL_SECONDS,
  });

  return token;
}

export async function verifyToken(
  token: string,
  env: Env
): Promise<string | null> {
  const deviceId = await env.TOKENS.get(`token:${token}`);
  return deviceId;
}
