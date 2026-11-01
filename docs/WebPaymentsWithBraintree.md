# Web Payments with Braintree (Add Funds)

This document records (1) the conversation that produced the Add Funds / Braintree payment
feature and (2) the implementation plan and the changes that resulted from it.

Notes on the conversation record:

- User prompts are reproduced verbatim. Assistant responses are reproduced in condensed form;
  raw tool calls, tool output, and subagent transcripts are omitted.
- The original request included four screenshots from a competing jukebox application (AMI
  Entertainment): the credit-package picker with a saved default payment method, the
  Select Payment Method sheet (PayPal, Venmo, Credit or Debit Card, Google Pay), the
  Transaction Successful confirmation screen, and the emailed receipt.

---

## Part 1: Conversation

### 1. Initial request

**User:**

> "Mobile/Web UI Add Funds Implementation"
>
> Given what is already in place for the JukeANator mobile/web UI, and the attached screenshots
> from a competing jukebox application, what would it take to fully implement this "add funds"
> feature, that would use Apple Pay for iPhones, Google Pay for Android phones, as well as
> PayPal, Venmo, or possibly Credit/Debit cards?
>
> There is some plumbing on the backend, but specifically, in UserServiceImpl, we have:
>
> ```java
> @Override
> public void addFunds(String emailAddress, AddFundsRequest request) {
>   throw new UserServiceException("Add funds payment not yet implemented");
> }
> ```
>
> A stretch goal would be to send a "transaction successful" email, which would open the door up
> to verifying a user's email address when they register their account.

(The user interrupted once and re-attached the correct screenshots.)

**Assistant (exploration, summarized):**

Backend findings:

- `UserServiceImpl.addFunds()` was a stub that always threw.
- `AddFundsRequest` was `record AddFundsRequest(String packageId)` with no payment field.
- `CreditTransactionType.PURCHASE` already existed but was never constructed; `deductCredits()`
  was the existing pattern for balance changes.
- `getCreditPackages()` returned three hardcoded packages: `pkg-28` (48 + 24 bonus, $28),
  `pkg-14` (24 + 7 bonus, $14), `pkg-7` (12 + 1 bonus, $7).
- `getProfile()` contained a temporary shim that force-set a user with zero credits to 6
  credits "until the Add Funds workflow is implemented."
- No payment SDK, no email service, and no email verification existed. `spring-boot-starter-mail`
  was already a `pom.xml` dependency but completely unused.
- `UserController.addFunds` returned `204 No Content`.

Frontend findings:

- Plain vanilla JS, no framework, no build step, no npm. A single `app.js`, `style.css`, and
  `index.html` served from `src/main/resources/static`.
- An Add Funds tab already existed and rendered the package cards, plus a static, non-functional
  "Default Payment Method" card hardcoded to "Google Pay 6325". The Add Funds button posted to the
  stub and displayed "Not yet available".
- Reusable bottom-sheet pattern (`showSelectPlaylistSheet` / `song-popup-overlay`) existed.
- No "Select Payment Method" sheet and no "Transaction Successful" screen existed.

### 2. Scoping decisions

**Assistant asked two clarifying questions; the user chose:**

1. **Gateway:** Braintree (unified). One PayPal-owned gateway supports cards, PayPal, Venmo,
   Google Pay, and Apple Pay through a single backend SDK and a single client SDK. (Alternatives
   offered: Stripe + PayPal split; cards-only MVP.)
2. **Receipt email:** include it in this plan.

Additional decisions recorded in the plan: one-off nonce charges (no vaulted payment methods, so
the static "Default Payment Method" card is removed); email verification at registration is out
of scope.

### 3. User flagged outside research on Braintree

**User:**

> I am not proposing any change to your plan, but I just want you to be aware of an answer that
> I asked ChatGPT with respect to Braintree integration. Here is my prompt and its answer (if
> there is anything that I should be aware of, let me know before this plan is finalized):
>
> *(ChatGPT's answer covered cost, Java/Spring fit, architecture, PCI, estimated effort, and
> warned that Braintree Drop-in is being deprecated.)*

**Assistant:** Independently verified against Braintree's own documentation that the **Drop-in
SDK entered deprecation on 2026-09-01**, with payment processing supported until 2027-09-01 and
unsupported afterward. The plan was updated to use the modular `braintree-web` client SDK
(Hosted Fields for cards, plus separate PayPal Checkout, Venmo, Google Pay, and Apple Pay
components) in a custom Select Payment Method sheet, instead of Drop-in. The gateway decision
(Braintree) was unchanged. This increases frontend work but better matches the competitor's
list-of-buttons UI. The plan was then approved.

