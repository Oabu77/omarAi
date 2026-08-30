# Google Play reviewer instructions

Artifact: `com.darcloud.omarai` v0.1.0/code 1, disconnected local-first foundation. Status: draft; copy into Play Console only after a signed test-track build passes.

## App access answer

**All functionality is available without special access.** There is no account, sign-in, OTP, paywall, reviewer credential, VPN, invitation, or location restriction in this build.

## Proposed reviewer note

```text
No username or password is required.

1. Launch Omar AI and complete the four short onboarding pages.
2. Home: enter “Prepare a checklist for a customer estimate” and tap Send.
3. The build intentionally has no configured Omar AI backend. It saves a local Planned task and clearly says that no analysis or external action occurred.
4. Tasks: open the planned item to inspect status, actions, permissions, result/error, and local cancellation.
5. Business: add fictional customer, lead, job, and invoice records. Dashboard values are calculated from those local records.
6. Home input controls: voice uses Android speech recognition; camera, photo, and file controls add local draft context. The disconnected build does not upload or analyze those items.
7. Settings → Integration status shows the Omar AI service and later modules as disconnected.
8. Settings → Plans & billing may load product metadata from Google Play, but purchase is disabled because backend verification is not configured.
9. Settings → Privacy & data can export supported local records to JSON or delete local database records, Omar AI camera-preview cache files, and legacy picker grants.

This release does not generate live AI answers, create an Omar AI account, place calls, send messages, link financial accounts, book providers, process customer payments, or submit company/legal filings.

Review support: [[CONFIRM_MONITORED_REVIEW_SUPPORT_EMAIL]]
```

## Before submission

- [ ] Every step above is tested from a clean Play-track installation.
- [ ] No test credential is entered because no restricted access exists.
- [ ] Onboarding does not require an unavailable integration.
- [ ] Disconnected request produces no network upload and no false AI/external result.
- [ ] All seeded/captured review data is fictional and created locally.
- [ ] Plans screen cannot start a purchase without server verification.
- [ ] Export/delete behavior matches the privacy policy.
- [ ] Support inbox is monitored throughout review/resubmission.
