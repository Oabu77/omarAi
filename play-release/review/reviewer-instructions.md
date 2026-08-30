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
8. Settings → Plans shows Pro and Business as unavailable and a disabled “Purchase restore unavailable” indicator. It has no purchase control or subscription-management link. This build does not include Play Billing, query products or existing purchases, start or restore a purchase, or activate paid entitlement.
9. Settings → Privacy & data can export supported local records to JSON or delete local database records. Cache/grant cleanup is attempted; use the checklist’s signed-device tests before claiming it always completes.

This release does not generate live AI answers, create an Omar AI account, place calls, send messages, link financial accounts, book providers, process customer payments, or submit company/legal filings.

Review support: [[CONFIRM_MONITORED_REVIEW_SUPPORT_EMAIL]]
```

## Before submission

- [ ] Every step above is tested from a clean Play-track installation.
- [ ] No test credential is entered because no restricted access exists.
- [ ] Onboarding does not require an unavailable integration.
- [ ] Disconnected request produces no network upload and no false AI/external result.
- [ ] All seeded/captured review data is fictional and created locally.
- [ ] Plans screen accurately reports that Play Billing is not included and product query, purchase, restore, and paid entitlement are unavailable.
- [ ] Export/delete behavior matches the privacy policy.
- [ ] Support inbox is monitored throughout review/resubmission.
