# Exact Google Play Console inputs — global closed test

Evidence review date: **August 30, 2026**. Target artifact:
`com.darcloud.omarai`, v0.1.0, version code 1. Console status at this review:
**not inspected and not proven submitted**.

This is the browser operator's ordered runbook. It does not authorize a public
site deployment, upload-key creation, AAB upload, declaration submission, or
closed-track rollout. Record each Console result rather than inferring success.

## Stop-before-Console inputs

| Input | Exact current value or required evidence | Current state |
|---|---|---|
| Authorized publisher | DarCloud LLC only after the account owner confirms the Console entity matches | **Confirmation required** |
| Authorized submitter | Signed-in account with “Release apps to testing tracks” and tester-management access | **Inspect Console** |
| Account type/date | Organization, or Personal plus account creation date | **Inspect Console** |
| Permanent package | `com.darcloud.omarai` | Ready in source; confirm no conflicting Play app |
| Default language/type/price | English (United States) / App / Free | Prepared; paid subscriptions stay inactive |
| Public support email | `omarabunadi@darcloud.net` only if monitored and authorized for public/reviewer use | **Confirmation required** |
| Public legal/support URLs | Privacy `/privacy`, deletion `/delete-account`, terms `/terms`, and support `/support` under `https://omar-ai-support.omarabunadi28.chatgpt.site` | **HTTP 200 verified August 30, 2026**; content/control, no-login/no-geofence, tracker, and legal approvals remain pending |
| Final artifact | Signed release AAB at `../../android/app/build/outputs/bundle/release/app-release.aab`; 6,921,439 bytes; SHA-256 `5932622c111d33b93afaa27c38c0c7e44ba871e88f8b1d6c5335f3a71a2edc97`; package `com.darcloud.omarai`; v0.1.0/code 1; min 26/target 36; label `Omar AI`; `bundletool validate` and OpenSSL CMS verification PASS | **Candidate exists and is cryptographically validated**; upload-key ownership/custody and Play upload remain unconfirmed |
| Genuine screenshots | Four 1,080 × 1,920 24-bit RGB captures from the exact Play-installed candidate | **Missing**; debug captures are QA-only |
| Tester access | Valid Google Account email list or Google Group; feedback contact | **Missing** |

Do not use the old rehearsal URLs under `.example`/`.test`; the release validator
now rejects reserved documentation/test hosts.

## 1. Create or select the Play app

If no exact Omar AI app already exists, use **Home → Create app**:

| Console field | Input |
|---|---|
| App name | `Omar AI` |
| Default language | English (United States), `en-US` |
| App or game | App |
| Free or paid | Free |
| Contact email | Confirmed monitored support email |
| Declarations | Authorized owner reviews/accepts Developer Program Policies, US export laws, and Play App Signing terms |

Package names become permanent once an artifact is uploaded. Stop if the
selected app does not own `com.darcloud.omarai` or if another app already uses
it. A Free app can keep future in-app products disabled; do not create or
activate Pro/Business products for v0.1.

## 2. Main store listing and settings

Copy the exact en-US candidate from `../listing/en-US.md`:

| Field | Exact input/source |
|---|---|
| App name | `Omar AI` |
| Short description | `Organize local business records and plan requests in one clear workspace` |
| Full description | The fenced full-description block in `../listing/en-US.md` |
| Category | Business |
| Tags | Select only currently offered business/productivity equivalents; omit any tag implying connected AI, finance, phone, marketplace, or payments |
| Store icon | `../assets/icon-512.png` — SHA-256 `225643a58c4993637b3abb27509bfe8928e8795ba94f8212ae188d676fef62ff` |
| Feature graphic | `../assets/feature-graphic-1024x500.png` — SHA-256 `69ef902c5dd47cfc14cadd693f7fcc24e971960b3725ed2e3e8ca63abbaba85b` |
| Phone screenshots | Four final files produced by `android/scripts/emulator_release_smoke.py --mode play-track`; never substitute debug images |
| Contact email/site | Confirmed monitored email and live support URL |
| Privacy policy | Live final privacy-policy URL |

Google requires at least two screenshots to publish. Four genuine 1,080 × 1,920
phone screenshots are prepared by the workflow for recommendation eligibility;
each must remain under 8 MB, 24-bit PNG without alpha, and show actual app UI.
Upload no TV, Wear OS, Automotive, XR, Chromebook, 7-inch, or 10-inch assets
without a matching tested build and device-specific evidence.

## 3. App content declarations for this exact disconnected artifact

Open **Policy → App content** and answer only after the final AAB/dependency,
manifest, and runtime evidence agrees with `../declarations/app-content.md` and
`../data-safety/play-console-template.md`.

