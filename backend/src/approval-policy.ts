import type { TaskPlan, TaskPlanStep } from "./types";

export interface EnforcedPlan {
  plan: TaskPlan;
  approvalRequired: boolean;
  policyReasons: string[];
}

const EXTERNAL_ACTION_PATTERN =
  /\b(send|pay|purchase|buy|sell|list|order|book|schedule|submit|file|register|delete|remove|cancel|transfer|refund|publish|post|call|message|email|text|invite|sign|execute|connect|share|upload|dispatch|hire|fire|payout|withdraw|trade)\b/i;

const SENSITIVE_CHANGE_PATTERN =
  /\b(change|update|reset|close|open|enable|disable|approve|accept|decline)\b.{0,80}\b(account|password|permission|role|subscription|payment|invoice|appointment|booking|contract|filing|employee|provider)\b/i;

const FINANCIAL_OR_LEGAL_PATTERN =
  /\b(charge|debit|credit|wire|bank transfer|investment|tax filing|legal filing|incorporat(?:e|ion)|license application|sign contract)\b/i;

const CONSEQUENCE_NOUN_PATTERN =
  /\b(make|process|initiate|authorize|complete|place|issue|move|create)\b.{0,60}\b(payment|purchase|order|booking|appointment|transfer|payout|withdrawal|trade|refund|listing|account)\b/i;

function riskyText(text: string): string[] {
  const reasons: string[] = [];
  if (EXTERNAL_ACTION_PATTERN.test(text)) reasons.push("external_or_consequential_action");
  if (SENSITIVE_CHANGE_PATTERN.test(text)) reasons.push("sensitive_state_change");
  if (FINANCIAL_OR_LEGAL_PATTERN.test(text)) reasons.push("financial_or_legal_action");
  if (CONSEQUENCE_NOUN_PATTERN.test(text)) reasons.push("consequential_state_change");
  return reasons;
}

function enforceStep(step: TaskPlanStep, requestForcesApproval: boolean): TaskPlanStep {
  const actionRisk = riskyText(step.action);
  const serverClassifiedExternal = actionRisk.includes("external_or_consequential_action");
  const externalAction = step.externalAction || serverClassifiedExternal;
  return {
    ...step,
    externalAction,
    requiresApproval:
      step.requiresApproval ||
      externalAction ||
      requestForcesApproval ||
      actionRisk.length > 0,
  };
}

/**
 * The model may request stricter approval, but it can never relax this policy.
 * Request text and planned actions are independently classified server-side.
 */
export function enforceApprovalPolicy(requestText: string, modelPlan: TaskPlan): EnforcedPlan {
  const requestReasons = riskyText(requestText);
  const requestForcesApproval = requestReasons.length > 0;
  const steps = modelPlan.steps.map((step) => enforceStep(step, requestForcesApproval));
  const policyReasons = new Set(requestReasons);
  for (const step of steps) {
    if (step.externalAction) policyReasons.add("external_action_requires_approval");
    if (step.requiresApproval && !modelPlan.steps[steps.indexOf(step)]?.requiresApproval) {
      policyReasons.add("server_policy_override");
    }
  }
  return {
    plan: { ...modelPlan, steps },
    approvalRequired: steps.some((step) => step.requiresApproval),
    policyReasons: Array.from(policyReasons),
  };
}