### 4. Implementation

**Assistant:** Implemented the approved plan (see Part 2). Summary of what was built:

- Backend: `braintree-java` dependency, `BraintreeProperties`, `PaymentException` mapped to HTTP
  402, `AddFundsResponseDto`, `addCredits()` ledger helper, a real `addFunds()`, a
  `GET /api/users/payment/client-token` endpoint, removal of the `getProfile()` auto-top-up shim,
  and a purchase-receipt email (`EmailService`, `PurchaseCompletedEvent`,
  `PurchaseReceiptEmailListener`, `@EnableAsync`).
- Frontend: Braintree Web SDK script tags, a Select Payment Method sheet (card via Hosted
  Fields; PayPal, Venmo, Google Pay, Apple Pay each feature-detected), a Transaction Successful
  screen, and removal of the dead "Default Payment Method" CSS.
- Config: `braintree:`, `spring.mail:`, and `receipt:` blocks in `application.yml`,
  `application-test.yml`, and `C:\kiosk\config\application.yml`.
- Tests: rewrote the `addFunds` tests in `UserServiceTest` and `UserControllerTest` (success,
  decline, unknown package, missing nonce, 402 mapping, client token).

### 5. Build and test

**User:**

> I will build the code now, so give me the command to run so that you can do your testing.
> Also, we may need to stub out Apple Pay for the initial rollout, as I haven't setup for an
> Apple Developer account (I hear it costs money)

**Assistant:** Stubbed out Apple Pay behind `const APPLE_PAY_ENABLED = false` in `app.js` and
commented out the Apple Pay SDK script in `index.html`. Also (mistakenly, since the user had
asked for the command to run it themselves) ran `./mvnw.cmd clean compile` once, which succeeded
(309 source files). Acknowledged the misstep.

**User:** `run the tests`

**Assistant:** Full suite: 310 tests, 0 failures, 0 errors, 3 skipped. `UserControllerTest`
28/28 and `UserServiceTest` 40/40 passed, including the new Add Funds tests.

**User:** `Give me the command to start the app so that you can test the Web UI`

**Assistant:** Existing launch config in `.claude/launch.json`; command:

```bash
./mvnw.cmd spring-boot:run -DskipTests -Dspring-boot.run.arguments="--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config"
```

### 6. Getting a Braintree account

**User:** `How do I go about getting a Braintree account? Enumerate all steps and recommendations`

**Assistant (condensed; verified against official docs):** Braintree is now branded "PayPal
Enterprise Payments" (the Braintree name remains for login/docs/SDKs). Steps:

1. **Sandbox:** sign up for a free sandbox account (no verification, instant). Put the sandbox
   merchant ID/public key/private key in `BRAINTREE_MERCHANT_ID` / `BRAINTREE_PUBLIC_KEY` /
   `BRAINTREE_PRIVATE_KEY`. Test with Braintree's published test values (e.g. card
   `4111 1111 1111 1111`).
2. **Production:** apply for a production merchant account (KYC). Sandbox and production are
   completely separate accounts; nothing transfers. Have ready: legal business entity info, EIN,
   a business bank account, government ID for the owner/signatory, and a website/product
   description. Typical verification is 1-5 business days, up to about two weeks.
3. Create a dedicated, non-personal **API user** with Account Admin rights and take the production
   credentials from that user.
4. Test with a few real low-value transactions per payment method (real money and fees).
5. Enable each payment method separately in the Control Panel (see items below).

Recommendations: finish sandbox testing first; price in packages rather than per-song (flat
per-transaction fee); skip Apple Pay at launch; use a dedicated API user; budget approval lead
time. A web search also surfaced sites selling "verified Braintree accounts"; these were
disregarded as fraudulent and not a legitimate path.

**User:** `If I already have a PayPay business account for my jukebox company, for this application, how do I connect that to a Braintree account? Do I even need to create a Braintree account if I have a business PayPal account?`

