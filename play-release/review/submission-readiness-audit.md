# Omar AI closed-test submission readiness audit

Audit date: **August 30, 2026**. Package: `com.darcloud.omarai`.
Release: `0.1.0` / version code `1`.

Status: **NOT SUBMITTED / NOT IN CLOSED TESTING**. A signed bundle and draft
Console inputs exist, but no Play Console app, upload, review, tester opt-in URL,
or Active closed release has been verified.

## Evidence ready

| Item | Verified evidence | State |
|---|---|---|
| Signed AAB | 6,921,439 bytes; SHA-256 `5932622c111d33b93afaa27c38c0c7e44ba871e88f8b1d6c5335f3a71a2edc97`; bundletool and CMS signature checks passed | Ready for authorized upload after gates below |
| Package/version | `com.darcloud.omarai`; `0.1.0` / code `1`; target API 36 | Ready |
| Store icon | 512 × 512 opaque RGBA PNG; SHA-256 `225643a58c4993637b3abb27509bfe8928e8795ba94f8212ae188d676fef62ff` | Ready |
| Feature graphic | 1,024 × 500 RGB PNG; SHA-256 `69ef902c5dd47cfc14cadd693f7fcc24e971960b3725ed2e3e8ca63abbaba85b` | Ready |
| en-US listing | Name, 72-character short description, full description, category draft, and release notes | Prepared; not entered in Console |
| Public pages | Privacy, deletion, support, and terms returned HTTP 200; no `[[CONFIRM…]]`, staged-source, `noindex`, or proposed-operator wording observed in the served HTML | Technically reachable; owner/legal approval still required |
| Screenshots | No PNG/JPEG store screenshots exist under `play-release/screenshots/` | **Blocking** |

Google requires at least two screenshots to publish a store listing. Four
portrait 1,080 × 1,920 captures from the exact Play-installed candidate remain
the release-pack target. Generated, edited-success, debug, and sideload-only
captures are not acceptable evidence for this pack.

## Eleven remaining gates and Console actions

Complete these in order. A checked preparation item is not evidence that the
corresponding Console action was submitted or accepted.

1. **Verify the publisher account.** Confirm the selected Play developer account
   is the authorized DarCloud LLC account, record whether it is Organization or
   Personal and its creation date, and complete identity/contact/device
   verification. Confirm the operator has release-to-testing and tester access.
2. **Approve public contacts and legal pages.** Confirm
   `omarabunadi@darcloud.net` is monitored and authorized for public and reviewer
   use; approve the served privacy, deletion, support, and terms text for the
   verified entity and launch countries.
3. **Confirm permanent package and signing ownership.** Create or select only the
   Play app intended to own `com.darcloud.omarai`; stop on any conflict. Review
   Play App Signing enrollment, upload-certificate ownership, recovery, and key
   custody with the account owner.
4. **Finish signed-artifact evidence.** Record the final source commit; inspect the
   release merged manifest and dependency/SBOM; prove Play Billing, ads, analytics,
   crash, push, Advertising ID, and unintended data transports are absent; retain
   16 KB/native-library and minified-release evidence.
5. **Pass signed-device and privacy tests.** From the exact candidate, test
   onboarding, records, dashboard reconciliation, task states, permissions,
   speech-provider behavior, export/deletion/cache/grant cleanup, accessibility,
   representative devices, and runtime network traffic. Reconcile the result to
   the privacy policy and Data safety inventory.
6. **Capture genuine store screenshots.** Produce at least two, preferably four,
   truthful 24-bit RGB phone screenshots from the exact Play-installed candidate
   using fictional records. Preserve package/version, AAB hash, device, window
   hierarchy, and capture metadata.
7. **Complete app setup and store presence.** In Console select English (United
   States), App, Free, Business category, approved contact/site/privacy values,
   exact listing copy, icon, feature graphic, and genuine screenshots. Omit phone
   contact unless an approved public number exists. Do not activate subscriptions.
8. **Complete every App content declaration.** Re-answer the live forms against
   the exact AAB: privacy policy, Ads, App access, Target audience, IARC content
   rating, Data safety, Health, Financial features, Government, News, account
   deletion, Advertising ID/permissions, and any other item shown under Needs
   attention. The current draft answers are not submitted answers.
9. **Configure the initial closed track.** Use the built-in initial closed track;
   select all intended countries/regions, add an authorized Google Account email
   list or Google Group, enter a monitored tester-feedback contact, and record the
   tester configuration without committing personal tester addresses.
10. **Create and submit the release.** Upload only the verified AAB, confirm package,
    version, code, and target API in App Bundle Explorer, use release name
    `0.1.0 closed test 1`, add the prepared en-US notes, resolve errors, review all
    warnings, then have the authorized operator send the closed rollout for review.
11. **Verify the Play result.** Do not report closed testing until Console shows
    the release **Active**, the official tester opt-in URL exists, and an
    allowlisted tester in an enabled Play country installs code 1 from Google Play.
    Run the Play-delivered smoke test and review pre-launch, crash, ANR, and policy
    reports. Record Draft, In review, Rejected, Ready to publish, or Active exactly.

## Production and monetization boundary

Closed testing does not itself monetize the app. If this is a Personal account
created after November 13, 2023, production access requires at least 12 testers
continuously opted in for the preceding 14 days when applying. Pro and Business
remain inactive because this artifact does not include Play Billing; monetization
requires a later connected, policy-reconciled, server-verified billing release.

## Official references checked for this audit

- [Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en-GB)
- [Store preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [New Personal account testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
