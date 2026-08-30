import { describe, expect, it } from "vitest";
import { enforceApprovalPolicy } from "../src/approval-policy";
import type { TaskPlan } from "../src/types";

const safePlan: TaskPlan = {
  title: "Review metrics",
  intent: "business_review",
  reply: "I prepared a review plan.",
  steps: [{
    agent: "Business Agent",
    action: "Read the stored dashboard metrics",
    requiresApproval: false,
    externalAction: false,
  }],
};

describe("server approval policy", () => {
  it("allows a read-only internal plan without an approval gate", () => {
    const result = enforceApprovalPolicy("Show my stored business metrics", safePlan);
    expect(result.approvalRequired).toBe(false);
    expect(result.plan.steps[0]).toMatchObject({ requiresApproval: false, externalAction: false });
  });

  it("forces approval when the request asks for a consequential external action", () => {
    const result = enforceApprovalPolicy("Book this customer for tomorrow", safePlan);
    expect(result.approvalRequired).toBe(true);
    expect(result.plan.steps[0]?.requiresApproval).toBe(true);
    expect(result.policyReasons).toContain("server_policy_override");
  });

  it("does not let a model mark sending or payment actions as approval-free", () => {
    const result = enforceApprovalPolicy("Help with this customer", {
      ...safePlan,
      steps: [{
        agent: "Finance Agent",
        action: "Pay the invoice and email the receipt",
        requiresApproval: false,
        externalAction: false,
      }],
    });
    expect(result.plan.steps[0]).toMatchObject({ requiresApproval: true, externalAction: true });
  });

  it("catches consequential noun forms even when the model emits a harmless-looking plan", () => {
    expect(enforceApprovalPolicy("Make this payment today", safePlan).approvalRequired).toBe(true);
    expect(enforceApprovalPolicy("Sell and list this couch", safePlan).approvalRequired).toBe(true);
  });
});
