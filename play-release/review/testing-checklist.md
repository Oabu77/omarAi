# Omar AI v0.1.0 release/testing checklist

Release status: **NOT READY / NOT SUBMITTED**. This checklist is scoped to package `com.darcloud.omarai`, version `0.1.0`/code 1, with the Omar API disconnected, no account/sign-in, no AI-generated answer, and Pro/Business purchase unavailable. A source inspection or UI screenshot is not a signed-artifact or external-service test.

## Known evidence and blockers at pack creation

| Item | Current evidence | Release meaning |
|---|---|---|
| Android SDK/permissions | Source declares min 26, target/compile 36 and only INTERNET/CAMERA; voice uses an external speech-recognition intent | Must still match merged manifest from signed AAB |
| Resource/manifest validation | AAPT2/resource validation was reported successful | Not a full Gradle build or install test |
| Build | Gradle distribution download and Java compiler were unavailable in the build environment | No release AAB, hash, signing, unit/UI result, or Play-track install exists |
| Backend | Backend source/tests exist but no production deployment/auth connection is configured in Android | Remote task, report, account, AI, and billing flows are not release features |
| Local deletion cleanup | Source clears Room, `camera-*` cache files, and legacy persisted URI grants | Must pass instrumentation/device verification |
| Store rasters | Icon and feature graphic metadata, hashes, and visual review are recorded in `../assets/asset-specs.md` | Publisher brand approval and Play upload remain pending |
| Legal site | Accessible local HTML sources exist with no tracking | Contacts, counsel approval, HTTPS hosting, configuration, and deployed checks remain pending |
| Data safety | Local-only candidate mapping is drafted | Speech-recognizer and Play Billing behavior plus signed-build network capture remain unresolved |

## 1. Publisher and Play Console

- [ ] An authorized representative confirms DarCloud LLC is the exact verified Play developer and payments-profile entity.
- [ ] Legal address/country, monitored support/privacy emails, public domain, trademark authority, and launch countries are confirmed.
- [ ] `com.darcloud.omarai` is approved as the permanent package and belongs to the intended Play app.
- [ ] Organization/personal account type, identity verification, merchant/tax setup, and app-signing ownership are confirmed.
- [ ] Current dashboard requirements are recorded. If the account is a qualifying new personal account, complete the account-specific closed-test requirement before requesting production access.

## 2. Reproducible signed build

- [ ] A documented Java 17 JDK, Android SDK, and committed Gradle wrapper produce a clean release build.
- [ ] CI records reviewed commit, `versionName`, `versionCode`, AAB SHA-256, dependency report/SBOM, and signing provenance.
- [ ] Release is non-debuggable, minified as intended, backup remains disabled, cleartext traffic is blocked, and no secret is embedded.
- [ ] Final approved privacy/support URLs are configured; no public-facing button resolves to `example.invalid` or remains misleadingly disabled.
- [ ] Merged AAB manifest contains only intended permissions/components/providers/deep links and no Advertising ID.
- [ ] Dependency/license/vulnerability review has no unresolved critical finding.
- [ ] Native-library 64-bit and 16 KB page-size checks pass, if the final AAB contains native libraries.
- [ ] Play App Signing/upload-key custody and recovery are documented.

## 3. Current local workflows

- [ ] Four-page onboarding completes, survives restart, and remains usable with large fonts and TalkBack.
- [ ] Typed input creates only a local planned/disconnected task and never claims AI analysis, submission, or completion.
- [ ] Deterministic intent labels and exact task states match repository data; refresh/cancel/error paths are truthful.
- [ ] Output reporting stays local and clearly says no report was submitted while the API is disconnected.
- [ ] Customer/lead/job/invoice add-and-read paths, supported lead-status update, validation, empty states, process death, and database persistence pass.
- [ ] No UI implies that an invoice was sent/paid by Omar AI, a job was booked, or a customer was contacted.
- [ ] Every dashboard number reconciles exactly to entered local records; no sample, hardcoded, or fabricated business result appears.
- [ ] Local JSON export contains all and only the record categories promised by the UI/policy and is valid after special characters/large data.
- [ ] In-app deletion clears all promised Room records, `camera-*` cache files, and legacy persisted URI grants; clear-storage/uninstall behavior is also verified.
- [ ] Current quick actions either perform the described local function or show an unmistakable unavailable/disconnected state before any screenshot/listing use.

