# Google Play closed-test launch plan

Release target: `com.darcloud.omarai`, version `0.1.0` / code `1`.

Status: **NOT SUBMITTED**. A closed test is complete only when Play Console shows the release as **Active**, eligible testers can opt in, and an allowlisted tester can install the exact candidate from Google Play. Draft, uploaded, and in-review states are not Active.

## 1. Account gate

- [ ] Record Play developer account type: `[[CONFIRM_PLAY_ACCOUNT_TYPE]]`.
- [ ] Record account creation date if Personal: `[[CONFIRM_PLAY_ACCOUNT_CREATION_DATE]]`.
- [ ] Confirm developer identity/contact verification is complete.
- [ ] If Play requires device verification, complete it with the authorized account owner on a physical, non-rooted Android 10+ device.
- [ ] Confirm whether Omar AI will be submitted through an Organization account. The long-term Money Manager vision may place later releases within Google's financial-services account requirements; do not infer account eligibility from this local-only v0.1 artifact.

For a Personal account created after November 13, 2023, production access normally requires at least 12 testers continuously opted in for 14 days, followed by a production-access application. This is a later production gate, not proof that a closed release is Active.

## 2. Human and legal confirmations

- [ ] Authorized publisher/legal entity: `[[CONFIRM_PUBLISHER_LEGAL_NAME]]`.
- [ ] Monitored public support email: `[[CONFIRM_SUPPORT_EMAIL]]`.
- [ ] Monitored privacy/deletion contact: `[[CONFIRM_PRIVACY_EMAIL]]`.
- [ ] Authorized Play Console submitter: `[[CONFIRM_PLAY_SUBMITTER]]`.
- [x] Public privacy endpoint returned HTTP 200 on August 30, 2026: `https://omar-ai-support.omarabunadi28.chatgpt.site/privacy`.
- [x] Public deletion endpoint returned HTTP 200 on August 30, 2026: `https://omar-ai-support.omarabunadi28.chatgpt.site/delete-account`.
- [x] Public support endpoint returned HTTP 200 on August 30, 2026: `https://omar-ai-support.omarabunadi28.chatgpt.site/support`.
- [x] Public terms endpoint returned HTTP 200 on August 30, 2026: `https://omar-ai-support.omarabunadi28.chatgpt.site/terms`.
- [ ] Confirm the hosted pages require no sign-in/geofence, contain no draft placeholders or trackers, match the signed app, and have publisher/privacy/legal approval.

## 3. Exact artifact evidence

- [x] Signed release AAB: `../../android/app/build/outputs/bundle/release/app-release.aab`, 6,921,439 bytes, SHA-256 `5932622c111d33b93afaa27c38c0c7e44ba871e88f8b1d6c5335f3a71a2edc97`.
- [ ] Source commit and clean-tree evidence: `[[CONFIRM_FINAL_SOURCE_COMMIT]]`.
- [ ] Upload-key provenance and recovery custody recorded outside the repository.
- [ ] Play App Signing enrollment reviewed by the authorized account owner.
- [x] AAB package/version/target API match this plan: `com.darcloud.omarai`, `0.1.0`/code 1, min API 26, target API 36, label `Omar AI`.
- [x] `bundletool validate` and OpenSSL CMS signature verification pass. Upload certificate SHA-256: `E3:9D:98:B1:11:C1:42:F6:58:7F:BE:54:0A:10:09:A6:88:D2:F4:0B:8F:3A:AF:BF:87:C1:58:C4:9C:62:A5:D0`.
- [ ] Release merged manifest and dependency inventory reviewed.
- [ ] Billing SDK, BILLING permission/queries, Google Data Transport billing telemetry, and purchase/restore flow are absent. Dormant future verification DTO/interface names have no call site, and no Play token can be received or transmitted.
- [ ] Release runtime/network capture and device workflow evidence are attached to `testing-checklist.md`.
- [ ] At least two genuine phone screenshots from the exact candidate are ready; four are preferred.

## 4. Console configuration

- [ ] Create/select app with package `com.darcloud.omarai`, default language English (United States), app type App, and free distribution.
- [ ] Complete the main store listing from `../listing/en-US.md` and upload only verified assets from `../assets/` and `../screenshots/`.
- [ ] Complete Ads, App access, Target audience, Content rating, Financial features, Data safety, privacy policy, and every other applicable App content declaration from the exact AAB.
- [ ] App access states no account or reviewer credentials are required for this build; reviewers can open the static Settings → Plans screen and see that paid plans are unavailable.
- [ ] Financial features answer reflects only the shipped local organizer: no linked financial account, payment processing, lending, investing, money transfer, or purchasable digital good.
- [ ] Data safety answers are reconciled to signed dependency and runtime evidence, including the external Android speech-recognition provider.
- [ ] Pro and Business products remain inactive; no paid plan or purchase is advertised as available.

## 5. Closed track

- [ ] Use the built-in initial closed-testing track so global country targeting remains available; Google's current documentation says additional custom closed tracks do not support country targeting.
- [ ] Create the release and upload the exact signed AAB.
- [ ] Add the authorized tester email list or Google Group: `[[CONFIRM_TESTER_GROUP]]`.
- [ ] Set tester feedback contact: `[[CONFIRM_TESTER_FEEDBACK_CONTACT]]`.
- [ ] Unsync the initial testing track from production if necessary, select every country/region currently offered for this app, and record the selection; tester eligibility follows each tester's registered Google Play country.
- [ ] Add release notes from `release-notes.txt`.
- [ ] Resolve every Console error and review every warning; document accepted warnings with rationale.
- [ ] Send the closed release for review only after explicit authorization.

## 6. Verified completion

- [ ] Play Console state is **Active**, not Draft or In review.
- [ ] Record Play release/track identifier and activation timestamp: `[[CONFIRM_CLOSED_TEST_ACTIVE_EVIDENCE]]`.
- [ ] Record the official tester opt-in URL: `[[CONFIRM_TESTER_OPT_IN_URL]]`.
- [ ] An allowlisted tester opts in and installs/updates version `0.1.0` code `1` from the Play Store.
- [ ] Run the signed Play-delivered smoke test and review pre-launch, crash, ANR, and policy reports.
- [ ] Report the exact outcome as Active, Failed, or still In review; never treat upload as activation.

## Official references

- [Closed testing setup](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)
- [New Personal account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Store preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Financial Features declaration](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en)

Exact browser inputs and stop conditions: `console-submission-inputs.md`.
