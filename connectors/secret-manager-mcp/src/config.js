const MIN_BEARER_LENGTH = 32;
const DEFAULT_ALLOWED_ORIGINS = [
  'https://chatgpt.com',
  'https://chat.openai.com',
];

export function parseCsv(value) {
  if (typeof value !== 'string') return [];
  return [...new Set(value.split(',').map((entry) => entry.trim()).filter(Boolean))];
}

function boundedInteger(value, fallback, min, max) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

function usableSecret(value, minimumLength = 1) {
  return typeof value === 'string' && value.trim().length >= minimumLength
    ? value.trim()
    : '';
}

export function loadConfig(env = process.env) {
  const allowedProjects = parseCsv(env.ALLOWED_VERCEL_PROJECTS);
  if (allowedProjects.includes('*')) {
    throw new Error('ALLOWED_VERCEL_PROJECTS must use exact project IDs or names; wildcards are forbidden.');
  }

  return Object.freeze({
    serviceName: 'omar-ai-secret-manager',
    serviceVersion: '0.1.0',
    provider: 'vercel',
    mcpBearerToken: usableSecret(env.MCP_BEARER_TOKEN, MIN_BEARER_LENGTH),
    vercelToken: usableSecret(env.VERCEL_TOKEN, 20),
    vercelTeamId: usableSecret(env.VERCEL_TEAM_ID, 8),
    allowedProjects,
    allowedOrigins: parseCsv(env.ALLOWED_ORIGINS).length
      ? parseCsv(env.ALLOWED_ORIGINS)
      : DEFAULT_ALLOWED_ORIGINS,
    rateLimitPerMinute: boundedInteger(env.MCP_RATE_LIMIT_PER_MINUTE, 30, 1, 600),
    upstreamTimeoutMs: boundedInteger(env.UPSTREAM_TIMEOUT_MS, 8000, 1000, 30000),
    maxRequestBytes: boundedInteger(env.MAX_REQUEST_BYTES, 262144, 1024, 1048576),
    maxUpstreamBytes: boundedInteger(env.MAX_UPSTREAM_BYTES, 1048576, 4096, 4194304),
  });
}

export function publicReadiness(config) {
  const authConfigured = Boolean(config.mcpBearerToken);
  const backendConfigured = Boolean(
    config.vercelToken &&
    config.vercelTeamId &&
    config.allowedProjects.length > 0
  );

  return Object.freeze({
    service: config.serviceName,
    version: config.serviceVersion,
    provider: config.provider,
    ready: authConfigured && backendConfigured,
    authConfigured,
    backendConfigured,
    allowedProjectCount: config.allowedProjects.length,
  });
}
