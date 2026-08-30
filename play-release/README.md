# Omar AI — Google Play release pack

This directory contains a **draft, truth-first release pack** for the Android v1 release of **Omar AI**. It does not publish, upload, submit, or represent that Google has approved the app.

## Proposed publisher

**DarCloud LLC** is the proposed publisher. Before any public use, an authorized representative must confirm:

- the exact legal entity name shown in Play Console;
- the organization address and country;
- a monitored support email and privacy email;
- a support phone number, if one will be listed;
- authorized control and legal approval of the deployed privacy, terms, support, and deletion pages;
- trademark rights for “Omar AI™”; and
- that the Play payments profile, tax profile, and developer verification belong to the same authorized entity.

No address, phone number, or legal-contact email has been invented in these files. Before deployment or submission, search for every `[[` token, `.invalid`, staged-source notice, and `proposed publisher/provider/operator` wording.

## Contents

| Path | Purpose |
|---|---|
| `listing/en-US.md` | English (United States) Play Store listing copy |
| `legal/index.html` | Accessible policy-site landing page source |
| `legal/support.html` | Accessible support-page source for the current local-only release |
| `legal/privacy-policy.html` | Local-only privacy-policy source |
| `legal/terms-of-service.html` | Local-only terms source |
| `legal/account-deletion.html` | No-account local-data deletion instructions |
| `legal/deployment-notes.md` | Current hosting gates and future account-form contract |
| `data-safety/inventory.md` | v1 data-flow inventory and evidence gates |
| `data-safety/play-console-template.md` | Draft Play Data safety answers |
| `declarations/app-content.md` | App access, ads, financial, audience, content-rating, and AI drafts |
| `screenshots/screenshot-plan.md` | Truthful capture plan and captions |
| `subscriptions/products.md` | Future Pro and Business product plan; inactive in v0.1.0 |
| `review/reviewer-instructions.md` | Draft reviewer-access instructions |
| `review/release-notes.txt` | v1 release notes |
| `review/testing-checklist.md` | End-to-end release gates |
| `review/submission-readiness-audit.md` | Current evidence and the eleven remaining closed-test submission gates |
| `assets/asset-specs.md` | Play asset requirements and export instructions |
| `assets/icon-source.svg` | Editable 512 × 512 icon source |
| `assets/icon-512.png` | Verified 512 × 512 Play icon raster |
| `assets/feature-graphic-source.svg` | Editable 1024 × 500 feature-graphic source |
| `assets/feature-graphic-1024x500.png` | Verified 1,024 × 500 RGB feature graphic |
| `SOURCES.md` | Current official Google/Android references |

## Submission rule

Only copy a draft answer into Play Console when the corresponding release-candidate evidence is marked **PASS**. If the implementation, dependency graph, merged Android manifest, production configuration, or vendor contract changes, re-audit the listing, privacy policy, Data safety form, and app-content declarations together.

## Current status

This is a preparation artifact, not proof of release readiness. It describes the disconnected, no-account, local-first v0.1.0 candidate only. A signed AAB now exists at `../android/app/build/outputs/bundle/release/app-release.aab` (6,921,439 bytes; SHA-256 `5932622c111d33b93afaa27c38c0c7e44ba871e88f8b1d6c5335f3a71a2edc97`), and its configured privacy/deletion URLs plus the public support and terms endpoints returned HTTP 200 on August 30, 2026. The bundle passed `bundletool validate` and OpenSSL CMS signature verification. A release remains blocked until every applicable item in `review/testing-checklist.md` is complete, legal/contact approvals are confirmed, genuine screenshots are captured from the signed candidate, and Play Console confirms the submission state. Pro and Business are inactive draft products: the current source does not bundle Play Billing, so product queries, purchases, and purchase restoration are unavailable. Merged-manifest/dependency, runtime-network, device, and Play-delivered inspection remain required.
