import type { MiddlewareHandler } from "hono";
import { ApiError, failure } from "./http";
import type { AppEnvironment, Bindings } from "./types";

function allowedOrigins(env: Bindings): Set<string> {
  return new Set(
    (env.ALLOWED_ORIGINS ?? "")
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
  );
}

export const requestContextMiddleware: MiddlewareHandler<AppEnvironment> = async (c, next) => {
  const inbound = c.req.header("x-request-id");
  const id = inbound && /^[A-Za-z0-9._:-]{8,128}$/.test(inbound) ? inbound : crypto.randomUUID();
  c.set("requestId", id);
  await next();
  c.header("X-Request-Id", id);
  c.header("X-Content-Type-Options", "nosniff");
  c.header("Referrer-Policy", "no-referrer");
  c.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
  c.header("Cache-Control", "no-store");
};

export const restrictedCorsMiddleware: MiddlewareHandler<AppEnvironment> = async (c, next) => {
  const origin = c.req.header("origin");
  if (!origin) {
    await next();
    return;
  }
  const allowed = allowedOrigins(c.env);
  if (!allowed.has(origin)) {
    c.res = failure(c, 403, "ORIGIN_NOT_ALLOWED", "This browser origin is not allowed.");
    return;
  }
  c.header("Access-Control-Allow-Origin", origin);
  c.header("Vary", "Origin");
  c.header("Access-Control-Allow-Headers", "Authorization, Content-Type, Idempotency-Key, X-Deletion-Confirmation, X-Omar-Internal-Token, X-Request-Id");
  c.header("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
  c.header("Access-Control-Max-Age", "600");
  if (c.req.method === "OPTIONS") {
    c.res = new Response(null, { status: 204, headers: c.res.headers });
    return;
  }
  await next();
};

function parsedLimit(raw: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(raw ?? "", 10);
  return Number.isInteger(parsed) && parsed > 0 && parsed <= 10_000 ? parsed : fallback;
}

export async function enforceRateLimit(
  env: Bindings,
  subject: string,
  scope: string,
  explicitLimit?: number,
): Promise<{ limit: number; remaining: number; resetAtSeconds: number }> {
  const db = env.DB;
  if (!db) throw new ApiError(503, "DATABASE_DISCONNECTED", "The D1 database binding is not configured.");
  const limit = explicitLimit ?? parsedLimit(env.RATE_LIMIT_PER_MINUTE, 120);
  const epochSeconds = Math.floor(Date.now() / 1_000);
  const windowStart = epochSeconds - (epochSeconds % 60);
  const row = await db
    .prepare(
      `INSERT INTO rate_limits (subject_key, scope, window_start, request_count)
       VALUES (?, ?, ?, 1)
       ON CONFLICT(subject_key, scope, window_start)
       DO UPDATE SET request_count = request_count + 1
       RETURNING request_count`,
    )
    .bind(subject, scope, windowStart)
    .first<{ request_count: number }>();
  if (!row) throw new ApiError(503, "RATE_LIMIT_UNAVAILABLE", "The rate limiter could not record this request.");
  const remaining = Math.max(0, limit - row.request_count);
  if (row.request_count > limit) {
    throw new ApiError(429, "RATE_LIMITED", "Too many requests. Try again after the current rate-limit window.", {
      limit,
      resetAtSeconds: windowStart + 60,
    });
  }
  return { limit, remaining, resetAtSeconds: windowStart + 60 };
}

export function assistantRateLimit(env: Bindings): number {
  return parsedLimit(env.ASSISTANT_RATE_LIMIT_PER_MINUTE, 20);
}

export async function internalTokenMatches(env: Bindings, provided: string | undefined): Promise<boolean> {
  const expected = env.INTERNAL_SERVICE_TOKEN;
  if (!expected || !provided || expected.length < 24 || provided.length > 1_024) return false;
  const encoder = new TextEncoder();
  const [expectedHash, providedHash] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(expected)),
    crypto.subtle.digest("SHA-256", encoder.encode(provided)),
  ]);
  const a = new Uint8Array(expectedHash);
  const b = new Uint8Array(providedHash);
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let index = 0; index < a.length; index += 1) difference |= a[index]! ^ b[index]!;
  return difference === 0;
}
