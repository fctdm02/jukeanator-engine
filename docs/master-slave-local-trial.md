# Master/slave local trial checklist

A step-by-step trial of a master/slave pair on **two separate computers** on the same LAN, to run
**before** pairing a real jukebox with a public master. It exercises every master/slave data flow
end to end, including the offline/catch-up paths, which no automated test covers against two real
instances. See [financial-ledger-refactor-and-sync.md](financial-ledger-refactor-and-sync.md)
(Parts C–E) for how each flow works and [multi-tenant-mode.md](multi-tenant-mode.md) for the
overall master/slave design.

Each machine runs one normal install on its own default port. The one extra step two separate
hosts need is pointing the slave at the master's real address on the LAN instead of `localhost`.

Tick each box as you go. Anything that fails is a finding to fix before a real pairing.

---

## 0. Prerequisites

- [ ] Full test suite passes on whichever machine you build from:
  ```bash
  ./mvnw -q test
  ```
- [ ] Each machine has its own working copy of the project (a clone, or a copy of the built WAR) --
  they're separate hosts, so there's nothing to share between them.
- [ ] The **master machine**'s database is on the current V1 schema (drop/recreate if Flyway
  reports a checksum mismatch -- see README). The **slave machine** needs no database at all; it
  uses the filesystem repositories.
- [ ] The two machines can reach each other on the LAN: note the master machine's IP address or
  hostname (`ipconfig` on Windows, `ip addr`/`ifconfig` on Linux/macOS), and confirm its firewall
  allows inbound TCP on whatever port it'll run on (8080 by default -- see step 1). From the slave
  machine, `ping <master-ip>` should succeed before you start either instance.
- [ ] Clock sync (Windows Time / NTP) is on, **on both machines**. This matters for real here, not
  as a hypothetical: split-period boundaries use the slave's clock while mobile spends are
  timestamped by the master's, so skew misplaces spends near a split boundary.

## 1. Set up the two machines

One config/data directory per machine, both using the project's default port (`8080`) -- since
each is a separate host, there's no port clash to avoid:

**Master machine**
- Config dir: `C:\kiosk-trial\config`
- Data dir: `C:\kiosk-trial\data`
- `server.port`: default (`8080`)
- `app.mode`: `master`
- `app.ui-enabled`: `false` (required for master)
- `app.repository-type`: `jpa` (database `jukeanator`)

**Slave machine**
- Config dir: `C:\kiosk-trial\config`
- Data dir: `C:\kiosk-trial\data`
- `server.port`: default (`8080`)
- `app.mode`: `slave`
- `app.ui-enabled`: `true` (Swing UI, for the bill-acceptor key)
- `app.repository-type`: `filesystem`

- [ ] **Master machine**: copy [application-master-mode.yml](application-master-mode.yml) to
  `C:\kiosk-trial\config\application.yml`.
- [ ] **Slave machine**: copy [application-slave-mode.yml](application-slave-mode.yml) to
  `C:\kiosk-trial\config\application.yml`, set `app.master-instance-url` to the master machine's
  LAN address, e.g. `http://192.168.1.50:8080` (use the IP/hostname you noted in step 0 --
  `localhost` here would point the slave at itself). Also point its song-player root folder at a
  small test music folder. `location-id`/`location-api-key` are filled in at step 3.
- [ ] Both configs carry the **same** `app.jwt.secret` as `GeneratePostmanJwtToken.SECRET` (the
  sample configs already do).

Launch each on its own machine, e.g.:
```bash
./mvnw.cmd spring-boot:run -DskipTests "-Dspring-boot.run.arguments=--app.data-dir=C:\kiosk-trial\data --app.config-dir=C:\kiosk-trial\config"
```
Start the master first and confirm it's up (step 2) before starting the slave -- the slave's
startup handshake needs a reachable master, though once both are running, restart order no longer
matters (see step 10).

## 2. Start the master

- [ ] Master starts; the log shows `Running with app.mode=master` and Flyway applying V1 to an
  empty `jukeanator` database.
- [ ] From the master machine, `GET http://localhost:8080/api/locations` returns `[]`.
- [ ] From the **slave machine**, `GET http://<master-ip>:8080/api/locations` also returns `[]` --
  confirms the LAN path the slave will actually use is open before you rely on it.

## 3. Provision the location

Two ways to do this -- pick one. Both end with the same `locationId`/`apiKey` pair landing in the
**slave machine**'s config.

### Option A: curl against master's API

