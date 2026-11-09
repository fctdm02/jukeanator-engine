# Legacy licensing: design, analysis and decisions

This document records how the legacy MP3Jukeboxx copy protection and subscription scheme works,
how jukeanator-engine preserves it, and the decisions made along the way. Deployment and operations
are covered separately in [../license-server-deployment.md](../license-server-deployment.md).

## Goal

Existing customers must keep using their existing license key (`JukeANator.CTL`) and subscription
file (`ExtendCode.CTL`), no matter how dated or insecure the scheme is. Two pieces of work:

1. **Client (standalone and slave modes):** startup license check, automatic provisioning,
   manual provisioning (registration screen), subscription validation, and falling back to
   non-commercial operation when the subscription lapses.
2. **Server (master mode):** replace the legacy JSP website `jukeanator.com` with jukeanator-engine
   deployed as a ROOT WAR on Tomcat at www.jukeanator.com, transparent to MP3Jukeboxx installs in
   the field, and also serving new engine instances.

Legacy sources analyzed:

- Client: `C:\dev-wks\projects\jukeanator\src\com\djt\jukeanator\view\MP3Jukeboxx.java`
  (`checkRuntime()`, `performSubscriptionValidation()`, `retrieveProvisionedLicenseKey()`,
  `readExtendCodeFile()` / `writeExtendCodeFile()`) and `RegistrationDialog.java`.
- Server: `C:\dev-wks\projects\jukeanator.com` (`FrontController`, `com.jukeanator.servercommand.*`,
  `com.djt.jukeanator.utils.DatabaseUtil` / `CryptoUtils` / `EncryptDecrypt` / `Md5File`).

## Decisions

| Topic | Decision |
|---|---|
| Site layout (master) | Marketing pages at `/`; the Web/Mobile jukebox UI moves to `/app/`. Standalone/slave redirect `/` to `/app/`. |
| Customer administration | Admin-only REST API (`/api/admin/licenses/**`) plus a static page `/admin/licenses.html`. |
| Legacy client bugs | The new client fixes the behavior; file formats and wire protocol stay byte-identical. |
| What non-commercial disables | Only the payment inputs: bill acceptor and card reader. No trial queue caps or nags beyond the registration screen. |
| Terminology | Legacy "ASCAP mode" is called **Subscription mode** everywhere. The business no longer deals with ASCAP/BMI/SESAC performance licensing. Only the frozen wire command `command=ascap` keeps the old name. |
| Song usage reporting | Not ported (`command=songusagereport` falls through to a redirect to `/`). Legacy clients never upload reports anyway. |
| Wire protocol for new installs | jukeanator-engine uses the same `/FrontController` protocol as MP3Jukeboxx (over HTTPS), plus optional `appVersion` / `appMode` parameters, so the server has one code path. |
| Schema | Two tables, `license_customer` and `license_machine`, in `V1__init_schema.sql` (single-migration rule). The per-IP/hostname table is dropped; heartbeat columns replace it. |

## How the legacy scheme works

### Corrections to the initial write-up

- **The server creates only `JukeANator.CTL`.** `ExtendCode.CTL` is never produced by the server.
  The client writes it itself, after `command=provision` succeeds for a subscription tier and
  after every successful `command=ascap` check.
