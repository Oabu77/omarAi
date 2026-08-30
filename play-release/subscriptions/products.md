# Proposed Google Play subscription products

Only two future paid tiers are contemplated in this plan: **Pro** and **Business**. Both are inactive in v0.1.0. There is no AI Operator SKU, phone-minute add-on, marketplace lead product, lifetime purchase, or external Android checkout in this release pack.

Nothing here proves that Play products exist or that billing is implemented. The v0.1.0 source deliberately does not bundle Play Billing. Do not create, activate, or advertise either subscription until a later signed connected build completes the future billing checklist below and the server is authoritative for entitlements.

## Product structure

| Tier | Proposed subscription product ID | Proposed base-plan ID | Billing period | Proposed US price | Status |
|---|---|---|---|---:|---|
| Pro | `omar_ai_pro` | `monthly-auto` | Auto-renewing monthly | USD 29.00 | Inactive draft; price and product creation require confirmation |
| Business | `omar_ai_business` | `monthly-auto` | Auto-renewing monthly | USD 99.00 | Inactive draft; price and product creation require confirmation |

Product IDs and package names are durable identifiers. Before creating them, confirm the final Play package, verified DarCloud LLC payments profile, pricing/tax strategy, local currency settings, and current Play Console constraints.

## Current artifact and future entitlement copy

The disconnected v0.1.0 artifact has one usable access level: **Free local foundation**. Its local request planning, input controls, customer/lead/job/invoice records, dashboard, task status, export, and deletion are not evidence of a paid entitlement. Pro and Business are inactive. The source does not bundle or initialize Play Billing, so it cannot query product details or existing purchases, start or restore a purchase, receive a purchase token, or unlock either tier.

Do not publish Pro/Business benefit copy yet. Before activation, replace the placeholders below with a finite, server-configured entitlement/limit matrix proven in the same signed, connected release candidate. A tier name, roadmap, or current local screen is not entitlement evidence.

| Tier | Required approved copy before activation |
|---|---|
| Pro | `[[CONFIRM_PRO_SHIPPED_ENTITLEMENTS_AND_LIMITS]]` |
| Business | `[[CONFIRM_BUSINESS_SHIPPED_ENTITLEMENTS_AND_LIMITS]]` |

Explicitly excluded from both tiers until a later verified release ships them:

- incoming-call answering or phone-number provisioning;
- SMS, external email sending, or automatic public posting;
- bank-account linking, transfers, investing, credit, or financial advice;
- service-provider marketplace booking, payouts, or live availability;
- end-to-end encrypted messaging/calling;
- automatic legal filings, licenses, company registration, or bank-account creation; and
- guaranteed AI accuracy, job pricing, savings, revenue, or business results.

## Current v0.1.0 artifact gates

Do not release the disconnected candidate until all are PASS for the exact signed AAB:

- [ ] Dependency graph contains no `com.android.billingclient` artifact and no Billing-added Google Data Transport diagnostic dependency.
- [ ] Merged manifest contains no billing permission, BillingClient query/service component, or Billing-added diagnostic receiver/service.
- [ ] Plans UI says Pro/Business are inactive and product query, purchase, and restore are unavailable; no displayed price, trial, success, purchase history, or entitlement is simulated.
- [ ] Runtime capture across cold start, the informational Plans screen, the disabled restore indicator, background/idle, and reinstall shows no billing request, reachable purchase-token receipt/transmission, or Billing SDK diagnostic/device telemetry; no purchase path exists. Dormant future verification DTO/interface names have no call site.
- [ ] Listing, legal pages, Data safety answers, reviewer notes, screenshots, and release notes match that Billing-free behavior.

## Future connected purchase-screen requirements

- Display the localized Play price and billing period returned by `ProductDetails`; never hardcode the checkout price as authoritative.
- Show material entitlements and usage limits before the purchase button.
- State that the subscription auto-renews, when the user is charged, how any trial/offer works, and that it can be managed/canceled in Google Play.
- Link the Terms and Privacy Policy.
- Provide a visible **Restore purchases** action.
- Do not preselect a more expensive tier, hide the free path, manufacture urgency, or obstruct cancellation.
- If no production product is returned, show “Subscriptions unavailable” rather than a simulated success.

## Future connected billing lifecycle gate

Neither subscription is launch-ready until all are PASS in the same later signed connected release:

- [ ] Current supported Play Billing Library is present in the signed release build.
- [ ] Products/base plans are active for the correct package and test track.
- [ ] Backend verifies every purchase token with the Google Play Developer API.
- [ ] Purchase token uniqueness prevents replay across users/workspaces.
- [ ] Purchase is acknowledged only after verification and entitlement assignment.
- [ ] Entitlements are server-authoritative and idempotent.
- [ ] Real-time Developer Notifications are authenticated and processed idempotently.
- [ ] Pending, active, renewed, grace period, account hold, paused, canceled, expired, refunded, and revoked states are tested.
- [ ] Restore/reinstall/new-device and user-account switch are tested.
- [ ] Upgrade/downgrade behavior is defined and tested if offered.
- [ ] App shows billing failures truthfully and never grants access from a client-only flag.
- [ ] User can open Google Play subscription management from the app.
- [ ] Support has tested refund/revocation and reconciliation procedures.
- [ ] Privacy policy/Data safety inventory include the actual purchase metadata and Google/processor flows.
- [ ] Analytics events do not include purchase tokens or other credentials.

## Test accounts

For the future connected release, use Play license testers and test-track installs, not sideloaded production-billing assumptions. Record for each scenario:

- test account alias (never commit its password);
- app version code and package;
- product/base plan/offer token;
- Play order/test identifier;
- backend verification result;
- entitlement before and after;
- RTDN event and idempotent replay result; and
- user-visible status.

## Console confirmations

Package: `com.darcloud.omarai`
`[[CONFIRM_US_PRICES_AND_LOCAL_PRICING]]`  
`[[CONFIRM_TAX_AND_MERCHANT_SETTINGS]]`  
`[[CONFIRM_FREE_PRO_BUSINESS_QUOTAS]]`  
`[[CONFIRM_REFUND_AND_SUPPORT_POLICY]]`