- [ ] On the master machine, generate an admin JWT: run `GeneratePostmanJwtToken` (plain Java main,
  no Spring context) and copy the printed token.
- [ ] Provision (run from either machine, against the master's address):

```
bash
curl -s -X POST http://<master-ip>:8080/api/locations -H "Authorization: Bearer <admin-jwt>" -H "Content-Type: application/json" -d "{\"name\":\"Trial Tavern\",\"latitude\":42.33,\"longitude\":-83.04}"
```
  
  Note the returned `locationId` and `apiKey` -- the key is shown **once** (only its hash is
  stored).
- [ ] Put both into the **slave machine**'s config (`app.location-id`, `app.location-api-key`). To
  also exercise the id-correction handshake, deliberately set `app.location-id` to a *different*
  number.

### Option B: the slave machine's own Admin Panel (no curl)

`LocationService`/`registerLocation()` exist regardless of `app.mode`, specifically so a
standalone or slave instance can locally register a location and hand the operator a ready-made
record to turn into a SQL insert against master's database (see `LocationConfig`'s class javadoc).
This lets you provision without ever calling master's API directly.

- [ ] **On the slave machine**, launch the instance once with its config as copied in step 1
  (placeholder `location-id`/`location-api-key` still in place). It'll fail to connect to the
  master with those placeholder credentials -- harmless, same failure mode as step 11 -- but the
  Swing Admin Panel comes up regardless.
- [ ] Open the Admin Panel and click **Add Location**. Enter the same name/latitude/longitude
  you'd otherwise pass to curl (e.g. `Trial Tavern` / `42.33` / `-83.04`) and click **Register
  Location**. Note the **Location ID** and **API Key** shown -- the key is shown **once**, same as
  the curl response.
- [ ] Still on the slave machine, open `C:\kiosk-trial\data\JukeANator_Locations.json` and copy the
  `apiKeyHash` value for the location you just registered (the dialog doesn't display the hash
  itself, only the plaintext key).
- [ ] Stop the instance.
- [ ] **On the master machine**, insert the row directly into its database:

```sql
  INSERT INTO jukeanator.location (id, name, latitude, longitude, api_key_hash, status, is_geo_fenced)
  VALUES (<locationId>, 'Trial Tavern', 42.33, -83.04, '<apiKeyHash>', 'PROVISIONED', 1);
```
  Leave `api_key_lookup` out of the statement (NULL) -- it's the newer SHA-256 index column, and
  it's optional: master self-backfills it via a one-time bcrypt check the first time this slave
  connects (see `LocationServiceImpl.verifyLegacyApiKey`). `is_geo_fenced` is `1` to match the
  default the UI's registration path itself used.
- [ ] Back on the **slave machine**, edit `application.yml`: set `app.location-id` and
  `app.location-api-key` to the Location ID / API Key the dialog showed you (`app.mode` and
  `app.master-instance-url` are already correct from step 1). Since this is the same instance,
  `JukeANator_Locations.json` already has the matching row at that id, so nothing else to change.

## 4. Start the slave and confirm the handshake

- [ ] Slave starts with its Swing UI; the log shows `Running with app.mode=slave` and
  `Connected to master at http://<master-ip>:8080`.
- [ ] If you used a wrong `location-id`: the log shows `Master assigned locationId <n> (was <m>
  locally)`, followed by the local location record being corrected, and the slave uses `<n>` from
  then on.
- [ ] From either machine, `GET http://<master-ip>:8080/api/locations` shows the location as
  online.
- [ ] Trigger a library scan on the slave (Admin Panel); master receives the library snapshot
  (`GET http://<master-ip>:8080/api/locations/<id>/song-library/albums` returns the albums).

## 5. Slave → master: cash and credit-card transactions (item 1)

- [ ] On the **slave machine**'s Swing UI press the bill-acceptor key (`a` by default) three
  times.
- [ ] If credit-card processing is enabled on the slave, press the card-reader key (`b` by
  default) once.
- [ ] Within seconds (pushed immediately; the 60 s sweep is the fallback), **on the master
  machine**:
  ```sql
  SELECT transaction_type, amount_dollars, location_id, source_transaction_id
  FROM jukeanator.location_transaction ORDER BY persistent_identity;
  ```
  Three `CASH` rows (and one `CREDIT_CARD` row) tagged with the location id, each with a
  `source_transaction_id`.
