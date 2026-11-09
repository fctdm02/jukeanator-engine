# License server and www.jukeanator.com deployment (master mode)

In production a jukeanator-engine **master** instance replaces the legacy JSP site
(`C:\dev-wks\projects\jukeanator.com`). The single WAR serves:

| Path | Purpose |
|---|---|
| `/` and `/*.html`, `/assets/**`, `/images/**` | Public marketing site (ported from the legacy JSP pages) |
| `/app/` | Web/Mobile jukebox UI (Braintree Add Funds) |
| `/FrontController`, `/servlet/FrontController` | Legacy license protocol used by MP3Jukeboxx installs **and** jukeanator-engine standalone/slave instances |
| `/temp/**`, `/webstart/**` | Legacy auto-update downloads, served from `license-server.downloads-dir` |
| `/admin/licenses.html` + `/api/admin/licenses/**` | License administration (ADMIN role) |
| `/api/**`, `/ws`, `/ws-slave` | Existing engine APIs (unchanged) |

The design, the legacy scheme analysis, the decisions and the open items are in
[license/legacy-licensing-design.md](license/legacy-licensing-design.md).

Legacy MP3Jukeboxx clients hardcode `http://www.jukeanator.com/FrontController?command=…` and
compare the replies byte for byte. The replacement keeps the URLs, parameter decoding, content
types and response bodies identical (see `LegacyFrontController`), so the switch is transparent to
them.

## Hosting requirements (to confirm with Mochahost)

- **Java 21 or later** (Java 25 also fine). The project is built for Java 21.
- **Apache Tomcat 11.x.** Spring Boot 4 targets Jakarta EE 11 / Servlet 6.1; Tomcat 9 (`javax.*`)
  and Tomcat 10.1 (Servlet 6.0) will not run it. Alternatively, the WAR runs stand-alone with
  `java -jar` behind the host's Apache httpd reverse proxy.
- **MySQL 8.x** (or a MySQL-compatible MariaDB 10.6+; MySQL is the tested vendor).
- **SMTP** credentials for outgoing mail (receipts, account verification, contact form).
- **TLS certificate** for `www.jukeanator.com` and `jukeanator.com` (needed for Braintree and the
  web UI).
- The WAR deployed as **`ROOT`** (context path `/`). Legacy clients call `/FrontController` at the
  root.
- **Plain HTTP must keep working** for `/FrontController`, `/servlet/FrontController`, `/temp/**`
  and `/webstart/**`. Legacy clients cannot follow a redirect to HTTPS. Do not enable a site-wide
  "force HTTPS" rule; if the host's panel only offers a global switch, exempt these paths.
- A writable folder outside the webapp for `license-server.downloads-dir`.
- Enough memory for the JVM (512 MB heap is plenty for master mode, which runs headless).

## Configuration

Start from `docs/application-master-mode.yml` (which sets `app.mode: master`,
`app.ui-enabled: false`, `app.repository-type: jpa`) and set at least:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jukeanator_engine
    username: ...
    password: ...          # from an environment variable, never committed
  mail:
    host: ...
    username: ...
    password: ...

license-server:
  time-zone: America/Detroit
  downloads-dir: /home/jukeanat/jukeanator_data/downloads
  legacy-jar-path: ~        # defaults to <downloads-dir>/temp/MP3Jukeboxx.jar
  latest-engine-version: ~  # e.g. 1.0.0 -- reported to engine clients
  engine-download-url: ~
  feedback-recipients: tmyers1@yahoo.com

email:
  verification-base-url: https://www.jukeanator.com
```

### Downloads folder

Copy the legacy files that are not in git into `downloads-dir`:

```
downloads/
  temp/
    MP3Jukeboxx.jar           # the LAST legacy build -- keep it frozen (see below)
    kiosk.zip
    JukeANatorReadme.txt
    jackson-*.jar, jna-3.5.2.jar, platform-3.5.2.jar, vlcj-2.4.1.jar
    messages_*.properties
    discid.exe
  webstart/
    jukeanator.jnlp, 64bit/jukeanator-64bit.jnlp and their jars
```

**Keep `MP3Jukeboxx.jar` frozen.** Legacy clients compare the date of this file
(`getJukeANatorBuildDate`, format `MM-dd-yyyy`) with their own build date and re-download on *any*
difference. Copying the jar with a new timestamp makes every legacy install update itself. Copy it
in a way that preserves the original modification time (`cp -p`, or `touch -d` afterwards).

## Cutover

1. Rotate the legacy database password: it is hardcoded in jukeanator.com's `DatabaseUtil` and
   committed to git.
2. Create the MySQL schema and start the master instance once, so Flyway creates the tables.
3. Load a fresh dump of the legacy database into a scratch schema `legacy_jukeanator` and run
   `docs/license/legacy-import.sql` against the engine schema. Compare the row counts it prints.
4. Create an administrator account for the web UI (used to sign in to `/admin/licenses.html`).
5. Copy the downloads folder (see above).
6. Smoke test against the new host before switching DNS:

   ```bash
   curl -i "http://NEW_HOST/FrontController?command=heartbeat&licenseType=1&serialNumber=TEST&customerEmail=test%40example.com"
   curl -i "http://NEW_HOST/FrontController?command=getJukeANatorBuildDate"
   ```

   The first must answer `OK` followed by 49 `a` characters (`text/html;charset=ISO-8859-1`); the
   second must print the legacy jar's date.
7. Point `www.jukeanator.com` and `jukeanator.com` at the new host.
8. Watch the log for `Provisioned`, `Subscription check` and heartbeat activity from the field.

## Customer administration

Use `/admin/licenses.html` (or the `/api/admin/licenses` endpoints):

- **New customer:** name, email, passcode (stored as the SHA-1 the clients send), license type,
  available licenses. Customers provision each computer themselves from the registration screen
  using their email and passcode; each new computer consumes one available license.
- **Renew a subscription:** "+1 year" on the machine extends its paid-through quarter by four
  quarters, counting from the current quarter if the subscription has lapsed. Clients reject a
  paid-through quarter more than four quarters ahead of today (legacy client rule), so "+1 year"
  only works once the current paid-through quarter has been reached. For an early renewal, use
  "Set quarter" to set it to at most four quarters ahead and record the remaining credit in the
  customer notes.
- **Revoke:** a revoked customer or machine is refused provisioning and fails the subscription
  check (legacy reply `REVOKED…`).

License types: `R`/`A` subscription (legacy "ASCAP mode"; remote / standalone), `C` commercial,
`P` professional (non-commercial), `L` lite.

## Client configuration (standalone and slave instances)

Instances check their license at startup and then in the background (see the `license:` block in
`application.yml`). The license files (`JukeANator.CTL`, `ExtendCode.CTL`) are looked up in
`license.directory`, or, when blank, in `app.data-dir`, the working directory, then `C:\kiosk`, the
same places the legacy application used, so existing customers' files keep working.
