# Omar AI cloud deployment

This deployment runs the existing Android-compatible backend on Cloudflare Workers, with D1 for durable data and Workers AI for inference. It requires no laptop, local Ollama process, or phone-hosted server.

The signed Android 0.3.6 / code 9 manifest names `https://omar-ai-api.darcloud.host/` and Firebase project `banded-splicer-467704-c5`. The deployment retains both. It does not change or publish an Android binary.

## Current verified state — September 11, 2026

- Existing backend typecheck and all 30 tests passed.
- Offline Worker bundling passed (202,098 bytes). The deployment workflow YAML parsed, and fixture checks confirmed database reuse, omission of credentials, and refusal to discard bindings or change Firebase projects.
- A provider-aware Wrangler dry run could not finish because network approval was cancelled in this environment. Cloud deployment validation remains pending.
- Vercel `omar-ai-max.vercel.app` serves a control-plane profile; this does not prove working cloud chat.
- The API domain returned Cloudflare HTTP 403 / error 1010 from the verification environment.
- Cloudflare's dashboard blocked the current cloud-browser session at security verification.
- GitHub cloud-readiness run `34619097069` confirmed both the Cloudflare API token and account ID are absent. No cloud deployment has been performed in this work.

## Secure configuration

In this repository's GitHub Actions secrets, configure `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`. The account ID can instead be an Actions variable. Never put a token in chat, source code, Android build settings, or a workflow input.

Use credentials scoped to the intended Cloudflare account and the `darcloud.host` zone, with the permissions needed to deploy Workers, bind Workers AI, manage the Omar AI D1 database, and attach the Worker custom domain. Do not use a global API key. No paid plan is purchased or upgraded by this workflow; normal usage limits and any existing account billing still apply.

Then rerun the **Omar AI cloud readiness** workflow on `codex/omar-ai-cloud`. It checks access, installs locked dependencies, runs backend tests, inspects existing Worker bindings, reuses its bound D1 database (or creates the named `omar-ai` database when none exists), applies additive migrations, and deploys the API to the existing Android hostname.

The deployment stops rather than silently discarding unexpected resource bindings or replacing a different Firebase configuration. Existing Worker secrets are retained. Before rerunning against an existing production service, review the current version and database recovery point in Cloudflare; Worker code rollback does not undo database migrations.

## Local invocation of the same cloud deployment

With the two credentials securely supplied as environment variables, from `backend/`:

```sh
npm ci
npm run check
node scripts/configure-cloud.mjs
npx --no-install wrangler deploy --config wrangler.cloud.json --dry-run
npx --no-install wrangler d1 migrations apply DB --remote --config wrangler.cloud.json
npx --no-install wrangler deploy --config wrangler.cloud.json --keep-vars
node scripts/verify-cloud.mjs
```

The configuration script contacts Cloudflare and may create the named database. The dry-run command bundles the Worker without publishing it. Generated `wrangler.cloud.json` stays ignored and contains no secret values.

## Acceptance check

Infrastructure verification checks the public HTTPS route, a real D1 read, authentication configuration, AI binding configuration, and rejection of anonymous task requests. A 503 readiness response is expected until successful authenticated use is persisted; this is not reported as working AI.

To verify inference, sign in to the Android app and send an ordinary question. For the scripted check, a fresh Firebase ID token for a dedicated test user can be supplied securely as `OMAR_SMOKE_ID_TOKEN`; it is never printed. The script sends one harmless request and checks persisted readiness evidence. Its prompt and response remain in that test account's cloud history.

The selected Workers AI model must be available to the account. Optional external-action, email, media, billing, and identity-deletion adapters remain separately gated; this deployment does not claim those integrations are connected. If Cloudflare blocks the API, inspect the specific security event and correct the app's API routing while preserving authentication; do not disable protection for the whole domain.