- [ ] **On the slave machine**, `C:\kiosk-trial\data\JukeANator_FinancialLedger.json`: those
  transactions now carry a non-null `syncedToMasterAt`.

## 6. Master → slave: mobile credit spends (item 3)

- [ ] Register a patron on master (new users start with 6 credits) -- run from either machine:
  ```bash
  curl -s -X POST http://<master-ip>:8080/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Pat\",\"lastName\":\"Ron\",\"emailAddress\":\"pat@example.com\",\"password\":\"password123\"}"
  ```
  Note the returned token. Do **not** top up credits by editing the database -- master holds users
  in memory and its next store would overwrite the edit.
- [ ] Queue one song at the location as that patron -- via the web UI served by master
  (`http://<master-ip>:8080`, reachable from either machine or a phone on the LAN), or:
  ```bash
  curl -s -X POST http://<master-ip>:8080/api/locations/<id>/song-queue/addSong -H "Authorization: Bearer <patron-jwt>" -H "Content-Type: application/json" -d "{\"username\":\"pat@example.com\",\"albumId\":<albumId>,\"songId\":<songId>,\"priority\":1,\"priorityPlay\":false}"
  ```
- [ ] The song appears in the **slave machine**'s queue.
- [ ] **On the master machine**, master charged it:
  ```sql
  SELECT amount, type, location_id, sync_id FROM jukeanator.user_song_credit_usage;
  ```
  One negative `QUEUE_ADD` row tagged with the location id and a `sync_id`.
- [ ] **On the slave machine**, the slave received it: `mobileCreditUsages` in
  `C:\kiosk-trial\data\JukeANator_FinancialLedger.json` holds one entry with the same
  `sourceSyncId`.
- [ ] Slave Admin Panel → Financial Ledger: the open period's **Mobile** total is non-zero
  (credits spent ÷ credits-per-dollar), not $0.

## 7. Slave → master: finalized split period (item 2)

- [ ] **On the slave machine**'s Admin Panel → Financial Ledger → **Add Split**.
- [ ] **On the master machine**:
  ```sql
  SELECT parent_location_id, source_period_id, start_date, end_date, cash_total, card_total,
         mobile_total, total_earned, amount_due_owner, amount_due_operator
  FROM jukeanator.location_jukebox_split;
  ```
  Exactly one row -- the finalized period, with the same totals the slave shows. The slave's new
  open period is **not** mirrored.

## 8. Slave → master: user activity (item 4)

- [ ] On the **slave machine**, click around the Swing UI (tabs, an artist, paging) and queue a
  song locally.
- [ ] Within ~60 s (activity is pushed in batches by the sweep, never per event), **on the master
  machine**:
  ```sql
  SELECT source, username, activity_type, occurred_at FROM jukeanator.user_activity
  WHERE location_id = <id> ORDER BY occurred_at;
  ```
  The Swing activity appears (`SWING_UI` / `LOCAL`). The step-6 mobile add appears exactly
  **once**, under `pat@example.com` -- the slave's own `SYSTEM`-attributed copy of that relayed
  command is never pushed.
- [ ] **On the slave machine**, `C:\kiosk-trial\data\activity\master-sync-cursor.json` exists and
  records an offset for today's day-file.

## 9. Offline and catch-up (the most important section)

- [ ] **Stop the master** (on the master machine).
- [ ] On the **slave machine**: press the bill-acceptor key twice, click around the UI, and
  **Add Split**. The slave keeps working normally -- no errors, no UI delay, even though it can no
  longer reach the master machine at all.
- [ ] **On the slave machine**, its data files: the two new cash transactions and the new
  finalized period have a null `syncedToMasterAt`; the activity cursor has not advanced.
- [ ] **Restart the master.** Within ~60 s of the slave reconnecting over the LAN:
  - [ ] Both cash transactions are in `location_transaction` (no duplicates of the step-5 rows).
  - [ ] The second finalized period is in `location_jukebox_split` (still exactly one row per
    period).
  - [ ] The offline activity is in `user_activity`, with nothing duplicated.
- [ ] Mobile dedupe: once master is back, queue another song as the patron (step 6). That spend
  reaches the slave twice -- by the live push, and again by the next sweep's catch-up pull (which
  re-pulls the whole open period after the failed sweeps) -- yet must appear exactly **once** in
  the slave's `mobileCreditUsages`, and the open period's Mobile total must count it once.

## 10. Restarts never re-push

- [ ] Restart the **slave** machine's instance. After a couple of sweeps, re-run the queries from
  steps 5, 7 and 8 on the master machine: row counts are unchanged.
