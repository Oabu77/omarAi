import type { Hono } from "hono";
import { currentUserId, database } from "../db";
import { ApiError, readJsonObject, success } from "../http";
import { isValidIdempotencyKey } from "../idempotency";
import type { AppEnvironment } from "../types";

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function exportQuery(db: D1Database, sql: string, value: string): Promise<unknown[]> {
  const result = await db.prepare(sql).bind(value).all();
  return result.results;
}

export function isAccountDeleterConfigured(env: AppEnvironment["Bindings"]): boolean {
  const url = env.AUTH_ACCOUNT_DELETER_URL?.trim();
  const token = env.AUTH_ACCOUNT_DELETER_TOKEN?.trim();
  if (!url || !token) return false;
  try {
    return new URL(url).protocol === "https:";
  } catch {
    return false;
  }
}

export function isDeletionConfirmed(header: string | undefined, body: Record<string, unknown>): boolean {
  return header === "DELETE OMAR AI ACCOUNT" && body.confirm === true;
}

export function registerAccountRoutes(app: Hono<AppEnvironment>): void {
  app.get("/v1/account/export", async (c) => {
    const db = database(c);
    const userId = currentUserId(c);
    const [
      profile,
      businesses,
      messages,
      tasks,
      taskSteps,
      taskReports,
      aiOutputReports,
      customers,
      leads,
      jobs,
      estimates,
      invoices,
      entitlements,
      billingVerifications,
      audit,
    ] = await Promise.all([
      exportQuery(db, "SELECT id, email, display_name, preferred_language, created_at, updated_at FROM users WHERE id = ?", userId),
      exportQuery(
        db,
        `SELECT b.id, b.name, b.currency, b.timezone, b.settings_json, m.role, b.created_at, b.updated_at
           FROM businesses b JOIN business_memberships m ON m.business_id = b.id WHERE m.user_id = ? LIMIT 10000`,
        userId,
      ),
      exportQuery(db, "SELECT * FROM assistant_messages WHERE user_id = ? ORDER BY created_at LIMIT 10000", userId),
      exportQuery(db, "SELECT * FROM tasks WHERE user_id = ? ORDER BY created_at LIMIT 10000", userId),
      exportQuery(
        db,
        "SELECT s.* FROM task_steps s JOIN tasks t ON t.id = s.task_id WHERE t.user_id = ? ORDER BY s.created_at LIMIT 10000",
        userId,
      ),
      exportQuery(
        db,
        "SELECT r.* FROM task_reports r JOIN tasks t ON t.id = r.task_id WHERE t.user_id = ? ORDER BY r.created_at LIMIT 10000",
        userId,
      ),
      exportQuery(
        db,
        "SELECT * FROM ai_output_reports WHERE user_id = ? ORDER BY created_at LIMIT 10000",
        userId,
      ),
      exportQuery(
        db,
        `SELECT c.* FROM customers c JOIN business_memberships m ON m.business_id = c.business_id
          WHERE m.user_id = ? ORDER BY c.created_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT l.* FROM leads l JOIN business_memberships m ON m.business_id = l.business_id
          WHERE m.user_id = ? ORDER BY l.created_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT j.* FROM jobs j JOIN business_memberships m ON m.business_id = j.business_id
          WHERE m.user_id = ? ORDER BY j.created_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT e.* FROM estimates e JOIN business_memberships m ON m.business_id = e.business_id
          WHERE m.user_id = ? ORDER BY e.created_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT i.* FROM invoices i JOIN business_memberships m ON m.business_id = i.business_id
          WHERE m.user_id = ? ORDER BY i.created_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT entitlement_key, product_id, provider, state, provider_transaction_id, verified_at, expires_at, updated_at
           FROM entitlements WHERE user_id = ? ORDER BY updated_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT id, provider, product_id, outcome, provider_reference, created_at
           FROM billing_verifications WHERE user_id = ? ORDER BY created_at LIMIT 10000`,
        userId,
      ),
      exportQuery(
        db,
        `SELECT id, actor_type, action, target_type, target_id, outcome, request_id, metadata_json, created_at
           FROM audit_logs WHERE actor_user_id = ? ORDER BY created_at LIMIT 10000`,
        userId,
      ),
    ]);
    return success(c, {
      schemaVersion: "2026-08-30.v1",
      exportedAt: new Date().toISOString(),
      scope: "authenticated-user-and-accessible-business-records",
      perCollectionLimit: 10_000,
      data: {
        profile,
        businesses,
        assistantMessages: messages,
        tasks,
        taskSteps,
        taskReports,
        aiOutputReports,
        customers,
        leads,
        jobs,
        estimates,
        invoices,
        entitlements,
        billingVerifications,
        audit,
      },
    });
  });

  app.delete("/v1/account", async (c) => {
    const db = database(c);
    const userId = currentUserId(c);
    const confirmation = c.req.header("x-deletion-confirmation");
    const body = await readJsonObject(c);
    if (!isDeletionConfirmed(confirmation, body)) {
      throw new ApiError(
        400,
        "DELETION_CONFIRMATION_REQUIRED",
        "Set X-Deletion-Confirmation to DELETE OMAR AI ACCOUNT.",
      );
    }
    const idempotencyKey = c.req.header("idempotency-key")?.trim();
    if (!isValidIdempotencyKey(idempotencyKey)) {
      throw new ApiError(400, "IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key header is required.");
    }
    const subjectHash = await sha256(`omar-ai-account:${userId}`);
    const keyHash = await sha256(idempotencyKey);
    const replay = await db
      .prepare(
        `SELECT response_body FROM account_deletion_receipts
         WHERE subject_hash = ? AND idempotency_key_hash = ? AND expires_at > ?`,
      )
      .bind(subjectHash, keyHash, new Date().toISOString())
      .first<{ response_body: string }>();
    if (replay) {
      // Authentication middleware may have recreated a minimal row with a still-valid
      // JWT after deletion. Remove it before replaying the completed receipt.
      await db.prepare("DELETE FROM users WHERE id = ?").bind(userId).run();
      return new Response(replay.response_body, {
        status: 200,
        headers: { "Content-Type": "application/json; charset=UTF-8", "Idempotency-Replayed": "true" },
      });
    }

    const otherMembers = await db
      .prepare(
        `SELECT b.id, b.name, COUNT(m.user_id) AS other_member_count
           FROM businesses b
           JOIN business_memberships m ON m.business_id = b.id AND m.user_id <> ?
          WHERE b.owner_user_id = ? GROUP BY b.id, b.name HAVING COUNT(m.user_id) > 0`,
      )
      .bind(userId, userId)
      .all<{ id: string; name: string; other_member_count: number }>();
    if (otherMembers.results.length > 0) {
      throw new ApiError(
        409,
        "BUSINESS_OWNERSHIP_TRANSFER_REQUIRED",
        "Transfer or remove other members from owned businesses before deleting the account.",
        { businesses: otherMembers.results },
      );
    }

    const deleterUrl = c.env.AUTH_ACCOUNT_DELETER_URL?.trim();
    const deleterToken = c.env.AUTH_ACCOUNT_DELETER_TOKEN?.trim();
    if (!deleterUrl || !deleterToken || !isAccountDeleterConfigured(c.env)) {
      throw new ApiError(
        503,
        "ACCOUNT_DELETION_DISCONNECTED",
        "The identity-provider account deleter is not configured; no data was deleted.",
      );
    }
    // Delete application data first. If the identity provider is unavailable, the
    // remaining identity can authenticate and retry; no inaccessible personal data
    // is stranded behind a deleted identity.
    await db.batch([
      db.prepare("DELETE FROM businesses WHERE owner_user_id = ?").bind(userId),
      db.prepare("DELETE FROM business_memberships WHERE user_id = ?").bind(userId),
      db.prepare("DELETE FROM audit_logs WHERE actor_user_id = ?").bind(userId),
      db.prepare("DELETE FROM users WHERE id = ?").bind(userId),
    ]);

    let identityResponse: Response;
    try {
      identityResponse = await fetch(deleterUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${deleterToken}`,
          "Content-Type": "application/json",
          "X-Request-Id": c.get("requestId"),
        },
        body: JSON.stringify({ subject: userId, issuer: c.get("principal").issuer }),
        signal: AbortSignal.timeout(10_000),
      });
    } catch {
      throw new ApiError(
        502,
        "ACCOUNT_IDENTITY_DELETION_PENDING",
        "Application data was deleted, but the identity-provider account deleter was unavailable. Retry account deletion.",
        { applicationData: "DELETED", identityProviderAccount: "PENDING" },
      );
    }
    let identityResult: unknown;
    try {
      identityResult = await identityResponse.json<unknown>();
    } catch {
      identityResult = null;
    }
    if (
      !identityResponse.ok ||
      typeof identityResult !== "object" ||
      identityResult === null ||
      (identityResult as Record<string, unknown>).deleted !== true ||
      (identityResult as Record<string, unknown>).subject !== userId
    ) {
      throw new ApiError(
        502,
        "ACCOUNT_IDENTITY_DELETION_PENDING",
        "Application data was deleted, but the identity provider did not confirm account deletion. Retry account deletion.",
        { applicationData: "DELETED", identityProviderAccount: "PENDING" },
      );
    }

    const deletionId = crypto.randomUUID();
    const now = new Date();
    const expires = new Date(now.getTime() + 24 * 60 * 60 * 1_000);
    const responsePayload = {
      ok: true,
      data: {
        deletionId,
        status: "COMPLETED",
        identityProviderAccount: "DELETED",
        applicationData: "DELETED",
        completedAt: now.toISOString(),
      },
      requestId: c.get("requestId"),
    };
    const responseBody = JSON.stringify(responsePayload);
    try {
      await db.batch([
        db
        .prepare(
          `INSERT INTO account_deletion_receipts
           (subject_hash, idempotency_key_hash, response_body, created_at, expires_at)
           VALUES (?, ?, ?, ?, ?)`,
        )
        .bind(subjectHash, keyHash, responseBody, now.toISOString(), expires.toISOString()),
        db
        .prepare(
          `INSERT INTO audit_logs
           (id, actor_user_id, actor_type, action, target_type, target_id, outcome, request_id, metadata_json, created_at)
           VALUES (?, NULL, 'system', 'account.delete', 'deletion_receipt', ?, 'success', ?, '{}', ?)`,
        )
          .bind(crypto.randomUUID(), deletionId, c.get("requestId"), now.toISOString()),
      ]);
    } catch {
      // Both deletion phases are already provider-confirmed. Receipt persistence
      // failure must not turn a completed deletion into a false failure claim.
      responsePayload.data.status = "COMPLETED";
    }
    return new Response(responseBody, {
      status: 200,
      headers: { "Content-Type": "application/json; charset=UTF-8", "Idempotency-Replayed": "false" },
    });
  });
}
