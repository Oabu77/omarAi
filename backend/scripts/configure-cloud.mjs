import { readFile, writeFile } from 'node:fs/promises';

const account = process.env.CLOUDFLARE_ACCOUNT_ID?.trim();
const token = process.env.CLOUDFLARE_API_TOKEN?.trim();
if (!account || !/^[a-f0-9]{32}$/i.test(account) || !token) {
  throw new Error('Set CLOUDFLARE_ACCOUNT_ID and a scoped CLOUDFLARE_API_TOKEN securely before cloud deployment. Global API keys are intentionally unsupported.');
}
const authenticationHeaders = { Authorization: `Bearer ${token}` };

const workerName = 'omar-ai-api';
const project = 'banded-splicer-467704-c5';
const origin = 'https://omar-ai-api.darcloud.host';
const root = `https://api.cloudflare.com/client/v4/accounts/${account}`;

async function api(path, { method = 'GET', body, optional = false } = {}) {
  const response = await fetch(`${root}${path}`, {
    method,
    headers: { ...authenticationHeaders, 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(30_000),
  });
  if (optional && response.status === 404) return null;
  const data = await response.json();
  if (!response.ok || !data.success) {
    // Provider response bodies can include account details: report only status/codes.
    const codes = data.errors?.map(error => error.code).join(',') || 'unknown';
    throw new Error(`Cloudflare ${method} ${path} failed: HTTP ${response.status}, codes ${codes}.`);
  }
  return data.result;
}

// Inspect the existing Worker before changing resources. Never silently replace
// an existing database or remove bindings belonging to another implementation.
const settings = await api(`/workers/scripts/${workerName}/settings`, { optional: true });
const bindings = settings?.bindings ?? [];
const unsupported = bindings.filter(binding => !['plain_text', 'secret_text'].includes(binding.type)
  && !(binding.type === 'd1' && binding.name === 'DB')
  && !(binding.type === 'ai' && binding.name === 'AI'));
if (unsupported.length) {
  throw new Error('Existing Worker has additional resource bindings. Review and preserve them before deployment.');
}
const previousVars = Object.fromEntries(bindings.filter(b => b.type === 'plain_text').map(b => [b.name, b.text]));
if (previousVars.AI_READINESS_EVIDENCE_TTL_SECONDS) {
  const healthSource = await readFile(new URL('../src/routes/health.ts', import.meta.url), 'utf8');
  if (!healthSource.includes('/internal/readiness/ai')) {
    throw new Error('The deployed Worker includes newer protected AI-readiness checks. Reconcile that deployed hotfix into this source before replacing the live Worker.');
  }
}
const authVars = {
  JWT_ISSUER: `https://securetoken.google.com/${project}`,
  JWT_AUDIENCE: project,
  JWKS_URL: 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com',
};
for (const [name, value] of Object.entries(authVars)) {
  if (previousVars[name] && previousVars[name] !== value) {
    throw new Error(`Existing ${name} differs from the signed Android release. Reconcile it before deployment.`);
  }
}

const existingDb = bindings.find(b => b.type === 'd1' && b.name === 'DB');
let database;
if (existingDb) {
  database = await api(`/d1/database/${encodeURIComponent(existingDb.id)}`);
} else {
  const databases = await api('/d1/database?name=omar-ai&per_page=100');
  const matches = databases.filter(db => db.name === 'omar-ai');
  if (matches.length > 1) throw new Error('Multiple Omar AI databases found; select the authoritative database first.');
  database = matches[0] ?? await api('/d1/database', { method: 'POST', body: { name: 'omar-ai' } });
}
if (!database?.uuid) throw new Error('Cloudflare did not return a valid D1 database ID.');

const config = {
  name: workerName,
  main: 'src/index.ts',
  account_id: account,
  compatibility_date: '2025-01-29',
  keep_vars: true,
  workers_dev: false,
  preview_urls: false,
  routes: [{ pattern: new URL(origin).hostname, custom_domain: true }],
  d1_databases: [{ binding: 'DB', database_name: database.name, database_id: database.uuid, migrations_dir: 'migrations' }],
  ai: { binding: 'AI' },
  vars: {
    ALLOWED_ORIGINS: previousVars.ALLOWED_ORIGINS ?? origin,
    RATE_LIMIT_PER_MINUTE: previousVars.RATE_LIMIT_PER_MINUTE ?? '120',
    ASSISTANT_RATE_LIMIT_PER_MINUTE: previousVars.ASSISTANT_RATE_LIMIT_PER_MINUTE ?? '20',
    MODEL_TEXT: previousVars.MODEL_TEXT ?? '@cf/zai-org/glm-5.2',
    PLAY_LIFECYCLE_VERIFICATION_ENABLED: previousVars.PLAY_LIFECYCLE_VERIFICATION_ENABLED ?? 'false',
    ...authVars,
    ENVIRONMENT: 'production',
  },
};
// No API tokens or secret bindings are serialized. Existing Worker secrets stay
// with Cloudflare; keep_vars preserves dashboard-managed configuration.
await writeFile('wrangler.cloud.json', `${JSON.stringify(config, null, 2)}\n`, { mode: 0o600 });
console.log(`Configured ${workerName} with the ${existingDb ? 'existing bound' : 'named'} D1 database.`);
console.log(`Android API origin remains ${origin}/. No local runtime is required.`);
