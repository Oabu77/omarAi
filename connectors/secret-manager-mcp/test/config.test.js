import assert from 'node:assert/strict';
import test from 'node:test';

import { loadConfig, parseCsv, publicReadiness } from '../src/config.js';

test('parseCsv trims and deduplicates values', () => {
  assert.deepEqual(parseCsv('a, b, a,,'), ['a', 'b']);
});

test('configuration fails closed on project wildcards', () => {
  assert.throws(
    () => loadConfig({ ALLOWED_VERCEL_PROJECTS: '*' }),
    /wildcards are forbidden/,
  );
});

test('public readiness reveals booleans and counts, not credentials', () => {
  const config = loadConfig({
    MCP_BEARER_TOKEN: 'm'.repeat(40),
    VERCEL_TOKEN: 'v'.repeat(30),
    VERCEL_TEAM_ID: 'team_example',
    ALLOWED_VERCEL_PROJECTS: 'project-a,project-b',
  });
  const status = publicReadiness(config);
  assert.equal(status.ready, true);
  assert.equal(status.allowedProjectCount, 2);
  const serialized = JSON.stringify(status);
  assert.equal(serialized.includes('m'.repeat(40)), false);
  assert.equal(serialized.includes('v'.repeat(30)), false);
  assert.equal(serialized.includes('project-a'), false);
});
