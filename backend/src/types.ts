export interface WorkersAiBinding {
  run(model: string, input: unknown): Promise<unknown>;
}

export interface Bindings {
  DB?: D1Database;
  AI?: WorkersAiBinding;
  ENVIRONMENT?: string;
  ALLOWED_ORIGINS?: string;
  JWT_ISSUER?: string;
  JWT_AUDIENCE?: string;
  JWKS_URL?: string;
  MODEL_TEXT?: string;
  MODEL_VISION?: string;
  MODEL_TRANSCRIPTION?: string;
  RATE_LIMIT_PER_MINUTE?: string;
  ASSISTANT_RATE_LIMIT_PER_MINUTE?: string;
  INTERNAL_SERVICE_TOKEN?: string;
  BILLING_VERIFIER_URL?: string;
  BILLING_VERIFIER_TOKEN?: string;
  ANDROID_PACKAGE_NAME?: string;
  PLAY_PRODUCT_ENTITLEMENTS?: string;
  PLAY_LIFECYCLE_VERIFICATION_ENABLED?: string;
  AUTH_ACCOUNT_DELETER_URL?: string;
  AUTH_ACCOUNT_DELETER_TOKEN?: string;
  ACTION_EVIDENCE_VERIFIER_URL?: string;
  ACTION_EVIDENCE_VERIFIER_TOKEN?: string;
}

export interface UserPrincipal {
  type: "user";
  id: string;
  email?: string;
  displayName?: string;
  issuer: string;
}

export interface Variables {
  requestId: string;
  principal: UserPrincipal;
}

export interface AppEnvironment {
  Bindings: Bindings;
  Variables: Variables;
}

export type TaskStatus =
  | "planning"
  | "planned"
  | "waiting_approval"
  | "queued"
  | "running"
  | "completed"
  | "failed"
  | "cancelled";

export type VerificationState =
  | "not_executed"
  | "unverified"
  | "provider_verified"
  | "failed";

export interface TaskPlanStep {
  agent: string;
  action: string;
  requiresApproval: boolean;
  externalAction: boolean;
}

export interface TaskPlan {
  title: string;
  intent: string;
  reply: string;
  steps: TaskPlanStep[];
}

export interface TaskReceipt {
  taskId: string;
  status: TaskStatus;
  verificationState: VerificationState;
  approvalRequired: boolean;
  cancellable: boolean;
  createdAt: string;
  providerEvidence: {
    state: "NONE" | "CLIENT_SUPPLIED" | "PROVIDER_VERIFIED" | "REJECTED";
    provider: string | null;
    referenceId: string | null;
  };
}

export interface LineItemInput {
  description: string;
  quantityMillis: number;
  unitPriceCents: number;
}

export interface CalculatedLineItem extends LineItemInput {
  lineTotalCents: number;
}

export interface CalculatedDocumentTotal {
  items: CalculatedLineItem[];
  subtotalCents: number;
  taxCents: number;
  totalCents: number;
}