## 4. Contextual input and permissions

- [ ] CAMERA is requested only after the matching user action; denial, “don’t ask again,” and retry paths do not block typed input. Voice input launches the device speech service without Omar AI requesting RECORD_AUDIO; cancellation/error paths return safely to typed input.
- [ ] Camera capture cancel/retake/rotation/background/low-storage paths do not leak or orphan previews.
- [ ] Camera previews are removed when removed from the draft, the draft is processed, Home is disposed, local deletion runs, or app storage is cleared.
- [ ] Android Photo Picker and Storage Access Framework work without broad media/storage permission.
- [ ] Selected photo/document raw bytes are not uploaded or analyzed; only displayed/stored filename, MIME type, and source metadata match the disclosure.
- [ ] No unnecessary persistable URI grant is taken; any legacy grant cleanup passes.
- [ ] Android speech-recognition unavailability, interruption, empty result, locale, headset/Bluetooth, and backgrounding behavior are tested.
- [ ] Omar AI stores the returned transcript as disclosed and does not save raw microphone audio.

## 5. Privacy, Data safety, and security

- [ ] Runtime network capture from the signed Play-track build covers cold start, every screen, typed/speech/camera/photo/file paths, Billing product query, export/delete, background/idle, and reinstall.
- [ ] Every observed destination and data field reconciles to `../data-safety/inventory.md`; no DarCloud/AI/upload/auth/analytics/crash/ad/push call occurs.
- [ ] A documented Play-policy decision resolves Android speech-recognizer processing.
- [ ] A documented Play-policy decision resolves BillingClient product-query behavior and applicable Google Play/payment treatment.
- [ ] Data safety global and per-type answers are completed only after the above evidence; no provisional “No collection” answer is submitted as final.
- [ ] App-private storage, backup-disabled behavior, cleartext prohibition, logs, exported file contents, URI handling, and database migration are security-tested.
- [ ] Privacy policy, in-app text, manifest, SDK graph, runtime behavior, retention, export, and deletion reconcile line by line.
- [ ] No end-to-end encryption, independent audit, anonymity, encrypted local database, AI accuracy, or external-action claim appears.

## 6. Free/Pro/Business and billing

- [ ] Only Free, Pro, and Business labels appear; no Operator or other sellable tier is shown.
- [ ] In the disconnected candidate, purchase cannot launch and no local flag grants Pro/Business entitlement.
- [ ] Missing/unavailable products produce truthful unavailable copy, not a fake success, price, trial, or entitlement.
- [ ] “Manage subscriptions” navigation does not imply the user has an Omar AI purchase.
- [ ] Listing, screenshots, review notes, and legal pages say Pro/Business are unavailable in this artifact.

Do not activate Pro or Business until every connected-billing gate in `../subscriptions/products.md` passes, including an authenticated user mapping, production server verification, acknowledgment/entitlement lifecycle, RTDN/reconciliation, Play test purchases, privacy/Data safety changes, refund/support operations, and approved entitlement copy.

## 7. Android quality and accessibility

- [ ] Unit, repository/database, static policy, and contract tests pass in CI from a clean checkout.
- [ ] Instrumented UI tests pass for onboarding, Home, Business, Tasks, Settings, permissions, export, deletion, and disconnected billing.
- [ ] API 26 through 36 coverage includes representative Pixel/Samsung classes, phone portrait/landscape, supported tablet/split screen, process death, rotation, low memory/storage, and offline mode.
- [ ] TalkBack order/labels/actions, keyboard and switch access, touch targets, contrast, light/dark mode, reduced motion, and 200% font/display scale pass.
- [ ] Startup/resume/jank targets, crash/ANR reports, dependency outage behavior, and rollback ownership are approved.

