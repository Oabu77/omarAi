# v1 data-flow inventory

Artifact: `com.darcloud.omarai` v0.1.0/code 1 with `OMAR_API_CONFIGURED=false`. This inventory is intentionally narrower than the product vision and backend source tree. It describes the Android artifact’s current reachable behavior.

Google Play treats data as “collected” when the app or its SDKs transmit it off device. Data handled only on device is not declared as collected in the Data safety form. A manifest permission by itself is not collection.

## Evidence snapshot

```text
Signed AAB SHA-256: [[CONFIRM_RELEASE_AAB_SHA256]]
Git commit: [[CONFIRM_RELEASE_COMMIT]]
Merged manifest/SBOM: [[CONFIRM_MANIFEST_SBOM_EVIDENCE]]
OMAR_API_BASE_URL / configured flag: [[CONFIRM_RELEASE_API_CONFIGURATION]]
Runtime network-capture report: [[CONFIRM_RUNTIME_NETWORK_REPORT]]
Data-flow reviewer/date: [[CONFIRM_DATA_FLOW_REVIEWER_DATE]]
```

## Data handled locally

| Local data | Source/storage | Current behavior | User control / release issue |
|---|---|---|---|
| Onboarding flag | Android DataStore | Stored on device | Cleared with app storage/uninstall |
| Request text and speech-recognition transcript | Room messages/tasks | Typed text or transcript is stored locally; response is deterministic routing/disconnection text | Included in JSON export/local database deletion |
| Task/audit/report records | Room | Task title, agent, status, actions, permissions, result/error, audit detail, and locally saved output report | Export/delete controls; disconnected report is not submitted |
| Customer records | Room | Name with optional phone/email | Export/delete controls |
| Lead records | Room | Title, status, optional estimated value, USD | Export/delete controls |
| Job records | Room | Title, status, optional schedule/completion time | Export/delete controls |
| Invoice records | Room | Label, status, total/paid amount, USD | Organizer record only; no invoice is sent and no payment is processed |
| Camera preview | App-private cache JPEG | Created only after user invokes camera; not uploaded while API disconnected; source removes it when removed/processed/Home disposes and during local deletion | Source cleanup exists; signed-device lifecycle/deletion test remains required |
| Selected photo/document context | Temporary Android picker URI plus filename/MIME/source metadata in the local draft | Raw bytes are not uploaded or analyzed while API disconnected; no persistable grant is requested in current source | Signed-device test must confirm temporary access ends and any legacy grant is released by local deletion |
| Billing UI state/product metadata | Memory/Google Play Billing client | May query active Play product metadata; purchase button disabled without backend verifier | No paid entitlement is granted locally |
| JSON export | User-chosen document destination | Contains supported local Room records | User controls destination; original local records remain until deleted |

Android backup is disabled in the source manifest/rules. Verify this in the signed artifact and on-device restore testing.

## Current off-device flows

| Destination | Trigger/data | Current result | Data safety decision |
|---|---|---|---|
| Android speech-recognition service selected by the device | User taps microphone; the speech service captures voice and returns text | Omar AI does not receive/save raw audio, but another app/service may process it under its own policy | **Open decision:** determine whether Play requires Omar AI to declare Voice or sound recordings as shared/collected for this invocation; test chosen device/provider and document rationale |
| Google Play Billing service | App initializes BillingClient and queries Pro/Business product details | Play may return product metadata; purchase launch is disabled because API verification is unconfigured | Review Google’s payment/Play-service exception and Billing SDK guidance; no raw card data or purchase token is collected by DarCloud in current flow |
| DarCloud/Omar API | None while `OMAR_API_CONFIGURED=false` | Request routing, attachments, reports, deletion, and purchase verification remain local/disconnected; no Omar API call should occur | Runtime proxy/DNS capture must confirm zero calls before selecting “no collection” |
| User-opened external legal/subscription link | Explicit user tap | Android/browser or Google Play app handles destination | User-initiated external navigation; verify configured URLs and no embedded tracking WebView |

No analytics, crash-reporting, push, advertising, authentication, upload, telephony, payment, banking, or marketplace SDK is declared in the current Android dependency file.

## Candidate Play mapping for current artifact

Subject to resolving the two SDK/system-service rows above and runtime testing:

| Play data type | DarCloud app collection | Sharing | Reason |
|---|---:|---:|---|
| Name/email/phone | No | No | Business records remain in local Room only |
| Purchase history/payment info | No | No | No purchase can launch; Google Play product query only; app receives no raw payment data |
| Other in-app messages / other user-generated content | No | No | Request/task/business content remains on device |
| Photos | No | No | Selected/captured image is not uploaded while API is disconnected |
| Files and docs | No | No | Selected document is not uploaded while API is disconnected |
| Voice or sound recordings | **Unresolved system-recognizer question** | **Unresolved** | Device speech service captures audio; Omar AI receives only transcript |
| App interactions/crash/diagnostics/device IDs | No expected | No expected | No analytics/crash/push/ad SDK; verify Play Billing/transitive/runtime behavior |

Do not submit “No data collected or shared” until the signed-AAB network audit and the speech/Billing analysis are documented. If either is in scope, select Yes and answer that exact data-type flow.

## Connected backend delta — not active in current artifact

A separate backend exists but is not deployed/connected to this Android build, and the Android app has no sign-in/token flow. Enabling an API URL without adding authentication would make protected calls fail; it would not create a working connected release.

Before a later connected build, inventory at minimum:

- JWT subject and optional account email/name;
- assistant prompt, task, approval, report, model/provider IDs, audit/request IDs, and idempotency response;
- business/customer contact, address, notes, leads, jobs, estimates, invoices, and amounts;
- Google Play product/order/entitlement evidence and purchase-token hash;
- service logs and rate-limit counters; and
- every AI/auth/hosting/deletion provider and its contract, retention, region, training, and deletion behavior.

The current backend intentionally does **not** store raw access/auth tokens, raw purchase tokens after verification, raw cards, files, camera images, microphone audio, ad IDs, precise location, device contacts, or IP addresses. Its current retention is generally until deletion; automated purge/retention jobs are not proven. Do not enable it until those gaps and the full connected Data safety form are resolved.

## Explicitly absent from current Android v1

- Omar AI account, sign-in, authentication token, or remote profile;
- live/generated AI response;
- file/photo/vision upload or analysis;
- phone/SMS/email/user messaging;
- push notifications;
- device contacts, calendar, location, call logs, phone state, broad media/storage, installed-app inventory, or web history;
- ads, Advertising ID, affiliate/sponsored placement;
- customer payment, financial-account connection, transfer, investing, credit, insurance, crypto, or raw card data;
- marketplace/provider booking, review, public listing, or shared workspace; and
- external company/legal filing.

## Final verification

Test the signed Play-track build with an approved network-capture method across cold start, onboarding, every navigation tab, typed request, speech recognition, camera, photo/file picker, task cancel/report, all business records, billing/product query, export/delete, legal links, background/idle, and uninstall/reinstall. Reconcile all destinations with the merged manifest, dependency graph, backend logs, Play SDK guidance, and public privacy policy. Any unexplained endpoint is a release blocker.
