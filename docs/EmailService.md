# Email Service

This document has three parts:

1. **Conversation** — the design and implementation conversation for the email domain service, in order. User requests are quoted verbatim; responses are summarized, with tool output omitted.
2. **Plan** — the implementation plan, updated to match what was actually built.
3. **Local development with Gmail** — how to send real email (verification links, receipts, split summaries) from a local dev environment through a Gmail account.

---

## Part 1 — Conversation

### 1. Request: Email Service

> Add a top-level domain service, EmailService (i.e. a sibling to songlibrary, songqueue, user, location, etc) that will be used for the following:
>
> 1: A verification email for "Create Account" for mobile/web UI.
>
> 2: A receipt for "Add Funds" for mobile/web UI
>
> 3: A summary email for the Financial Ledger "Split" use case for the JFC/Swing UI.
>
> Properties for this email service should include:
> a. From email address (assumed to be the owner/operator, i.e. me), with the default being tmyers1@yahoo.com
> b. financial-ledger email list: comma separated list of email adresses, with the default being tmyers1@yahoo.com
>
> For the verification process for Create Account, this may necessitate an interim state, such as PENDING, which would move to verified once a user clicks on the verification link from the email that is sent to them upon account creaation.

**Exploration findings (plan mode):**

- Email support already existed, but inside the `user` domain: `EmailService`, `EmailServiceImpl` and `PurchaseReceiptEmailListener` in `domain/user/service`, wired in `AppConfig`. The from-address came from `@Value("${receipt.from-address}")`. Only the Add Funds receipt was implemented.
- `spring-boot-starter-mail`, the `spring.mail.*` settings and `@EnableAsync` were already in place, so no new dependencies were needed.
- Each domain is plain Java wired by `@Bean` methods in a configuration class (`LocationConfig` and `FinancialLedgerConfig` are the templates). Properties use `@ConfigurationProperties` with a top-level kebab-case prefix, picked up by `@ConfigurationPropertiesScan`.
- `ExternalConfigMerger` copies missing bundled properties into the external `application.yml` at startup, so new keys only need adding to `src/main/resources/application.yml`.
- `UserEntity` had no status field. Register returned a JWT immediately. Login is the natural place to block a PENDING account. A 401 makes the web UI sign out, so a different status is needed for "not verified".
- Users are stored either as JSON (filesystem repository, via `UserDto`/`UserMapper`) or in MySQL (JPA, Flyway, `ddl-auto: validate`).
- The Financial Ledger's "Add Split" already runs `FinancialLedgerService.addSplit()` off the Swing event thread, and `addSplit()` publishes `JukeboxSplitFinalizedEvent(locationId, periodId)`.
- There was no public base-URL property for building a verification link.

**Questions asked and answers:**

| Question | Answer |
|---|---|
| What can a PENDING account do before verifying? | Block login (Recommended): register returns no token, the web UI shows a "check your email" screen, a PENDING login is refused with a resend option, and links expire after 24 hours. |
| How should the existing user-domain email code move? | Move and rename (Recommended): move into `domain/email`, add `email.from-address` and `email.financial-ledger-recipients`, and keep reading the old `receipt.from-address` as a fallback. |
| When is the split summary sent? | Automatically on split (Recommended), in the background after "Yes, finalize" succeeds. |

The plan was written and approved (see Part 2).

**Implementation summary:**

- New `domain/email` package: `EmailService`/`EmailServiceImpl` (moved from `user`, now sending the verification email, the receipt and the split summary), `EmailConfig`, `EmailProperties`, and three `@Async @EventListener` listeners.
- `email:` properties block with `from-address` and `financial-ledger-recipients` (both defaulting to `tmyers1@yahoo.com`), `verification-required`, `verification-base-url` and `verification-token-ttl` (24h).
- Create Account: new accounts start PENDING; `GET /api/users/verify` activates them and redirects to the web UI with `?verify=success|expired|invalid`; a PENDING login returns 403 with a "Resend Verification Email" option; admin and internal kiosk accounts are never PENDING; existing users read as ACTIVE.
- Split: the summary is emailed in the background to the recipient list, and the Swing status line reads "Split finalized. Emailing the summary to N recipients."

**Deviations from the plan:**

- `register` still returns `AuthResponse`, with a `null` token while the account is PENDING, so the Swing admin path and existing tests were unchanged.
- Added `email.verification-required` as an off switch; `application-test.yml` turns it off so the existing end-to-end tests run as before.

**Issues found during implementation:**

