import { describe, expect, it } from "vitest";
import { app } from "../src/index";

describe("public health boundary", () => {
  it("returns an explicit degraded state when bindings are absent", async () => {
    const response = await app.request("https://api.example.test/v1/health", {}, {});
    expect(response.status).toBe(503);
    const body = await response.json() as {
      data: {
        status: string;
        integrations: Record<string, { status: string; evidence: string }>;
      };
    };
    expect(body.data.status).toBe("DEGRADED");
    expect(body.data.integrations.database?.status).toBe("DISCONNECTED");
    expect(body.data.integrations.aiText?.status).toBe("DISCONNECTED");
    expect(body.data.integrations.googlePlayBilling?.status).toBe("DISCONNECTED");
  });

  it("does not call an AI binding connected before request-time inference", async () => {
    const response = await app.request("https://api.example.test/v1/health", {}, {
      AI: { async run() { return { response: "unused" }; } },
      JWT_ISSUER: "https://issuer.example",
      JWT_AUDIENCE: "omar-ai",
      JWKS_URL: "https://issuer.example/jwks.json",
    });
    expect(response.status).toBe(503);
    const body = await response.json() as {
      data: { integrations: Record<string, { status: string; configured: boolean; verified: boolean }> };
    };
    expect(body.data.integrations.aiText).toMatchObject({
      status: "PENDING",
      configured: true,
      verified: false,
    });
  });

  it("stays degraded until authenticated and inference evidence exists", async () => {
    const database = {
      prepare(sql: string) {
        return {
          async first() {
            if (sql.includes("FROM users")) return null;
            if (sql.includes("FROM assistant_messages")) return null;
            return { ok: 1 };
          },
        };
      },
    };
    const response = await app.request("https://api.example.test/v1/health", {}, {
      DB: database,
      AI: { async run() { return { response: "unused" }; } },
      JWT_ISSUER: "https://issuer.example",
      JWT_AUDIENCE: "omar-ai",
      JWKS_URL: "https://issuer.example/jwks.json",
    });
    expect(response.status).toBe(503);
    const body = await response.json() as {
      data: { status: string; coreReady: boolean; integrations: Record<string, { status: string }> };
    };
    expect(body.data.status).toBe("DEGRADED");
    expect(body.data.coreReady).toBe(false);
    expect(body.data.integrations.authentication?.status).toBe("PENDING");
    expect(body.data.integrations.aiText?.status).toBe("PENDING");
  });

  it("is ready after successful authentication and inference are evidenced", async () => {
    const database = {
      prepare(sql: string) {
        return {
          async first() {
            if (sql.includes("FROM users")) return { updated_at: "2026-08-30T16:20:00.000Z" };
            if (sql.includes("FROM assistant_messages")) {
              return {
                model_provider: "cloudflare-workers-ai",
                model_id: "@cf/zai-org/glm-5.2",
                created_at: "2026-08-30T16:21:00.000Z",
              };
            }
            return { ok: 1 };
          },
        };
      },
    };
    const response = await app.request("https://api.example.test/v1/health", {}, {
      DB: database,
      AI: { async run() { return { response: "unused" }; } },
      JWT_ISSUER: "https://issuer.example",
      JWT_AUDIENCE: "omar-ai",
      JWKS_URL: "https://issuer.example/jwks.json",
    });
    expect(response.status).toBe(200);
    const body = await response.json() as {
      data: { status: string; coreReady: boolean; integrations: Record<string, { status: string; verified: boolean }> };
    };
    expect(body.data.status).toBe("READY");
    expect(body.data.coreReady).toBe(true);
    expect(body.data.integrations.authentication).toMatchObject({ status: "CONNECTED", verified: true });
    expect(body.data.integrations.aiText).toMatchObject({ status: "CONNECTED", verified: true });
  });

  it("reports liveness without implying readiness", async () => {
    const response = await app.request("https://api.example.test/health/live", {}, {});
    expect(response.status).toBe(200);
    const body = await response.json() as { data: { status: string; note: string } };
    expect(body.data.status).toBe("LIVE");
    expect(body.data.note).toContain("does not imply");
  });
});

describe("browser and authentication boundaries", () => {
  it("rejects a browser origin not on the exact allowlist", async () => {
    const response = await app.request(
      "https://api.example.test/v1/health",
      { headers: { Origin: "https://attacker.example" } },
      { ALLOWED_ORIGINS: "https://admin.example" },
    );
    expect(response.status).toBe(403);
    expect(response.headers.get("access-control-allow-origin")).toBeNull();
  });

  it("does not permit wildcard origins", async () => {
    const response = await app.request(
      "https://api.example.test/v1/health",
      { headers: { Origin: "https://admin.example" } },
      { ALLOWED_ORIGINS: "*" },
    );
    expect(response.status).toBe(403);
  });

  it("fails closed when protected routes have no JWT verifier config", async () => {
    const response = await app.request("https://api.example.test/v1/integrations", {}, {});
    expect(response.status).toBe(503);
    const body = await response.json() as { error: { code: string } };
    expect(body.error.code).toBe("AUTH_DISCONNECTED");
  });
});
