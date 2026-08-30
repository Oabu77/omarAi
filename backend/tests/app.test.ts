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
