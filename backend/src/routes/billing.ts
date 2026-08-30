import type { Hono } from "hono";
import { isRecord, requireString } from "../contracts";
import { currentUserId, database, d1Rows, writeAudit } from "../db";
import { ApiError, readJsonObject, success } from "../http";
import { withIdempotency } from "../idempotency";
import type { AppEnvironment, Bindings } from "../types";

export interface VerifiedPurchase {
  verified: boolean;
  productId: string;
  packageName: string;
  providerTransactionId: string;
  purchaseState: "PURCHASED" | "PENDING" | "CANCELED";
  acknowledgementState: "ACKNOWLEDGED" | "PENDING";
  productType: "SUBSCRIPTION" | "ONE_TIME";
  verifiedAt: string;
  expiryTime?: string | null;
  testPurchase?: boolean;
  lifecycleEvidence?: {
    rtdnConfigured: boolean;
    revocationReconciliationConfigured: boolean;
    lastReconciledAt: string;
    evidenceReference: string;
  };
}

function verifierString(value: unknown, field: string, max: number): string {
  try {
    return requireString(value, field, max);
  } catch {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", `The billing verifier returned an invalid ${field}.`);
  }
}

async function hashSecret(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function productMap(env: Bindings): Record<string, string> {
  if (!env.PLAY_PRODUCT_ENTITLEMENTS?.trim()) {
    throw new ApiError(503, "BILLING_DISCONNECTED", "PLAY_PRODUCT_ENTITLEMENTS is not configured.");
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(env.PLAY_PRODUCT_ENTITLEMENTS);
  } catch {
    throw new ApiError(503, "BILLING_CONFIGURATION_INVALID", "PLAY_PRODUCT_ENTITLEMENTS is invalid JSON.");
  }
  if (!isRecord(parsed)) throw new ApiError(503, "BILLING_CONFIGURATION_INVALID", "PLAY_PRODUCT_ENTITLEMENTS must be an object.");
  const mapped: Record<string, string> = {};
  for (const [product, entitlement] of Object.entries(parsed)) {
    if (!product || typeof entitlement !== "string" || !entitlement) {
      throw new ApiError(503, "BILLING_CONFIGURATION_INVALID", "PLAY_PRODUCT_ENTITLEMENTS contains an invalid entry.");
    }
    mapped[product] = entitlement;
  }
  return mapped;
}

function billingConfig(env: Bindings): {
  url: string;
  token: string;
  packageName: string;
  products: Record<string, string>;
} {
  const url = env.BILLING_VERIFIER_URL?.trim();
  const token = env.BILLING_VERIFIER_TOKEN?.trim();
  const packageName = env.ANDROID_PACKAGE_NAME?.trim();
  if (!url || !token || !packageName) {
    throw new ApiError(
      503,
      "BILLING_DISCONNECTED",
      "Google Play billing verification is not configured on the server.",
    );
  }
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    throw new ApiError(503, "BILLING_CONFIGURATION_INVALID", "BILLING_VERIFIER_URL is invalid.");
  }
  if (parsed.protocol !== "https:") {
    throw new ApiError(503, "BILLING_CONFIGURATION_INVALID", "BILLING_VERIFIER_URL must use HTTPS.");
  }
  return { url, token, packageName, products: productMap(env) };
}

