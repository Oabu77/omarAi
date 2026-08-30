import type { TaskPlan } from "../types";

export interface PlanRequest {
  text: string;
  locale: string;
  conversationId: string;
  businessId?: string;
}

export interface PlanResult {
  plan: TaskPlan;
  provider: string;
  model: string;
  evidence: {
    state: "PROVIDER_VERIFIED";
    operation: "model_inference";
  };
}

export interface AiProvider {
  plan(request: PlanRequest): Promise<PlanResult>;
}

export class AiProviderError extends Error {
  constructor(
    public readonly code: "AI_DISCONNECTED" | "AI_PROVIDER_FAILED" | "AI_INVALID_OUTPUT",
    message: string,
  ) {
    super(message);
  }
}
