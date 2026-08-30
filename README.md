# OMAR AI™

Omar AI is an active implementation workspace for an Android-first assistant for
life and business. The long-term product vision includes multimodal assistance,
business tools, task approvals, and authorized integrations.

Repository contents are not proof that a feature is deployed, connected,
published, approved by Google Play, or monetizing. External actions must follow:

**ASK → PLAN → APPROVE WHEN REQUIRED → ACT → VERIFY → REPORT**

## Current repository surfaces

The following paths were verified to exist before this document was updated:

| Path | What is present | Verification boundary |
|---|---|---|
| Repository root | Python conversational CLI, local-runtime metrics, and truthfulness regression tests | Prototype only; it has no external action tools or ecosystem telemetry |
| `android/` | Android Gradle/Kotlin project with manifest, Compose UI, API/local-data source, and Play Billing source/configuration | Source present; this document does not claim a signed bundle, device-tested release, upload, review, or publication |
| `backend/` | TypeScript Cloudflare Worker-oriented scaffold with migrations, auth/security/contracts, task/business/billing route source, and an AI-provider interface | Source present; no production deployment, database, provider credential, or endpoint health is verified here |
| `play-release/` | Draft legal, listing, subscription, asset, and release-preparation material | Preparation only; it does not submit anything to Play Console |

Some product areas in the master vision remain future phases. A source file,
screen, button, environment variable, or draft policy must not be described as a
working production integration without an end-to-end verification result.

## Python CLI

### Requirements

- Python 3.11+
- `psutil` for measured metrics from the machine or container running the CLI
- An OpenAI-compatible Python client and `OPENAI_API_KEY` for model responses
  (optional)

Install:

```bash
python -m pip install -r requirements.txt
```

Optional AI-provider configuration:

```bash
export OPENAI_API_KEY="your-key-here"
export OMAR_AI_MODEL="gpt-4o"
```

Run:

```bash
python app.py
```

An API key creates a configured client; it does not prove connectivity until a
request succeeds. The provider receives the current prompt and in-memory
conversation history when a general chat request is made. Review the selected
provider's privacy and retention terms before sending sensitive data.

### Status behavior

| Command | Output |
|---|---|
| `status` | Measured local-runtime counters plus explicit unknown external states |
| `show ecosystem status` | Reports that no external component probes are configured |
| `show infrastructure health` | Local host/container measurements only; no inferred service health |
| `show network performance` | Unknown unless authoritative network telemetry is added |
| `show service adoption metrics` | Unknown unless analytics, CRM, billing, or membership data is added |
| `generate operational report` | Evidence-bounded verification report |
| `generate strategic analysis` | Prepared recommendations, not actions performed |
| `switch mode <mode>` | Changes the local conversation mode |

The CLI deliberately never converts missing measurements into fallback uptime,
TPS, user, member, revenue, adoption, deployment, or security claims.

### Tests

```bash
python -m unittest -v tests.py
# or, when pytest is installed
python -m pytest tests.py -v
```

The regression suite covers missing-data behavior, partial metric-collection
failures, deterministic status reporting, provider configuration versus verified
connectivity, and the prepared/submitted/completed result-state contract.

## Android and backend development

The Android client and backend are separate subprojects. Their presence does not
make the Python CLI a backend for the Android application.

- Android configuration belongs under `android/`. Keep production API URLs and
  policy URLs outside source control and never put privileged secrets in the APK.
- Backend configuration belongs under `backend/`. The checked-in example files
  are templates, not proof of configured production services.
- Billing entitlements, external side effects, and consequential task states must
  be server-authoritative and verified with provider evidence.
- Unavailable integrations must remain `DISCONNECTED`, `UNKNOWN`, or hidden;
  test and production states must be visibly distinct.

Before claiming release readiness, verify the signed Android App Bundle,
release-build behavior, accessibility, permissions, billing lifecycle, account
deletion, privacy/Data Safety declarations, backend production configuration,
end-to-end actions, and Play Console status.

## Result states

- **PREPARED:** a local draft, plan, or payload exists.
- **AWAITING APPROVAL:** a consequential action still needs authorization.
- **ATTEMPTED:** a request was sent without confirmed success.
- **SUBMITTED:** the destination confirmed receipt.
- **COMPLETED:** the authoritative service confirmed the intended final state.
- **FAILED:** an attempted action returned a failure.
- **UNKNOWN:** no authoritative evidence is available.
- **DISCONNECTED:** the required integration is unavailable.

## Secrets and local data

- Production secrets must be supplied through protected backend configuration,
  never hard-coded into Python or Android source.
- The Python CLI keeps conversation history in process memory for the current
  session; configured model requests transmit that history to the AI provider.
- Local runtime counters come from `psutil` and describe only the executing host
  or container.
- No root CLI command verifies Google Play, payments, phone calls, messages,
  bookings, bank accounts, company filings, or external deployments.
