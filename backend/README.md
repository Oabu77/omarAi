# Omar AI Worker backend v1

This directory contains the production-oriented Cloudflare Worker boundary for the Omar AI Android client. It is deliberately fail-closed: an absent database, JWT verifier, AI binding, billing verifier, identity deleter, or external provider is reported as disconnected. The API never turns client text into proof that an external action succeeded.

## Implemented in v1

- Hono/TypeScript Cloudflare Worker with exact-origin CORS, security headers, request IDs, JWT verification, D1-backed rate-limit hooks, idempotency reservations, and audit records.
- Cloudflare Workers AI text-planning adapter. `MODEL_TEXT` is configurable and defaults to `@cf/zai-org/glm-5.2`; the `AI` binding must exist.
- Typed `ASK -> PLAN -> APPROVE -> QUEUE -> VERIFY -> REPORT` task receipts. Approval is enforced by a server risk policy; model flags can only make the gate stricter.
- Task list/detail, approval, cancellation, AI/executor output receipts, and user-visible audit history.
- D1-backed businesses, memberships, customers, leads, jobs, draft estimates, and draft invoices.
- Server-calculated line totals, tax, job profit, and dashboard metrics. Revenue only comes from `provider_verified` payment rows; v1 does not offer an endpoint that lets a client fabricate those rows.
- Server-authoritative Google Play verification boundary with immutable transaction/token ownership. A trusted verifier must prove purchase, acknowledgment, and lifecycle state; without fresh RTDN/revocation evidence the row remains `pending_activation` and grants no access.
- User AI-output/moderation reports are isolated from executor task receipts. Only a trusted backend evidence adapter can verify external task completion.
- User data export and coordinated identity-provider/application-data deletion.
- D1 migrations and unit/contract tests.

The following are **not** implemented and report `DISCONNECTED` or a 503 response: vision, transcription, file storage, push notifications, telephony/receptionist calls, financial-account connections, customer payment collection/webhooks, email, marketplace-provider availability, and external task executors. A queued task is not a completed task.

## Android contract

Every JSON response uses this envelope:

```json
{
  "ok": true,
  "data": {},
  "requestId": "uuid-or-forwarded-request-id"
}
```

Errors use `ok: false` and `error: { code, message, details? }`. Protected routes require `Authorization: Bearer <JWT>`. Mutating routes require `Idempotency-Key` with 8-128 characters from `[A-Za-z0-9._:-]`.

| Method | Route | State/evidence behavior |
|---|---|---|
| GET | `/health/live` | Process liveness only; explicitly does not imply readiness. |
| GET | `/v1/health` | Public readiness and integration states; returns 503 when core D1/auth/AI configuration is not ready. |
| GET | `/v1/integrations` | Authenticated detailed `CONNECTED`, `PENDING`, `DISCONNECTED`, or `FAILED` states. |
| POST | `/v1/tasks/plan` | Calls Workers AI, validates a typed plan, persists messages/task/steps, and returns a task receipt. No external action is claimed. |
| POST | `/v1/assistant/messages` | Compatibility alias for `/v1/tasks/plan`. |
| GET | `/v1/tasks` | Lists stored tasks; optional exact `status` query. |
| GET | `/v1/tasks/{id}` | Returns the stored task and steps. |
| POST | `/v1/tasks/{id}/approve` | Requires `{ "approved": true }`; records scope and changes waiting steps to `queued`. |
| POST | `/v1/tasks/{id}/cancel` | Requires a JSON object; cancels supported non-terminal task states. |
| POST | `/v1/reports/ai-output` | Stores a user moderation report only. It accepts `prepared` and can never transition task state. |
| GET | `/v1/reports/ai-output` | Lists moderation-report metadata for the current user. |
| POST | `/v1/tasks/{id}/execution-reports` | Trusted executor receipt boundary. Completion changes state only after a separate server evidence adapter verifies the provider receipt. |
| GET | `/v1/tasks/{id}/reports` | Returns provider/client output receipts. |
| GET | `/v1/audit` | Returns up to 200 audit events for the current user. |
| GET/POST | `/v1/businesses` | Lists/creates businesses. |
| GET | `/v1/businesses/{businessId}/dashboard` | Derives UTC-window metrics from stored D1 rows; unavailable metrics are `null`, not invented. |
| GET/POST | `/v1/businesses/{businessId}/customers` | CRM customer list/create. |
| GET/POST | `/v1/businesses/{businessId}/leads` | CRM lead list/create. |
| GET/POST | `/v1/businesses/{businessId}/jobs` | Job list/create, including derived profitability. |
| GET/POST | `/v1/businesses/{businessId}/estimates` | Draft estimate list/create with server-derived totals. |
| GET/POST | `/v1/businesses/{businessId}/invoices` | Draft invoice list/create with server-derived totals. |
| POST | `/v1/businesses/{businessId}/invoices/{invoiceId}/payments` | Deliberately returns `PAYMENTS_DISCONNECTED` until a verified payment webhook exists. |
| POST | `/v1/billing/google-play/verify` | Verifies purchase and acknowledgment, enforces transaction ownership, hashes the token, and returns `pending_activation` unless lifecycle evidence is operational. |
| GET | `/v1/billing/entitlements` | Returns server-verified entitlement rows. |
| GET | `/v1/account/export` | Exports the user and accessible business data, capped at 10,000 rows per collection. |
| DELETE | `/v1/account` | Requires body `{ "confirm": true }`, `Idempotency-Key`, and `X-Deletion-Confirmation: DELETE OMAR AI ACCOUNT`. |