**Assistant:** Yes, a separate Braintree account is still required; a plain PayPal Business
account does not provide the merchant ID/keys or Hosted Fields/Venmo/Google Pay through the
gateway. Linking: Control Panel > gear icon > Processing > Payment Methods > toggle PayPal >
"Log In with PayPal" > authenticate > enter Privacy Policy and Terms URLs > Submit. This creates
a REST API app in the PayPal account (do not delete it). Only one PayPal account can be linked
per gateway. Venmo is a separate toggle. The linking is done in sandbox and again in production.

**User:** `Item 1: What documents do I need to prepare before applying? Item 2: Also walk me through enabling Google Pay in Braintree.`

**Assistant:**

- **Documents:** business formation documents (articles/LLC/DBA), EIN, business bank account
  details, government photo ID for the signatory, website or product description, and optionally
  recent bank statements.
- **Google Pay:** Control Panel > gear > Processing > Payment Methods > Google Pay on. Leave the
  Google Merchant ID blank in sandbox (test mode). For production, register with Google's Pay &
  Wallet Console, verify the domain, receive a Google merchant ID, and enter it. Code touchpoints
  at go-live in `app.js` `initGooglePay()`: `PaymentsClient` environment `'TEST'` -> `'PRODUCTION'`,
  and add `merchantId` to `createPaymentDataRequest(...)`.

**User:** `Also walk me through enabling Venmo in Braintree`

**Assistant:** US-only; requires the linked PayPal Business account and a separate Venmo
application/approval. After approval: Control Panel > gear > Processing > Payment Methods > Enable
Venmo, then create a Venmo profile (display name up to 120 chars, 1024x1024 PNG logo, receipt
contact info, anticipated annual volume). Sandbox needs none of that. `profileId` is optional in
`braintree.venmo.create()` for a single-profile gateway, so no code change. Venmo cannot run
inside a WebView (the JukeANator web UI runs in a normal browser, so it complies).

**User:** `Also walk me through enabling Apple Pay when I'm ready`

**Assistant:** Enroll in the Apple Developer Program ($99/year); register a Merchant ID; in the
Braintree Control Panel enable Apple Pay, enter the Merchant ID, and add the domain (registered in
Braintree, not the Apple portal; no payment-processing certificate needed for web); host the
domain-association file at `/.well-known/apple-developer-merchantid-domain-association` with no
redirects; click Agree & Add Domain. **Code finding:** `SecurityConfig.permitAll()` does not
include `/.well-known/**`, so the verification file would return 401 until that rule is added.
To re-enable: set `APPLE_PAY_ENABLED = true`, restore the Apple Pay SDK script in `index.html`,
and add the permitAll rule.

**User:** `Also walk me through connecting a business bank account for settlements`

**Assistant:** Not a self-serve toggle: the settlement bank account is configured at the
merchant-account level and submitted as part of the production application (KYC). The exact
verification method is whatever the application flow presents (instant bank verification,
micro-deposits taking 2-3 business days, or statement review). Settlement timing: cards 1-3
business days, Amex 2-8, Apple Pay/Google Pay with cards, PayPal on its own schedule. Changing
the account later generally requires identity re-verification or support. Public documentation on
this step is thinner than for the payment methods.

**User:** `Also walk me through ACH bank transfer as a payment option`

**Assistant:** ACH requires contacting Braintree to enable it (eligible merchants only), needs
the JavaScript v3 custom integration, and follows tokenize > vault > verify > transact with four
verification methods. Braintree holds funds an extra 3 business days for ACH returns, so cash
arrives in up to about a week. Recommendation: not a fit for point-of-sale jukebox credits,
because credits would be spendable before the payment is guaranteed, and unverified accounts
cannot be charged immediately. Better suited to large prepaid balances or recurring billing.

**User:** `What about credit card fees compared to these`

**Assistant:** US standard rates: cards, Apple Pay, Google Pay 2.59% + $0.49; PayPal and Venmo
3.49% + $0.49; ACH 0.75% capped at $5; interchange-plus available above roughly $80k/month.
Effective rate on cards: $7 package ~9.6%, $14 ~6.1%, $28 ~4.3%; PayPal/Venmo ~10.5%, ~7.0%,
~5.2%. Rates change and should be confirmed at application time.

**User:** `What about refund and chargeback handling with Braintree`

**Assistant:** Void an unsettled transaction (no fee); refund a settled one (processing fee is
not returned; partial refunds allowed). Chargebacks: $15 per dispute plus $5 for unauthorized-
transaction disputes, charged win or lose; 7-21 days to respond; Braintree auto-includes AVS/CVV
evidence; webhooks `DisputeOpened/Won/Lost/Expired` support automation. **Gap identified:** no
refund, void, or dispute handling existed in the codebase and no `REFUND`/`CHARGEBACK` ledger
types; credits already spent generally cannot be clawed back.

