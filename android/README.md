# Omar AI Android v1

Android-first, policy-conscious local foundation for **Omar AI™**.

## What is implemented

- Four-step onboarding with optional interests, honest integration scope, and contextual-permission explanation.
- Material 3 home using the included Omar AI icon and the primary prompt: “What do you want Omar AI to do?”
- Text, Android speech recognition, contextual camera capture, Android Photo Picker, and Storage Access Framework file picker. Voice opens Android's recognition service; the app itself does not request microphone permission or retain audio.
- Typed HTTPS API client matching the backend `{ok,data,requestId}` envelope, authenticated mutation headers/bodies, an in-memory session-token seam, and configurable server URL. No API credential is embedded. Remote features remain disabled because v1 has no sign-in flow.
- Deterministic local intent routing and explicit “Coming later” responses for live calls, financial accounts, user messaging/calls, and provider marketplace actions.
- Photo/job-estimate warning: estimates are preliminary ranges, not guaranteed professional assessments.
- Chat history and in-app AI-output moderation reporting. Offline reports say they are saved locally, not submitted; accepted reports do not change task execution state.
- Command Center sections: Active, Waiting for Approval, Scheduled, Completed, and Failed. Details show agent, exact state, timestamps, actions, permissions, results, errors, and verification evidence.
- Approval, cancellation, and remote refresh hooks. Remote timeouts/failures never become success.
- Room-backed local CRM for customers, leads, jobs, and invoice records with editable lead status.
- Business metrics calculated only from those records, with honest empty/unknown states and no sample revenue.
- Integration-status UI using `DISCONNECTED`, `PENDING`, `CONNECTED_TEST`, `CONNECTED_PRODUCTION`, `DEGRADED`, and `FAILED`.
- Local JSON export and destructive local-data deletion confirmation. Export reports success only after the selected stream is written. Deletion clears Room tables and onboarding/app preferences and attempts to remove Omar camera-preview cache files and legacy persisted URI grants; full cleanup still requires signed-device verification. Android cloud backup is disabled.
- Paid plans are explicitly unavailable. This v0.1 artifact does not bundle or initialize the Play Billing SDK, query products or purchases, launch purchase flows, restore purchases, collect purchase tokens, or grant paid entitlements. The dormant typed backend verification contract remains for a future connected build.
- Unit tests for task states, routing, business metrics, plus source-level Play policy assertions.

## Deliberately not claimed or enabled

This v1 does **not** provide account creation/sign-in, raw photo/file upload or analysis, push notifications, live phone/SMS, connected finance, user-to-user messaging/calling, provider booking/availability, external customer payments, company filings, guaranteed estimates, or local subscription entitlement. Those modules are visible only as disabled states or “Coming later” integration rows. Photo/file references and filename/MIME/source metadata are held in memory only while the draft is open. An authenticated future request can include that metadata so the current backend returns its explicit `FILE_SERVICE_DISCONNECTED` response; the disconnected artifact neither sends nor persists it.

## Build configuration

| Setting | Value |
|---|---|
| Package | `com.darcloud.omarai` (`.debug` suffix in debug) |
| Version | `0.1.0` / code `1` |
| Minimum SDK | 26 |
| Compile / target SDK | 36 / 36 |
| Language / UI | Kotlin 2.2.0 / Jetpack Compose Material 3 |
| AGP / Gradle | 8.12.1 / 8.13 |
| Paid plans | Disabled; Play Billing SDK not bundled |

Required local environment:

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME=/path/to/full-jdk-17
```

Build and test:

```bash
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew --no-daemon :app:testReleaseUnitTest :app:lintRelease :app:bundleRelease
```

The repository includes the official Gradle 8.13 wrapper JAR and pins the binary distribution SHA-256 in `gradle-wrapper.properties`. A real upload key/signing configuration is intentionally not stored in source control. Release pre-build and lint tasks reject an unsafe configured API URL and placeholder/non-public legal URLs. On August 30, 2026, the final-source debug URL-policy task, 32 unit tests, Android-test compilation, lint (`No errors or warnings`), and APK assembly passed with Gradle 8.13/AGP 8.12.1. The matching release rehearsal also passed 32 unit tests, lint, R8, and AAB assembly, but used clearly non-production rehearsal legal URLs and no approved upload key. Those artifacts are QA evidence—not a signed Play candidate, upload, or Play-delivered device-test result.

## Runtime configuration

Set non-secret properties in untracked `local.properties`, Gradle `-P...`, or environment variables:

```properties
OMAR_API_BASE_URL=<approved public HTTPS API URL, or omit for disconnected v0.1>
OMAR_PRIVACY_POLICY_URL=<approved public HTTPS privacy URL>
OMAR_ACCOUNT_DELETION_URL=<approved public HTTPS deletion URL>
```

Replace the bracketed instructions rather than copying them literally. Release validation rejects reserved `.invalid`, `.example`, and `.test` hosts; RFC documentation domains (`example.com`, `example.net`, and `example.org`), including their subdomains; local hosts; IP literals; cleartext URLs; embedded credentials; and URL fragments.

Release builds reject cleartext traffic. Debug builds allow cleartext only to support a local development backend. Production secrets and privileged provider calls belong on the backend.

Typed v1 endpoints:

- `GET /v1/health`
- `POST /v1/tasks/plan`
- `GET /v1/tasks/{id}`
- `POST /v1/tasks/{id}/approve`
- `POST /v1/tasks/{id}/cancel`
- `POST /v1/reports/ai-output`
- `GET /v1/integrations`
- `POST /v1/billing/google-play/verify` (dormant future-only contract; unreachable from disconnected v0.1)
- `DELETE /v1/account`

## Permission and data notes

The source manifest requests only `INTERNET` and `CAMERA`. The merged release manifest also includes AndroidX's app-specific signature permission for non-exported dynamic receivers, its non-exported Startup provider and Room invalidation service, and a Profile Installer receiver exported only behind the system-protected `android.permission.DUMP` permission. Camera is requested after the user taps Take photo and sees a purpose explanation. Voice input launches Android's speech-recognition service and does not require this app to request `RECORD_AUDIO`; the resulting text is placed in the input field. Photos and documents use system pickers, so the app requests no broad media/storage permission and does not retain a persistable picker grant. Source paths attempt removal of camera-preview cache files when discarded/submitted or when the screen closes, and local deletion clears Room data/preferences while attempting cleanup of remaining preview files and legacy grants; signed-device verification is still required. `POST_NOTIFICATIONS`, contacts, SMS, call-log, location, background location, exact alarm, Advertising ID, and Accessibility permissions are absent.

Before any Play production submission, implement a real authentication flow that populates the explicit in-memory token provider with a verified JWT (and a secure persistence/revocation design if sessions survive restarts), configure real public privacy/deletion URLs, connect and test the backend, add and audit Play Billing only when paid plans are actually offered, create the Play subscription products, configure backend purchase acknowledgment/RTDN, supply release signing, and complete the actual Data Safety/App Access/financial-feature declarations from the final binary and SDK inventory. Paid purchases are unavailable in this artifact; integration refresh, remote tasks, and remote deletion remain gated while no authenticated session exists.