Task status is one of:

```text
planning | planned | waiting_approval | queued | running | completed | failed | cancelled
```

Task verification is one of:

```text
not_executed | unverified | provider_verified | failed
```

Provider evidence is returned as an exact state (`NONE`, `CLIENT_SUPPLIED`, `PROVIDER_VERIFIED`, or `REJECTED`) plus provider and reference ID. Workers AI inference evidence proves only that a model inference returned; it never proves an external action occurred.

### Plan request

```http
POST /v1/tasks/plan
Authorization: Bearer <JWT>
Idempotency-Key: 11b8dc63-6e8c-455a-a831-e8b29848e9af
Content-Type: application/json

{
  "text": "Prepare a junk-removal estimate for this customer",
  "locale": "en-US",
  "conversationId": "optional-client-conversation-id",
  "businessId": "optional-authorized-business-id"
}
```

Attachments currently produce `FILE_SERVICE_DISCONNECTED`; raw file content is not silently ignored.

### AI output moderation report

A user/client may submit a `prepared` AI-output report. It is stored in the moderation table as `CLIENT_SUPPLIED`; it cannot submit, complete, or fail the task. Other `reportedState` values are rejected.

```json
{
  "taskId": "task-uuid",
  "provider": "cloudflare-workers-ai",
  "modelId": "@cf/zai-org/glm-5.2",
  "output": "Draft prepared; nothing was sent.",
  "reportedState": "prepared"
}
```

Execution receipts use `/v1/tasks/{id}/execution-reports`, never the moderation route. A trusted executor must send `X-Omar-Internal-Token` and a provider reference. `ACTION_EVIDENCE_VERIFIER_URL` independently checks that reference and must return matching task, provider, state, timestamp, and evidence digest. A caller-supplied `PROVIDER_VERIFIED` string is ignored and cannot complete a task.

The executor request includes:

```json
{
  "providerEvidence": {
    "state": "PROVIDER_VERIFIED",
    "referenceId": "provider-owned-receipt-id"
  }
}
```

Never put `INTERNAL_SERVICE_TOKEN`, `ACTION_EVIDENCE_VERIFIER_TOKEN`, or another backend secret in Android, web JavaScript, or a repository.

## Authentication

The Worker verifies JWT signature, issuer, audience, time claims, and subject with `jose` and a remote HTTPS JWKS. Firebase Authentication works with:

```text
JWT_ISSUER=https://securetoken.google.com/<firebase-project-id>
JWT_AUDIENCE=<firebase-project-id>
JWKS_URL=https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com
```

The configuration is intentionally generic enough for another standards-compliant JWT issuer. A protected route returns `AUTH_DISCONNECTED` (503) when verifier configuration is absent and `AUTH_INVALID` (401) when a token cannot be verified.

## Google Play verification boundary

Configure all of:

- `BILLING_VERIFIER_URL` — HTTPS URL of a trusted private service that uses the Google Play Developer API.
- `BILLING_VERIFIER_TOKEN` — secret shared only between trusted backends.
- `ANDROID_PACKAGE_NAME` — exact Android application ID.
- `PLAY_PRODUCT_ENTITLEMENTS` — server-controlled JSON map, such as `{"omar_ai_pro":"pro","omar_ai_business":"business"}`.

The verifier receives:

```json
{
  "packageName": "com.darcloud.omarai",
  "productId": "omar_ai_pro",
  "purchaseToken": "raw-token-used-only-for-verification"
}
```

It must return a response backed by the Google Play Developer API:

```json
{
  "verified": true,
  "productId": "omar_ai_pro",
  "packageName": "com.darcloud.omarai",
  "providerTransactionId": "Google-owned-transaction-reference",
  "purchaseState": "PURCHASED",
  "acknowledgementState": "ACKNOWLEDGED",
  "productType": "SUBSCRIPTION",
  "verifiedAt": "2026-08-30T00:00:00.000Z",
  "expiryTime": "2026-09-30T00:00:00.000Z",
  "testPurchase": false,
  "lifecycleEvidence": {
    "rtdnConfigured": true,
    "revocationReconciliationConfigured": true,
    "lastReconciledAt": "2026-08-30T00:00:00.000Z",
    "evidenceReference": "trusted-verifier-lifecycle-reference"
  }
}
```

