# Play Console app-content declaration draft

Artifact scope: Android package `com.darcloud.omarai`, v0.1.0/code 1, with `OMAR_API_BASE_URL` left at the disconnected `.invalid` default. Status: **draft only; not submitted**. Re-answer everything if a backend URL, authentication, any purchase or subscription flow, AI generation, upload, analytics, ads, or another integration is enabled.

## Current-build decision summary

| Console area | Proposed answer | Evidence/gate |
|---|---|---|
| Ads | **No** | Only INTERNET and CAMERA in the source manifest; no ad SDK or AD_ID in declared dependencies; signed-AAB/runtime audit still required |
| App access | **All functionality is available without special access** | No account/sign-in/paywall; Play Billing is not bundled and Pro/Business query, purchase, and restore are unavailable |
| Target audience | **18 and over only** | Adult local business organizer; not designed/marketed for children |
| News app | **No** | No news or magazine content |
| Health app | **No health features** | No diagnosis, treatment, health record, or device function |
| Government app | **No** | No government affiliation or authorization |
| Financial features | **My app doesn’t provide any financial features** | Local invoice/lead amounts are user-entered organizer records; no banking, wallet, payment, transfer, lending, insurance, crypto, investing, credit, or advice |
| Gambling | **No** | No gambling, wager, contest, or prize feature |
| Cryptocurrency | **No** | No wallet, exchange, token, NFT, transfer, or portfolio feature |
| Advertising ID | **Not used** | No AD_ID permission/SDK expected; verify signed merged manifest |
| Account creation/deletion | **The app does not allow account creation** | No sign-up, sign-in, SSO, or redirected registration flow; local data can be exported/deleted |
| Generative AI | **Not active in this artifact** | Current app uses deterministic local routing and fixed truthful status text; API is disconnected and no AI answer is generated |
| User-to-user/UGC | **No** | No public profile, messaging, group, comment, review, or content-sharing service |

## Ads declaration

Proposed selection: **No, my app does not contain ads.**

Before selecting No, inspect the signed AAB merged manifest and full dependency graph, and capture runtime traffic from cold start through every screen. Confirm no display/native/video ad, affiliate/sponsored result, remotely supplied promotion, mediation SDK, or `com.google.android.gms.permission.AD_ID` exists.

## App access

Proposed selection: **All functionality in my app is available without any access restrictions.**

No credentials should be entered in Play Console for this build. Reviewers complete the four onboarding pages and can access Home, Business, Tasks, Settings, local export/delete, and integration status. The Plans page is informational, shows a static unavailable state, and includes only a disabled “Purchase restore unavailable” indicator—no purchase control or subscription-management link. The Play Billing SDK is not bundled, so Pro/Business product queries, purchase launch, purchase restoration, and paid entitlement activation are unavailable.

If authentication or a remote API is configured in a later artifact, change the selection and supply stable private reviewer credentials/instructions.

## Target audience and content

Proposed age group: **18 and over** only.

Draft explanation if requested:

> Omar AI v1 is an adult small-business organizer with customer, lead, job, invoice, and task records. Its copy, workflow, and visual design are not directed to children.

Keep onboarding, listing, screenshots, country settings, and marketing consistent. Do not add under-18 groups merely to broaden availability.

## Content rating / IARC worksheet

Google/IARC assigns the rating. Answer the exact current questionnaire against the signed build.

| Topic | Current-build factual input |
|---|---|
| App type | Non-game |
| Developer-authored violence | None |
| Sexuality/nudity | None |
| Profanity/crude humor | None |
| Controlled substances | None |
| Fear/horror | None |
| Gambling | None |
| User-to-user interaction | No |
| User content publication/exchange | No |
| General-purpose browser/unrestricted web access | No |
| Location sharing | No |
| Purchasable digital goods | No in this disconnected artifact; reassess when verified Pro/Business purchase is enabled |
| AI-generated variable content | No in this disconnected artifact; deterministic routing/status only |

## Financial features declaration

Proposed selection: **My app doesn’t provide any financial features.**

Local invoice totals, paid amounts, and lead values are manual on-device organizer records. The app does not connect a financial account, process customer payment, move/hold/lend/exchange/invest money, advise on investments, provide rewards/credit/insurance, or verify financial data. The Play Billing SDK is not bundled, and the disconnected artifact cannot query products or existing purchases, launch or restore a purchase, or activate Pro/Business entitlement.

