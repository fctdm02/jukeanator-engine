# Master/slave local trial checklist

A step-by-step trial of a master/slave pair on **two separate computers** on the same LAN, to run
**before** pairing a real jukebox with a public master. It exercises every master/slave data flow
end to end, including the offline/catch-up paths, which no automated test covers against two real
instances. See [financial-ledger-refactor-and-sync.md](financial-ledger-refactor-and-sync.md)
(Parts C–E) for how each flow works, [per-location-accounts.md](per-location-accounts.md) for
per-location credits and mobile purchases, and [multi-tenant-mode.md](multi-tenant-mode.md) for the
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
- [ ] The **master machine**'s database is on the current schema, V2 (drop/recreate if Flyway
  reports a checksum mismatch -- see "Resetting the databases" in MasterConfig.md). The **slave machine** needs no database at all; it
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
  small test music folder. `location-api-key` is filled in at step 3. Note that `app.mode: slave`
  refuses to start while it is blank (`AppModeValidator`), so if you provision with Option B in
  step 3, do that part in `app.mode: standalone` as described there. `location-id` can stay blank:
  master assigns the slave its id when it connects.
- [ ] Set the **same** `JWT_SECRET` environment variable (32+ characters) on both machines, and in
  the environment `GeneratePostmanJwtToken` runs in. The sample configs read `app.jwt.secret` from
  it; if it is unset, each instance signs with its own random per-run key.

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
- [ ] The Web/Mobile UI is served under `/app/`: `http://<master-ip>:8080/app/` here, and
  `https://www.jukeanator.com/app/` in production (`/app` without the trailing slash redirects
  there). On master, the root URL `/` is the public website's home page, not the jukebox UI. On a
  standalone or slave instance, `/` redirects to `/app/`. Master serves every location, so its UI
  starts with a location picker.

## 3. Provision the location

Two ways to do this -- pick one. Both end with the location's `apiKey` in the **slave machine**'s
config. The slave needs no location id: master identifies it by its API key alone and tells it
its id when it connects (step 4).

### Option A: curl against master's API

- [ ] On the master machine, generate an admin JWT: run `GeneratePostmanJwtToken` (plain Java main,
  no Spring context) and copy the printed token.
- [ ] Provision (run from either machine, against the master's address):
  ```bash
  curl -s -X POST http://<master-ip>:8080/api/locations -H "Authorization: Bearer <admin-jwt>" -H "Content-Type: application/json" -d "{\"name\":\"Trial Tavern\",\"latitude\":42.33,\"longitude\":-83.04}"
  ```
  Note the returned `locationId` and `apiKey` -- the key is shown **once** (only its hash is
  stored).
- [ ] Put the key into the **slave machine**'s config (`app.location-api-key`). Leave
  `app.location-id` blank; the handshake in step 4 gives the slave master's id.

### Option B: the slave machine's own Admin Panel (no curl)

A standalone or slave instance runs as exactly one location: its *own* location, the lowest-id
entry in its `JukeANator_Locations.json`, created automatically on first startup (named
`Location Name`, with default coordinates). The Admin Panel's **Location** dialog edits that one
record and produces everything master needs, so you provision without ever calling master's API.

- [ ] If master already has a location with the name you are about to use (for example, an
  existing jukebox being demoted to a development instance), rename that one first: on its own
  machine, while connected to master, open **Location**, change the name, and click **Save
  Changes**. The new name syncs to master's `location` row. Location names are unique (the
  `location` table has a unique constraint on `name`), so the new location's `INSERT` below fails
  while another row still has its name, and master ignores a name sync that would duplicate
  another location's name. Check master's row shows the new name before continuing.
- [ ] **On the slave machine**, set `app.mode: standalone` in its `application.yml` (leave
  `app.master-instance-url` as is) and launch the instance. Standalone mode is required here:
  `app.mode: slave` with a blank `app.location-api-key` fails at startup with `app.mode=slave
  requires app.location-api-key to be set, but it was blank/missing.` Standalone mode skips that
  check and never contacts master.
- [ ] Open the Admin Panel and click **Location**. Fill in:
  - **Name**, **Latitude**, **Longitude**, and **Geo-fenced** as wanted.
  - **Logo**: click **Choose...** and pick an image. It is copied into `<data-dir>/images` and its
    file name is filled in. A thumbnail shows below the field. (You can also type the name of a
    file already in `<data-dir>/images`.)
- [ ] Click **Save Changes** if you want to save before continuing; **Copy SQL** below also
  saves. Saving a new name also renames the song library's `.oos` file to match. There is no
  location id to enter: master picks it when you run the SQL below.
