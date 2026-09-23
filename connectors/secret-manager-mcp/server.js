import http from 'node:http';

import healthHandler from './api/healthz.js';
import mcpHandler from './api/mcp.js';

const host = process.env.HOST || '127.0.0.1';
const port = Number.parseInt(process.env.PORT || '8787', 10);

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (url.pathname === '/healthz') return healthHandler(req, res);
    if (url.pathname === '/mcp') return await mcpHandler(req, res);
    res.statusCode = 404;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    return res.end(JSON.stringify({ error: 'NOT_FOUND' }));
  } catch {
    if (!res.headersSent) {
      res.statusCode = 500;
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      return res.end(JSON.stringify({ error: 'INTERNAL_ERROR' }));
    }
    return res.end();
  }
});

server.requestTimeout = 15_000;
server.headersTimeout = 10_000;
server.keepAliveTimeout = 5_000;

server.listen(port, host, () => {
  console.log(`Omar AI Secret Manager MCP listening on http://${host}:${port}/mcp`);
});
