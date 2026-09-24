# Master/slave local trial checklist

A step-by-step trial of a master/slave pair on one machine, to run **before** pairing a real
jukebox with a public master. It exercises every master/slave data flow end to end, including the
offline/catch-up paths, which no automated test covers against two real instances. See
[financial-ledger-refactor-and-sync.md](financial-ledger-refactor-and-sync.md) (Parts C–E) for how
each flow works and [multi-tenant-mode.md](multi-tenant-mode.md) for the overall master/slave
design.

Tick each box as you go. Anything that fails is a finding to fix before a real pairing.

---

## 0. Prerequisites

- [ ] Full test suite passes, including the slave-mode context tests:
  ```bash
  ./mvnw -q test
  ```
- [ ] Both local databases are on the current V1 schema (drop/recreate if Flyway reports a
  checksum mismatch -- see README). The master uses `jukeanator`; the slave below uses the
  filesystem repositories, so it needs no database.
- [ ] Clock sync (Windows Time / NTP) is on. On one machine this is moot, but on a real pair
  split-period boundaries use the slave's clock while mobile spends are timestamped by the
  master's -- skew misplaces spends near a split boundary.

## 1. Lay out two instances

Two separate config/data directory pairs, so the instances never share state:

| | Master | Slave |
|---|---|---|
| Config dir | `C:\kiosk-trial\master\config` | `C:\kiosk-trial\slave\config` |
| Data dir | `C:\kiosk-trial\master\data` | `C:\kiosk-trial\slave\data` |
| `server.port` | `8081` | `8080` |
| `app.mode` | `master` | `slave` |
| `app.ui-enabled` | `false` (required for master) | `true` (Swing UI, for the bill-acceptor key) |
| `app.repository-type` | `jpa` (database `jukeanator`) | `filesystem` |

- [ ] Master config: copy [application-master-mode.yml](application-master-mode.yml) to
  `C:\kiosk-trial\master\config\application.yml` and add `server.port: 8081`.
- [ ] Slave config: copy [application-slave-mode.yml](application-slave-mode.yml) to
  `C:\kiosk-trial\slave\config\application.yml`, set `server.port: 8080`,
  `app.master-instance-url: http://localhost:8081`, and point its song-player root folder at a
  small test music folder. `location-id`/`location-api-key` are filled in at step 3.
- [ ] Both configs carry the **same** `app.jwt.secret` as
  `GeneratePostmanJwtToken.SECRET` (the sample configs already do).

Launch each with its own directories, e.g.:
```bash
./mvnw.cmd spring-boot:run -DskipTests "-Dspring-boot.run.arguments=--app.data-dir=C:\kiosk-trial\master\data --app.config-dir=C:\kiosk-trial\master\config"
```
(and the same with `slave` paths for the slave). A shared working copy means `target/` is shared
too -- start one instance, wait until it is up, then start the other.

## 2. Start the master

- [ ] Master starts; the log shows `Running with app.mode=master` and Flyway applying V1 to an
  empty `jukeanator` database.
- [ ] `GET http://localhost:8081/api/locations` returns `[]`.

## 3. Provision the location

- [ ] Generate an admin JWT: run `GeneratePostmanJwtToken` (plain Java main, no Spring context)
  and copy the printed token.
- [ ] Provision:
  ```bash
  curl -s -X POST http://localhost:8081/api/locations -H "Authorization: Bearer <admin-jwt>" -H "Content-Type: application/json" -d "{\"name\":\"Trial Tavern\",\"latitude\":42.33,\"longitude\":-83.04}"
  ```
  Note the returned `locationId` and `apiKey` -- the key is shown **once** (only its hash is
  stored).
- [ ] Put both into the slave config (`app.location-id`, `app.location-api-key`). To also exercise
  the id-correction handshake, deliberately set `app.location-id` to a *different* number.

## 4. Start the slave and confirm the handshake

- [ ] Slave starts with its Swing UI; the log shows `Running with app.mode=slave` and
  `Connected to master at http://localhost:8081`.
- [ ] If you used a wrong `location-id`: the log shows `Master assigned locationId <n> (was <m>
  locally)`, followed by the local location record being corrected, and the slave uses `<n>` from
  then on.
- [ ] `GET http://localhost:8081/api/locations` shows the location as online.
- [ ] Trigger a library scan on the slave (Admin Panel); master receives the library snapshot
  (`GET http://localhost:8081/api/locations/<id>/song-library/albums` returns the albums).

## 5. Slave → master: cash and credit-card transactions (item 1)

- [ ] On the slave's Swing UI press the bill-acceptor key (`a` by default) three times.
- [ ] If credit-card processing is enabled on the slave, press the card-reader key (`b` by
  default) once.
- [ ] Within seconds (pushed immediately; the 60 s sweep is the fallback), on master:
  ```sql
  SELECT transaction_type, amount_dollars, location_id, source_transaction_id
  FROM jukeanator.location_transaction ORDER BY persistent_identity;
  ```
  Three `CASH` rows (and one `CREDIT_CARD` row) tagged with the location id, each with a
  `source_transaction_id`.