- [ ] Click **Generate API Key**, then **Confirm New Key**. Copy the **API Key** shown -- it is
  shown **once**; only its hash is saved. On a location that is already connected to master, a
  new key locks it out until master's row has the new hash, which is why the dialog asks twice.
  Generating a key is the provisioning step, so it also starts this instance's books from
  scratch: its financial ledger (split periods, cash and card transactions) is deleted and a new
  split period starts, and user activity not yet sent to master is discarded. Anything recorded
  while testing in standalone mode therefore never reaches master's ledger. The song library,
  play statistics, queue and settings are kept. Never do this on a slave that is already taking
  real money.
- [ ] Click **Copy SQL**. It saves the form, then copies the SQL for master to the clipboard and
  writes it to `<data-dir>/location.sql`. It refuses until a key has been generated, since the SQL
  needs the key's hash. The SQL looks like this:

```sql
-- Run in master's database. Master assigns the id; the slave learns it when it connects.
INSERT INTO location (id, name, latitude, longitude, logo_name, api_key_hash, api_key_lookup, status, is_geo_fenced)
SELECT COALESCE(MAX(id), 0) + 1, 'Rock On Third', 42.4894, -83.1438, 'RockOnThirdLogo.jpg', '$2a$10$...', 'df15...', 'PROVISIONED', 1 FROM location;
UPDATE persistent_identity_seq SET next_val = (SELECT MAX(id) + 1 FROM location) WHERE next_val <= (SELECT MAX(id) FROM location);

-- If master already has this location's row and only the API key changed, run this instead of the statements above (check the id against master's location table):
-- UPDATE location SET api_key_hash = '$2a$10$...', api_key_lookup = 'df15...' WHERE id = 1;
```

- [ ] Stop the instance.
- [ ] **On the master machine**, run the SQL in master's database (for example, in phpMyAdmin).
  Master does not need a restart: it loads the `location` table at startup, and when a slave
  later connects with a key it doesn't recognize, it re-reads the table and picks up the new row
  (its log shows `Picked up location [<id>] (<name>), added to the repository after startup`).
  Hand edits to an existing `location` row are safe while master runs too: master writes only
  the fields it changed itself, and copies your edit into memory at its next save of the location
  list (for example, when a slave connects or syncs its library), or at a restart.
  The `INSERT` gives the new row one past the highest existing location id. The
  `UPDATE persistent_identity_seq` line is a safeguard for one case only: master's own
  `POST /api/locations` (Option A) allocates location ids from `persistent_identity_seq`, which
  every `AbstractPersistentEntity` subclass shares, so if that sequence's `next_val` were at or
  below the new id, a later API-provisioned location would collide with it. On a master that
  already has users, playlists and so on, `next_val` is normally well past it, and the line changes
  nothing.
- [ ] Back on the **slave machine**, edit `application.yml`: set `app.mode` back to `slave` and
  set `app.location-api-key` to the API Key the dialog showed you (`app.master-instance-url` is
  already correct from step 1). Leave `app.location-id` blank. During the handshake (step 4),
  master finds the location by its API key and tells the slave its id; the slave renumbers its own
  location to match and re-tags its locally stored rows.
- [ ] Nothing needs to be created on master's filesystem. Master keeps each location's synced
  data under `{location.storage-root}/{locationId}/` (`library.json`, `cover-art/`), named by
  id rather than by location name, and creates that folder on the location's first library sync.
  Renaming a location does not move or rename its folder. The logo needs no copying either: the
  slave uploads it into master's `<data-dir>/images` each time it connects and whenever the
  location is saved.
- [ ] A changed logo appears on the slave's own screen after a restart; master's Web/Mobile UI
  picks it up after the upload above.

## 4. Start the slave and confirm the handshake

- [ ] Slave starts with its Swing UI; the log shows `Running with app.mode=slave` and
  `Connected to master at http://<master-ip>:8080 using location id [<n>]`, where `<n>` is the id
  of master's row for this slave's API key.
- [ ] If master's id differs from the slave's local id (usually `1` on a fresh install), that line
  is preceded by `Master assigned locationId <n> (was <m> locally)`, and the slave uses `<n>` from
  then on. If they already match, only the connected line is logged.
- [ ] If instead the slave warns `Connected to master at ..., but master does not accept this
  slave's app.location-api-key`, no row on master holds this key. Compare master's row with the
  key in the slave's `application.yml` (step 3; `printf '%s' '<key>' | sha256sum` must equal the
  row's `api_key_lookup`). The slave disconnects and keeps retrying every few seconds, so it
  connects as soon as master's row is fixed, without a restart of either instance.
