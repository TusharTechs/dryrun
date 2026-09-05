import { Env } from "./types";

const KV_TTL = 60 * 60 * 48;

function todayKey(): string {
  return new Date().toISOString().slice(0, 10);
}

export interface RateLimitResult {
  allowed: boolean;
  remaining: number;
  limit: number;
}

export async function checkRateLimit(
  token: string,
  ip: string,
  env: Env
): Promise<RateLimitResult> {
  const day = todayKey();
  const tokenLimit = parseInt(env.PER_TOKEN_DAILY_LIMIT ?? "20", 10);
  const ipLimit = parseInt(env.PER_IP_DAILY_LIMIT ?? "50", 10);

  const tokenKey = `rl:token:${token}:${day}`;
  const ipKey = `rl:ip:${ip}:${day}`;

  const [tokenCount, ipCount] = await Promise.all([
    env.RATE_LIMIT.get(tokenKey, { type: "text" }).then((v) => parseInt(v ?? "0", 10)),
    env.RATE_LIMIT.get(ipKey, { type: "text" }).then((v) => parseInt(v ?? "0", 10)),
  ]);

  const tokenRemaining = tokenLimit - tokenCount;
  const ipRemaining = ipLimit - ipCount;

  const allowed = tokenRemaining > 0 && ipRemaining > 0;

  if (allowed) {
    await Promise.all([
      env.RATE_LIMIT.put(tokenKey, String(tokenCount + 1), { expirationTtl: KV_TTL }),
      env.RATE_LIMIT.put(ipKey, String(ipCount + 1), { expirationTtl: KV_TTL }),
    ]);
  }

  return {
    allowed,
    remaining: Math.min(tokenRemaining, ipRemaining),
    limit: Math.min(tokenLimit, ipLimit),
  };
}
