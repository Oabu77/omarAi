import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';

import { loadConfig } from '../src/config.js';
import {
  applySecurityHeaders,
  readJsonBody,
  sendJson,
} from '../src/http.js';
import { createSecretManagerMcpServer } from '../src/mcp.js';
import {
  authorizeBearer,
  checkRateLimit,
  rateLimitIdentity,
  validateOrigin,
} from '../src/security.js';

export default async function handler(req, res) {
  applySecurityHeaders(res);

  let config;
  try {
    config = loadConfig();
  } catch {
    return sendJson(res, 503, { error: 'CONFIGURATION_INVALID' });
  }

  const originCheck = validateOrigin(req.headers, config.allowedOrigins);
  if (!originCheck.ok) return sendJson(res, 403, { error: 'ORIGIN_NOT_ALLOWED' });
  if (originCheck.origin) {
    res.setHeader('Access-Control-Allow-Origin', originCheck.origin);
    res.setHeader('Vary', 'Origin');
  }

  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, Mcp-Protocol-Version');
    res.statusCode = 204;
    return res.end();
  }

  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST, OPTIONS');
    return sendJson(res, 405, { error: 'METHOD_NOT_ALLOWED' });
  }

  const authorization = authorizeBearer(req.headers, config.mcpBearerToken);
  if (!authorization.ok) {
    if (authorization.status === 401) res.setHeader('WWW-Authenticate', 'Bearer');
    return sendJson(res, authorization.status, { error: authorization.code });
  }

  const identity = rateLimitIdentity(req.headers, req.socket?.remoteAddress || '');
  const rate = checkRateLimit(identity, config.rateLimitPerMinute);
  res.setHeader('X-RateLimit-Remaining', String(rate.remaining));
  if (!rate.ok) {
    res.setHeader('Retry-After', String(rate.retryAfterSeconds));
    return sendJson(res, 429, { error: 'RATE_LIMITED' });
  }

  let body;
  try {
    body = await readJsonBody(req, config.maxRequestBytes);
  } catch (error) {
    const status = error?.code === 'REQUEST_TOO_LARGE' ? 413 : 400;
    return sendJson(res, status, { error: error?.code || 'INVALID_REQUEST' });
  }

  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
    enableJsonResponse: true,
  });
  const server = createSecretManagerMcpServer(config);

  const closeTransport = () => {
    Promise.resolve(transport.close()).catch(() => {});
  };
  res.once('close', closeTransport);

  try {
    await server.connect(transport);
    await transport.handleRequest(req, res, body);
  } catch {
    return sendJson(res, 500, {
      jsonrpc: '2.0',
      error: { code: -32603, message: 'Internal server error' },
      id: body?.id ?? null,
    });
  }
}