export function parseVerification(value: unknown): VerifiedPurchase {
  if (!isRecord(value) || typeof value.verified !== "boolean") {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "The billing verifier returned an invalid response.");
  }
  const productId = verifierString(value.productId, "productId", 200);
  const packageName = verifierString(value.packageName, "packageName", 300);
  const providerTransactionId = verifierString(value.providerTransactionId, "providerTransactionId", 500);
  const purchaseState = verifierString(value.purchaseState, "purchaseState", 20);
  if (!["PURCHASED", "PENDING", "CANCELED"].includes(purchaseState)) {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "The billing verifier returned an invalid purchase state.");
  }
  const acknowledgementState = verifierString(value.acknowledgementState, "acknowledgementState", 20);
  if (acknowledgementState !== "ACKNOWLEDGED" && acknowledgementState !== "PENDING") {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "The billing verifier returned an invalid acknowledgement state.");
  }
  const productType = verifierString(value.productType, "productType", 20);
  if (productType !== "SUBSCRIPTION" && productType !== "ONE_TIME") {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "The billing verifier returned an invalid product type.");
  }
  const verifiedAt = verifierString(value.verifiedAt, "verifiedAt", 40);
  const verifiedTimestamp = Date.parse(verifiedAt);
  if (!Number.isFinite(verifiedTimestamp) || verifiedTimestamp > Date.now() + 5 * 60_000) {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "The billing verifier returned an invalid verification time.");
  }
  const expiryTime = typeof value.expiryTime === "string" ? value.expiryTime : null;
  if (productType === "SUBSCRIPTION" && (!expiryTime || !Number.isFinite(Date.parse(expiryTime)))) {
    throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "A verified subscription requires a valid expiry time.");
  }
  let lifecycleEvidence: VerifiedPurchase["lifecycleEvidence"];
  if (isRecord(value.lifecycleEvidence)) {
    const lastReconciledAt = verifierString(value.lifecycleEvidence.lastReconciledAt, "lifecycleEvidence.lastReconciledAt", 40);
    const evidenceReference = verifierString(value.lifecycleEvidence.evidenceReference, "lifecycleEvidence.evidenceReference", 500);
    if (!Number.isFinite(Date.parse(lastReconciledAt))) {
      throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "Lifecycle evidence has an invalid reconciliation time.");
    }
    lifecycleEvidence = {
      rtdnConfigured: value.lifecycleEvidence.rtdnConfigured === true,
      revocationReconciliationConfigured: value.lifecycleEvidence.revocationReconciliationConfigured === true,
      lastReconciledAt,
      evidenceReference,
    };
  }
  return {
    verified: value.verified,
    productId,
    packageName,
    providerTransactionId,
    purchaseState: purchaseState as VerifiedPurchase["purchaseState"],
    acknowledgementState: acknowledgementState as VerifiedPurchase["acknowledgementState"],
    productType: productType as VerifiedPurchase["productType"],
    verifiedAt,
    expiryTime,
    testPurchase: value.testPurchase === true,
    ...(lifecycleEvidence ? { lifecycleEvidence } : {}),
  };
}

export function lifecycleAllowsActivation(env: Bindings, purchase: VerifiedPurchase): boolean {
  if (env.PLAY_LIFECYCLE_VERIFICATION_ENABLED?.trim().toLowerCase() !== "true") return false;
  const evidence = purchase.lifecycleEvidence;
  if (!evidence?.rtdnConfigured || !evidence.revocationReconciliationConfigured) return false;
  if (purchase.productType === "SUBSCRIPTION") {
    const expiry = Date.parse(purchase.expiryTime ?? "");
    if (!Number.isFinite(expiry) || expiry <= Date.now()) return false;
  }
  const reconciledAt = Date.parse(evidence.lastReconciledAt);
  return Number.isFinite(reconciledAt) && reconciledAt <= Date.now() + 5 * 60_000 && Date.now() - reconciledAt <= 24 * 60 * 60_000;
}

export interface EntitlementOwnershipRow {
  id: string;
  user_id: string;
  purchase_token_hash: string;
  provider_transaction_id: string;
}

export function hasPurchaseOwnershipConflict(
  rows: readonly EntitlementOwnershipRow[],
  userId: string,
  tokenHash: string,
): boolean {
  return rows.length > 1 || rows.some((row) => row.user_id !== userId || row.purchase_token_hash !== tokenHash);
}

export function isBillingConfigured(env: Bindings): boolean {
  try {
    billingConfig(env);
    return true;
  } catch {
    return false;
  }
}

