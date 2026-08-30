# v1 data-flow inventory

Artifact: `com.darcloud.omarai` v0.1.0/code 1 with `OMAR_API_CONFIGURED=false`. This inventory is intentionally narrower than the product vision and backend source tree. It describes the Android artifact’s current reachable behavior.

Google Play treats data as “collected” when the app or its SDKs transmit it off device. Data handled only on device is not declared as collected in the Data safety form. A manifest permission by itself is not collection.

## Evidence snapshot

```text
Signed AAB: ../../android/app/build/outputs/bundle/release/app-release.aab (6,921,439 bytes)
Signed AAB SHA-256: 5932622c111d33b93afaa27c38c0c7e44ba871e88f8b1d6c5335f3a71a2edc97
Bundle/signature checks: bundletool validate PASS; OpenSSL CMS signature verification PASS
Upload certificate SHA-256: E3:9D:98:B1:11:C1:42:F6:58:7F:BE:54:0A:10:09:A6:88:D2:F4:0B:8F:3A:AF:BF:87:C1:58:C4:9C:62:A5:D0
Git commit: [[CONFIRM_RELEASE_COMMIT]]
Merged manifest/SBOM: [[CONFIRM_MANIFEST_SBOM_EVIDENCE]]
OMAR_API_BASE_URL / configured flag: [[CONFIRM_RELEASE_API_CONFIGURATION]]
Runtime network-capture report: [[CONFIRM_RUNTIME_NETWORK_REPORT]]
Data-flow reviewer/date: [[CONFIRM_DATA_FLOW_REVIEWER_DATE]]
```

## Data handled locally

| Local data | Source/storage | Current behavior | User control / release issue |
|---|---|---|---|
| Onboarding flag | Android DataStore | Stored on device | Cleared by the in-app local-data deletion control, app storage clearing, or uninstall |
| Request text and speech-recognition transcript | Room messages/tasks | Typed text or transcript is stored locally; response is deterministic routing/disconnection text | Included in JSON export/local database deletion |
| Task/audit/report records | Room | Task title, agent, status, actions, permissions, result/error, audit detail, and locally saved output report | Export/delete controls; disconnected report is not submitted |
| Customer records | Room | Name with optional phone/email | Export/delete controls |
| Lead records | Room | Title, status, optional estimated value, USD | Export/delete controls |
| Job records | Room | Title, status, optional schedule/completion time | Export/delete controls |
| Invoice records | Room | Label, status, total/paid amount, USD | Organizer record only; no invoice is sent and no payment is processed |
| Camera preview | App-private cache JPEG | Created only after user invokes camera; not uploaded while API disconnected; source attempts removal when removed/processed/Home disposes and during local deletion | File-deletion results are not surfaced; signed-device lifecycle/deletion testing is required before promising complete cleanup |
| Selected photo/document context | Temporary in-memory Android picker URI plus filename/MIME/source metadata while the draft is open | Raw bytes are not uploaded or analyzed while API disconnected; the metadata is not persisted to Room/export after submission; no persistable grant is requested in current source | Signed-device test must confirm temporary access ends and any legacy grant is released by local deletion |
| Plans UI state | Memory/static unavailable state | The source does not bundle or initialize the Play Billing SDK. No product or existing-purchase query runs; purchase and restore actions are unavailable; no purchase token is received or stored | Pro and Business remain inactive and no paid entitlement is granted locally. Confirm this exact behavior in the signed artifact |
| JSON export | User-chosen document destination | Contains supported local Room records | User controls destination; original local records remain until deleted |

Android backup is disabled in the source manifest/rules. Verify this in the signed artifact and on-device restore testing.

## Current off-device boundaries

| Destination/boundary | Trigger/data | Current result | Data safety decision |
|---|---|---|---|
| Android speech-recognition service selected by the device | User taps microphone; the speech service captures voice and returns text | Omar AI does not receive/save raw audio, but another app/service may process it under its own policy | **Source-level candidate: No Voice or sound recordings collected/shared by Omar AI.** The device provider captures audio itself; Omar AI receives only a transcript that remains local. Keep the third-party disclosure and confirm the signed runtime/provider behavior. |
| Google Play Billing service (not integrated) | No trigger in the current source | No BillingClient is bundled or initialized, so the app cannot query products or purchases, start a purchase, restore a purchase, receive a purchase token, or emit Billing SDK diagnostic/device telemetry | **Source-level candidate: No Purchase history/payment info or Billing SDK diagnostics collected/shared by Omar AI.** Confirm the signed dependency graph, merged manifest, and runtime traffic contain no Billing Library, Google Data Transport component introduced by it, billing-service request, or related telemetry. |
| DarCloud/Omar API | None while `OMAR_API_CONFIGURED=false` | Request routing, attachments, reports, and deletion remain local/disconnected; no Omar API call should occur. The dormant future billing-verification contract has no Android runtime caller | Runtime proxy/DNS capture must confirm zero calls before selecting “no collection” |
| User-opened external legal link | Explicit user tap | Android/browser handles the configured policy destination | User-initiated external navigation; verify configured URLs, no subscription-management link, and no embedded tracking WebView |

