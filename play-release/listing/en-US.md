# Main store listing — English (United States)

Status: **candidate copy for the disconnected v1 foundation, not submitted**. It describes package `com.darcloud.omarai`, version `0.1.0` (`versionCode 1`) as currently implemented. Use only after a signed AAB build and device tests pass.

## App details

**App name** (7/30 characters)

```text
Omar AI
```

**Short description** (72/80 characters)

```text
Organize local business records and plan requests in one clear workspace
```

**Full description** (under 4,000 characters)

```text
Omar AI v1 is a local-first workspace for adults who want to plan requests and organize basic small-business records on one Android device.

PLAN REQUESTS HONESTLY

Type a request or dictate text using Android’s speech-recognition service. Omar AI routes the request to a clearly labeled capability and records its task state. The online Omar AI service is not connected in this build, so the app marks the request as planned and does not claim that AI analysis or an external action occurred.

ADD CONTEXT TO A DRAFT

Use the camera, Android Photo Picker, or system file picker to add context to a draft. While the service is disconnected, selected items are not uploaded or analyzed by Omar AI.

ORGANIZE LOCAL BUSINESS RECORDS

Create on-device customer, lead, job, and invoice records. Track lead and job status, scheduled jobs, invoice totals, and paid amounts. Dashboard summaries are calculated only from records saved on your device, with clear empty states instead of invented business results.

SEE EXACT TASK STATUS

The Command Center shows local task status, actions, permissions used, results, and errors. A local planned task can be canceled. The app separates a saved plan from a submitted request or verified completion.

CONTROL LOCAL DATA

Export supported local records to a JSON file you choose, or delete local database records from Privacy & data. Android backup is disabled for app data. Camera permission is requested only when you use that control. Voice input launches your device's speech-recognition service without Omar AI requesting microphone permission; photo and document selection uses Android system pickers.

CURRENT RELEASE LIMITS

This build has no Omar AI account or sign-in, no connected AI-answer service, and no live phone calls, linked financial accounts, user messaging, marketplace booking, customer payments, or company filings. Pro and Business purchase controls remain unavailable until a production backend can verify Google Play purchases.

Omar AI is intended for adults age 18 and older. Review important records and task details before relying on them.
```

## Store settings draft

| Field | Draft | Gate |
|---|---|---|
| Default language | English (United States) | Confirm Play app setup |
| App or game | App | None |
| Category | Business | Confirm the exact current Play category list |
| Tags | Business management/productivity equivalents, if offered | Select only exact current tags; do not imply connected AI |
| Contains ads | No | Signed AAB manifest/dependency/runtime audit confirms no ad SDK, sponsored placement, or Advertising ID use |
| Contact email | `[[CONFIRM_SUPPORT_EMAIL]]` | Required, monitored, and authorized for public display |
| Website | `[[CONFIRM_PUBLIC_SUPPORT_URL]]` | Live HTTPS support page |
| Phone | `[[CONFIRM_SUPPORT_PHONE_OR_OMIT]]` | Optional; do not invent or expose a personal number |
| Privacy policy | `[[CONFIRM_PUBLIC_PRIVACY_URL]]` | Current local-only policy is live and placeholder-free |

## Claim-evidence gate

| Listing claim | Required evidence before use |
|---|---|
| Local request routing | Unit test plus signed-device test proves deterministic route and truthful disconnected message |
| Voice-to-text | Android speech recognizer launches contextually; denial/error path works; policy identifies the device speech service |
| Camera/photo/file draft context | Only user-invoked controls work; raw bytes are not uploaded/analyzed while API is unconfigured; stored filename/MIME/source metadata is accurate; temporary camera files and grants are cleaned up |
| Local CRM | Supported customer, lead, job, and invoice add/read paths plus lead-status updates persist correctly; full local deletion works without implying per-record edit/delete |
| Local dashboard | Every metric reconciles to local records; no sample or hardcoded revenue is displayed |
| Command Center | Planned/disconnected/canceled states and audit details match local repository state |
| Export/delete | Exported JSON matches displayed local records; deletion clears all app-created local content and grants promised by the UI/policy |
| No account/sign-in | No hidden or redirected registration/auth flow exists in the release artifact |
| Paid plans unavailable | Purchase button is disabled without backend verification; no entitlement is granted locally |
| No ads | Signed AAB and runtime traffic confirm no ad/AD_ID/sponsored content |
| Adults 18+ | Onboarding, listing, policy, and distribution settings consistently target adults |

Do not replace the disconnected paragraph with connected-AI copy until authentication, the production API, provider processing, Data safety answers, moderation, deletion, and billing verification all pass and a new listing review is completed.
