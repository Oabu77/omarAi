import { describe, expect, it } from "vitest";
import {
  calculateDocumentTotals,
  isModerationReportState,
  parseJsonObject,
  validateTaskPlan,
} from "../src/contracts";

describe("task plan contract", () => {
  it("accepts a supported typed plan wrapped in a JSON fence", () => {
    const parsed = parseJsonObject(`\`\`\`json
      {
        "title": "Prepare an estimate",
        "intent": "contractor_estimate",
        "reply": "I prepared a plan; no external action has run.",
        "steps": [
          {
            "agent": "Estimating Agent",
            "action": "Prepare estimate inputs",
            "requiresApproval": false,
            "externalAction": false
          }
        ]
      }
    \`\`\``);
    expect(validateTaskPlan(parsed)).toMatchObject({
      title: "Prepare an estimate",
      steps: [{ agent: "Estimating Agent", externalAction: false }],
    });
  });

  it("rejects unsupported agents and ambiguous permission flags", () => {
    expect(() => validateTaskPlan({
      title: "Do it",
      intent: "unknown",
      reply: "Planning only.",
      steps: [{
        agent: "Unlimited Agent",
        action: "Do everything",
        requiresApproval: "maybe",
        externalAction: true,
      }],
    })).toThrow();
  });
});

describe("AI output moderation report contract", () => {
  it("cannot be used as an execution completion receipt", () => {
    expect(isModerationReportState("prepared")).toBe(true);
    expect(isModerationReportState("submitted")).toBe(false);
    expect(isModerationReportState("completed")).toBe(false);
    expect(isModerationReportState("failed")).toBe(false);
  });
});

describe("financial document calculation", () => {
  it("derives line totals, tax, and total using integer cents", () => {
    expect(calculateDocumentTotals([
      { description: "Labor", quantityMillis: 1_500, unitPriceCents: 10_000 },
      { description: "Disposal", quantityMillis: 1_000, unitPriceCents: 5_000 },
    ], 775)).toEqual({
      items: [
        { description: "Labor", quantityMillis: 1_500, unitPriceCents: 10_000, lineTotalCents: 15_000 },
        { description: "Disposal", quantityMillis: 1_000, unitPriceCents: 5_000, lineTotalCents: 5_000 },
      ],
      subtotalCents: 20_000,
      taxCents: 1_550,
      totalCents: 21_550,
    });
  });

  it("does not accept client totals or unsafe numbers as line inputs", () => {
    expect(() => calculateDocumentTotals([
      { description: "Bad", quantityMillis: 0, unitPriceCents: 100 },
    ], 0)).toThrow("greater than zero");
    expect(() => calculateDocumentTotals([
      { description: "Bad", quantityMillis: 1_000, unitPriceCents: -1 },
    ], 0)).toThrow("non-negative integer");
  });
});
