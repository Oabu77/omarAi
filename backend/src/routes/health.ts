import type { Hono } from "hono";
import { isAuthConfigured } from "../auth";
import { isAccountDeleterConfigured } from "./account";
import { isBillingConfigured } from "./billing";
import { isActionEvidenceVerifierConfigured } from "../evidence";
import type { AppEnvironment, Bindings } from "../types";
import { success } from "../http";

type IntegrationStatus = "CONNECTED" | "DISCONNECTED" | "PENDING" | "FAILED";

interface IntegrationState {
  status: IntegrationStatus;
  mode: "production" | "test" | "unconfigured";
  configured: boolean;
  verified: boolean;
  evidence: string;
  model?: string;
  adapterImplemented?: boolean;
}

function mode(env: Bindings): "production" | "test" {
  return env.ENVIRONMENT?.toLowerCase() === "production" ? "production" : "test";
}

function disconnected(evidence: string): IntegrationState {
  return {
    status: "DISCONNECTED",
    mode: "unconfigured",
    configured: false,
    verified: false,
    evidence,
  };
}

async function integrationStates(env: Bindings): Promise<Record<string, IntegrationState>> {
  let database: IntegrationState;
  let authenticatedRequestAt: string | undefined;
  let successfulInferenceAt: string | undefined;
  let successfulInferenceModel: string | undefined;
  if (!env.DB) {
    database = disconnected("D1 binding DB is absent.");
  } else {
    try {
      await env.DB.prepare("SELECT 1 AS ok").first();
      database = {
        status: "CONNECTED",
        mode: mode(env),
        configured: true,
        verified: true,
        evidence: "A D1 read succeeded during this request.",
      };
      try {
        const authenticatedRequest = await env.DB
          .prepare("SELECT updated_at FROM users ORDER BY updated_at DESC LIMIT 1")
          .first<{ updated_at: string }>();
        authenticatedRequestAt = authenticatedRequest?.updated_at;

        const successfulInference = await env.DB
          .prepare(
            `SELECT model_provider, model_id, created_at
               FROM assistant_messages
              WHERE role = 'assistant'
                AND model_provider IS NOT NULL
                AND model_id IS NOT NULL
              ORDER BY created_at DESC
              LIMIT 1`,
          )
          .first<{ model_provider: string; model_id: string; created_at: string }>();
        successfulInferenceAt = successfulInference?.created_at;
        successfulInferenceModel = successfulInference
          ? `${successfulInference.model_provider}/${successfulInference.model_id}`
          : undefined;
      } catch {
        // A fresh database can pass the D1 probe before migrations or first use.
        // Keep dependent integrations pending until request-time evidence exists.
      }
    } catch {
      database = {
        status: "FAILED",
        mode: mode(env),
        configured: true,
        verified: false,
        evidence: "The D1 binding exists, but its read probe failed.",
      };
    }
  }

  const auth = isAuthConfigured(env)
    ? authenticatedRequestAt
      ? {
          status: "CONNECTED" as const,
          mode: mode(env),
          configured: true,
          verified: true,
          evidence: `A JWT-authenticated request was persisted at ${authenticatedRequestAt}.`,
        }
      : {
          status: "PENDING" as const,
          mode: mode(env),
          configured: true,
          verified: false,
          evidence: "JWT verification is configured, but no successful authenticated request has been persisted yet.",
        }
    : disconnected("JWT_ISSUER, JWT_AUDIENCE, or JWKS_URL is absent.");
  const textAi = env.AI
    ? successfulInferenceAt
      ? {
          status: "CONNECTED" as const,
          mode: mode(env),
          configured: true,
          verified: true,
          evidence: `A model-backed assistant response was persisted at ${successfulInferenceAt}.`,
          model: successfulInferenceModel || env.MODEL_TEXT?.trim() || "@cf/zai-org/glm-5.2",
          adapterImplemented: true,
        }
      : {
          status: "PENDING" as const,
          mode: mode(env),
          configured: true,
          verified: false,
          evidence: "Cloudflare Workers AI is configured, but no successful inference has been persisted yet.",
          model: env.MODEL_TEXT?.trim() || "@cf/zai-org/glm-5.2",
          adapterImplemented: true,
        }
    : disconnected("Cloudflare Workers AI binding AI is absent.");
  const billing = isBillingConfigured(env)
    ? {
        status: "PENDING" as const,
        mode: mode(env),
        configured: true,
        verified: false,
        evidence: "Server verifier configuration exists; each purchase is verified on demand.",
      }
    : disconnected("Google Play server verifier URL/token, package name, or entitlement map is absent.");
  const accountDeletion = isAccountDeleterConfigured(env)
    ? {
        status: "PENDING" as const,
        mode: mode(env),
        configured: true,
        verified: false,
        evidence: "Identity deleter configuration exists; deletion is verified per request.",
      }
    : disconnected("Identity-provider account deleter URL/token is absent.");
  const actionEvidence = isActionEvidenceVerifierConfigured(env)
    ? {
        status: "PENDING" as const,
        mode: mode(env),
        configured: true,
        verified: false,
        evidence: "Provider-evidence verifier is configured; individual task receipts are verified on demand.",
      }
    : disconnected("Provider-evidence verifier URL/token is absent; terminal task reports remain unverified.");

  return {
    database,
    authentication: auth,
    aiText: textAi,
    aiVision: {
      ...disconnected("A vision adapter is not implemented in backend v1."),
      configured: Boolean(env.MODEL_VISION && env.AI),
      adapterImplemented: false,
      ...(env.MODEL_VISION ? { model: env.MODEL_VISION } : {}),
    },
    aiTranscription: {
      ...disconnected("A transcription adapter is not implemented in backend v1."),
      configured: Boolean(env.MODEL_TRANSCRIPTION && env.AI),
      adapterImplemented: false,
      ...(env.MODEL_TRANSCRIPTION ? { model: env.MODEL_TRANSCRIPTION } : {}),
    },
    googlePlayBilling: billing,
    actionEvidence,
    accountDeletion,
    customerPayments: disconnected("No customer-payment provider webhook is implemented in backend v1."),
    phoneReceptionist: disconnected("No telephony provider is implemented in backend v1."),
    financialAccounts: disconnected("No financial-data provider is implemented in backend v1."),
    email: disconnected("No email provider is implemented in backend v1."),
    pushNotifications: disconnected("No push-notification provider is implemented in backend v1."),
    fileStorage: disconnected("No object-storage binding or media service is implemented in backend v1."),
  };
}

export function registerHealthRoutes(app: Hono<AppEnvironment>): void {
  app.get("/v1/health", async (c) => {
    const integrations = await integrationStates(c.env);
    const coreReady =
      integrations.database?.status === "CONNECTED" &&
      integrations.authentication?.status === "CONNECTED" &&
      integrations.aiText?.status === "CONNECTED";
    const payload = {
      service: "omar-ai-api",
      version: "0.2.0",
      status: coreReady ? "READY" : "DEGRADED",
      checkedAt: new Date().toISOString(),
      coreReady,
      integrations,
    };
    return success(c, payload, coreReady ? 200 : 503);
  });

  app.get("/health/live", (c) => success(c, {
    service: "omar-ai-api",
    status: "LIVE",
    checkedAt: new Date().toISOString(),
    note: "Liveness does not imply integrations are connected.",
  }));

  app.get("/v1/integrations", async (c) => {
    const integrations = await integrationStates(c.env);
    return success(c, {
      checkedAt: new Date().toISOString(),
      environment: mode(c.env),
      integrations,
    });
  });
}