The Worker cross-checks product/package, requires acknowledgment, maps entitlements server-side, and stores only a SHA-256 token hash. Provider transaction IDs and token hashes are uniquely bound and can never be reassigned to another user by an upsert. `PLAY_LIFECYCLE_VERIFICATION_ENABLED` defaults to false. Even when enabled, activation requires fresh evidence (within 24 hours) that RTDN and revocation reconciliation are operational, plus a non-expired subscription. Otherwise verification returns HTTP 202 with `state: pending_activation` and `grantsAccess: false`.

## Account deletion boundary

`AUTH_ACCOUNT_DELETER_URL` and secret `AUTH_ACCOUNT_DELETER_TOKEN` must point to a trusted service that deletes the authenticated JWT subject at the configured identity provider. It receives `{ subject, issuer }` and must return `{ "deleted": true, "subject": "same-subject" }` only after provider confirmation. Without that service, `DELETE /v1/account` returns `ACCOUNT_DELETION_DISCONNECTED` and deletes nothing.

Deletion is blocked when an owned business has other members, because silently deleting their shared business data would be destructive. Transfer ownership or remove members first. The saga removes application data first, then requests identity deletion. If the identity provider is unavailable, the API truthfully reports `applicationData: DELETED` and `identityProviderAccount: PENDING`; the still-existing identity can authenticate and retry. Successful deletion leaves only a hashed 24-hour idempotency receipt and an unlinkable system audit event. Replay also removes any minimal user row recreated by a still-valid token.

## Data stored and retention

D1 stores the authenticated subject; optional JWT email/name; businesses and membership; customer names/contact/address/notes; leads; jobs; estimates/invoices and line items; verified payment references; assistant messages; task plans/approvals/execution reports; user moderation reports; model/provider IDs; entitlement and billing verification records (including acknowledgment/lifecycle evidence); request IDs and audit metadata; idempotency responses; and per-minute rate-limit counters.

The Worker does **not** intentionally persist raw access tokens, raw Google Play purchase tokens, card credentials, file content, microphone/camera data, advertising IDs, precise location, contact books, or IP addresses. Purchase tokens are hashed after the verifier call.

Current retention mechanics:

- User/business records remain until account/business deletion or an operator-defined retention job.
- Idempotency rows expire logically after 24 hours; schedule a maintenance job to physically purge expired rows.
- Rate-limit windows should be physically purged by a maintenance job after they are no longer useful.
- Hashed account-deletion receipts expire logically after 24 hours.
- Account export is generated on demand and is not stored by this Worker.
- Legal retention requirements for verified transactions must be defined before production. Do not claim automatic retention enforcement until that maintenance workflow is deployed.

## Setup

```bash
cd backend
npm install
cp wrangler.toml.example wrangler.toml
```

Create D1, replace `database_id`, then apply migrations:

```bash
npx wrangler d1 migrations apply omar-ai --local
npx wrangler d1 migrations apply omar-ai --remote
```

Set secrets without committing them:

```bash
npx wrangler secret put INTERNAL_SERVICE_TOKEN
npx wrangler secret put BILLING_VERIFIER_TOKEN
npx wrangler secret put AUTH_ACCOUNT_DELETER_TOKEN
npx wrangler secret put ACTION_EVIDENCE_VERIFIER_TOKEN
```

Set the non-secret variables in `wrangler.toml`, including an exact comma-separated `ALLOWED_ORIGINS`. `*` is never treated as a wildcard. Native Android requests do not need CORS.

Run verification:

```bash
npm run check
```

Deploy only after tests pass, remote migrations succeed, secrets/bindings exist, verifier contracts are live, and a staging `/v1/tasks/plan` returns request-time inference evidence. Binding presence alone remains `PENDING` in `/v1/health`; this v1 health route intentionally stays degraded rather than claiming AI connectivity without an inference probe. Deployment itself does not publish an Android release or create Play subscription products.

## Production gates still required

1. Provision D1 and apply all migrations in order.
2. Configure and validate the JWT issuer/JWKS.
3. Bind Workers AI and confirm the selected model is available in the Cloudflare account/region.
4. Deploy the Google Play Developer API verifier, purchase acknowledgment, RTDN/revocation reconciliation, package name, and entitlement map; leave lifecycle activation disabled until evidence is operational.
5. Deploy the identity-provider account deleter and external deletion web flow required by Google Play policy.
6. Add verified customer-payment webhooks before displaying nonzero collected revenue.
7. Implement R2/media, vision, transcription, notifications, phone, email, and financial-data adapters before presenting those features as connected.
8. Deploy the provider-evidence verifier before any executor is allowed to report external completion.
9. Add Cloudflare WAF/bot controls, dependency scanning, staging load tests, observability, database backups, purge jobs, and an independent security review.