If any Money Manager, customer-payment, payout, wallet, investment, credit, insurance, or financial-advice capability becomes active, stop and redo the declaration, licensing review, listing, policy, and Data safety form.

## Generative AI policy status

The current Android artifact does not generate AI content because its API is unconfigured. It stores a deterministic local response explaining the disconnection. A local **Report output** control exists, but it cannot submit to a moderation backend while disconnected and says so.

Before enabling generative output in any release, require:

- production authentication/API/provider evidence;
- restricted-content input/output safeguards;
- an in-app report that reaches a monitored moderation queue;
- abuse rate limits and appeal/support process;
- data/retention/training contracts and accurate Data safety changes; and
- new end-to-end tests, listing copy, screenshots, reviewer access, and app-content answers.

## Account and data deletion

The current app has **no app account**, so Play’s account-creation deletion rule is not triggered by this artifact. It provides:

- Settings → Privacy & data → Export local data;
- Settings → Privacy & data → Delete local data; and
- Android clear-storage/uninstall controls.

The external deletion page in this pack should explain the no-account local-data workflow. Do not answer that an app account can be deleted and do not submit a disabled web form. If accounts are later enabled, both a working in-app account-deletion path and a working public request path are required before release.

## Data safety summary

Source-level candidate for the disconnected artifact: **No user data is collected or shared by DarCloud through the app**, because records remain on device and no DarCloud API is configured. The device speech provider captures audio itself and returns only a locally stored transcript. The source does not bundle Play Billing, access purchase data, or invoke Billing SDK telemetry. This answer is not final until the signed AAB audit confirms:

- Android speech-recognizer/provider behavior matches the reviewed source flow;
- the Play Billing SDK, its billing-service manifest/query components, and Billing-added diagnostic/transitive components are absent;
- product/purchase queries, purchase launch, restore, reachable purchase-token receipt/transmission, and Billing SDK diagnostic/device telemetry do not occur; dormant future verification DTO/interface names remain unreachable;
- all other transitive SDK behavior; and
- every network request observed in production-like testing.

See `../data-safety/inventory.md` and `../data-safety/play-console-template.md`. A configured backend changes the answer to Yes and activates a larger data inventory.

## Permissions and APIs

Expected signed manifest:

| Permission/API | Current use | Required behavior |
|---|---|---|
| `android.permission.INTERNET` | Reserved for future API capability; Omar API remains unconfigured and Play Billing is not bundled | No cleartext; signed runtime capture must show no app-initiated endpoint in current flows |
| `android.permission.CAMERA` | User-invoked `TakePicturePreview` | Request contextually; no upload while API disconnected; delete temporary camera files correctly |
| Android speech-recognition intent | User-invoked device speech service; Omar AI declares no `RECORD_AUDIO` permission | Device service controls its own permission; Omar AI receives transcript text and does not receive or save raw audio |
| Android Photo Picker | User-selected image draft context | No broad media permission |
| Storage Access Framework | User-selected document draft context/export | No broad storage permission; release unused URI grants |

Expected absent: `RECORD_AUDIO`, POST_NOTIFICATIONS, location, device contacts, SMS, call logs, phone state, broad media/storage, `MANAGE_EXTERNAL_STORAGE`, AccessibilityService, VPN, device admin, package visibility, exact alarm, full-screen intent, health permissions, notification listener, Advertising ID, and foreground services.

The merged release manifest may also contain AndroidX's app-specific signature permission for non-exported dynamic receivers, non-exported Startup provider and Room invalidation service, and a Profile Installer receiver exported only behind the system-protected `android.permission.DUMP` permission. These are library infrastructure, not user-granted dangerous permissions. Confirm their exact names, origins, exported flags, and protections from the final signed AAB rather than claiming that the merged manifest contains only the two source permissions.

## Other release checks

- Privacy policy is required in app and at a stable public HTTPS URL even though data is local-only.
- Target API is 36 for this artifact; re-check the official requirement at submission.
- No independent-security-review badge may be claimed.
- Inspect all native libraries for 64-bit and 16 KB page-size compatibility.
- The signed AAB has been produced and passed `bundletool validate` and OpenSSL CMS signature verification. Remaining merged-manifest/dependency, runtime-network, device, policy, and Play-track tests are still release blockers.
