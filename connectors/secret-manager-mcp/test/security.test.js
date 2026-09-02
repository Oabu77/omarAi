import assert from 'node:assert/strict';
import test from 'node:test';

import {
  authorizeBearer,
  checkRateLimit,
  constantTimeEqual,
  isAllowedProject,
  resetRateLimitsForTests,
  validateOrigin,
} from '../src/security.js';

test('bearer authorization fails closed when missing configuration', () => {
  assert.equal(authorizeBearer({}, '').status, 503);
});

test('bearer authorization accepts only the exact token', () => {
  const token = 't'.repeat(40);
  assert.equal(authorizeBearer({ authorization: `Bearer ${token}` }, token).ok, true);
  assert.equal(authorizeBearer({ authorization: `Bearer ${token}x` }, token).ok, false);
  assert.equal(constantTimeEqual(token, token), true);
});

test('project authorization is exact and rejects near matches', () => {
  assert.equal(isAllowedProject('alpha', ['alpha']), true);
  assert.equal(isAllowedProject('alpha-prod', ['alpha']), false);
});

test('origin policy accepts server-to-server and allowlisted origins only', () => {
  const allowed = ['https://chatgpt.com'];
  assert.equal(validateOrigin({}, allowed).ok, true);
  assert.equal(validateOrigin({ origin: 'https://chatgpt.com' }, allowed).ok, true);
  assert.equal(validateOrigin({ origin: 'https://attacker.example' }, allowed).ok, false);
});

test('rate limiter blocks requests over the configured limit', () => {
  resetRateLimitsForTests();
  assert.equal(checkRateLimit('identity', 2, 1_000).ok, true);
  assert.equal(checkRateLimit('identity', 2, 1_001).ok, true);
  assert.equal(checkRateLimit('identity', 2, 1_002).ok, false);
});
