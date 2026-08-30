import type {
  CalculatedDocumentTotal,
  LineItemInput,
  TaskPlan,
  TaskPlanStep,
} from "./types";

export const ALLOWED_AGENTS = new Set([
  "Omar Core Agent",
  "Business Agent",
  "Receptionist Agent",
  "Sales Agent",
  "Customer-Service Agent",
  "Scheduling Agent",
  "CRM Agent",
  "Finance Agent",
  "Shopping Agent",
  "Research Agent",
  "Language Agent",
  "Contractor Agent",
  "Estimating Agent",
  "Marketplace Agent",
  "Reseller Agent",
  "Marketing Agent",
  "Company Builder Agent",
  "Document Agent",
  "Communication Agent",
  "Notification Agent",
  "Security Agent",
]);

const recordValue = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const isShortString = (value: unknown, max: number): value is string =>
  typeof value === "string" && value.trim().length > 0 && value.length <= max;

export function parseJsonObject(value: string): Record<string, unknown> {
  const withoutFence = value
    .trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "");
  const first = withoutFence.indexOf("{");
  const last = withoutFence.lastIndexOf("}");
  if (first < 0 || last <= first) {
    throw new Error("The AI provider did not return a JSON object.");
  }
  const parsed: unknown = JSON.parse(withoutFence.slice(first, last + 1));
  if (!recordValue(parsed)) {
    throw new Error("The AI provider response was not an object.");
  }
  return parsed;
}

export function validateTaskPlan(value: unknown): TaskPlan {
  if (!recordValue(value)) throw new Error("Plan must be an object.");
  if (!isShortString(value.title, 160)) throw new Error("Invalid plan title.");
  if (!isShortString(value.intent, 100)) throw new Error("Invalid plan intent.");
  if (!isShortString(value.reply, 4_000)) throw new Error("Invalid assistant reply.");
  if (!Array.isArray(value.steps) || value.steps.length < 1 || value.steps.length > 8) {
    throw new Error("A plan must contain between one and eight steps.");
  }

  const steps: TaskPlanStep[] = value.steps.map((item) => {
    if (!recordValue(item)) throw new Error("Invalid plan step.");
    if (!isShortString(item.agent, 80) || !ALLOWED_AGENTS.has(item.agent)) {
      throw new Error("Plan referenced an unsupported agent.");
    }
    if (!isShortString(item.action, 500)) throw new Error("Invalid step action.");
    if (typeof item.requiresApproval !== "boolean") {
      throw new Error("Plan step is missing requiresApproval.");
    }
    if (typeof item.externalAction !== "boolean") {
      throw new Error("Plan step is missing externalAction.");
    }
    return {
      agent: item.agent,
      action: item.action,
      requiresApproval: item.requiresApproval,
      externalAction: item.externalAction,
    };
  });

  return {
    title: value.title.trim(),
    intent: value.intent.trim(),
    reply: value.reply.trim(),
    steps,
  };
}

export function requireString(
  value: unknown,
  field: string,
  max = 500,
): string {
  if (!isShortString(value, max)) throw new Error(`${field} is required and must be at most ${max} characters.`);
  return value.trim();
}

export function optionalString(
  value: unknown,
  field: string,
  max = 500,
): string | null {
  if (value === undefined || value === null || value === "") return null;
  if (!isShortString(value, max)) throw new Error(`${field} must be at most ${max} characters.`);
  return value.trim();
}

export function requireNonNegativeInteger(value: unknown, field: string): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) {
    throw new Error(`${field} must be a non-negative integer.`);
  }
  return value as number;
}

export function calculateDocumentTotals(
  rawItems: unknown,
  taxRateBasisPoints: unknown,
): CalculatedDocumentTotal {
  if (!Array.isArray(rawItems) || rawItems.length < 1 || rawItems.length > 100) {
    throw new Error("items must contain between one and 100 line items.");
  }
  const basisPoints = requireNonNegativeInteger(taxRateBasisPoints ?? 0, "taxRateBasisPoints");
  if (basisPoints > 100_000) throw new Error("taxRateBasisPoints is outside the accepted range.");

  const items = rawItems.map((raw): LineItemInput & { lineTotalCents: number } => {
    if (!recordValue(raw)) throw new Error("Each line item must be an object.");
    const description = requireString(raw.description, "item.description", 500);
    const quantityMillis = requireNonNegativeInteger(raw.quantityMillis, "item.quantityMillis");
    const unitPriceCents = requireNonNegativeInteger(raw.unitPriceCents, "item.unitPriceCents");
    if (quantityMillis === 0) throw new Error("item.quantityMillis must be greater than zero.");
    const lineTotalCents = Math.round((quantityMillis * unitPriceCents) / 1_000);
    if (!Number.isSafeInteger(lineTotalCents)) throw new Error("Line item amount is too large.");
    return { description, quantityMillis, unitPriceCents, lineTotalCents };
  });
  const subtotalCents = items.reduce((sum, item) => sum + item.lineTotalCents, 0);
  const taxCents = Math.round((subtotalCents * basisPoints) / 10_000);
  const totalCents = subtotalCents + taxCents;
  if (![subtotalCents, taxCents, totalCents].every(Number.isSafeInteger)) {
    throw new Error("Document amount is too large.");
  }
  return { items, subtotalCents, taxCents, totalCents };
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return recordValue(value);
}

export function isModerationReportState(value: string): value is "prepared" {
  return value === "prepared";
}
