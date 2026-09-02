import {
  createHash,
  timingSafeEqual,
} from 'node:crypto';

const rateWindows = new Map();
const MAX_RATE_IDENTITIES = 2048;

function headerValue(headers, name) {
  if (!headers) return '';
  if (typeof headers.get === 'function') return headers.get(name) || '';
  const direct = headers[name] ?? headers[name.toLowerCase()] ?? headers[name.toUpperCase()];
  return Array.isArray(direct) ? direct[0] || '' : direct || '';
}

export function constantTimeEqual(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string') return false;
  const leftBuffer = Buffer.from(left, 'utf8');
  const rightBuffer = Buffer.from(right, 'utf8');
  if (leftBuffer.length !== rightBuffer.length) return false;
  return timingSafeEqual(leftBuffer, rightBuffer);
}

export function authorizeBearer(headers, expectedToken) {
  if (!expectedToken) {
    return { ok: false, status: 503, code: 'AUTH_NOT_CONFIGURED' };
  }

  const authorization = String(headerValue(headers, 'authorization')).trim();
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match || !constantTimeEqual(match[1].trim(), expectedToken)) {
    return { ok: false, status: 401, code: 'UNAUTHORIZED' };
  }

  return { ok: true, status: 200, code: 'AUTHORIZED' };
}

export function validateOrigin(headers, allowedOrigins) {
  const origin = String(headerValue(headers, 'origin')).trim();
  if (!origin) return { ok: true, origin: '' };
  if (allowedOrigins.includes(origin)) return { ok: true, origin };
  return { ok: false, origin: '' };
}

export function isAllowedProject(project, allowedProjects) {
  return typeof project === 'string' && allowedProjects.includes(project.trim());
}

export function rateLimitIdentity(headers, remoteAddress = '') {
  const authorization = String(headerValue(headers, 'authorization'));
  const source = authorization || remoteAddress || 'anonymous';
  return createHash('sha256').update(source).digest('hex').slice(0, 24);
}

export function checkRateLimit(identity, limit, now = Date.now()) {
  const windowMs = 60_000;
  const current = rateWindows.get(identity);

  if (!current || now - current.startedAt >= windowMs) {
    if (rateWindows.size >= MAX_RATE_IDENTITIES) {
      for (const [key, value] of rateWindows) {
        if (now - value.startedAt >= windowMs) rateWindows.delete(key);
      }
      if (rateWindows.size >= MAX_RATE_IDENTITIES) {
        const oldestKey = rateWindows.keys().next().value;
        if (oldestKey) rateWindows.delete(oldestKey);
      }
    }
    rateWindows.set(identity, { startedAt: now, count: 1 });
    return { ok: true, remaining: Math.max(0, limit - 1), retryAfterSeconds: 0 };
  }

  current.count += 1;
  if (current.count > limit) {
    return {
      ok: false,
      remaining: 0,
      retryAfterSeconds: Math.max(1, Math.ceil((windowMs - (now - current.startedAt)) / 1000)),
    };
  }

  return { ok: true, remaining: Math.max(0, limit - current.count), retryAfterSeconds: 0 };
}

export function safeErrorCode(error) {
  const code = error && typeof error === 'object' && typeof error.code === 'string'
    ? error.code
    : 'INTERNAL_ERROR';
  return /^[A-Z0-9_]{3,64}$/.test(code) ? code : 'INTERNAL_ERROR';
}

export function resetRateLimitsForTests() {
  rateWindows.clear();
}