## 8. Listing, legal site, and assets

- [ ] Store copy remains within current character limits and every claim row in `../listing/en-US.md` has signed-device evidence.
- [ ] Approved privacy/support pages are live at stable public HTTPS URLs without login, geofence, tracker, placeholder, draft notice, or broken relative link.
- [ ] Terms and no-account deletion instructions receive legal/privacy approval before public use.
- [ ] Final privacy URL opens from the signed app; support inbox is monitored.
- [ ] App-content, content-rating, ads, financial, audience, access, account, generative-AI, and Data safety answers match the exact AAB.
- [ ] Store icon and feature graphic retain the verified dimensions/format/hashes unless a re-export is revalidated.
- [ ] Four or more recommended phone screenshots are captured from the signed build using fictional local records and the plan in `../screenshots/screenshot-plan.md`.
- [ ] No screenshot shows an unbuilt feature, edited success state, fake revenue, personal data, account, AI answer, paid entitlement, or external action.

## 9. Review and controlled release

- [ ] Reviewer instructions are re-tested from a clean Play-track install with no credentials or special access.
- [ ] Internal testing passes before closed testing; device coverage, deletion, permissions, Data safety evidence, pre-launch report, crashes/ANRs, and feedback are reviewed.
- [ ] Release notes describe only this disconnected local-first artifact.
- [ ] No unresolved Play policy/pre-review warning remains.
- [ ] Authorized Play Console operator approves upload, declarations, countries, pricing status, staged rollout, monitoring, rollback, and support coverage.
- [ ] Console state is reported exactly as Draft, In review, Rejected, Ready to publish, or Available; never infer “live” from an upload or generated document.

## Connected-release stop conditions

If a real API URL, authentication, AI generation, upload, remote report, subscription purchase, analytics, crash reporting, push, ads, payments, telephony, finance, messaging, marketplace, or another integration is enabled, stop using this pack as-is. Rebuild the feature inventory, tests, listing, screenshots, reviewer access, legal pages, Data safety form, content declarations, moderation/reporting, account deletion, and operational evidence from the new signed artifact.

## Sign-off record

| Area | Owner | Evidence link/path | Result | Date |
|---|---|---|---|---|
| Product scope/truthfulness | `[[CONFIRM_PRODUCT_OWNER]]` | `[[CONFIRM_PRODUCT_EVIDENCE]]` | NOT RUN | `[[CONFIRM_PRODUCT_DATE]]` |
| Android/build | `[[CONFIRM_ANDROID_OWNER]]` | `[[CONFIRM_ANDROID_EVIDENCE]]` | NOT RUN | `[[CONFIRM_ANDROID_DATE]]` |
| Privacy/Data safety/security | `[[CONFIRM_PRIVACY_SECURITY_OWNER]]` | `[[CONFIRM_PRIVACY_SECURITY_EVIDENCE]]` | NOT RUN | `[[CONFIRM_PRIVACY_SECURITY_DATE]]` |
| Billing/support | `[[CONFIRM_BILLING_SUPPORT_OWNER]]` | `[[CONFIRM_BILLING_SUPPORT_EVIDENCE]]` | NOT RUN | `[[CONFIRM_BILLING_SUPPORT_DATE]]` |
| Legal/publisher | `[[CONFIRM_LEGAL_PUBLISHER_OWNER]]` | `[[CONFIRM_LEGAL_PUBLISHER_EVIDENCE]]` | NOT RUN | `[[CONFIRM_LEGAL_PUBLISHER_DATE]]` |
| Play Console submitter | `[[CONFIRM_PLAY_SUBMITTER]]` | `[[CONFIRM_PLAY_CONSOLE_EVIDENCE]]` | NOT RUN | `[[CONFIRM_PLAY_SUBMISSION_DATE]]` |