- **The license key holds no secret, expiry, name or email.** It is an MD5 hash of the tier
  delimiter and the machine id. Expiry lives on the server (the machine's paid-through quarter)
  and locally in `ExtendCode.CTL`.
- The legacy client is in the `jukeanator` project, not `jukeanator-engine`.

### License tiers

| Tier | Wire code | DB letter | Delimiter | Meaning |
|---|---|---|---|---|
| CR | -1 | R | `&@` | Subscription, remote ("downloading") data source |
| CD | 0 | A | `__` | Subscription, standalone |
| C | 1 | C | `##` | Commercial (perpetual) |
| NC | 5 | P | `@#` | Professional (non-commercial) |
| L | 6 | L | `&&` | Lite |
| D | 10 | — | — | Trial (no key matched) |

Lower codes mean more functionality: `<= C` is commercial (payments allowed), `<= CD` requires a
valid subscription. Code: `domain/license/legacy/LicenseTier`.

### `JukeANator.CTL` (license key)

- Content: `MD5(delimiter + machineId + delimiter)` over ASCII bytes, 16 bytes.
- File: Java serialization of a `byte[16]`, 43 bytes in total. Header:
  `AC ED 00 05 75 72 00 02 5B 42 AC F3 17 F8 06 08 54 E0 02 00 00 78 70 00 00 00 10`.
- The legacy client searches `dataDir\JukeANator.CTL`, then `JukeANator1.CTL` … `JukeANator10.CTL`,
  and tests each against the machine ids in tier order R, A, C, P, L. The first match wins.
- Code: `LegacyCrypto.licenseKeyBytes`, `LegacyCtlFiles.readLicenseKey` / `writeLicenseKey`.

### Machine id ("serial number")

The legacy client computes two ids and tests the network-card id first:

1. **Network-card id.**
   - On a 32-bit JVM: the `ServiceName` (adapter GUID, e.g. `{1D38215A-…}`) of the first
     `HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\NetworkCards\0..20` subkey, read through
     `win32native.dll`.
   - On a 64-bit JVM: the first `Physical Address` in `ipconfig /all`, cut at fixed offsets
     (`substring(index+36, index+53)`, e.g. `00-1A-70-F9-9D-DE`).
2. **Motherboard id.** The "Computer ID" printed by `diskid32.exe` (data dir, then `C:\kiosk`),
   only if the exe's CRC32 is `4224116446` or `396496930`. Output lines are joined with CRLF and
   parsed at fixed offsets. If the id is `0` or `600000000`, its first MAC address is used instead.

When provisioning, the motherboard id is sent if available, otherwise the network-card id.

The new client (64-bit JVM, no JNI DLL) produces all three candidates and tests each, so a key
matches whichever source the legacy install used. The registry GUID is read with
`reg query … /s` from both the 32- and 64-bit views. Code: `LegacyMachineIdentityProvider`.

### Symmetric crypto (subscription file and `ascap` reply)

- Key: PBKDF2WithHmacSHA1, password `5JfWSeritY31`, salt `"300"`, 1024 iterations, 128 bits.
  Derived key: `6f018ccd57a6f1d5757a13674a75c1c2`.
- Cipher: `Cipher.getInstance("AES")`, i.e. AES/ECB/PKCS5Padding, no IV.
- Encoding: lowercase hex. The client's `EncryptDecrypt` passes `""` through unchanged; the
  server's `CryptoUtils` always encrypts.
- Passcodes: SHA-1 over ISO-8859-1 bytes, lowercase hex (`SHA1("jukeanator22") =
  d6b2818523532fe7169b182adb8bd9f1d2061588`).
- Code: `LegacyCrypto`.

### `ExtendCode.CTL` (subscription)

- File: Java serialization of a `String[4]` of hex ciphertexts:
  `{quarter "nQyyyy", hoursRemaining, machineId, customerEmail}`.
- Looked up in the data dir, the working dir, then `C:\kiosk`.
- A quarter expires at local midnight at the **start** of its last day (Mar 31, Jun 30, Sep 30,
  Dec 31).
- Hours remaining are decremented once per runtime hour, and the file's last-modified time is
  restored after each rewrite to hide the hourly write.
- Checks and messages:

  | Code | Condition | Effect |
  |---|---|---|
  | 444 | hours remaining <= 0 | expired |
  | 555 | hours remaining <= 336 (14 days) | warning |
  | 888 | within 7 days of expiry | warning |
  | 999 | at or after expiry | expired |
  | 002 | file's machine id or email does not match | legacy: fatal exit; new: commercial disabled |
  | 003 | server reply does not cover the current quarter | expired |
  | 005 | file missing or unreadable | expired |
  | 006 / 007 | offline fallback, warning / expired | as 555/888 and 444/999 |

- Code: `ExtendCode`, `LegacyCtlFiles.readExtendCode` / `writeExtendCode`,
  `SubscriptionQuarter`, `LicenseServiceImpl.evaluateExtendCode`.

### Wire protocol (`/FrontController`)

All calls are GETs to `http://www.jukeanator.com/FrontController?command=<name>&…`; the legacy
server also maps `/servlet/FrontController`. The server decodes every parameter once more after
the servlet container (the passcode three times), so parameter values are effectively
double-encoded. `command` is case-insensitive.

Text replies are `text/html;charset=ISO-8859-1` with body `URLEncoder.encode(text, UTF-8) + "\n"`.
Fixed reply constants (padding is part of the protocol):

| Constant | Text |
|---|---|
| OK | `OK` + 49 × `a` |
| ERROR | `ERROR` + 52 × `a` |
| REVOKED | `REVOKED` + 52 × `a` |
| invalid email | `invalidCustomerEmail` + 49 × `a` |
| invalid name | `invalidCustomerName` + 49 × `a` |

| Command | Parameters | Reply |
|---|---|---|
| `provision` | customerEmail, passcode (SHA-1 hex), serialNumber, buildDate | 16 raw key bytes (`application/octet-stream`), or a refusal text |
| `ascap` (subscription check) | licenseType, serialNumber, buildDate, [customerEmail, customerName, locationName] | `hex(AES(URLEncode(serial + lastPaidQuarter)))`, or REVOKED / ERROR |
| `heartbeat` | as `ascap` | OK, or invalid email |
| `getcustomername` | customerEmail, … | name, or invalid email / invalid name |
| `getJukeANatorBuildDate` | (ignored) | `MM-dd-yyyy` of the legacy `MP3Jukeboxx.jar` |
| `getJukeANatorBuildSize` | (ignored) | byte length of that jar |
| `feedback` | contact form fields | email to the site owner, then the thank-you page |
| `getengineversion` (new) | appVersion, appMode | `<version>|<downloadUrl>` for engine clients |

Provisioning refusal texts: `REVOKED: Unpaid subscription for customer email: <email>`,
`customerEmail and serialNumber are null`, `Incorrect credentials given for customer email:
<email>`, `No more available licenses for given customer email: <email>`.

The client accepts an `ascap` reply whose plaintext equals `URLEncode(machineId)` followed by any
quarter from the current one through four quarters ahead.

**Auto-update:** legacy clients re-download `http://www.jukeanator.com/temp/MP3Jukeboxx.jar`
whenever the server's build date differs from their own (`01-27-2024`), on any difference, not
just a newer one. The server must keep reporting the frozen legacy jar.

Code: `LegacyFrontController`, `LegacyResponses`, `LicenseServerClient`.

### Server business rules (legacy, preserved)

- **Provision:** revoked check → credentials (email + SHA-1 passcode) → register the machine if new
  (paid-through = current quarter of next year) → if the machine was already paid for this period,
  issue the key free → else require and consume one available license → key for the customer's
  tier.
- **Heartbeat:** an unknown email auto-creates a customer (passcode = SHA-1 of the email, zero
  licenses); an unknown serial is registered (subscription tiers get next year's quarter).
- **Subscription check:** runs the heartbeat side effects first, then answers.
- The key type comes from the **customer's** license type, not the request.

## Legacy bugs and how each is handled

| Bug | Where | Handling |
|---|---|---|
| Startup "hours since last run" deduction never ran (hours not yet loaded) | client | Fixed: uses a `License.STAMP` file touched hourly |
| A server "not paid" reply at startup was overridden by a still-valid local file (NPE in message building) | client | Fixed: the server's verdict wins and the local file is zeroed |
| New extend code written with +4 quarters but only 3 quarters of hours | client | Fixed: hours computed from the calendar to the quarter's expiry |
| Provisioned bytes saved without validation | client | Fixed: key verified against this computer before replacing the old one |
| Machine/email mismatch called `System.exit` | client | Fixed: commercial disabled, shown on screen |
| Auto-update size check protected nothing | client | Not applicable (legacy client only) |
| Available-license decrement was read-then-write | server | Fixed: atomic conditional update |
| Provisioning wrote the CTL path into LOCATION_NAME and the quarter into BUILD_DATE | server | Fixed; the import script repairs the 14 affected rows |
| Revocations hardcoded in source | server | Now `revoked` flags, seeded from the hardcoded lists by the import script |
| Missing email/serial caused HTTP 500 (NPE) | server | Fixed: legacy text replies |
| `index.jsp` read the `home` parameter instead of `command`, so every page rendered home | site | Fixed: page commands redirect to the matching static page |
| Heartbeat auto-creates customers | server | Preserved (flagged `auto_created`) for transparency |

## Setup-time licensing screens

These screens are shown while the PC and jukebox are being set up, so a physical keyboard is
assumed. All of them also accept the on-screen keyboard, and none closes by itself.

- **Registration screen.** Shown at startup when the instance is unlicensed, or when the
  subscription needs the registered email or has a warning.
  - Register: provisions with the email and passcode.
  - Import File: installs a `JukeANator.CTL`, then saves the typed email and location.
  - Try: runs unlicensed. On the details-only variant this button is Later.
  - While a licensing screen is up, Escape, the admin-login and Now Playing taps, and the
    screen saver are blocked. Physical keystrokes never reach the payment-key bindings.
- **Customer name and location name.** The only legacy command that returns customer details is
  `getcustomername`, which returns the customer name for an email. No command returns a location
  name or an email.
  - For a licensed instance with a known email, the client asks the server for the name at these
    points: startup, after provisioning or import, after the details are saved, and with each
    subscription check.
  - A name the server returns is written to `license.customer-name` in the external
    `application.yml`. The server's name replaces a configured one.
  - Whatever is still missing (customer name, then location name) is asked for in a separate
    prompt and saved to `license.customer-name` / `license.location-name`.
  - Skip leaves the value unset, and the prompt appears again at the next start.
  - Unlicensed (trial) instances are not prompted.

## Implementation map

| Area | Location |
|---|---|
| Compatibility core (pure Java) | `domain/license/legacy/` |
| Client service, machine id, scheduler, startup check | `domain/license/service/`, `domain/license/client/`, `domain/license/config/` |
| Server service, entities, repository, controllers | `domain/licenseserver/` |
| Registration screen | `ui/components/RegistrationCard.java` |
| Customer / location name prompts | `ui/components/LicenseDetailPromptCard.java`, `JukeANatorFrame.showMissingLicenseDetails` |
| Physical keyboard on the licensing screens | `ui/components/PhysicalKeyboardInput.java` |
| Payment gating | `ui/components/JukeANatorFrame.java` (`paymentsLicensed`) |
| Admin License screen (kiosk) | `ui/components/AdminPanel.java` (`doLicense`) |
| Static routing (`/` vs `/app/`) | `config/StaticSiteConfig.java` |
| Security rules | `config/SecurityConfig.java` |
| Config write-back | `config/ExternalConfigUpdater.persistSectionValues` |
| Marketing site, web UI, admin page | `src/main/resources/static/`, `static/app/`, `static/admin/` |
| Schema | `db/migration/mysql/V1__init_schema.sql` |
| Data import / existing-DB update | `docs/license/legacy-import.sql`, `docs/license/license-tables-update.sql` |

Configuration: `license.*` (client) and `license-server.*` (master) in `application.yml` and
`docs/application-*-mode.yml`.

## Verification

Unit tests:
- `LegacyCryptoTest`: values computed independently, plus a copy of the legacy `CryptoUtils`
  used as an oracle.
- `LegacyCtlFilesTest`: golden byte layouts.
- `SubscriptionQuarterTest`.
- `LegacyMachineIdentityProviderTest`.
- `LicenseServiceImplTest`.
- `LicenseServerServiceImplTest`.
- `LegacyFrontControllerTest`: exact reply bytes and content types.
- `ExternalConfigUpdaterSectionTest`.

Still to do:
1. Read a real customer's `JukeANator.CTL` + `ExtendCode.CTL` on its own machine and confirm the
   tier, machine id and quarter decode.
2. End to end: master locally on 8080 with imported legacy data; a legacy MP3Jukeboxx with
   `ServerDebugMode=true` (targets `localhost:8080`) for provision, subscription check, heartbeat
   and build-date check; a standalone engine with `license.server-url=http://localhost:8080` for
   registration, extend-code creation, payment gating and the admin page.
3. Browser check of the marketing pages, `/app/` and `/admin/licenses.html`.

## Open items

- **Unlicensed bill pulses are lost.** With a pulse acceptor, a bill inserted while the kiosk is
  non-commercial is accepted by the hardware but credits nothing (same as legacy). Consider
  disabling the acceptor in hardware or showing a notice.
- **Early renewals.** Clients reject a paid-through quarter more than four quarters ahead, so
  "+1 year" only works once the current paid-through quarter has been reached; earlier renewals
  need "Set quarter" plus a note of the remaining credit.
- **Order page pricing.** The Commercial Subscription still shows $549.95 per year; review.
- **Legacy DB password.** Hardcoded in jukeanator.com's `DatabaseUtil` and committed to git;
  rotate at cutover.
- **Hosting.** Confirm Tomcat 11, Java 21+, MySQL, SMTP and plain HTTP on the legacy paths with
  Mochahost.
- **Engine self-update.** `getengineversion` only reports the latest version; installing it is not
  implemented.
