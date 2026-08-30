# Google Play Data safety form template

Artifact profile: disconnected local-first Android v0.1.0. Do not import this Markdown file. Use the current Play Console form/CSV and answer from the signed artifact.

## Candidate global answer

**Does the app collect or share any required user data types?**

Candidate: **No**, only if all of the following are documented:

- `OMAR_API_CONFIGURED=false` in the signed build;
- runtime capture shows no DarCloud, AI, upload, analytics, crash, push, ad, auth, or other user-data endpoint;
- all CRM/request/task/report/photo/document information remains on device;
- the Android speech-recognizer invocation is determined not to be collection/sharing by Omar AI under the current Play definition, or the relevant audio type is instead declared accurately; and
- Google Play Billing product queries introduce no app-declared data type outside an applicable Play/payment-service treatment.

If any condition fails, answer **Yes** and disclose the exact observed types. Do not use a broad connected-backend template for a disconnected artifact, and do not use this No answer after configuring the backend.

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
Signed AAB / SHA-256: [[CONFIRM_RELEASE_AAB_SHA256]]
Merged manifest / dependency graph: [[CONFIRM_MANIFEST_DEPENDENCY_REPORT]]
Runtime network report: [[CONFIRM_RUNTIME_NETWORK_REPORT]]
Speech recognizer Data safety rationale: [[CONFIRM_SPEECH_RECOGNIZER_RATIONALE]]
Play Billing SDK/data rationale: [[CONFIRM_PLAY_BILLING_DATA_RATIONALE]]
Local delete/camera-cache/legacy-URI-grant test: [[CONFIRM_LOCAL_DELETION_TEST]]
Privacy policy reconciliation approval: [[CONFIRM_PRIVACY_RECONCILIATION]]
```

## Stop condition for a connected release

If the release sets a real `OMAR_API_BASE_URL`, adds sign-in, permits a Google Play purchase, submits an AI-output report remotely, uploads an attachment, or enables an AI response, stop. Answer Yes and rebuild the inventory from actual production flows, including personal info, messages/user content, purchase history, task/audit/business data, providers, purposes, optionality, retention, security, sharing exceptions, and working deletion.