export function registerBillingRoutes(app: Hono<AppEnvironment>): void {
  app.post("/v1/billing/google-play/verify", async (c) => {
    const userId = currentUserId(c);
    return withIdempotency(c, userId, async () => {
      const config = billingConfig(c.env);
      const body = await readJsonObject(c);
      const purchaseToken = requireString(body.purchaseToken, "purchaseToken", 8_192);
      const productId = requireString(body.productId, "productId", 200);
      const packageName = requireString(body.packageName, "packageName", 300);
      if (packageName !== config.packageName) {
        throw new ApiError(400, "PACKAGE_NAME_MISMATCH", "packageName does not match the configured Android application.");
      }
      const entitlementKey = config.products[productId];
      if (!entitlementKey) throw new ApiError(400, "UNKNOWN_PRODUCT", "productId is not mapped to a server entitlement.");
      const tokenHash = await hashSecret(purchaseToken);
      const db = database(c);
      let response: Response;
      try {
        response = await fetch(config.url, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${config.token}`,
            "Content-Type": "application/json",
            "X-Request-Id": c.get("requestId"),
          },
          body: JSON.stringify({ packageName, productId, purchaseToken }),
          signal: AbortSignal.timeout(10_000),
        });
      } catch {
        await db
          .prepare(
            `INSERT INTO billing_verifications
             (id, user_id, provider, product_id, purchase_token_hash, outcome, created_at)
             VALUES (?, ?, 'google-play', ?, ?, 'provider_error', ?)`,
          )
          .bind(crypto.randomUUID(), userId, productId, tokenHash, new Date().toISOString())
          .run();
        throw new ApiError(502, "BILLING_VERIFIER_UNAVAILABLE", "The server-side Google Play verifier was unavailable.");
      }
      if (!response.ok) {
        throw new ApiError(502, "BILLING_VERIFIER_FAILED", "The server-side Google Play verifier rejected the verification request.", {
          verifierHttpStatus: response.status,
        });
      }
      let raw: unknown;
      try {
        raw = await response.json<unknown>();
      } catch {
        throw new ApiError(502, "BILLING_VERIFIER_INVALID_RESPONSE", "The billing verifier did not return JSON.");
      }
      const verified = parseVerification(raw);
      if (verified.productId !== productId || verified.packageName !== packageName) {
        throw new ApiError(502, "BILLING_VERIFIER_MISMATCH", "The verified purchase does not match the requested product and package.");
      }
      const verificationId = crypto.randomUUID();
      const now = new Date().toISOString();
      if (
        !verified.verified ||
        verified.purchaseState !== "PURCHASED" ||
        verified.acknowledgementState !== "ACKNOWLEDGED"
      ) {
        await db
          .prepare(
            `INSERT INTO billing_verifications
             (id, user_id, provider, product_id, purchase_token_hash, outcome, provider_reference,
              acknowledgement_state, lifecycle_evidence_json, created_at)
             VALUES (?, ?, 'google-play', ?, ?, 'rejected', ?, ?, ?, ?)`,
          )
          .bind(
            verificationId,
            userId,
            productId,
            tokenHash,
            verified.providerTransactionId,
            verified.acknowledgementState,
            verified.lifecycleEvidence ? JSON.stringify(verified.lifecycleEvidence) : null,
            now,
          )
          .run();
        throw new ApiError(422, "PURCHASE_NOT_VERIFIED", "Google Play did not confirm a purchased and acknowledged entitlement.", {
          purchaseState: verified.purchaseState,
          acknowledgementState: verified.acknowledgementState,
        });
      }
      const existingOwnership = await db
        .prepare(
          `SELECT id, user_id, purchase_token_hash, provider_transaction_id
             FROM entitlements
            WHERE provider = 'google-play'
              AND (purchase_token_hash = ? OR provider_transaction_id = ?)`,
        )
        .bind(tokenHash, verified.providerTransactionId)
        .all<EntitlementOwnershipRow>();
      const ownershipConflict = hasPurchaseOwnershipConflict(existingOwnership.results, userId, tokenHash);
      if (ownershipConflict) {
        await writeAudit(db, {
          actorUserId: userId,
          actorType: "user",
          action: "billing.google_play.verify",
          targetType: "entitlement",
          targetId: null,
          outcome: "denied",
          requestId: c.get("requestId"),
          metadata: { productId, reason: "purchase_ownership_conflict" },
        });
        throw new ApiError(
          409,
          "PURCHASE_OWNERSHIP_CONFLICT",
          "This provider transaction or purchase token is already bound to another entitlement identity.",
        );
      }
      const entitlementId = existingOwnership.results[0]?.id ?? crypto.randomUUID();
      const activationAllowed = lifecycleAllowsActivation(c.env, verified);
      const entitlementState = activationAllowed ? "active" : "pending_activation";
      await db.batch([
        db
          .prepare(
            `INSERT INTO billing_verifications
             (id, user_id, provider, product_id, purchase_token_hash, outcome, provider_reference,
              acknowledgement_state, lifecycle_evidence_json, created_at)
             VALUES (?, ?, 'google-play', ?, ?, 'verified', ?, ?, ?, ?)`,
          )
          .bind(
            verificationId,
            userId,
            productId,
            tokenHash,
            verified.providerTransactionId,
            verified.acknowledgementState,
            verified.lifecycleEvidence ? JSON.stringify(verified.lifecycleEvidence) : null,
            now,
          ),
        db
          .prepare(
            `INSERT INTO entitlements
             (id, user_id, provider, product_id, entitlement_key, state, purchase_token_hash,
              provider_transaction_id, verified_at, expires_at, updated_at)
             VALUES (?, ?, 'google-play', ?, ?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT(provider, purchase_token_hash) DO UPDATE SET
               product_id = excluded.product_id,
               entitlement_key = excluded.entitlement_key,
               state = excluded.state,
               provider_transaction_id = excluded.provider_transaction_id,
               verified_at = excluded.verified_at,
               expires_at = excluded.expires_at,
               updated_at = excluded.updated_at
             WHERE entitlements.user_id = excluded.user_id`,
          )
          .bind(
            entitlementId,
            userId,
            productId,
            entitlementKey,
            entitlementState,
            tokenHash,
            verified.providerTransactionId,
            verified.verifiedAt,
            verified.expiryTime ?? null,
            now,
          ),
      ]);
      const persistedOwnership = await db
        .prepare(
          `SELECT id, user_id, purchase_token_hash, provider_transaction_id
             FROM entitlements WHERE provider = 'google-play' AND purchase_token_hash = ?`,
        )
        .bind(tokenHash)
        .first<EntitlementOwnershipRow>();
      if (
        !persistedOwnership ||
        persistedOwnership.user_id !== userId ||
        persistedOwnership.purchase_token_hash !== tokenHash ||
        persistedOwnership.provider_transaction_id !== verified.providerTransactionId
      ) {
        throw new ApiError(409, "PURCHASE_OWNERSHIP_CONFLICT", "The entitlement ownership write was not confirmed.");
      }
      await writeAudit(db, {
        actorUserId: userId,
        actorType: "user",
        action: "billing.google_play.verify",
        targetType: "entitlement",
        targetId: entitlementId,
        outcome: "success",
        requestId: c.get("requestId"),
        metadata: {
          productId,
          entitlementKey,
          entitlementState,
          acknowledgementState: verified.acknowledgementState,
          lifecycleActivationAllowed: activationAllowed,
          testPurchase: verified.testPurchase === true,
        },
      });
      return success(c, {
        entitlement: {
          key: entitlementKey,
          state: entitlementState,
          productId,
          provider: "google-play",
          verifiedAt: verified.verifiedAt,
          expiresAt: verified.expiryTime ?? null,
          mode: verified.testPurchase ? "test" : "production",
          grantsAccess: activationAllowed,
        },
        providerEvidence: {
          state: "PROVIDER_VERIFIED",
          provider: "google-play",
          referenceId: verified.providerTransactionId,
          verifier: "server-authoritative",
          acknowledgementState: verified.acknowledgementState,
          lifecycleState: activationAllowed ? "VERIFIED_OPERATIONAL" : "PENDING_LIFECYCLE_VERIFICATION",
        },
      }, activationAllowed ? 200 : 202);
    });
  });

  app.get("/v1/billing/entitlements", async (c) => {
    const rows = await database(c)
      .prepare(
        `SELECT entitlement_key, product_id, provider, state, verified_at, expires_at, updated_at,
                CASE WHEN state IN ('active', 'grace_period') THEN 1 ELSE 0 END AS grants_access
         FROM entitlements WHERE user_id = ? ORDER BY updated_at DESC`,
      )
      .bind(currentUserId(c))
      .all();
    return success(c, { source: "SERVER_VERIFIED_ROWS", entitlements: d1Rows(rows) });
  });
}
