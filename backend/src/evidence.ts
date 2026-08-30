import { isRecord, requireString } from "./contracts";
import { ApiError } from "./http";
import type { Bindings } from "./types";

export interface ActionEvidenceRequest {
  taskId: string;
  provider: string;
  referenceId: string;
  reportedState: "submitted" | "completed";
  requestId: string;
}

export interface VerifiedActionEvidence {
  verified: true;
  provider: string;
  referenceId: string;
  taskId: string;
  state: "submitted" | "completed";
  verifiedAt: string;
  evidenceDigest: string;
}

function evidenceString(value: unknown, field: string, max: number): string {
  try {
    return requireString(value, field, max);
  } catch {
    throw new ApiError(502, "ACTION_EVIDENCE_INVALID", `The evidence verifier returned an invalid ${field}.`);
  }
}

export function isActionEvidenceVerifierConfigured(env: Bindings): boolean {
  const url = env.ACTION_EVIDENCE_VERIFIER_URL?.trim();
  const token = env.ACTION_EVIDENCE_VERIFIER_TOKEN?.trim();
  if (!url || !token) return false;
  try {
    return new URL(url).protocol === "https:";
  } catch {
    return false;
  }
}

function parseEvidence(value: unknown): VerifiedActionEvidence {
  if (!isRecord(value) || value.verified !== true) {
    throw new ApiError(422, "ACTION_EVIDENCE_REJECTED", "The evidence verifier did not confirm this provider receipt.");
  }
  const provider = evidenceString(value.provider, "provider", 120);
  const referenceId = evidenceString(value.referenceId, "referenceId", 500);
  const taskId = evidenceString(value.taskId, "taskId", 128);
  const state = evidenceString(value.state, "state", 20);
  if (state !== "submitted" && state !== "completed") {
    throw new ApiError(502, "ACTION_EVIDENCE_INVALID", "The evidence verifier returned an invalid state.");
  }
  const verifiedAt = evidenceString(value.verifiedAt, "verifiedAt", 40);
  const timestamp = Date.parse(verifiedAt);
  if (!Number.isFinite(timestamp) || timestamp > Date.now() + 5 * 60_000) {
    throw new ApiError(502, "ACTION_EVIDENCE_INVALID", "The evidence verifier returned an invalid timestamp.");
  }
  const evidenceDigest = evidenceString(value.evidenceDigest, "evidenceDigest", 256);
  return { verified: true, provider, referenceId, taskId, state, verifiedAt, evidenceDigest };
}

export async function verifyActionEvidence(
  env: Bindings,
  request: ActionEvidenceRequest,
): Promise<VerifiedActionEvidence | null> {
  if (!isActionEvidenceVerifierConfigured(env)) return null;
  const url = env.ACTION_EVIDENCE_VERIFIER_URL!.trim();
  const token = env.ACTION_EVIDENCE_VERIFIER_TOKEN!.trim();
  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "X-Request-Id": request.requestId,
      },
      body: JSON.stringify({
        taskId: request.taskId,
        provider: request.provider,
        referenceId: request.referenceId,
        reportedState: request.reportedState,
      }),
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new ApiError(502, "ACTION_EVIDENCE_VERIFIER_UNAVAILABLE", "The provider-evidence verifier was unavailable.");
  }
  if (!response.ok) {
    if (response.status >= 400 && response.status < 500) {
      throw new ApiError(422, "ACTION_EVIDENCE_REJECTED", "The provider-evidence verifier rejected this receipt.");
    }
    throw new ApiError(502, "ACTION_EVIDENCE_VERIFIER_FAILED", "The provider-evidence verifier failed.");
  }
  let raw: unknown;
  try {
    raw = await response.json<unknown>();
  } catch {
    throw new ApiError(502, "ACTION_EVIDENCE_INVALID", "The provider-evidence verifier did not return JSON.");
  }
  const verified = parseEvidence(raw);
  if (
    verified.taskId !== request.taskId ||
    verified.provider !== request.provider ||
    verified.referenceId !== request.referenceId ||
    verified.state !== request.reportedState
  ) {
    throw new ApiError(422, "ACTION_EVIDENCE_MISMATCH", "Verified evidence did not match this task report.");
  }
  return verified;
}
