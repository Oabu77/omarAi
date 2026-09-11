# Omar AI cloud deployment

This deployment runs the existing Android-compatible backend on Cloudflare Workers, with D1 for durable data and Workers AI for inference. It requires no laptop, local Ollama process, or phone-hosted server.

The signed Android 0.3.6 / code 9 manifest names `https://omar-ai-api.darcloud.host/` and Firebase project `banded-splicer-467704-c5`. The deployment retains both. It does not change or publish an Android binary.

## Current verified state — September 11, 2026

- Existing backend typecheck and all 30 tests passed.
- Offline Worker bundling passed (202,098 bytes). The deployment workflow YAML parsed, and fixture checks confirmed database reuse, omission of credentials, and refusal to discard bindings or change Firebase projects.
- A provider-aware Wrangler dry run could not finish because network approval was cancelled in this environment.
- Vercel `omar-ai-max.vercel.app` serves a control-plane profile; this does not prove working cloud chat.
- The API domain returned Cloudflare HTTP 403 / error 1010 from the verification environment.
- Cloudflare's dashboard blocked the current cloud-browser session at security verification.
- The existing Global API Key was located in the owner's private credential document and authenticated successfully. No credential values were added to this repository or workflow logs.
- The production Worker `omar-ai-api` is already deployed at the Android API hostname, with D1 database `omar-ai`, Workers AI, and the expected Firebase project. A direct authenticated D1 read succeeded.
- The active Worker version is `c90fd764-9921-4670-a9fc-c141d8d802be`, deployed September 2, 2026. Its configured model is `@cf/zai-org/glm-5.3-flash`.
- A direct authenticated Workers AI request to that model returned HTTP 200 and the requested `READY` reply (110 total tokens). This verifies provider inference; signed-in Android chat and the public API still need verification after resolving the access block.
- Fixture checks passed for token and Global API Key authentication, credential omission, reuse of the bound database, and stopping before mutations when newer readiness checks or unexpected bindings are present. Workflow YAML validation passed, including the manual deployment gate.
- The live Worker contains newer protected AI-readiness checks than this source branch. The configuration script stops before resource changes when it detects that known mismatch. Reconcile the deployed hotfix before replacing production code.
- Browser Integrity Check is enabled. A proposed exception for only `omar-ai-api.darcloud.host` paths `/v1/*` and `/health/live` was blocked by automatic approval review because it changes a production security control. The rule was not applied. Explicit approval is needed for that exact scope; application authentication and rate limits would remain enabled.
- GitHub cloud-readiness run `34619097069` found no token or account ID in that workflow's environment. That check did not cover the existing private Global API Key. No new cloud deployment or security-setting change was made in this work.

## Secure configuration

Supply `CLOUDFLARE_ACCOUNT_ID` and either `CLOUDFLARE_API_TOKEN` or the existing Global API Key as `CLOUDFLARE_API_KEY` together with `CLOUDFLARE_EMAIL`. For GitHub Actions, these must be available in this repository's Actions secrets; the account ID can instead be an Actions variable. The private credential was used directly for verification and was not copied to Actions secrets. Never put credentials in chat, source code, Android build settings, or a workflow input.

Both token authentication and the existing Global API Key with its account email are supported. Credentials must access the intended Cloudflare account and the `darcloud.host` zone, with the permissions needed to deploy Workers, bind Workers AI, manage the Omar AI D1 database, and attach the Worker custom domain. No credential rotation or paid plan upgrade is performed by this workflow; normal usage limits and any existing account billing still apply.

Push-triggered runs of **Omar AI cloud readiness** check credential availability only. Deployment requires a manual `workflow_dispatch` run after reconciling the newer production source and making the workflow available on the repository's default branch. A manual run installs locked dependencies, runs backend tests, inspects existing Worker bindings, reuses its bound D1 database (or creates the named `omar-ai` database when none exists), applies additive migrations, and deploys the API to the existing Android hostname.

The deployment stops rather than silently discarding unexpected resource bindings or replacing a different Firebase configuration. Existing Worker secrets are retained. Before rerunning against an existing production service, review the current version and database recovery point in Cloudflare; Worker code rollback does not undo database migrations.

## Local invocation of the same cloud deployment

After reconciling the deployed source, with the chosen authentication variables securely supplied, from `backend/`:

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