- **Add Funds receipts had never actually been sent.** `ServiceSecurityAspect` rejects any `domain..service.*Service` call without an authenticated principal, and `@Async` threads carry no security context, so every send was silently rejected. All three listeners now run as the system principal (`SecurityContextPropagatingRunnable` with `SystemPrincipal.SystemAuthenticationToken.INSTANCE`), the same pattern `FinancialLedgerSyncService` uses.
- Because of the `receipt.from-address` fallback, the kiosk's external config (which still sets that key) would send from that address rather than `tmyers1@yahoo.com` until the old key is removed.
- Sending as a Yahoo address through Gmail's SMTP server may be rejected by Yahoo's anti-spoofing (DMARC) policy; Yahoo's own SMTP server avoids this.
- `docs/application-master-mode.yml` had a mis-indented `user-interface:` line, previously nested under `receipt:`; it was moved back to the top level.

### 2. Request: commit message

> Create a one line summary of the changes made, suitable for a git commit message, use the past tense

**Response:**

> Added a top-level email domain service that sends Create Account verification emails (new accounts stay PENDING until the emailed link is opened), Add Funds receipts and Financial Ledger split summaries, with new email.* properties for the from address and the financial-ledger recipient list, and made the email listeners run as the system principal so the service security check no longer blocks sends

### 3. Request: merge the Flyway migration into V1

> Per earlier requests, merge the V2 flymigration migration into V1.  I will drop and re-create all databases, as I want to deploy to production with a single flyway migration script (V1)

**Response:** The three verification columns were added to the `user_account` table in `V1__init_schema.sql`, and `V2__user_account_verification.sql` was deleted:

