import assert from 'node:assert/strict';
import test from 'node:test';

import { loadConfig } from '../src/config.js';
import {
  assertNoSecretFields,
  auditMetadata,
  buildVercelEnvUrl,
  sanitizeEnvRecord,
  VercelSecretMetadataClient,
} from '../src/vercel.js';

const config = loadConfig({
  MCP_BEARER_TOKEN: 'm'.repeat(40),
  VERCEL_TOKEN: 'v'.repeat(30),
  VERCEL_TEAM_ID: 'team_example',
  ALLOWED_VERCEL_PROJECTS: 'project-a',
});

test('Vercel URL always explicitly disables decryption', () => {
  const url = buildVercelEnvUrl({ project: 'project-a', teamId: 'team_example' });
  assert.equal(url.searchParams.get('decrypt'), 'false');
  assert.equal(url.searchParams.get('teamId'), 'team_example');
});

test('sanitizer strips every value-bearing field', () => {
  const raw = {
    id: 'env_1',
    key: 'OPENAI_API_KEY',
    type: 'sensitive',
    target: ['production'],
    value: 'plaintext-must-never-escape',
    vsmValue: 'ciphertext-must-never-escape',
    legacyValue: 'legacy-must-never-escape',
    internalContentHint: { encryptedValue: 'must-never-escape' },
    contentHint: { storeId: 'private-store' },
  };
  const sanitized = sanitizeEnvRecord(raw);
  assert.equal(assertNoSecretFields(sanitized), true);
  const serialized = JSON.stringify(sanitized);
  assert.equal(serialized.includes('must-never-escape'), false);
  assert.equal(serialized.includes('private-store'), false);
});

test('metadata client returns only sanitized allowlisted project records', async () => {
  let requestedUrl;
  const fakeFetch = async (url) => {
    requestedUrl = url;
    return new Response(JSON.stringify({
      envs: [{
        id: 'env_1',
        key: 'STRIPE_SECRET_KEY',
        type: 'sensitive',
        target: ['production'],
        value: 'synthetic-secret-value',
      }],
    }), { status: 200, headers: { 'content-type': 'application/json' } });
  };

  const client = new VercelSecretMetadataClient(config, fakeFetch);
  const records = await client.listMetadata({ project: 'project-a', target: 'production' });
  assert.equal(requestedUrl.searchParams.get('decrypt'), 'false');
  assert.equal(records.length, 1);
  assert.equal(JSON.stringify(records).includes('synthetic-secret-value'), false);
  await assert.rejects(
    () => client.listMetadata({ project: 'project-b' }),
    (error) => error.code === 'PROJECT_NOT_ALLOWED',
  );
});

test('hygiene audit flags sensitive-looking plain variables only', () => {
  const findings = auditMetadata([
    { key: 'OPENAI_API_KEY', type: 'plain', target: ['production'], system: false },
    { key: 'STRIPE_WEBHOOK_SECRET', type: 'sensitive', target: ['production'], system: false },
    { key: 'PUBLIC_SITE_URL', type: 'plain', target: ['production'], system: false },
  ]);
  assert.deepEqual(findings.map((finding) => finding.key), ['OPENAI_API_KEY']);
});
