import { describe, expect, it } from "vitest";
import { app } from "../src/index";

describe("public health boundary", () => {
  it("returns an explicit degraded state without exposing integration detail when bindings are absent", async () => {
    const response = await app.request("https://api.example.test/v1/health", {}, {});
    expect(response.status).toBe(503);
    const body = await response.json() as {
      data: {
        status: string;
        coreReady: boolean;
        integrations?: unknown;
      };
    };
    expect(body.data.status).toBe("DEGRADED");
    expect(body.data.coreReady).toBe(false);
    expect(body.data.integrations).toBeUndefined();
  });

  it("does not expose configured AI, provider, or verification metadata on anonymous health", async () => {
    const response = await app.request("https://api.example.test/v1/health", {}, {
      AI: { async run() { return { response: "unused" }; } },
      MODEL_TEXT: "@cf/example/private-model-id",
      JWT_ISSUER: "https://issuer.example",
      JWT_AUDIENCE: "omar-ai",
      JWKS_URL: "https://issuer.example/jwks.json",
      PLAY_VERIFIER_URL: "https://billing.example/verify",
      PLAY_VERIFIER_TOKEN: "test-only-placeholder-token-value",
      PLAY_PACKAGE_NAME: "com.example.omar",
      PLAY_ENTITLEMENT_MAP: "{}",
    });
    expect(response.status).toBe(503);
    const body = await response.json() as { data: Record<string, unknown> };
    expect(body.data.integrations).toBeUndefined();
    expect(JSON.stringify(body)).not.toContain("private-model-id");
    expect(JSON.stringify(body)).not.toContain("billing.example");
    expect(JSON.stringify(body)).not.toContain("PLAY_VERIFIER");
  });

  it("stays degraded until authenticated and inference evidence exists without revealing evidence timestamps", async () => {
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
      data: { status: string; coreReady: boolean; integrations?: unknown };
    };
    expect(body.data.status).toBe("DEGRADED");
    expect(body.data.coreReady).toBe(false);
    expect(body.data.integrations).toBeUndefined();
  });

  it("reports readiness without exposing authentication or inference evidence", async () => {
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
      data: { status: string; coreReady: boolean; integrations?: unknown };
    };
    expect(body.data.status).toBe("READY");
    expect(body.data.coreReady).toBe(true);
    expect(body.data.integrations).toBeUndefined();
    const serialized = JSON.stringify(body);
    expect(serialized).not.toContain("2026-08-30T16:20:00.000Z");
    expect(serialized).not.toContain("2026-08-30T16:21:00.000Z");
    expect(serialized).not.toContain("glm-5.2");
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
