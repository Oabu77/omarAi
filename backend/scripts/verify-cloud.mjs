import { randomUUID } from 'node:crypto';

const origin = 'https://omar-ai-api.darcloud.host';
const token = process.env.OMAR_SMOKE_ID_TOKEN?.trim();

async function request(path, options = {}) {
  const response = await fetch(`${origin}${path}`, {
    ...options,
    redirect: 'error',
    signal: AbortSignal.timeout(60_000),
  });
  let body;
  try { body = await response.json(); } catch {
    throw new Error(`${path} returned HTTP ${response.status} without an API response. Check routing/security configuration.`);
  }
  return { response, body };
}

const live = await request('/health/live');
if (live.response.status !== 200 || live.body.data?.service !== 'omar-ai-api') {
  throw new Error('The public URL did not reach the deployed Omar AI backend.');
}
const readiness = await request('/v1/health');
const integrations = readiness.body.data?.integrations;
if (![200, 503].includes(readiness.response.status)
    || integrations?.database?.status !== 'CONNECTED'
    || !integrations?.authentication?.configured
    || !integrations?.aiText?.configured) {
  throw new Error('Cloud database, authentication, or AI configuration is not ready.');
}
const unauthenticated = await request('/v1/tasks');
if (unauthenticated.response.status !== 401 || unauthenticated.body.error?.code !== 'AUTH_REQUIRED') {
  throw new Error('The public task route did not enforce the expected sign-in requirement.');
}
console.log('Public HTTPS API, D1 read, and sign-in requirement verified.');

if (!token) {
  console.log('AI_CHAT_NOT_VERIFIED: sign in with the Android app and send a message, or supply a fresh test-user Firebase ID token through OMAR_SMOKE_ID_TOKEN.');
  console.log('A deployed API and configured AI binding do not establish successful cloud inference.');
} else {
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    'Idempotency-Key': randomUUID(),
  };
  const planned = await request('/v1/tasks/plan', {
    method: 'POST', headers,
    body: JSON.stringify({ text: 'Say hello in one sentence. Do not perform any external action.', locale: 'en-US' }),
  });
  if (!planned.response.ok || !planned.body.ok) throw new Error('Signed-in cloud inference failed.');
  const health = await request('/v1/health');
  if (health.response.status !== 200 || !health.body.data?.coreReady) {
    throw new Error('Cloud inference returned, but persisted readiness evidence was not verified.');
  }
  console.log('Signed-in cloud AI response and persisted database evidence verified.');
}
