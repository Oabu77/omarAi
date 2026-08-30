import type { Context } from "hono";
import type { AppEnvironment } from "./types";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
  }
}

export function requestId(c: Context<AppEnvironment>): string {
  return c.get("requestId");
}

export function success<T>(c: Context<AppEnvironment>, data: T, status = 200): Response {
  return c.json({ ok: true, data, requestId: requestId(c) }, status as never);
}

export function failure(
  c: Context<AppEnvironment>,
  status: number,
  code: string,
  message: string,
  details?: unknown,
): Response {
  const error: Record<string, unknown> = { code, message };
  if (details !== undefined) error.details = details;
  return c.json({ ok: false, error, requestId: requestId(c) }, status as never);
}

export async function readJsonObject(c: Context<AppEnvironment>): Promise<Record<string, unknown>> {
  const contentType = c.req.header("content-type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) {
    throw new ApiError(415, "JSON_REQUIRED", "Content-Type must be application/json.");
  }
  let body: unknown;
  try {
    body = await c.req.json<unknown>();
  } catch {
    throw new ApiError(400, "INVALID_JSON", "The request body is not valid JSON.");
  }
  if (typeof body !== "object" || body === null || Array.isArray(body)) {
    throw new ApiError(400, "OBJECT_REQUIRED", "The request body must be a JSON object.");
  }
  return body as Record<string, unknown>;
}