- `status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE'`
- `verification_token_hash VARCHAR(64) NULL UNIQUE` (replacing V2's separate unique index)
- `verification_token_expires_at TIMESTAMP NULL`

A database that already ran the old V1 fails Flyway's checksum validation until it is dropped and re-created.

### 4. Report: verification email not sent

> I tried to create a user account via web UI and all I saw in the console output was this:
>
> `2026-10-09 19:44:55.816 [SimpleAsyncTaskExecutor-4] WARN  c.d.j.d.e.service.EmailServiceImpl - Failed to send account verification email to tmyers1@yahoo.com: Authentication failed`

**Diagnosis:** The code worked; the SMTP server refused the login. `C:\kiosk\config\application.yml` takes `spring.mail.username` and `spring.mail.password` from `SMTP_USERNAME` and `SMTP_PASSWORD`, and neither variable was set, so the app signed in to `smtp.gmail.com` with blank credentials. The legacy `receipt.from-address` in that file also meant emails were going out from `noreply@jukeanator.com`.

**Recommended fix:** send through Yahoo, since the From address is a Yahoo account.

1. Generate a Yahoo app password (a normal Yahoo password does not work over SMTP).
2. Set user environment variables: `SMTP_HOST=smtp.mail.yahoo.com`, `SMTP_PORT=587`, `SMTP_USERNAME=tmyers1@yahoo.com`, `SMTP_PASSWORD=<app password>`, `MAIL_FROM_ADDRESS=tmyers1@yahoo.com`.
3. Delete the `receipt:` block from `C:\kiosk\config\application.yml`.
4. Restart the app from a new terminal or IDE session.

The account already created stays PENDING; once mail works, signing in with it offers "Resend Verification Email". To test the web UI without mail, set `email.verification-required: false` in the external config.

### 5. Question: Yahoo app password

> For Yahoo Account Security, how do I generate an app password?

**Response** (from general knowledge; Yahoo's labels may differ slightly):

1. Sign in at https://login.yahoo.com/account/security (or Yahoo Mail → profile icon → **Account info** → **Account security**).
2. Find **Generate app password** / **Generate and manage app passwords**. If it is missing, turn on **2-step verification** first.
3. Click **Get started** or **Generate app password**, enter a name such as `JukeANator`, and click **Generate password**.
4. Copy the 16-character password immediately; it is shown only once.
5. Use it as `SMTP_PASSWORD` (without spaces), with `SMTP_USERNAME=tmyers1@yahoo.com`.

Windows user variables can be set with `setx` (for example `setx SMTP_HOST smtp.mail.yahoo.com`); `setx` only affects programs started afterward. Access can be revoked on the same Yahoo page.

### 6. Request: save this document

> Save this entire conversation, then the plan for these changes to a markdown file and save it to the docs directory as EmailService.md

This file.

---

## Part 2 — Plan (as implemented)

### Context

Email sending lived inside the `user` domain and only sent the Add Funds receipt. The goal was a top-level `email` domain, a sibling of `location`, `user`, `financialledger` and the others, that owns all outbound mail and serves three use cases:

1. **Create Account verification** (web/mobile): accounts start `PENDING` and become `ACTIVE` when the emailed link is opened; PENDING accounts cannot log in.
2. **Add Funds receipt** (web/mobile): already implemented; moved into the new domain with unchanged content.
3. **Financial Ledger split summary** (Swing): sent automatically in the background after a split is finalized.

No new dependencies: `spring-boot-starter-mail`, `spring.mail.*` and `@EnableAsync` were already present.

### 1. Email domain (`domain/email`)

- `config/EmailProperties.java`: `@ConfigurationProperties(prefix = "email")`
  - `fromAddress` (default `tmyers1@yahoo.com`)
  - `financialLedgerRecipients` (`List<String>`, bound from a comma-separated value, default `tmyers1@yahoo.com`; blank entries dropped)
  - `verificationRequired` (default `true`)
  - `verificationBaseUrl` (blank means "use the URL the request arrived on")
  - `verificationTokenTtl` (`Duration`, default 24h)
- `config/EmailConfig.java`: `@Bean`s for `EmailService` and the three listeners; the email beans were removed from `AppConfig`.
- `service/EmailService.java`: an interface, so `@Async` JDK proxies work.
  - `sendPurchaseReceiptEmail(...)`
  - `sendAccountVerificationEmail(toAddress, firstName, verificationUrl, expiresAt)`
  - `sendFinancialLedgerSplitSummaryEmail(JukeboxSplitPeriodDto)`: sent to all recipients; does nothing (with a log line) when the list is empty.
- `service/EmailServiceImpl.java`: plain-text `SimpleMailMessage`. A send failure is caught and logged as a warning, so it never fails the operation that triggered the email. The split body lists period start/end, cash, card, mobile, total, owner split %, amount due owner and amount due operator.
- `listener/`: `@Async @EventListener` classes, each running its body as the system principal so `ServiceSecurityAspect` allows the service calls:
  - `PurchaseReceiptEmailListener` on `PurchaseCompletedEvent`
  - `AccountVerificationEmailListener` on `VerificationEmailRequestedEvent`
  - `FinancialLedgerSplitEmailListener` on `JukeboxSplitFinalizedEvent`; loads the finalized period via `FinancialLedgerService.getAllPeriods()`.
- Deleted `domain/user/service/EmailService.java`, `EmailServiceImpl.java` and `PurchaseReceiptEmailListener.java`.

### 2. Properties

Bundled `src/main/resources/application.yml` (replacing the `receipt:` block; `ExternalConfigMerger` copies it into the external config):

```yaml
email:
  from-address: ${MAIL_FROM_ADDRESS:${receipt.from-address:tmyers1@yahoo.com}}
  financial-ledger-recipients: tmyers1@yahoo.com
  verification-required: true
  verification-base-url: ~
  verification-token-ttl: 24h
```

The nested `${receipt.from-address:...}` keeps a legacy external `receipt.from-address` effective. The block is mirrored in `src/test/resources/application-test.yml` (with `verification-required: false`) and the three `docs/application-*-mode.yml` examples.

### 3. Account verification (user domain)

- `model/UserStatus.java`: `PENDING`, `ACTIVE`.
- `UserEntity`: `status`, `verificationTokenHash` (SHA-256 hex; the raw token exists only in the email) and `verificationTokenExpiresAt`, plus `isPending()`, `issueVerificationToken(hash, expiresAt)` and `markVerified()`. A null status reads as ACTIVE. `anonymize()` clears the token.
- Persistence:
  - Filesystem: `UserDto` and `UserMapper` carry the three fields; a missing status maps to ACTIVE.
  - MySQL: columns added to `user_account` in `V1__init_schema.sql` (single-migration policy; databases are dropped and re-created).
- `service/EmailVerificationPolicy.java`: record `(required, baseUrl, tokenTtl)` with a `DISABLED` constant used by the older `UserServiceImpl` constructors.
- `event/VerificationEmailRequestedEvent.java`: `(emailAddress, firstName, verificationUrl, expiresAt)`, published on registration and on resend.
- `exception/AccountNotVerifiedException.java`: subclass of `InvalidCredentialsException`, mapped to **403** in `GlobalExceptionHandler`.
- `service/VerificationResult.java`: `VERIFIED`, `EXPIRED`, `INVALID`.
- `UserService` / `UserServiceImpl`:
  - `register(request, requestBaseUrl)`: when verification is required, creates the account PENDING with a 32-byte random URL-safe token, saves it, publishes the event and returns `AuthResponse` with a `null` token. Otherwise it logs the user in as before. `register(request)` delegates with a null base URL.
  - Registering an email that already has a PENDING account returns 409 with wording that points to resending the link.
  - `addAdminUser` and the kiosk's internal local user are always ACTIVE.
  - `login`: after the password check, a PENDING account throws `AccountNotVerifiedException`, so a wrong password never reveals the account's state.
  - `verifyAccount(token)`: single-use; returns `VERIFIED`, `EXPIRED` or `INVALID`.
  - `resendVerification(email, requestBaseUrl)`: issues a new token (invalidating the old one) for a PENDING account; silently does nothing otherwise.
- `UserController`:
  - `POST /api/users/register`: passes the request's base URL.
  - `GET /api/users/verify?token=...`: 302 redirect to `/?verify=success|expired|invalid`.
  - `POST /api/users/resend-verification` `{emailAddress}`: always 200.
- `SecurityConfig`: `/api/users/resend-verification` and `GET /api/users/verify` are `permitAll()`.
- `AppConfig`: builds `EmailVerificationPolicy` from `EmailProperties` for the `userService` bean.

### 4. Web UI (`static/js/app.js`, `static/css/style.css`)

- Register: a `null` token shows a "Check Your Email" screen naming the address, with "Resend Email" and "Back to Sign In".
- Login: a 403 shows the server's message and a "Resend Verification Email" button, with the email field pre-filled. `renderLogin` now escapes its messages and accepts a green `notice`.
- Startup: `handleVerificationRedirect()` reads `?verify=`, removes it from the address bar with `history.replaceState`, and shows the outcome on the login screen.
- New `.notice-msg`, `.verify-email-text` and `.verify-email-actions` styles.

### 5. Financial Ledger split (Swing)

- `email.financial-ledger-recipients` is passed through `JukeANatorUserInterfaceApplication` → `JukeANatorFrame` → `AdminPanel`.
- After `addSplit()` succeeds, `FinancialLedgerDialog.reload(doneMessage)` shows "Split finalized. Emailing the summary to N recipient(s)." ("Split finalized." when the list is empty).
- Comments on `JukeboxSplitFinalizedEvent` and `FinancialLedgerServiceImpl.addSplit()` updated to mention the email listener.

### 6. Tests

- `EmailServiceImplTest`: receipt, verification and split-summary content and recipients; no send with an empty list; send failures swallowed.
- `UserServiceTest`: PENDING registration with no JWT, login refused until verified, single-use tokens, expired and unknown tokens, resend replaces the token and sends nothing for ACTIVE or unknown accounts, PENDING re-registration rejected, admin accounts ACTIVE, verification disabled logs straight in.
- `UserMapperVerificationTest`: a legacy user file with no status reads as ACTIVE; PENDING status and token round-trip.
- `UserControllerTest`: register mock updated for the two-argument method; verify redirects; resend returns 200.

### 7. Operational notes

- SMTP credentials come from `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` and `MAIL_FROM_ADDRESS`. With a Yahoo From address, use `smtp.mail.yahoo.com` and a Yahoo app password.
- Remove the legacy `receipt:` block from `C:\kiosk\config\application.yml` so `email.from-address` takes effect.
- On the master, set `email.verification-base-url` to the public web address so links are correct behind a proxy.
- Split emails are sent from the kiosk that performs the split, so that instance needs working mail settings.
- Abandoned PENDING accounts are not purged.

### 8. Verification

- Run the unit tests above.
- Web UI: register → "Check Your Email" screen; sign-in refused with a resend option; open the emailed link → login screen shows "verified"; sign in.
- Add Funds → receipt arrives.
- Swing: Add Split → status line, and the summary arrives at each recipient.
- `app.repository-type=jpa` against a freshly created database, to confirm V1 validates.

---

## Part 3 — Local development with Gmail

These steps let a local dev environment (for example `http://localhost:8080`) send real email through a Gmail account, so you can test Create Account verification, Add Funds receipts and split summaries end to end. They are for local testing only; production should use the operator's own mail account (see Part 1, item 4).

### Why email doesn't arrive by default

- `spring.mail` reads its server and login from environment variables: `SMTP_HOST` (default `smtp.gmail.com`), `SMTP_PORT` (default `587`), `SMTP_USERNAME` and `SMTP_PASSWORD` (both blank by default).
- With no username or password set, Gmail refuses the login. The app logs a warning such as `Failed to send account verification email to ...: Authentication failed` and carries on, so the new account stays PENDING with no email sent.
- Gmail never accepts your normal Google password from an app. It needs an **app password**.

### Step 1: Create a Gmail app password

1. Sign in to the Gmail account the app will send from.
2. Go to https://myaccount.google.com/security and turn on **2-Step Verification** if it isn't on already. App passwords are only offered once it is on.
3. Go to https://myaccount.google.com/apppasswords (or search for "App passwords" in your Google Account).
4. Enter a name such as `JukeANator local dev` and click **Create**.
5. Copy the 16-character password straight away. Google shows it only once. Spaces in it don't matter; you can leave them out.

If the App passwords page says the setting isn't available, the account is probably a work or school (Google Workspace) account with app passwords turned off, or it uses Advanced Protection. Use a personal Gmail account instead.

You can delete the app password from the same page at any time, which immediately stops the app from sending.

### Step 2: Give the app the mail settings

Set these five values. Use the same Gmail address for both `SMTP_USERNAME` and `MAIL_FROM_ADDRESS`: Gmail sends every message from the account that signed in, and replaces any other From address.

- `SMTP_HOST` = `smtp.gmail.com`
- `SMTP_PORT` = `587`
- `SMTP_USERNAME` = your Gmail address, for example `you@gmail.com`
- `SMTP_PASSWORD` = the app password from step 1
- `MAIL_FROM_ADDRESS` = the same Gmail address

**Option A: in the Eclipse launch configuration (recommended for dev).** The values apply only when you run the app from Eclipse, and nothing else on the PC sees them.

1. **Run → Run Configurations...**, then select the JukeANator launch configuration.
2. Open the **Environment** tab and click **Add...** for each of the five variables.
3. Click **Apply**, then **Run**.

Keep the launch configuration as a local one (the **Common** tab's **Local file** option, which is the default). A **Shared file** is saved as a `.launch` file in the project, where the password could be committed to git.

**Option B: as Windows user environment variables.** These apply to every program you start afterward, including a jar run from a terminal. Run these in a terminal, then close and reopen Eclipse (or the terminal) so it picks them up:

```bash
setx SMTP_HOST smtp.gmail.com
setx SMTP_PORT 587
setx SMTP_USERNAME you@gmail.com
setx SMTP_PASSWORD yourapppassword
setx MAIL_FROM_ADDRESS you@gmail.com
```

To remove them later, open **Edit environment variables for your account** from the Start menu and delete the five entries.

### Step 3: Check the config file

In the `application.yml` the app actually reads (on the jukebox PC, `C:\kiosk\config\application.yml`):

- `email.verification-required` should be `true`, so registering sends a verification link.
- `email.verification-base-url` can stay blank (`~`). The link then uses the address the request came in on, such as `http://localhost:8080`, which is right for local testing. Only open the link on the same PC, since `localhost` means "this computer".
- If the file still has the legacy block below, either delete it or make sure `MAIL_FROM_ADDRESS` is set. Without `MAIL_FROM_ADDRESS`, that block makes the From address `noreply@jukeanator.com`, which Gmail replaces with your Gmail address anyway.

```yaml
receipt:
  from-address: ${MAIL_FROM_ADDRESS:noreply@jukeanator.com}
```

### Step 4: Test it

1. Start the app with the settings from step 2.
2. In the web UI, **Create Account** with an address you can read. The "Check Your Email" screen appears.
3. The verification email arrives within a minute or so. Check the spam folder if it doesn't show up in the inbox.
4. Open the link on the same PC. The login screen says the account is verified, and you can sign in.
5. For an account that is already PENDING, sign in with it and click **Resend Verification Email**. The new link replaces the old one; the old one stops working.
6. Optional: buy a package in **Add Funds** (Braintree sandbox) and check the receipt email, and run **Add Split** on the kiosk and check the summary email.

### Troubleshooting

Look in the console or `logs/jukeanator-service.log` for a `Failed to send ... email` warning:

- **`Authentication failed`** or **`535-5.7.8 Username and Password not accepted`**: the app password or username is wrong, or the variables weren't picked up. Check that Eclipse (or the terminal) was restarted after `setx`, or that the launch configuration's Environment tab has all five values.
- **Connection timed out**: something on the network is blocking port 587. Try another network, or check the firewall or antivirus.
- **No warning, but no email**: check the spam folder, and confirm the Gmail account's **Sent** folder shows the message.
- **Link opens "expired"**: links last 24 hours (`email.verification-token-ttl`). Sign in and use **Resend Verification Email**.

A personal Gmail account can send about 500 messages a day, which is plenty for testing.
