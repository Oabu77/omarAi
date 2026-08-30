import { afterEach, describe, expect, it, vi } from "vitest";
import { verifyActionEvidence } from "../src/evidence";

const request = {
  taskId: "task-1",
  provider: "calendar-provider",
  referenceId: "provider-receipt-1",
  reportedState: "completed" as const,
  requestId: "request-1",
};

afterEach(() => vi.unstubAllGlobals());

describe("provider action evidence", () => {
  it("remains unverified when no backend verifier adapter is configured", async () => {
    await expect(verifyActionEvidence({}, request)).resolves.toBeNull();
  });

  it("cross-checks trusted adapter evidence against task, provider, reference, and state", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({
      verified: true,
      taskId: request.taskId,
      provider: request.provider,
      referenceId: request.referenceId,
      state: request.reportedState,
      verifiedAt: new Date().toISOString(),
      evidenceDigest: "sha256:verified-provider-payload",
    }), { status: 200, headers: { "Content-Type": "application/json" } })));
    const result = await verifyActionEvidence({
      ACTION_EVIDENCE_VERIFIER_URL: "https://verifier.example/evidence",
      ACTION_EVIDENCE_VERIFIER_TOKEN: "server-secret-token",
    }, request);
    expect(result).toMatchObject({ verified: true, taskId: "task-1", referenceId: "provider-receipt-1" });
  });

  it("rejects a caller reference that does not match adapter evidence", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({
      verified: true,
      taskId: request.taskId,
      provider: request.provider,
      referenceId: "different-receipt",
      state: request.reportedState,
      verifiedAt: new Date().toISOString(),
      evidenceDigest: "sha256:verified-provider-payload",
    }), { status: 200, headers: { "Content-Type": "application/json" } })));
    await expect(verifyActionEvidence({
      ACTION_EVIDENCE_VERIFIER_URL: "https://verifier.example/evidence",
      ACTION_EVIDENCE_VERIFIER_TOKEN: "server-secret-token",
    }, request)).rejects.toMatchObject({ code: "ACTION_EVIDENCE_MISMATCH" });
  });
});