- [ ] Slave's `C:\kiosk-trial\slave\data\JukeANator_FinancialLedger.json`: those transactions now
  carry a non-null `syncedToMasterAt`.

## 6. Master → slave: mobile credit spends (item 3)

- [ ] Register a patron on master (new users start with 6 credits):
  ```bash
  curl -s -X POST http://localhost:8081/api/users/register -H "Content-Type: application/json" -d "{\"firstName\":\"Pat\",\"lastName\":\"Ron\",\"emailAddress\":\"pat@example.com\",\"password\":\"password123\"}"
  ```
  Note the returned token. Do **not** top up credits by editing the database -- master holds users
  in memory and its next store would overwrite the edit.
- [ ] Queue one song at the location as that patron -- via the web UI served by master
  (`http://localhost:8081`), or:
  ```bash
  curl -s -X POST http://localhost:8081/api/locations/<id>/song-queue/addSong -H "Authorization: Bearer <patron-jwt>" -H "Content-Type: application/json" -d "{\"username\":\"pat@example.com\",\"albumId\":<albumId>,\"songId\":<songId>,\"priority\":1,\"priorityPlay\":false}"
  ```
- [ ] The song appears in the slave's queue.
- [ ] Master charged it:
  ```sql
  SELECT amount, type, location_id, sync_id FROM jukeanator.user_song_credit_usage;
  ```
  One negative `QUEUE_ADD` row tagged with the location id and a `sync_id`.
- [ ] The slave received it: `mobileCreditUsages` in the slave's `JukeANator_FinancialLedger.json`
  holds one entry with the same `sourceSyncId`.
- [ ] Slave Admin Panel → Financial Ledger: the open period's **Mobile** total is non-zero
  (credits spent ÷ credits-per-dollar), not $0.

## 7. Slave → master: finalized split period (item 2)

- [ ] Slave Admin Panel → Financial Ledger → **Add Split**.
- [ ] On master:
  ```sql
  SELECT parent_location_id, source_period_id, start_date, end_date, cash_total, card_total,
         mobile_total, total_earned, amount_due_owner, amount_due_operator
  FROM jukeanator.location_jukebox_split;
  ```
  Exactly one row -- the finalized period, with the same totals the slave shows. The slave's new
  open period is **not** mirrored.

## 8. Slave → master: user activity (item 4)

- [ ] Click around the slave's Swing UI (tabs, an artist, paging) and queue a song locally.
- [ ] Within ~60 s (activity is pushed in batches by the sweep, never per event), on master:
  ```sql
  SELECT source, username, activity_type, occurred_at FROM jukeanator.user_activity
  WHERE location_id = <id> ORDER BY occurred_at;
  ```
  The Swing activity appears (`SWING_UI` / `LOCAL`). The step-6 mobile add appears exactly
  **once**, under `pat@example.com` -- the slave's own `SYSTEM`-attributed copy of that relayed
  command is never pushed.
- [ ] Slave's `C:\kiosk-trial\slave\data\activity\master-sync-cursor.json` exists and records an
  offset for today's day-file.

## 9. Offline and catch-up (the most important section)

- [ ] **Stop the master.**
- [ ] On the slave: press the bill-acceptor key twice, click around the UI, and **Add Split**.
  The slave keeps working normally -- no errors, no UI delay.
- [ ] Slave data files: the two new cash transactions and the new finalized period have a null
  `syncedToMasterAt`; the activity cursor has not advanced.
- [ ] **Restart the master.** Within ~60 s of the slave reconnecting:
  - [ ] Both cash transactions are in `location_transaction` (no duplicates of the step-5 rows).
  - [ ] The second finalized period is in `location_jukebox_split` (still exactly one row per
    period).
  - [ ] The offline activity is in `user_activity`, with nothing duplicated.
- [ ] Mobile dedupe: once master is back, queue another song as the patron (step 6). That spend
  reaches the slave twice -- by the live push, and again by the next sweep's catch-up pull (which
  re-pulls the whole open period after the failed sweeps) -- yet must appear exactly **once** in
  the slave's `mobileCreditUsages`, and the open period's Mobile total must count it once.

## 10. Restarts never re-push

- [ ] Restart the **slave**. After a couple of sweeps, re-run the queries from steps 5, 7 and 8:
  row counts are unchanged.
- [ ] Restart the **master**. Same check.

## 11. Negative checks

- [ ] Stop the slave, change its `app.location-api-key` to a wrong value, start it: it never
  connects, and nothing it records reaches master (all its outbox entries stay pending). Restore
  the key and confirm the backlog then drains.
- [ ] A patron queueing a song while the slave is stopped gets an immediate "location offline"
  error rather than hanging, and is not charged.

---

## Before a real pairing

Not covered by this trial, but required before a public master and a real jukebox:

- [ ] HTTPS for `app.master-instance-url` -- the location API key travels in request headers.
- [ ] Braintree production credentials on master (the trial never charged real money).
- [ ] Master database backups.
- [ ] Known gaps accepted or fixed: location-scoped `addAlbum`/`addMultipleSongs` don't charge
  credits; a mobile spend that reaches the slave only after its period was finalized is stored
  but not counted in that split (master's records remain authoritative for reconciling it).