| Console area | Exact v0.1 candidate answer | Stop/change condition |
|---|---|---|
| Privacy policy | Final public HTTPS privacy URL | URL is missing, private, redirected to login, stale, or mismatched |
| Ads | No | Any ad/affiliate/sponsored content SDK, AD_ID, or runtime ad request |
| App access | All functionality available without special access; no credentials | Any login, invitation, location restriction, or paywall |
| Target audience | 18 and over only | Product/listing is changed to target minors |
| Content rating | Non-game; none for violence, sexuality, profanity, controlled substances, fear, gambling, interaction, UGC, browser, or location sharing; no purchasable digital goods; no variable AI content | Questionnaire wording differs or a feature is enabled |
| News | No | News/magazine content is added |
| Health | No health features | Health functionality/content is added |
| Government | No | Government affiliation/service is added |
| Financial features | My app doesn’t provide any financial features | Any linked finance, payment, wallet, transfer, lending, insurance, investing, crypto, credit, or regulated advice feature is enabled |
| Account creation/deletion | App does not allow account creation | Any in-app or redirected registration/sign-in exists |
| Generative AI | Not active in this artifact; deterministic local routing/status only | A model response or remote AI service becomes reachable |
| User-to-user/UGC | No | Profiles, messaging, comments, reviews, uploads to other users, or groups become reachable |
| Advertising ID | Not used | Final manifest/SDK contains AD_ID access |

Data safety is mandatory for closed testing. The source-level candidate is **No
user data collected or shared by DarCloud through this app**, but the operator
must not submit that answer until the final signed dependency/manifest audit and
runtime capture prove: API remains unconfigured; no app/SDK endpoint transmits
CRM, prompts, transcripts, task/report, photo/file, diagnostics, identifier, or
purchase data; Android's external speech service behavior matches the policy;
and Play Billing/Data Transport billing components are absent. Any contradiction
changes the answer to **Yes** and requires a fresh per-data-type inventory.

## 4. Initial closed track with global country availability

Use the built-in **initial closed testing track**, not an additional custom
closed track. Google's current documentation says additional closed tracks do
not support country targeting.

1. Go to **Test and release → Testing → Closed testing → Manage track**.
2. Open **Countries/regions**. Unsync from production if the option is available,
   choose **Edit countries**, select every country/region currently offered by
   Play for this app, confirm, and record the count/list. Country eligibility is
   based on each tester's registered Google Play country, not physical location.
3. Open **Testers** and choose one access method:
   - Email list: create/select a named list of valid Google Account emails. CSV
     must be UTF-8 without BOM; an upload overwrites the list.
   - Google Groups: enter the exact `group@googlegroups.com`; testers must join
     the group before opting into the Play test.
4. Enter the confirmed monitored feedback email or URL and save changes.
5. Record the tester-list/group name, member count, feedback contact, and global
   country-selection evidence. Never place tester emails in this repository.

The initial closed release can become Active without proving the later
production-access tester gate. If this is a Personal developer account created
after November 13, 2023, recruit at least 12 testers who remain continuously
opted in for 14 days before applying for production access; a tester who opts
out resets their consecutive period. Keep more than 12 invited to absorb normal
dropout and preserve real engagement/feedback evidence.

## 5. Create, review, and roll out the release

1. Select **Create new release**. Stop if the button is blocked by unfinished
   dashboard tasks or another outstanding release.
2. Configure/review Play App Signing with the authorized account owner.
3. Upload only the final signed AAB and verify Console reads:
   - package `com.darcloud.omarai`;
   - version name `0.1.0`;
   - version code `1`; and
   - target API 36.
4. Use release name `0.1.0 closed test 1` (Console-only label).
5. Copy `release-notes.txt` for en-US.
6. Save as draft; run pre-review checks; reconcile the App Bundle Explorer
   manifest, permissions, device support, SDK warnings, and generated APKs.
7. Resolve every error. Record any warning and the authorized rationale rather
   than silently accepting it.
8. At the final representational step, the authorized operator selects the
   Console action to send/start the closed rollout and records the resulting
   state exactly: Draft, In review, Rejected, Ready to publish, or Active.

## 6. What proves closed testing is complete

All of the following are required before saying Omar AI is in closed testing:

- the closed-track release shows **Active** (not merely uploaded, Draft, Pending
  publication, or In review);
- the tester opt-in URL is visible and recorded—Google says it appears only when
  the app status is Published;
- an allowlisted tester in an enabled Play country opens the link, opts in with
  the allowlisted Google account, and installs/updates v0.1.0 code 1 from Google
  Play; and
- the Play-delivered package passes the deterministic signed-device workflow,
  with its screenshot/evidence hashes and any pre-launch/crash/ANR findings
  recorded.

## Official Google sources verified August 30, 2026

- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
- [Set up a closed test and tester opt-in](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en-GB)
- [Closed-track country availability](https://support.google.com/googleplay/android-developer/answer/7550024?hl=en)
- [New Personal account testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Target API requirement](https://developer.android.com/google/play/requirements/target-sdk)
- [Store preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Financial features declaration](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en)
- [Target audience and app content](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en)