- [ ] When the slave connects with data recorded before it was provisioned, it purges that data
  before the connected line, and logs `Local financial ledger and user activity were recorded
  under locationId [<m>], before this slave was provisioned as locationId [<n>]; purging them so
  they are never sent to master`, followed by the purge counts. This happens when master assigns
  a different id, or when the financial-ledger or user-activity outbox still holds data tagged
  with an id other than master's (for example, after the locations JSON was edited by hand). The
  financial ledger is deleted and a new split period starts; unsent user activity is discarded.
  Everything else (song library, statistics, queue, settings) is kept. A slave is never switched
  from standalone to slave at a location that is already taking money, so that data is testing,
  not revenue.
- [ ] Do not edit the slave's locations JSON to change the id by hand; leave it to the handshake.
- [ ] From either machine, `GET http://<master-ip>:8080/api/locations` shows the location as
  online.
- [ ] Trigger a library scan on the slave (Admin Panel); master receives the library snapshot
  (`GET http://<master-ip>:8080/api/locations/<id>/song-library/albums` returns the albums).

### Updating a running pairing to a new build

"Update" here means installing the new application build. A slave's `application.yml` and its
locations JSON stay as they are.

The id handshake changed: right after connecting, the slave now asks master for its id with
`GET /api/locations/id-for-api-key`. An older master has no such endpoint, and a newer slave
disconnects when it cannot learn its id, so master must be updated first. An older slave never
learns its id from a newer master, so any slave whose local id differs from master's must run the
new build too. A slave whose local id already matches master's has nothing to learn and keeps
syncing on an older build, but updating it too is recommended, so that every instance has the same
code.

- [ ] Stop all slaves.
- [ ] Update master and start it. Wait until it is up.
- [ ] Update the slave whose local id differs from master's (for example, a newly provisioned
  slave still at local id `1` while master has it as `2`). Start it without editing its locations
  JSON.
- [ ] Its log shows `Master assigned locationId <n> (was <m> locally)`, then `Connected to master
  at http://<master-ip>:8080 using location id [<n>]`.
- [ ] Within one sync cycle, any `Master rejected mirrored ... 401 UNAUTHORIZED` warnings stop,
  and the entries they named are pushed under the new id.
- [ ] Recommended: install the new build on each remaining slave (one whose local id already
  matches master's) and restart it. It logs only `Connected to master at ... using location id
  [<n>]`.
- [ ] The order of the slaves does not matter, but each one's location row must already exist on
  master (step 3), and location names must be unique, so rename an existing location before
  inserting a row that reuses its name.

## 5. Slave -> master: cash and credit-card transactions (item 1)

- [ ] On the **slave machine**'s Swing UI press the bill-acceptor key (`a` by default) three
  times.
- [ ] If credit-card processing is enabled on the slave, press the card-reader key (`b` by
  default) once.
- [ ] Within seconds (pushed immediately; the 60 s sweep is the fallback), **on the master
  machine**:
  ```sql
  SELECT transaction_type, amount, location_id, source_transaction_id
  FROM jukeanator.location_transaction ORDER BY persistent_identity;
  ```
  Three `CASH` rows (and one `CREDIT_CARD` row) tagged with the location id, each with a
  `source_transaction_id`.
- [ ] **On the slave machine**, `C:\kiosk-trial\data\JukeANator_FinancialLedger.json`: those
  transactions now carry a non-null `syncedToMasterAt`.

## 6. Master -> slave: mobile purchases and credit spends (item 3)

Credits belong to one location: a patron buys them for the selected location, and they can be
spent only there (see [per-location-accounts.md](per-location-accounts.md)).

- [ ] Register a patron on master (new users start with 0 credits at every location) -- run from
  either machine:
  ```bash
  curl -s -X POST http://<master-ip>:8080/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Pat\",\"lastName\":\"Ron\",\"emailAddress\":\"pat@example.com\",\"password\":\"password123\"}"
  ```
  Note the returned token. Do **not** top up credits by editing the database -- master holds users
  in memory and its next store would overwrite the edit.
- [ ] Buy credits for the location as that patron: in the web UI served by master
  (`http://<master-ip>:8080/app/`), select the location, open **Add Funds** (it names the
  location and says the credits can be used only there), and pay with the Braintree sandbox test
  card `4111 1111 1111 1111`. Master needs sandbox Braintree keys for this.
- [ ] **On the master machine**, the purchase is in the location's ledger, once, for the full
  package price:
  ```sql
  SELECT transaction_type, amount, location_id, user_email, package_code, payment_reference
  FROM jukeanator.location_transaction WHERE transaction_type = 'MOBILE_WEB';
  ```
- [ ] **On the slave machine**, `C:\kiosk-trial\data\JukeANator_MobileTransactions.json` holds
  the same purchase, with `sourceTransactionId` equal to master's `persistent_identity`.
