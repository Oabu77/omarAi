import { loadConfig, publicReadiness } from '../src/config.js';
import { applySecurityHeaders, sendJson } from '../src/http.js';

export default function handler(_req, res) {
  applySecurityHeaders(res);
  try {
    const readiness = publicReadiness(loadConfig());
    return sendJson(res, 200, readiness);
  } catch {
    return sendJson(res, 200, {
      service: 'omar-ai-secret-manager',
      version: '0.1.0',
      provider: 'vercel',
      ready: false,
      authConfigured: false,
      backendConfigured: false,
      allowedProjectCount: 0,
    });
  }
}