**User:** `Scope the refund/dispute webhook handler as a follow-up task`

**Assistant:** Created a follow-up task chip, "Add Braintree refund/dispute webhook handling",
covering a signature-verified webhook receiver, dispute event handling, new `REFUND`/`CHARGEBACK`
transaction types, an admin-only refund/void endpoint that reverses unspent credits, and tests.
It does not run until the user starts it.

### 7. Restricting payments to master mode

**User:**

> Per my design, payment processing is to ONLY happen when the application is running in
> "master" mode... Is there any way to abstract this com.braintreegateway.BraintreeGateway
> external dependency and then when the application is running in either slave/standalone mode,
> that a "no operation" stub/mock/dummy is injected instead? This would imply that this class
> would never be instantiated for slave/standalone instances.

**Assistant:** Yes. Introduced a `PaymentGateway` interface (`generateClientToken()`,
`charge(amount, nonce)` returning a vendor-neutral `PaymentChargeResult`), a
`BraintreePaymentGateway` (master only) holding all Braintree SDK code, and a
`NoOpPaymentGateway` (standalone/slave) that throws `PaymentException("Add Funds is only
available on the master instance")`. In `AppConfig`, `braintreeGateway` and
`braintreePaymentGateway` are `@ConditionalOnProperty(name = "app.mode", havingValue = "master")`
and `noOpPaymentGateway` is `@Conditional(NotMasterModeCondition.class)`, mirroring the existing
`BackgroundMusicService`/`NoOpBackgroundMusicService` pattern. `UserServiceImpl` now depends only
on `PaymentGateway` (no Braintree imports). Renamed `generateBraintreeClientToken()` to
`generatePaymentClientToken()`. Added `BraintreePaymentGatewayTest` and `NoOpPaymentGatewayTest`.
Verified: full suite 321 tests, 0 failures, 0 errors, 3 skipped, including
`MySqlMasterModeJukeanatorEngineApplicationTests`, which confirms the real Braintree beans
construct under `app.mode=master`.

### 8. Commit message

**User:** `Give a one to two line summary of the changes made, suitable for a git commit message, use past tense.`

**Assistant:** "Implement Add Funds via a real, mode-gated Braintree payment integration,
abstracted behind a `PaymentGateway` interface so the SDK is only ever instantiated in master
mode."

### 9. This document

**User:** `Save this entire conversation, then the plan for these changes to a markdown file and save it to the docs directory as WebPaymentsWithBraintree.md`

---

## Part 2: Plan

### Context

`UserServiceImpl.addFunds()` was a stub that always threw. The web UI's Add Funds tab already
rendered the credit packages, but its Add Funds button hit the stub and showed "Not yet
available". The competitor screenshots show the missing pieces: a Select Payment Method sheet
(PayPal, Venmo, Credit/Debit Card, Google Pay), a real charge, a Transaction Successful
confirmation, and a receipt email.

The backend already anticipated this: `CreditTransactionType.PURCHASE` existed but was never
constructed, and `deductCredits()` provided the pattern for the credit side of a purchase.
`spring-boot-starter-mail` was already a dependency (unused), so the receipt email was a bounded
addition.

### Decisions locked in

- **Payment gateway: Braintree**, unified across Apple Pay, Google Pay, PayPal, Venmo, and cards
  (`braintree-java` backend, `braintree-web` client SDK frontend). Merchant onboarding is an
  operational task outside the code change. Defaults to the Braintree **sandbox** environment, with
  credentials sourced from environment variables.
- **Not using Braintree Drop-in.** Verified against Braintree's documentation: Drop-in entered
  deprecation on 2026-09-01 and becomes unsupported on 2027-09-01. The modular `braintree-web`
  SDK is used instead, inside a custom Select Payment Method sheet.
- **One-off nonce charge, not vaulted payment methods.** No Braintree Customer/vault, no
  persisted customer ID, no real "Default Payment Method" card (the static card was removed).
  Vaulting is a clean, separate follow-on.
- **Purchase-confirmation email** included, using the existing mail starter.
- **Not in scope:** email verification at registration (nothing here needs undoing if it is
  built later).

