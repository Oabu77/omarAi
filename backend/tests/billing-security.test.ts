import { describe, expect, it } from "vitest";
import {
  hasPurchaseOwnershipConflict,
  lifecycleAllowsActivation,
  parseVerification,
  type VerifiedPurchase,
} from "../src/routes/billing";

function purchase(overrides: Partial<VerifiedPurchase> = {}): VerifiedPurchase {
  return {
    verified: true,
    productId: "omar_ai_pro",
    packageName: "com.darcloud.omarai",
    providerTransactionId: "transaction-1",
    purchaseState: "PURCHASED",
    acknowledgementState: "ACKNOWLEDGED",
    productType: "SUBSCRIPTION",
    verifiedAt: new Date().toISOString(),
    expiryTime: new Date(Date.now() + 30 * 24 * 60 * 60_000).toISOString(),
    testPurchase: false,
    ...overrides,
  };
}

describe("Google Play ownership and lifecycle boundary", () => {
  it("rejects transaction ownership by a different user or token", () => {
    const row = {
      id: "entitlement-1",
      user_id: "user-a",
      purchase_token_hash: "hash-a",
      provider_transaction_id: "transaction-1",
    };
    expect(hasPurchaseOwnershipConflict([row], "user-b", "hash-a")).toBe(true);
    expect(hasPurchaseOwnershipConflict([row], "user-a", "hash-b")).toBe(true);
    expect(hasPurchaseOwnershipConflict([row], "user-a", "hash-a")).toBe(false);
  });

  it("never activates solely because a purchase was verified", () => {
    expect(lifecycleAllowsActivation({}, purchase())).toBe(false);
    expect(lifecycleAllowsActivation({ PLAY_LIFECYCLE_VERIFICATION_ENABLED: "true" }, purchase())).toBe(false);
  });

  it("requires fresh RTDN/revocation evidence and a non-expired subscription", () => {
    const enabled = { PLAY_LIFECYCLE_VERIFICATION_ENABLED: "true" };
    const complete = purchase({
      lifecycleEvidence: {
        rtdnConfigured: true,
        revocationReconciliationConfigured: true,
        lastReconciledAt: new Date().toISOString(),
        evidenceReference: "lifecycle-proof-1",
      },
    });
    expect(lifecycleAllowsActivation(enabled, complete)).toBe(true);
    expect(lifecycleAllowsActivation(enabled, {
      ...complete,
      expiryTime: new Date(Date.now() - 1_000).toISOString(),
    })).toBe(false);
    expect(lifecycleAllowsActivation(enabled, {
      ...complete,
      lifecycleEvidence: {
        ...complete.lifecycleEvidence!,
        lastReconciledAt: new Date(Date.now() - 25 * 60 * 60_000).toISOString(),
      },
    })).toBe(false);
  });

  it("requires acknowledgment and product type in verifier evidence", () => {
    expect(() => parseVerification({
      ...purchase(),
      acknowledgementState: undefined,
    })).toThrow();
    expect(parseVerification(purchase()).acknowledgementState).toBe("ACKNOWLEDGED");
  });
});
