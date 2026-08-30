# Truthful screenshot capture plan

Capture from the signed v0.1.0 release candidate installed through the intended Play test track. Do not use generated UI, a connected-AI mock, fake revenue, fake payment, or an externally completed task.

## Phone set

Target: 4–6 portrait PNG/JPEG images at 1,080 × 1,920 (9:16), no alpha.

| Order | Actual screen | Optional overlay | Alt text (under 140 characters) | Gate |
|---:|---|---|---|---|
| 1 | Home with request field and real input controls | Plan a request | Omar AI home with a request field and voice, camera, photo, and file controls | Signed build; no dead quick action shown |
| 2 | Business dashboard after entering clearly fictional local records | Organize business records | Local business dashboard with record-based leads, jobs, invoices, and revenue totals | Every value reconciles to local demo records labeled for capture |
| 3 | Business record lists/forms | Keep work on device | On-device customer, lead, job, and invoice records | Supported add/read/lead-status update, validation, and empty/error states pass |
| 4 | Command Center with a disconnected Planned task | See the exact status | Planned task showing that the Omar AI service is disconnected and no action occurred | Must be produced by current repository flow, not image editing |
| 5 | Integration status page | Connected means connected | Integration screen showing the Omar AI service and future modules as disconnected | States match current build configuration |
| 6 | Privacy & data page | Export or delete local data | Privacy screen with local JSON export and local-data deletion controls | Export/delete and cleanup tests pass |

Use only rows whose gates pass. Do not show an AI answer, photo estimate, submitted report, paid entitlement, account-deletion flow, receptionist, finance, messages, marketplace, payment, or filing screen in this release.

## Capture data

- Manually create a dedicated set of fictional local records; the app does not preload a reviewer workspace.
- Do not use real names, customer details, addresses, phone numbers, emails, notifications, purchases, or a personal account.
- Use modest values and do not imply the screenshot data is actual business performance.
- If an overlay is used, keep it below 20% of the image and do not cover the disconnected status.

## Visual hygiene

- Capture on a clean emulator/device with no unrelated notifications.
- Show the actual app UI prominently; no device frame, finger, Play badge, ranking, award, testimonial, price, “Free,” “Best,” or install call to action.
- Do not composite screens, alter task status, or add provider evidence that the app did not produce.
- Use JPEG or 24-bit PNG without alpha; 320–3,840 px; longest side no more than 2× shortest side.
- Add accurate alt text and revise it if the final screen differs.

## Tablet set

Upload tablet screenshots only after adaptive layouts pass representative 7-inch/10-inch devices, rotation, split-screen, keyboard, TalkBack, and large fonts. Do not reuse phone captures in tablet slots.

## Capture record

```text
Filename:
Package/versionCode/versionName:
Git commit/AAB SHA-256:
Play track:
Device/API/resolution:
Locally created fictional records:
OMAR_API_CONFIGURED value:
Capture date/reviewer:
Claim-gate result:
```