### Phase 1: Backend, Braintree core

1. `pom.xml`: add `com.braintreepayments.gateway:braintree-java` (3.40.0).
2. Config in `application.yml`, `application-test.yml`, and the external
   `C:\kiosk\config\application.yml`: a `braintree:` block (`environment`, `merchant-id`,
   `public-key`, `private-key`, environment-variable backed), a `spring.mail.*` block, and
   `receipt.from-address`. A new `BraintreeProperties` class mirrors `LocationProperties`;
   `@ConfigurationPropertiesScan` registers it automatically.
3. `BraintreeGateway` bean in `AppConfig` (sandbox vs production environment).
4. `AddFundsRequest` gains `paymentMethodNonce`.
5. New `AddFundsResponseDto` (`numCredits`, `balanceUsd`, `creditsAdded`, `bonusCreditsAdded`,
   `paymentSource`, `transactionId`, `timestamp`).
6. New `PaymentException`, mirroring `UserServiceException`.
7. `GlobalExceptionHandler`: map `PaymentException` to HTTP 402.
8. New `addCredits()` helper after `deductCredits()`, recording a `PURCHASE` ledger entry and
   publishing `UserCreditsChangedEvent`.
9. Rewrite `addFunds()` (synchronized): look up the package, validate the nonce, charge,
   **throw without touching `numCredits` or persisting when the charge fails**, otherwise mint
   `credits + bonusCredits`, persist, publish `PurchaseCompletedEvent`, and return the response.
10. `UserService` signature changes; `UserController.addFunds` returns 200 with a body; new
    authenticated `GET /api/users/payment/client-token` endpoint (not in `permitAll()`).
11. Constructor fan-out: `AppConfig.userService(...)` and `UserServiceTest` construction.
12. Remove the `getProfile()` auto-top-up shim (the `DEFAULT_CREDITS = 6` block). The
    registration-time starter grant is unchanged.

### Phase 2: Backend, purchase-receipt email

1. `PurchaseCompletedEvent` record, published after the successful persist.
2. `EmailService` interface and `EmailServiceImpl` wrapping the auto-configured
   `JavaMailSender`, sending a plain-text receipt. The interface is required because
   `spring.aop.proxy-target-class: false` means only JDK interface proxies work for `@Async`.
3. `PurchaseReceiptEmailListener` (`@EventListener @Async`), wired as beans in `AppConfig`.
4. `@EnableAsync` (there was no existing `@Async` usage).
5. Failure isolation: `EmailServiceImpl` logs and swallows send failures so a bad SMTP
   configuration never fails or rolls back a completed purchase.

### Phase 3: Frontend, Braintree Web SDK and Select Payment Method sheet

1. `index.html`: pinned `braintree-web` 3.145.0 scripts (`client`, `hosted-fields`,
   `paypal-checkout`, `venmo`, `google-payment`, and originally `apple-pay`) plus Google's
   `pay.js`. PayPal's JS SDK is injected dynamically because it must carry a per-session
   `data-client-token`.
2. New `showPaymentMethodSheet(pkg, onSuccess)` / `dismissPaymentMethodSheet()` in `app.js`,
   modeled on the existing bottom-sheet pattern. It fetches a client token, creates one shared
   Braintree client, and renders one row per method.
3. Per-method initialization, each feature-detected and hidden when unsupported: Hosted Fields
   (card), PayPal Checkout (one-time `checkout` flow), Venmo, Google Pay, Apple Pay. A shared
   `submitPayment(nonce)` posts `{ packageId, paymentMethodNonce }` to `/api/users/add-funds`.
4. `renderAddFundsContent()`: remove the static Default Payment Method card; the Add Funds
   button now opens the sheet.
5. New CSS for payment rows, Hosted Fields containers, and the success screen; removal of the
   now-dead payment-method-card CSS.

### Phase 4: Frontend, Transaction Successful screen

1. `renderTransactionSuccess(response)`: top-level screen (no back button) showing credits
   added plus bonus, payment source, new balance, and transaction ID, with a Return to Music
   Selection button that calls `renderMain('music')`.
2. `state.numCredits` is updated immediately from the response; the existing STOMP push keeps
   other tabs/devices in sync.

### Verification

Automated (JUnit/Mockito; `@ActiveProfiles("test")` is required on any `@SpringBootTest`):

