import { createRemoteJWKSet, jwtVerify } from "jose";
import type { MiddlewareHandler } from "hono";
import { ApiError } from "./http";
import type { AppEnvironment, Bindings, UserPrincipal } from "./types";

const jwksCache = new Map<string, ReturnType<typeof createRemoteJWKSet>>();

function authConfig(env: Bindings): { issuer: string; audience: string; jwksUrl: string } {
  const issuer = env.JWT_ISSUER?.trim();
  const audience = env.JWT_AUDIENCE?.trim();
  const jwksUrl = env.JWKS_URL?.trim();
  if (!issuer || !audience || !jwksUrl) {
    throw new ApiError(
      503,
      "AUTH_DISCONNECTED",
      "JWT verification is not configured. JWT_ISSUER, JWT_AUDIENCE, and JWKS_URL are required.",
    );
  }
  let parsed: URL;
  try {
    parsed = new URL(jwksUrl);
  } catch {
    throw new ApiError(503, "AUTH_CONFIGURATION_INVALID", "JWKS_URL is invalid.");
  }
  if (parsed.protocol !== "https:") {
    throw new ApiError(503, "AUTH_CONFIGURATION_INVALID", "JWKS_URL must use HTTPS.");
  }
  return { issuer, audience, jwksUrl };
}

function jwks(url: string): ReturnType<typeof createRemoteJWKSet> {
  const existing = jwksCache.get(url);
  if (existing) return existing;
  const created = createRemoteJWKSet(new URL(url), {
    cooldownDuration: 30_000,
    timeoutDuration: 5_000,
  });
  jwksCache.set(url, created);
  return created;
}

function optionalClaim(value: unknown, max: number): string | undefined {
  return typeof value === "string" && value.length > 0 && value.length <= max ? value : undefined;
}

export async function verifyBearerToken(env: Bindings, header: string | undefined): Promise<UserPrincipal> {
  const config = authConfig(env);
  if (!header?.startsWith("Bearer ")) {
    throw new ApiError(401, "AUTH_REQUIRED", "A Bearer access token is required.");
  }
  const token = header.slice("Bearer ".length).trim();
  if (!token || token.length > 16_384) {
    throw new ApiError(401, "AUTH_INVALID", "The access token is invalid.");
  }

  try {
    const verified = await jwtVerify(token, jwks(config.jwksUrl), {
      issuer: config.issuer,
      audience: config.audience,
      algorithms: ["RS256", "ES256"],
      clockTolerance: 5,
      maxTokenAge: "24h",
    });
    const subject = verified.payload.sub;
    if (!subject || subject.length > 255) {
      throw new Error("Missing subject");
    }
    const email = optionalClaim(verified.payload.email, 320);
    const displayName = optionalClaim(verified.payload.name, 200);
    return {
      type: "user",
      id: subject,
      ...(email ? { email } : {}),
      ...(displayName ? { displayName } : {}),
      issuer: config.issuer,
    };
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(401, "AUTH_INVALID", "The access token could not be verified.");
  }
}

export const authenticationMiddleware: MiddlewareHandler<AppEnvironment> = async (c, next) => {
  const principal = await verifyBearerToken(c.env, c.req.header("authorization"));
  const db = c.env.DB;
  if (!db) throw new ApiError(503, "DATABASE_DISCONNECTED", "The D1 database binding is not configured.");
  const now = new Date().toISOString();
  await db
    .prepare(
      `INSERT INTO users (id, email, display_name, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         email = COALESCE(excluded.email, users.email),
         display_name = COALESCE(excluded.display_name, users.display_name),
         updated_at = excluded.updated_at`,
    )
    .bind(principal.id, principal.email ?? null, principal.displayName ?? null, now, now)
    .run();
  c.set("principal", principal);
  await next();
};

export function isAuthConfigured(env: Bindings): boolean {
  return Boolean(env.JWT_ISSUER?.trim() && env.JWT_AUDIENCE?.trim() && env.JWKS_URL?.trim());
}
