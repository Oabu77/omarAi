import type { Context } from "hono";
import { ApiError } from "./http";
import type { AppEnvironment } from "./types";

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

interface StoredIdempotency {
  request_hash: string;
  state: "processing" | "completed";
  response_status: number | null;
  response_body: string | null;
}

export function isValidIdempotencyKey(value: string | undefined): value is string {
  return Boolean(value && /^[A-Za-z0-9._:-]{8,128}$/.test(value));
}

export async function withIdempotency(
  c: Context<AppEnvironment>,
  userId: string,
  handler: () => Promise<Response>,
): Promise<Response> {
  const db = c.env.DB;
  if (!db) throw new ApiError(503, "DATABASE_DISCONNECTED", "The D1 database binding is not configured.");
  const key = c.req.header("idempotency-key")?.trim();
  if (!isValidIdempotencyKey(key)) {
    throw new ApiError(400, "IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key header (8-128 safe characters) is required.");
  }
  const route = `${c.req.method.toUpperCase()} ${new URL(c.req.url).pathname}`;
  const bodyText = await c.req.raw.clone().text();
  if (new TextEncoder().encode(bodyText).byteLength > 1 * 1024 * 1024) {
    throw new ApiError(413, "REQUEST_BODY_TOO_LARGE", "Request bodies are limited to 1 MiB.");
  }
  const requestHash = await sha256(`${route}\n${bodyText}`);
  const now = new Date();
  const expires = new Date(now.getTime() + 24 * 60 * 60 * 1_000);
  const inserted = await db
    .prepare(
      `INSERT OR IGNORE INTO idempotency_keys
       (user_id, route, idempotency_key, request_hash, state, created_at, expires_at)
       VALUES (?, ?, ?, ?, 'processing', ?, ?)`,
    )
    .bind(userId, route, key, requestHash, now.toISOString(), expires.toISOString())
    .run();

  if ((inserted.meta.changes ?? 0) === 0) {
    const stored = await db
      .prepare(
        `SELECT request_hash, state, response_status, response_body
         FROM idempotency_keys WHERE user_id = ? AND route = ? AND idempotency_key = ?`,
      )
      .bind(userId, route, key)
      .first<StoredIdempotency>();
    if (!stored) throw new ApiError(503, "IDEMPOTENCY_UNAVAILABLE", "The idempotency record could not be read.");
    if (stored.request_hash !== requestHash) {
      throw new ApiError(409, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used with a different request.");
    }
    if (stored.state === "processing") {
      throw new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "A request with this Idempotency-Key is still processing.");
    }
    if (stored.response_status === null || stored.response_body === null) {
      throw new ApiError(503, "IDEMPOTENCY_UNAVAILABLE", "The stored idempotent response is incomplete.");
    }
    return new Response(stored.response_body, {
      status: stored.response_status,
      headers: {
        "Content-Type": "application/json; charset=UTF-8",
        "Idempotency-Replayed": "true",
      },
    });
  }

  try {
    const response = await handler();
    if (response.status >= 500) {
      await db
        .prepare("DELETE FROM idempotency_keys WHERE user_id = ? AND route = ? AND idempotency_key = ?")
        .bind(userId, route, key)
        .run();
      return response;
    }
    const responseBody = await response.clone().text();
    if (responseBody.length > 256_000) {
      await db
        .prepare("DELETE FROM idempotency_keys WHERE user_id = ? AND route = ? AND idempotency_key = ?")
        .bind(userId, route, key)
        .run();
      return response;
    }
    await db
      .prepare(
        `UPDATE idempotency_keys SET state = 'completed', response_status = ?, response_body = ?
         WHERE user_id = ? AND route = ? AND idempotency_key = ?`,
      )
      .bind(response.status, responseBody, userId, route, key)
      .run();
    response.headers.set("Idempotency-Replayed", "false");
    return response;
  } catch (error) {
    await db
      .prepare("DELETE FROM idempotency_keys WHERE user_id = ? AND route = ? AND idempotency_key = ?")
      .bind(userId, route, key)
      .run();
    throw error;
  }
}