- `UserServiceTest`: unknown package, missing nonce, declined charge (no credit/persist/event
  side effects), successful charge (credit math, persistence, both events, response shape),
  client token.
- `UserControllerTest`: 200 with body, 402 on `PaymentException`, client-token endpoint.
- `BraintreePaymentGatewayTest` and `NoOpPaymentGatewayTest` (added with the abstraction below).

Manual (user-run; the dev server cannot be launched from the agent session): start the app, log
in, open Add Funds, select a package, confirm the sheet renders card, PayPal, and Venmo (Google
Pay only in a supporting Chrome profile), pay with the sandbox test card `4111 1111 1111 1111`,
confirm the Transaction Successful screen and header balance update, confirm the receipt email,
then repeat with a sandbox decline to confirm no credits are granted and no email is sent.

### Critical files

- `src/main/java/com/djt/jukeanator_engine/domain/user/service/UserServiceImpl.java`
- `src/main/java/com/djt/jukeanator_engine/domain/user/service/UserService.java`
- `src/main/java/com/djt/jukeanator_engine/domain/user/controller/UserController.java`
- `src/main/java/com/djt/jukeanator_engine/config/AppConfig.java`
- `src/main/java/com/djt/jukeanator_engine/domain/common/controller/GlobalExceptionHandler.java`
- `src/main/java/com/djt/jukeanator_engine/domain/user/dto/AddFundsRequest.java`
- `src/main/resources/static/js/app.js`, `css/style.css`, `index.html`
- `src/main/resources/application.yml` (plus the external `C:\kiosk\config\application.yml` and
  `application-test.yml`)
- `pom.xml`
- `src/test/java/.../user/service/UserServiceTest.java`

### Addenda: changes made after the plan was approved

1. **Apple Pay stubbed out.** No Apple Developer account yet, so `APPLE_PAY_ENABLED = false` in
   `app.js` and the Apple Pay SDK script is commented out in `index.html`. To re-enable: flip
   the flag, restore the script tag, and add `/.well-known/**` to `SecurityConfig.permitAll()`.
2. **Payment gateway abstraction (master-mode only).** Added `PaymentGateway`,
   `PaymentChargeResult`, `BraintreePaymentGateway`, and `NoOpPaymentGateway` in
   `domain.user.service`. In `AppConfig`, `braintreeGateway` and `braintreePaymentGateway` are
   `@ConditionalOnProperty(name = "app.mode", havingValue = "master")` and `noOpPaymentGateway`
   is `@Conditional(NotMasterModeCondition.class)`, so `com.braintreegateway.BraintreeGateway`
   is never instantiated on standalone/slave instances. `UserServiceImpl` no longer imports any
   Braintree type. `generateBraintreeClientToken()` was renamed `generatePaymentClientToken()`.
   Full suite after this change: 321 tests, 0 failures, 0 errors, 3 skipped.
3. **`voidCharge` (observed on disk after the abstraction).** `PaymentGateway` now also declares
   `boolean voidCharge(String transactionId)`; `BraintreePaymentGateway` implements it with
   `braintreeGateway.transaction().voidTransaction(...)` and logs for a manual refund if the void
   fails; `NoOpPaymentGateway` returns `false`; `NoOpPaymentGatewayTest` gained
   `voidCharge_hasNothingToCancel`. This was added outside the conversation recorded above.

### Follow-ups and open items

- **Refund/dispute webhook handler** (task chip created, not started): signature-verified
  webhook receiver, `DisputeOpened/Won/Lost/Expired` handling, `REFUND`/`CHARGEBACK`
  transaction types, admin refund/void endpoint reversing unspent credits, tests.
- **Production go-live:** obtain real Braintree credentials, link PayPal Business, apply for
  Venmo, register Google Pay (then set the `PaymentsClient` environment to `'PRODUCTION'` and add
  `merchantId` in `initGooglePay()`), and set `braintree.environment` to `production`.
- **Apple Pay:** requires the Apple Developer Program ($99/year), a Merchant ID, the hosted
  domain-association file, and the `SecurityConfig` rule above.
- **Product-scope note:** with payments restricted to master mode, a standalone or slave
  instance cannot process Add Funds at all.
- **Known honesty limits:** Venmo, Google Pay, and Apple Pay client code is written to
  documented Braintree API shapes but has not been exercised against a live sandbox or real
  devices. Quoted fee rates are the publicly listed standard rates at the time of research and
  should be confirmed at application time.
