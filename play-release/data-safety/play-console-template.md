# Google Play Data safety form template

Artifact profile: disconnected local-first Android v0.1.0. Do not import this Markdown file. Use the current Play Console form/CSV and answer from the signed artifact.

## Candidate global answer

**Does the app collect or share any required user data types?**

Source-level candidate: **No**. This is not the final Console answer; use it only if all of the following are documented for the signed artifact:

- `OMAR_API_CONFIGURED=false` in the signed build;
- runtime capture shows no DarCloud, AI, upload, analytics, crash, push, ad, auth, or other user-data endpoint;
- all CRM/request/task/report/photo/document information remains on device;
- the device speech provider—not Omar AI—captures audio, Omar AI receives only the transcript, and that transcript remains local; and
- the signed dependency graph and merged manifest confirm that the Play Billing SDK and its billing-diagnostic/transitive components are absent; no product or existing-purchase query, purchase, restore, reachable purchase-token receipt/transmission, or Billing SDK telemetry occurs. Dormant future API contract names may remain but must have no call site.

If any condition fails, answer **Yes** and disclose the exact observed types. Do not use a broad connected-backend template for a disconnected artifact, and do not use this No answer after configuring the backend.

## Source-level rationale

- Google defines collection as user data transmitted off device from the app or its libraries/SDKs; data processed only on device is outside collection.
- The current `ACTION_RECOGNIZE_SPEECH` flow hands control to the device speech provider, which captures speech under its own terms. Omar AI receives no raw audio and stores only the returned transcript locally. This supports No for Omar AI Voice or sound recordings, pending signed-device confirmation.
- The current source does not bundle or initialize Play Billing and therefore cannot access product, existing-purchase, or purchase-token data. Purchase and restore are unavailable, Pro/Business are inactive, and no Billing SDK diagnostic/device telemetry should occur. This supports No for Purchase history/payment info and Billing-related diagnostics/device identifiers without relying on the payment-service exception, pending signed dependency, manifest, and runtime confirmation.
- Any runtime endpoint, SDK behavior, provider behavior, or future configuration that contradicts these facts invalidates the candidate No answer.

Primary source: [Google Play Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), including its collection/on-device definitions, user-initiated sharing treatment, and payment-service guidance.

## Local-only data not selected as collected

Under Google’s on-device-processing definition, the following are not selected when they never leave the device:

- name, phone, and email in local customer records;
- invoice/lead amounts and status;
- typed request, returned speech transcript, deterministic response, task/audit/report text;
- selected/captured photo;
- selected document/reference; and
- local app interactions and database state.

## Security-practice fields

If the form follows the No-collection branch, some security questions may not appear. Do not claim:

- an independent security review;
- account deletion (the app creates no account);
- end-to-end encryption; or
- server encryption/retention that the current artifact does not use.

The app does provide local JSON export and local database deletion, and Android backup is disabled. Describe those in the privacy policy/listing without converting them into a false remote-account claim.

## Required pre-submit evidence

```text
Signed AAB: ../../android/app/build/outputs/bundle/release/app-release.aab (6,921,439 bytes)
SHA-256: 5932622c111d33b93afaa27c38c0c7e44ba871e88f8b1d6c5335f3a71a2edc97
Bundle/signature checks: bundletool validate PASS; OpenSSL CMS signature verification PASS
Merged manifest / dependency graph: [[CONFIRM_MANIFEST_DEPENDENCY_REPORT]]
Runtime network report: [[CONFIRM_RUNTIME_NETWORK_REPORT]]
Speech recognizer Data safety rationale: [[CONFIRM_SPEECH_RECOGNIZER_RATIONALE]]
Play Billing SDK absence / dependency-data rationale: [[CONFIRM_PLAY_BILLING_DATA_RATIONALE]]
Local delete/camera-cache/legacy-URI-grant test: [[CONFIRM_LOCAL_DELETION_TEST]]
Privacy policy reconciliation approval: [[CONFIRM_PRIVACY_RECONCILIATION]]
```

## Stop condition for a connected release

If the release sets a real `OMAR_API_BASE_URL`, adds sign-in, bundles or initializes Play Billing, permits a product/purchase/restore query or Google Play purchase, submits an AI-output report remotely, uploads an attachment, or enables an AI response, stop. Rebuild the inventory from actual production flows—including personal info, messages/user content, purchase history, task/audit/business data, SDK diagnostics/device data, providers, purposes, optionality, retention, security, sharing exceptions, and working deletion—and answer the current Console form from the new signed artifact.
