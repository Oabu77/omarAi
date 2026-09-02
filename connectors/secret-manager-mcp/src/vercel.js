import { isAllowedProject } from './security.js';

const SECRET_FIELD_NAMES = new Set([
  'value',
  'legacyValue',
  'vsmValue',
  'internalContentHint',
  'contentHint',
  'encryptedValue',
]);

const SAFE_TYPES = new Set(['encrypted', 'secret', 'sensitive', 'system']);
const SENSITIVE_KEY_PATTERN = /(?:^|_)(?:API_?KEY|TOKEN|SECRET|PASSWORD|PASSCODE|PRIVATE_?KEY|CLIENT_?SECRET|SIGNING_?KEY|WEBHOOK_?SECRET|DATABASE_?URL|MONGO_?URI|AUTH_?TOKEN)(?:$|_)/i;
const PROVIDER_KEY_PATTERN = /(?:OPENAI|STRIPE|CLOUDFLARE|GITHUB|GOOGLE|VERCEL|AWS|AZURE|TWILIO|SENDGRID|KRAKEN|ALPACA)/i;

export class SecretManagerError extends Error {
  constructor(code, status = 500) {
    super(code);
    this.name = 'SecretManagerError';
    this.code = code;
    this.status = status;
  }
}

function safeArray(value) {
  if (Array.isArray(value)) return value.filter((entry) => typeof entry === 'string').slice(0, 16);
  return typeof value === 'string' && value ? [value] : [];
}

function safeString(value, maxLength = 256) {
  return typeof value === 'string' ? value.slice(0, maxLength) : '';
}

function safeTimestamp(value) {
  return Number.isFinite(Number(value)) ? Number(value) : null;
}

export function sanitizeEnvRecord(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const key = safeString(raw.key, 256);
  if (!key) return null;

  return Object.freeze({
    id: safeString(raw.id, 256),
    key,
    type: safeString(raw.type, 32),
    target: safeArray(raw.target),
    gitBranch: safeString(raw.gitBranch, 256),
    comment: safeString(raw.comment, 512),
    visibility: safeString(raw.visibility, 32),
    system: Boolean(raw.system),
    createdAt: safeTimestamp(raw.createdAt),
    updatedAt: safeTimestamp(raw.updatedAt),
  });
}

export function assertNoSecretFields(value) {
  if (!value || typeof value !== 'object') return true;
  for (const [key, child] of Object.entries(value)) {
    if (SECRET_FIELD_NAMES.has(key)) return false;
    if (child && typeof child === 'object' && !assertNoSecretFields(child)) return false;
  }
  return true;
}

export function buildVercelEnvUrl({ project, teamId, gitBranch }) {
  const url = new URL(`https://api.vercel.com/v10/projects/${encodeURIComponent(project)}/env`);
  url.searchParams.set('decrypt', 'false');
  url.searchParams.set('teamId', teamId);
  url.searchParams.set('source', 'omar-ai-secret-manager-mcp');
  if (gitBranch) url.searchParams.set('gitBranch', gitBranch);
  return url;
}

export function auditMetadata(records) {
  const findings = [];
  for (const record of records) {
    if (!record || record.system) continue;
    const nameLooksSensitive = SENSITIVE_KEY_PATTERN.test(record.key) || PROVIDER_KEY_PATTERN.test(record.key);
    const safelyTyped = SAFE_TYPES.has(String(record.type).toLowerCase());
    if (nameLooksSensitive && !safelyTyped) {
      findings.push(Object.freeze({
        key: record.key,
        type: record.type || 'unknown',
        target: record.target,
        severity: 'high',
        recommendation: 'Store this key as a sensitive or encrypted runtime variable.',
      }));
    }
  }
  return findings.sort((left, right) => left.key.localeCompare(right.key));
}

async function boundedResponseText(response, maxBytes) {
  const advertised = Number.parseInt(response.headers?.get?.('content-length') || '0', 10);
  if (Number.isFinite(advertised) && advertised > maxBytes) {
    throw new SecretManagerError('UPSTREAM_RESPONSE_TOO_LARGE', 502);
  }
  const text = await response.text();
  if (Buffer.byteLength(text, 'utf8') > maxBytes) {
    throw new SecretManagerError('UPSTREAM_RESPONSE_TOO_LARGE', 502);
  }
  return text;
}

export class VercelSecretMetadataClient {
  constructor(config, fetchImpl = globalThis.fetch) {
    this.config = config;
    this.fetchImpl = fetchImpl;
  }

  assertReady() {
    if (!this.config.vercelToken || !this.config.vercelTeamId || this.config.allowedProjects.length === 0) {
      throw new SecretManagerError('BACKEND_NOT_CONFIGURED', 503);
    }
  }

  assertProject(project) {
    if (!isAllowedProject(project, this.config.allowedProjects)) {
      throw new SecretManagerError('PROJECT_NOT_ALLOWED', 403);
    }
  }

  async listMetadata({ project, target, gitBranch = '' }) {
    this.assertReady();
    this.assertProject(project);

    const url = buildVercelEnvUrl({ project, teamId: this.config.vercelTeamId, gitBranch });
    let response;
    try {
      response = await this.fetchImpl(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${this.config.vercelToken}`,
          Accept: 'application/json',
          'User-Agent': 'omar-ai-secret-manager-mcp/0.1.0',
        },
        redirect: 'error',
        signal: AbortSignal.timeout(this.config.upstreamTimeoutMs),
      });
    } catch {
      throw new SecretManagerError('UPSTREAM_UNAVAILABLE', 502);
    }

    const text = await boundedResponseText(response, this.config.maxUpstreamBytes);
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        throw new SecretManagerError('BACKEND_AUTHORIZATION_FAILED', 502);
      }
      if (response.status === 404) throw new SecretManagerError('PROJECT_NOT_FOUND', 404);
      throw new SecretManagerError('UPSTREAM_REQUEST_FAILED', 502);
    }

    let payload;
    try {
      payload = JSON.parse(text);
    } catch {
      throw new SecretManagerError('UPSTREAM_INVALID_RESPONSE', 502);
    }

    const rawRecords = Array.isArray(payload) ? payload : Array.isArray(payload.envs) ? payload.envs : [];
    const records = rawRecords
      .map(sanitizeEnvRecord)
      .filter(Boolean)
      .filter((record) => !target || record.target.includes(target))
      .sort((left, right) => left.key.localeCompare(right.key));

    if (!assertNoSecretFields(records)) {
      throw new SecretManagerError('REDACTION_INVARIANT_FAILED', 500);
    }

    return records;
  }

  async probe(project = this.config.allowedProjects[0]) {
    try {
      await this.listMetadata({ project });
      return { reachable: true, authorized: true };
    } catch (error) {
      if (error instanceof SecretManagerError && error.code === 'BACKEND_AUTHORIZATION_FAILED') {
        return { reachable: true, authorized: false };
      }
      if (error instanceof SecretManagerError && error.code === 'BACKEND_NOT_CONFIGURED') {
        return { reachable: false, authorized: false };
      }
      return { reachable: false, authorized: false };
    }
  }
}
