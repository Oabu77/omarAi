# Legal-site deployment and future deletion-form notes

The files in this directory are static, tracking-free source for the current **no-account, disconnected Android v0.1.0** candidate. They contain relative links and can be used as the source of a GitHub Pages policy site only after the confirmation gates below pass. They are not evidence that a website, app, or Play release is deployed.

The current `account-deletion.html` is intentionally an instruction page, not a web form. This release creates no Omar AI account and sends no app data to a DarCloud backend, so users delete data in the app, clear Android app storage, or uninstall. Do not add or submit a disabled or fake account-deletion form for this artifact.

## Current site map

| Source | Intended public role |
|---|---|
| `index.html` | Policy/support landing page |
| `support.html` | Current-scope help and monitored support contact |
| `privacy-policy.html` | Privacy policy for local-only v0.1.0 |
| `terms-of-service.html` | Terms for local-only v0.1.0 |
| `account-deletion.html` | No-account local-data deletion instructions |

All pages use relative navigation and include no analytics, ads, pixels, remote fonts, external scripts, cookies, or form submission. Keep that property unless a later privacy/Data safety review approves a change.

## Required confirmation before public hosting

- Exact verified Play publisher/entity name and DarCloud LLC authority.
- Legal mailing address and country.
- Monitored support and privacy email addresses.
- Governing law, dispute, liability, and region-specific terms approved by qualified counsel.
- Trademark/brand authority and launch countries.
- Stable public HTTPS domain and final paths controlled by the authorized publisher.
- Product, security, privacy, support, and legal approval of the exact hosted files.

Before hosting or entering a URL in Play Console:

1. Replace every unique `[[CONFIRM_…]]` token with approved information.
2. Remove every visible staged-source notice and proposed-entity qualifier as approved.
3. Change `noindex,nofollow` to `index,follow` on pages intended for public search indexing.
4. Configure the final privacy URL in the Android release build; verify the in-app link on a Play-track install.
5. Run HTML, keyboard, screen-reader, contrast, mobile-layout, and relative-link checks against the deployed HTTPS site.
6. Confirm the hosted response contains no tracker, redirect, login, geofence, broken asset, or placeholder.
7. Archive the published source and effective/review date.

Suggested paths are deliberately not filled in until the domain is authorized:

```text
https://[[CONFIRM_PUBLIC_DOMAIN]]/omar-ai/
https://[[CONFIRM_PUBLIC_DOMAIN]]/omar-ai/support.html
https://[[CONFIRM_PUBLIC_DOMAIN]]/omar-ai/privacy-policy.html
https://[[CONFIRM_PUBLIC_DOMAIN]]/omar-ai/terms-of-service.html
https://[[CONFIRM_PUBLIC_DOMAIN]]/omar-ai/account-deletion.html
```

## Future connected-account form description — not for v0.1.0

If a later release adds account creation, replace the no-account instructions before release with both a working in-app deletion path and a working public request path. The public form should request only the minimum identifier needed to locate and verify the account plus optional feedback. It must explain the data deleted, any legally retained data, expected timing, and how subscriptions are canceled separately.

The future form must connect to a real protected backend and provide:

- HTTPS POST, appropriate CSRF defense, accessible bot/rate-limit controls, and no advertising tracker;
- generic responses that do not reveal whether an account exists;
- short-lived, single-use verification tied to the account’s verified channel;
- a durable request ID, timestamps, authenticated state transitions, and privileged audit log;
- session/token revocation and deletion across every applicable database, object store, index, notification identity, analytics identity, identity provider, and processor;
- documented lawful-retention exceptions and backup-expiry tracking;
- idempotent retry, partial-failure handling, completion/failure notice, and support escalation; and
- tests for duplicates, expiration, federated identity, active Play subscriptions, workspaces, processor outage, restoration, and legal hold.

Recommended future states:

```text
RECEIVED → VERIFYING → VERIFIED → DELETION_SCHEDULED → PROCESSING
→ COMPLETED | PARTIAL_RETRYING | REJECTED_WITH_REASON
```

Never block an account-deletion request solely because a Play subscription remains active. Explain cancellation separately, and prevent entitlement reconciliation from silently recreating a deleted account. None of this future flow may be described as available until production-like end-to-end evidence exists.
