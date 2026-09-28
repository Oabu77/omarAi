# Omar AI Secret Manager MCP

A read-minimized MCP connector for secret **metadata** and hygiene checks. The connector uses Vercel sensitive environment variables as its first backend and is designed so the model never receives plaintext secret values, ciphertext, access tokens, or provider-internal content hints.

## Security contract

- The MCP endpoint fails closed unless a long `MCP_BEARER_TOKEN` is configured.
- The Vercel API is always queried with `decrypt=false`.
- Project access uses an exact allowlist; `*` is rejected.
- Provider responses are rebuilt from an explicit metadata allowlist.
- `value`, `legacyValue`, `vsmValue`, `internalContentHint`, `contentHint`, and nested encrypted values are discarded.
- Requests are size-limited and rate-limited, upstream calls have timeouts, responses are `no-store`, and browser origins are restricted.
- A fresh MCP server and transport are created per stateless request to prevent cross-client state reuse.
- No write, rotate, reveal, export, or delete tool exists in this release.

## Tools

### `secret_manager_status`
Returns readiness booleans and optional provider connectivity status. It does not return project names or credentials.

### `list_secret_metadata`
Returns allowlisted project metadata: key name, storage type, target, branch, comment, visibility, system flag, and timestamps.

### `audit_secret_hygiene`
Flags keys that look sensitive but are stored with a non-sensitive variable type.

## Runtime variables

Copy `.env.example` only as a naming reference. Do not commit a populated `.env` file.

Required:

- `MCP_BEARER_TOKEN`: at least 32 random characters; development/testing authentication only.
- `VERCEL_TOKEN`: scoped Vercel API token stored as a sensitive runtime variable.
- `VERCEL_TEAM_ID`: exact authorized team ID.
- `ALLOWED_VERCEL_PROJECTS`: comma-separated exact project IDs or names.

Recommended:

- `ALLOWED_ORIGINS=https://chatgpt.com,https://chat.openai.com`
- `MCP_RATE_LIMIT_PER_MINUTE=30`
- `UPSTREAM_TIMEOUT_MS=8000`

## Local validation

```bash
npm install
npm run check
npm test
MCP_BEARER_TOKEN="$(openssl rand -hex 32)" \
VERCEL_TOKEN="runtime-only" \
VERCEL_TEAM_ID="team_example" \
ALLOWED_VERCEL_PROJECTS="project_example" \
npm start
```

The local server binds to `127.0.0.1:8787` by default:

- MCP: `http://127.0.0.1:8787/mcp`
- Readiness: `http://127.0.0.1:8787/healthz`

## Vercel deployment

Create a dedicated Vercel project whose Root Directory is `connectors/secret-manager-mcp`. Add every required variable as a **sensitive** project variable. Do not reuse a broadly privileged personal Vercel token; create a narrowly scoped token for only the team/projects this connector must inventory.

The remote endpoint will be:

```text
https://<dedicated-domain>/mcp
```

## ChatGPT connection gate

The static bearer mode is for controlled development and connector scanning only. Before wider workspace publication, place the MCP endpoint behind an OAuth 2.1-compatible authorization server with refresh-token support, or an approved secure MCP tunnel. Then add the remote HTTPS endpoint from ChatGPT's custom-app/developer-mode UI and scan the three read-only tools.

A write-capable release must use a separate out-of-band one-time secret-ingest flow. Plaintext values must never be passed as MCP tool arguments or returned in MCP results.
