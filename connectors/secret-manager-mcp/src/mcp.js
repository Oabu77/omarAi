import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

import { publicReadiness } from './config.js';
import { safeErrorCode } from './security.js';
import {
  auditMetadata,
  SecretManagerError,
  VercelSecretMetadataClient,
} from './vercel.js';

function success(result, summary) {
  return {
    content: [{ type: 'text', text: summary }],
    structuredContent: result,
  };
}

function failure(error) {
  const code = error instanceof SecretManagerError ? error.code : safeErrorCode(error);
  return {
    isError: true,
    content: [{ type: 'text', text: `Secret manager request failed: ${code}` }],
    structuredContent: { ok: false, error: code },
  };
}

export function createSecretManagerMcpServer(config, fetchImpl = globalThis.fetch) {
  const client = new VercelSecretMetadataClient(config, fetchImpl);
  const server = new McpServer({
    name: config.serviceName,
    version: config.serviceVersion,
  });

  server.registerTool(
    'secret_manager_status',
    {
      title: 'Secret Manager Status',
      description: 'Use this when you need readiness and connectivity status for the configured secret metadata backend. Never returns secret values, project names, credentials, or tokens.',
      inputSchema: {
        probeBackend: z.boolean().optional().default(false),
      },
      annotations: {
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
    },
    async ({ probeBackend = false }) => {
      try {
        const readiness = publicReadiness(config);
        const probe = probeBackend && readiness.backendConfigured
          ? await client.probe()
          : { reachable: null, authorized: null };
        const result = Object.freeze({ ok: true, ...readiness, backendProbe: probe });
        return success(result, `Secret manager ready: ${result.ready}; backend configured: ${result.backendConfigured}.`);
      } catch (error) {
        return failure(error);
      }
    },
  );

  server.registerTool(
    'list_secret_metadata',
    {
      title: 'List Secret Metadata',
      description: 'Use this when you need an inventory of configured environment-variable names, types, targets, and timestamps for one explicitly allowlisted Vercel project. Values and encrypted payloads are always removed.',
      inputSchema: {
        project: z.string().min(1).max(256),
        target: z.enum(['production', 'preview', 'development']).optional(),
        gitBranch: z.string().min(1).max(256).optional(),
        limit: z.number().int().min(1).max(200).optional().default(100),
      },
      annotations: {
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: true,
      },
    },
    async ({ project, target, gitBranch, limit = 100 }) => {
      try {
        const records = await client.listMetadata({ project, target, gitBranch });
        const limited = records.slice(0, limit);
        const result = Object.freeze({
          ok: true,
          provider: config.provider,
          count: limited.length,
          truncated: records.length > limited.length,
          records: limited,
        });
        return success(result, `Found ${limited.length} secret metadata record(s); no values were returned.`);
      } catch (error) {
        return failure(error);
      }
    },
  );

  server.registerTool(
    'audit_secret_hygiene',
    {
      title: 'Audit Secret Hygiene',
      description: 'Use this when you need to detect keys that look sensitive but are not stored using a sensitive, secret, or encrypted variable type. Reads metadata only and never returns values.',
      inputSchema: {
        project: z.string().min(1).max(256),
        target: z.enum(['production', 'preview', 'development']).optional(),
        gitBranch: z.string().min(1).max(256).optional(),
      },
      annotations: {
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: true,
      },
    },
    async ({ project, target, gitBranch }) => {
      try {
        const records = await client.listMetadata({ project, target, gitBranch });
        const findings = auditMetadata(records);
        const result = Object.freeze({
          ok: true,
          provider: config.provider,
          scanned: records.length,
          findingCount: findings.length,
          findings,
        });
        return success(result, `Scanned ${records.length} metadata record(s) and found ${findings.length} hygiene issue(s).`);
      } catch (error) {
        return failure(error);
      }
    },
  );

  return server;
}