No analytics, crash-reporting, push, advertising, authentication, upload, telephony, Play Billing, payment, banking, or marketplace SDK is declared in the current Android dependency file. The current source also does not declare Google Data Transport for billing diagnostics. Confirm both statements against the exact signed dependency graph, merged manifest, and runtime capture.

The minified artifact intentionally retains dormant future-backend contract names such as `v1/billing/google-play/verify`, `BillingVerificationRequest.purchaseToken`, and `VerifiedEntitlement` because the API contracts are preserved for Moshi/Retrofit. Those strings/classes are not the Play Billing SDK. In v0.1.0 there is no call site that can obtain a Play purchase token or invoke the verification method. Release review must prove that this dormant contract remains unreachable and that no token is received or transmitted; it must not use a blanket search for the words `billing` or `purchaseToken` as the SDK-absence test.

## Candidate Play mapping for current artifact

Subject to signed-artifact inspection and runtime testing that confirm the source-level behavior above:

| Play data type | DarCloud app collection | Sharing | Reason |
|---|---:|---:|---|
| Name/email/phone | No | No | Business records remain in local Room only |
| Purchase history/payment info | No expected | No expected | The source has no Play Billing SDK and cannot query, purchase, restore, or receive a token. Reclassify if the signed artifact or runtime evidence differs. |
| Other in-app messages / other user-generated content | No | No | Request/task/business content remains on device |
| Photos | No | No | Selected/captured image is not uploaded while API is disconnected |
| Files and docs | No | No | Selected document is not uploaded while API is disconnected |
| Voice or sound recordings | No expected | No expected | Device speech service captures audio itself; Omar AI receives only the transcript, which stays local. Reclassify if provider/runtime evidence differs. |
| App interactions/crash/diagnostics/device IDs | No expected | No expected | No analytics/crash/push/ad/Billing SDK is declared; verify the signed dependency graph contains no Billing-added telemetry/transitive component and runtime capture shows no unexpected destination |

Google defines collection as transmitting user data off device from the app. The current source does not access Google Play purchase data because it does not include Play Billing, so the candidate No mapping does not rely on the payment-service exception. This source-level analysis is not final evidence. Do not submit “No data collected or shared” until the signed-AAB network audit, merged dependency/manifest review, and named reviewer/date confirm that the Billing SDK and related telemetry are absent and runtime behavior matches the full inventory. If any observed flow contradicts the rationale, select Yes and disclose the exact data type, purpose, optionality, and recipient.

## Connected backend delta — not active in current artifact

Backend source exists in this repository, but this inventory has no evidence of its deployment state and the Android build does not connect to it. The Android app also has no sign-in/token flow. Enabling an API URL without adding authentication would make protected calls fail; it would not create a working connected release.

Before a later connected build, inventory at minimum:

- JWT subject and optional account email/name;
- assistant prompt, task, approval, report, model/provider IDs, audit/request IDs, and idempotency response;
- business/customer contact, address, notes, leads, jobs, estimates, invoices, and amounts;
- if subscriptions are added later, Google Play product/order/entitlement evidence and purchase-token hash;
- service logs and rate-limit counters; and
- every AI/auth/hosting/deletion provider and its contract, retention, region, training, and deletion behavior.

The reviewed application schema/source has no intentional storage field for raw access/auth tokens, raw purchase tokens after verification, raw cards, files, camera images, microphone audio, ad IDs, precise location, or device contacts. Hosting/provider logs, IP handling, deployment configuration, and actual retention are not established by this Android inventory. Automated purge/retention jobs are also not proven. Do not enable the backend until those gaps and the full connected Data safety form are resolved.

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

Test the signed Play-track build with an approved network-capture method across cold start, onboarding, every navigation tab, typed request, speech recognition, camera, photo/file picker, task cancel/report, all business records, the informational Plans screen and its disabled “Purchase restore unavailable” indicator, export/delete, legal links, background/idle, and uninstall/reinstall. Confirm there is no purchase control or subscription-management link. Confirm the dependency graph and merged manifest contain no Play Billing Library, BillingClient service/query component, or Billing-added Google Data Transport component; confirm runtime capture shows no billing request, reachable purchase-token receipt/transmission, or diagnostic/device telemetry. Separately document that the dormant future verification DTO/interface has no call site. Reconcile every destination with the signed artifact, backend logs, Play SDK guidance, and public privacy policy. Any unexplained endpoint is a release blocker.