- [ ] Restart the **master** machine's instance. Same check.

## 11. Negative checks

- [ ] On the **slave machine**, stop the instance, change its `app.location-api-key` to a wrong
  value, start it: it never connects, and nothing it records reaches the master machine (all its
  outbox entries stay pending). Restore the key and confirm the backlog then drains.
- [ ] A patron queueing a song while the slave machine's instance is stopped gets an immediate
  "location offline" error rather than hanging, and is not charged.
- [ ] Unplug the slave machine's network connection (or block the master's port at the slave's
  firewall) instead of stopping the process: the slave keeps serving its own local UI/queue with
  no visible delay, and resumes pushing once connectivity returns -- this is the failure mode an
  API-key change can't exercise, since the process never even attempts a connection in that case.

## 12. Geo-fencing

Geo-fencing refuses a Web/Mobile UI patron's queue operations (Play Song, Play Priority Song,
Play Playlist, move up, move down, remove) at a location whose **Geo-fenced** box is ticked,
unless the patron's device is near the location's latitude/longitude. Admin users and the
JFC/Swing kiosk are never fenced. It is enforced only on master, and only with
`app.geo-fence.enabled: true` (see `src/main/resources/application.yml` for every setting).

How it works:
- The slave pushes its own name, logo, latitude, longitude and geo-fence flag to master on every
  connect, and again whenever **Edit Location Info** is saved.
- The Web UI asks `GET /api/locations/{id}/geo-fence` whether the fence is enforced. If it is,
  each queue request carries the device position in `X-Geo-Latitude`, `X-Geo-Longitude`,
  `X-Geo-Accuracy`, `X-Geo-Timestamp` (and `X-Geo-Simulated` for a hand-entered position).
- Master allows the request when `distance - accuracy <= radius-meters` (default 50 m), the
  accuracy is at most `max-accuracy-meters` (default 150 m) and the reading is at most
  `max-position-age-seconds` old (default 120 s). Otherwise it returns 403
  `GeoFenceViolationException` with a message for the patron.

Browsers only provide device location on https pages or `http://localhost`, so a phone on the LAN
over plain http cannot send a real position -- this applies regardless of which machine serves the
page, since it's the phone's own browser enforcing it. For QA, set this in the **master
machine**'s `application.yml`:

```yaml
app:
  geo-fence:
    enabled: true
    allow-simulated-position: true   # QA only
```

- [ ] On the **slave machine**, tick **Geo-fenced** in Edit Location Info and save. The **master
  machine**'s log shows `Syncing location info for locationId [...]` with the new values.
- [ ] Browser on the **master machine** itself (`http://localhost:8080`), logged in as a patron:
  allow location access, then Play Song. It is refused with "You must be at ... to queue songs."
  (the master machine's real position is not at the location's coordinates), and no credits are
  charged.
- [ ] Phone on the LAN, pointed at `http://<master-ip>:8080`: Account > **Simulate My Location
  (QA)** > **Use Location's Coordinates** > Save, then Play Song. It is queued.
- [ ] Simulate My Location > **Offset 250 m North** > Save, then Play Song. It is refused.
- [ ] Simulate My Location > **Clear**, then Play Song. It is refused with the https message.
- [ ] Untick **Geo-fenced** on the slave machine and save. Play Song works with no position.
- [ ] Logged in as an admin, queueing works regardless of position.

Production checklist for the master at https://www.jukeanator.com:
- [ ] `app.geo-fence.enabled: true`.
- [ ] `app.geo-fence.allow-simulated-position` absent or `false` (master logs a WARN at startup if
  it is on).
- [ ] Each geo-fenced location's latitude/longitude are accurate (a phone map pin at the
  jukebox is usually close enough).

---

## Before a real pairing

This trial already proves the master/slave data flows work across real, independently-clocked
hosts. Still not covered, and required before a public master and a real jukebox:

- [ ] HTTPS for `app.master-instance-url` -- plain `http://<lan-ip>:8080` is fine on a trusted LAN,
  but the location API key travels in request headers, so a public master needs TLS.
- [ ] Braintree production credentials on master (the trial never charged real money).
- [ ] Master database backups.
- [ ] Known gaps accepted or fixed: location-scoped `addAlbum`/`addMultipleSongs` don't charge
  credits; a mobile spend that reaches the slave only after its period was finalized is stored
  but not counted in that split (master's records remain authoritative for reconciling it).
