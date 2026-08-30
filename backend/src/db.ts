import type { Context } from "hono";
import { ApiError } from "./http";
import type { AppEnvironment } from "./types";

export function database(c: Context<AppEnvironment>): D1Database {
  if (!c.env.DB) throw new ApiError(503, "DATABASE_DISCONNECTED", "The D1 database binding is not configured.");
  return c.env.DB;
}

export function currentUserId(c: Context<AppEnvironment>): string {
  return c.get("principal").id;
}

export async function requireBusinessRole(
  db: D1Database,
  userId: string,
  businessId: string,
  allowed: readonly string[] = ["owner", "admin", "member", "viewer"],
): Promise<string> {
  const membership = await db
    .prepare("SELECT role FROM business_memberships WHERE business_id = ? AND user_id = ?")
    .bind(businessId, userId)
    .first<{ role: string }>();
  if (!membership || !allowed.includes(membership.role)) {
    throw new ApiError(404, "BUSINESS_NOT_FOUND", "Business not found or access is not permitted.");
  }
  return membership.role;
}

export async function writeAudit(
  db: D1Database,
  values: {
    actorUserId: string | null;
    actorType: "user" | "service" | "system";
    action: string;
    targetType: string;
    targetId: string | null;
    outcome: "success" | "failure" | "denied";
    requestId: string;
    metadata?: Record<string, unknown>;
  },
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO audit_logs
       (id, actor_user_id, actor_type, action, target_type, target_id, outcome, request_id, metadata_json, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    .bind(
      crypto.randomUUID(),
      values.actorUserId,
      values.actorType,
      values.action,
      values.targetType,
      values.targetId,
      values.outcome,
      values.requestId,
      JSON.stringify(values.metadata ?? {}),
      new Date().toISOString(),
    )
    .run();
}

export function d1Rows<T>(result: D1Result<T>): T[] {
  return result.results ?? [];
}
