export class RequestTooLargeError extends Error {
  constructor() {
    super('Request too large');
    this.name = 'RequestTooLargeError';
    this.code = 'REQUEST_TOO_LARGE';
  }
}

export class InvalidJsonError extends Error {
  constructor() {
    super('Invalid JSON');
    this.name = 'InvalidJsonError';
    this.code = 'INVALID_JSON';
  }
}

function byteLength(value) {
  return Buffer.byteLength(typeof value === 'string' ? value : JSON.stringify(value), 'utf8');
}

export async function readJsonBody(req, maxBytes) {
  if (req.body !== undefined && req.body !== null) {
    if (byteLength(req.body) > maxBytes) throw new RequestTooLargeError();
    if (Buffer.isBuffer(req.body)) {
      try {
        return JSON.parse(req.body.toString('utf8'));
      } catch {
        throw new InvalidJsonError();
      }
    }
    if (typeof req.body === 'string') {
      try {
        return JSON.parse(req.body);
      } catch {
        throw new InvalidJsonError();
      }
    }
    if (typeof req.body === 'object') return req.body;
    throw new InvalidJsonError();
  }

  const contentLength = Number.parseInt(String(req.headers?.['content-length'] || '0'), 10);
  if (Number.isFinite(contentLength) && contentLength > maxBytes) {
    throw new RequestTooLargeError();
  }

  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > maxBytes) throw new RequestTooLargeError();
    chunks.push(buffer);
  }

  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new InvalidJsonError();
  }
}

export function applySecurityHeaders(res) {
  res.setHeader('Cache-Control', 'no-store, max-age=0');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  res.setHeader('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none'");
}

export function sendJson(res, status, payload) {
  if (res.headersSent || res.writableEnded) return;
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(payload));
}