- [ ] Queue one song at the location as that patron -- via the web UI served by master
  (`http://<master-ip>:8080/app/`, reachable from either machine or a phone on the LAN; see step
  2), or:
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
- [ ] Slave Admin Panel -> Financial Ledger: the open period's **Mobile** total equals the
  package price paid above. Spending the credits does not change it; the spend is kept on the
  slave for audit only.

## 7. Slave -> master: finalized split period (item 2)

- [ ] **On the slave machine**'s Admin Panel -> Financial Ledger -> **Add Split**.
- [ ] **On the master machine**:
  ```sql
  SELECT parent_location_id, source_period_id, start_date, end_date, cash_total, card_total,
         mobile_total, total_earned, amount_due_owner, amount_due_operator
  FROM jukeanator.location_jukebox_split;
  ```
  Exactly one row -- the finalized period, with the same totals the slave shows. The slave's new
  open period is **not** mirrored.

## 8. Slave -> master: user activity (item 4)

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
  the slave's `mobileCreditUsages`.
- [ ] Purchase dedupe and catch-up: stop the **slave**, buy another package for the location as
  the patron, then start the slave. Within one sweep the purchase appears exactly **once** in the
  slave's `JukeANator_MobileTransactions.json`, and the open period's Mobile total includes it.

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
  connect, and again whenever the Admin Panel's **Location** dialog is saved.
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

- [ ] On the **slave machine**, tick **Geo-fenced** in the **Location** dialog and click **Save Changes**. The **master
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
- [x] Location-scoped `addAlbum`/`addMultipleSongs` don't charge credits. Addressed:
  `addMultipleSongs` now charges web users per song queued, and `addAlbum` is admin-only.
- [ ] Known gap accepted or fixed: a mobile spend that reaches the slave only after its period was finalized is stored
  but not counted in that split (master's records remain authoritative for reconciling it).

---

## Resetting test data before going live

After provisioning a production slave, you will usually test it on site: award yourself credits,
spend them from the Web/Mobile UI, feed the bill acceptor and swipe the card reader. Before the
location starts taking real money, delete that test data on both the slave and master. The
examples below use location id `2`; substitute the slave's own id (the one in its "Connected to
master at ... using location id [n]" log line).

Where the test data ends up:

- Bill acceptor and card reader transactions: the slave's financial ledger, mirrored to master's
  `location_transaction` (`location_id`).
- Finalized split periods: the slave's financial ledger, mirrored to master's
  `location_jukebox_split` (`parent_location_id`).
- Web/Mobile credit spends at the location: master's `user_song_credit_usage` (`location_id`),
  mirrored into the slave's financial ledger.
- User activity: the slave's `activity` folder (or `user_activity` table under JPA), mirrored to
  master's `user_activity` (`location_id`).

Reset both sides in the same maintenance window. Deleting the slave's ledger restarts its
transaction and period ids at 1, and master ignores a mirrored record whose id it already holds
for that location. If master still has the test rows, the first real transactions after the reset
reuse those ids and are dropped without any error.

**Step 1.** Stop the slave. It holds the ledger in memory and would write it back to disk on the
next change if it kept running.

**Step 2.** On master's database, run:

```sql
DELETE FROM location_transaction   WHERE location_id = 2;  -- includes MOBILE_WEB purchases
DELETE FROM location_jukebox_split WHERE parent_location_id = 2;
DELETE FROM user_song_credit_usage WHERE location_id = 2;
DELETE FROM user_activity          WHERE location_id = 2;  -- optional, not financial
```

**Step 3.** On the slave, delete `JukeANator_FinancialLedger.json` from its data directory (for a
JPA slave, delete all rows from its own `location_transaction`, `location_mobile_credit_usage` and
`location_jukebox_split` tables instead). Also delete `JukeANator_MobileTransactions.json` from the
same directory. This also removes the slave's copy of the Web/Mobile purchases and spends. Optionally delete the `activity` folder in the same directory (for a JPA slave, the rows
of its `user_activity` table) to drop the test activity as well.

**Step 4.** Start the slave. With no ledger, it creates an empty one and opens a new split period
starting now. Confirm the "Connected to master" log line and no 401 or ledger errors.

Not covered by the steps above:

- Your credit balance at the location is `user_location_account.num_credits` on master, along
  with your Add Funds records (`user_add_funds_transaction`) for it. Stop master before changing
  them by hand (it holds users in memory), and refund or void the Braintree sandbox charges if
  needed.
- Song play counts on the slave and the Web/Mobile play history at the location on master
  (`user_song_play_history`) still include the test plays. They have no effect on money.
