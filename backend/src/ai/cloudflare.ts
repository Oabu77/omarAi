import { parseJsonObject, validateTaskPlan } from "../contracts";
import type { Bindings } from "../types";
import { AiProviderError, type AiProvider, type PlanRequest, type PlanResult } from "./provider";

const DEFAULT_TEXT_MODEL = "@cf/zai-org/glm-5.2";

function outputText(value: unknown): string | null {
  if (typeof value === "string") return value;
  if (typeof value !== "object" || value === null) return null;
  const record = value as Record<string, unknown>;
  for (const key of ["response", "output_text", "result", "text"]) {
    if (typeof record[key] === "string") return record[key] as string;
  }
  return null;
}

export class CloudflareWorkersAiProvider implements AiProvider {
  constructor(private readonly env: Bindings) {}

  async plan(request: PlanRequest): Promise<PlanResult> {
    if (!this.env.AI) {
      throw new AiProviderError("AI_DISCONNECTED", "The Cloudflare Workers AI binding is not configured.");
    }
    const model = this.env.MODEL_TEXT?.trim() || DEFAULT_TEXT_MODEL;
    const system = [
      "You are the planning layer for Omar AI.",
      "Return only one JSON object with title, intent, reply, and steps.",
      "Each step must have agent, action, requiresApproval, and externalAction.",
      "Use only the agent names supplied below.",
      "A plan is not proof an action occurred. Never say submitted, paid, booked, sent, filed, connected, or completed unless the request itself includes verifiable provider evidence.",
      "Mark any external communication, purchase, payment, booking, account change, legal filing, or destructive action as requiresApproval=true and externalAction=true.",
      "Use one to eight concise steps.",
      "Allowed agents: Omar Core Agent; Business Agent; Receptionist Agent; Sales Agent; Customer-Service Agent; Scheduling Agent; CRM Agent; Finance Agent; Shopping Agent; Research Agent; Language Agent; Contractor Agent; Estimating Agent; Marketplace Agent; Reseller Agent; Marketing Agent; Company Builder Agent; Document Agent; Communication Agent; Notification Agent; Security Agent.",
      `Reply in locale ${request.locale}.`,
    ].join("\n");

    let raw: unknown;
    try {
      raw = await this.env.AI.run(model, {
        messages: [
          { role: "system", content: system },
          { role: "user", content: request.text },
        ],
        max_tokens: 1_600,
        temperature: 0.2,
      });
    } catch {
      throw new AiProviderError("AI_PROVIDER_FAILED", "Cloudflare Workers AI did not complete the planning request.");
    }

    const text = outputText(raw);
    if (!text) throw new AiProviderError("AI_INVALID_OUTPUT", "Cloudflare Workers AI returned no usable text output.");
    try {
      const plan = validateTaskPlan(parseJsonObject(text));
      return {
        plan,
        provider: "cloudflare-workers-ai",
        model,
        evidence: { state: "PROVIDER_VERIFIED", operation: "model_inference" },
      };
    } catch {
      throw new AiProviderError("AI_INVALID_OUTPUT", "Cloudflare Workers AI returned an invalid task plan.");
    }
  }
}
