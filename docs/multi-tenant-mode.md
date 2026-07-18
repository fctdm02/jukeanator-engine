"Multi Tenant Mode" and "Master/Slave"
======================================

Overview:
Currently, jukeanator-engine, especially when ui-enabled is true showing the JFC/Swing interface, is a standalone jukebox application.  The main interface is 
the JFC/Swing UI and users typically interact by putting money into the bill acceptor, getting credits, then browsing/searching for music on the UI, via a touchscreen monitor, 
then selecting songs to add to the queue, all the while listening to the music that is being played by the player service, that is assumed to be connected to the sound system of
the bar/restaurant where the physical jukebox is located.  This jukebox will be on the internet, but will NOT have a public IP address...   This would be considered "slave" mode.

What is desired is to have an instance of jukeanator-engine running on a hosted service with a domain name, let's say: http://www.jukeanator.com/jukeanator-engine that would be running
headless (i.e. no JFC/Swing UI) and it would be considered the "master".  This instance would be "location agnostic" in that there is no one single song library/song queue/song player
corresponding to what would be at a physical location with a touch screen, bill acceptor and sound system.

Item 1: Each slave instance would upload its song library, location name, coordinates, etc. to the master instance.  In application-yml, there would be settings for "master/slave" mode
and "master-instance-url" if in slave mode.

Item 2: All of the APIs that exist now to serve content to the web application / mobile application would be refactored to take a locationId, where needed, in order for a user to interact
with the slave instance at that physical location.  It would be assumed that each slave instance be on a tailscale, or other VPN, and would be able to securely communicate with the 
master instance running on a public IP in the cloud/private hosted provider.

Item 3: Thus, with the web application, were there is a static list of "Rock on Third" for location drop down, it would be comprised of all slave instances of jukeanator-engine.

Item 4: There would need to be some sort of LocationService that would be used to provision a slave instance, that is, uploading name, song library, coordinates, and then it would be
assumed that cover art would be duplicated to this master instance and that the song library service and song queue service APIs of the web/mobile app would be for whatever location
that the user picked.

Item 5: There would need to be a persistent web socket connection between master and slave, as if the user is interacting via web/mobile app to the master, that any "song plays", or 
requests to add songs to a physical location's slave instance song queue, then the song added to queue event would need to flow down to the user (assuming they have enough credits, 
which would solely be the domain of the master instance, although it would be required to know which song credit was used at what location for accounting/financial purposes with the
bar owner)

The end result is that a person sitting at a table at the Rock on Third would not need to walk over to the physical jukebox to play a song, rather, they would just purchase credits
via the web/mobile app, choose "Rock on Third" for location and then interact with the song library service and song queue service through the web/mobile app.

Let me know if you have any questions before formulating an implementation plan.























PLAN:
=====

# Multi-Tenant "Master/Slave" Mode

## Context

Today `jukeanator-engine` is a fully standalone, single-tenant application: one deployment = one physical bar/restaurant location, with its own song library, queue, player, and (for web/mobile users) credit balance, all as process-wide singletons. There is **zero location/tenant concept anywhere** in the codebase — confirmed across song-library, song-queue, song-player, and user domains.

The goal is to run one additional headless "master" instance on the public internet (e.g. `jukeanator.com`) that is location-agnostic, while every existing physical jukebox becomes a "slave" behind a VPN (no public IP). A web/mobile user picks a location and interacts with that location's library/queue remotely through the master, exactly as if they walked up to the physical machine. Physical walk-up patrons keep using the bill acceptor exactly as today — that flow is untouched by this feature.

Decisions already made with the user, driving every design choice below:
- **Library sync**: metadata + cover art only travel to master; audio files stay on the slave (only the slave plays audio).
- **Credits**: all *web/mobile* credit purchases/balance live on master only; each spend must be tagged with the location it was spent at, for bar-owner accounting.
- **Slave auth**: master authenticates each slave via a per-location API key/secret issued at provisioning, not just VPN/network trust.
- **Offline resilience**: a slave must keep working fully independently of master connectivity — local touchscreen, bill acceptor, library, and queue never depend on master being reachable.
- **Local walk-up credits**: physical patrons keep paying via the bill acceptor exactly as today (`JukeANatorFrame`'s local `CreditManager`, a Swing-UI-only, in-memory counter, entirely separate from `UserService`'s web credit balance). This mechanism is **not modified** by this feature.

**Topology, stated explicitly**: a web/mobile user's connection (both its HTTP calls and its browser-facing `/ws` STOMP subscription) goes **exclusively to the master**. It never reaches a slave directly — slaves have no public IP, so this is a hard architectural constraint, not just a preference. The master↔slave persistent connection (Phase 2) is a **separate, internal, backend-to-backend channel**, established outbound by the slave and authenticated with its location API key; it is not part of, and not reachable from, the web/mobile app's connection to master. Within that internal channel, the flow is:
- **Master → slave**: commands (e.g. "add this song to your queue"), triggered only by a web/mobile user's action against the master API.
- **Slave → master**: (a) the synchronous reply to a command (success/failure/result, correlated back to the waiting master HTTP request — see Phase 2), and (b) the slave's own real-time events (song started/finished, local walk-up queue changes), which master then rebroadcasts on its own `/topic/location/{locationId}/**` destinations for web/mobile clients already subscribed to master. The web/mobile app never talks to, or is aware of, the slave's side of this channel — it only ever sees master's API and master's broadcast topics.

This plan is additive-only wherever possible: standalone-mode deployments (the default, `app.mode` unset/`standalone`) must be byte-for-byte unaffected — no new bean is constructed, no existing controller method signature changes, unless `app.mode` is explicitly `master` or `slave`.

## Key existing patterns being reused

- **Repository swap pattern**: every domain (`song-library`, `song-queue`, `user`) has a repository interface with `filesystem`/`postgres` impls selected via `@ConditionalOnProperty(name="X.repository-type", havingValue=...)` in [AppConfig.java](src/main/java/com/djt/jukeanator_engine/config/AppConfig.java). New `location` and `credit-ledger` domains follow the same shape.
- **Dead-code precedent for remote-backed services**: [SongQueueServiceHttpClient.java](src/main/java/com/djt/jukeanator_engine/domain/songqueue/client/SongQueueServiceHttpClient.java) and its song-player counterpart already implement the live service interfaces via `RestClient` against a configurable base URL — unused today (no callers), but exactly the shape a "remote-backed" implementation should take. It assumes the caller can dial the target directly, which works slave→master but not master→slave (no public IP), so master's proxy needs a different transport (see Phase 2).
- **Domain event → WebSocket broadcast**: services publish Spring `ApplicationEvent`s; [WebSocketEventBroadcaster.java](src/main/java/com/djt/jukeanator_engine/web/event/WebSocketEventBroadcaster.java) has `@EventListener`s pushing to STOMP topics via `SimpMessagingTemplate`. [WebSocketConfig.java](src/main/java/com/djt/jukeanator_engine/config/WebSocketConfig.java) registers one `/ws` STOMP endpoint (`/topic`,`/queue` brokers, `/user` prefix), secured by `StompJwtChannelInterceptor`.
- **Two existing auth shapes**: (1) JWT web/mobile auth (`JwtUtil`, `JwtAuthenticationFilter`, `StompJwtChannelInterceptor`) — principal is a plain `String` email. (2) `LocalPrincipal`/`LocalAuthenticationToken` — a process-global singleton `"LOCAL"`/`"ROLE_LOCAL"` identity for the physical touchscreen, used by [SongQueueController.java](src/main/java/com/djt/jukeanator_engine/domain/songqueue/controller/SongQueueController.java) to skip web-credit charging for local actions (those are already paid for via the separate bill-acceptor `CreditManager` in the Swing UI, not this backend's credit system).
- **IDs are scan-order ints, not stable**: `SongScanner` assigns sequential `persistentIdentity` ints per filesystem scan; every slave's first album is `albumId=0`. **Master must never merge multiple slaves' libraries into one ID space** — each location's synced snapshot is stored and addressed separately, `(locationId, sourceAlbumId, sourceSongId)`.
- `UserEntity.numCredits` is a bare int, floor-at-zero; **no credit transaction ledger exists today**, and `UserServiceImpl.addFunds()` is itself an unimplemented stub (`throw new UserServiceException("Add funds payment not yet implemented")`) — so there's no working purchase flow to "turn off" on slaves; this feature only needs to cover *spend* tracking for now.
- `SecurityConfig.java` hardcodes literal path lists per rule (public GETs, authenticated POSTs, admin POSTs) — every new location-scoped endpoint needs an explicit sibling rule added here; forgetting one falls through to the safe-but-breaking `.anyRequest().authenticated()` catch-all (fails closed, but silently breaks a feature rather than exposing one).
- `spring-boot-starter-websocket` already provides both server-side STOMP broker infra and client-side STOMP classes (`WebSocketStompClient`, `StandardWebSocketClient`) needed for the slave to dial out to master — no new dependency required.

## Phase 1 — Location domain + provisioning + metadata/cover-art sync

Standalone mode fully unaffected. New `app.mode` config: `standalone` (default) | `master` | `slave`, plus `master-instance-url`, `location-id`, `location-api-key` (slave-only), added to [AppProperties.java](src/main/java/com/djt/jukeanator_engine/config/AppProperties.java) alongside the existing `uiEnabled`/`rootPathWindows` fields, with `isMaster()`/`isSlave()`/`isStandalone()` convenience methods. New `application.yml` block:

```yaml
app:
  mode: standalone   # standalone | master | slave
  master-instance-url: ~
  location-id: ~
  location-api-key: ~
```

New package `domain/location/` (mirrors existing domain layout — `model`, `repository`, `service`, `controller`, `dto`, `event`, `exception`, `config`):

- `model/LocationEntity.java` — `locationId` (UUID, natural identity — new stable scheme, not the scan-order int scheme), `name`, `latitude`/`longitude`, `apiKeyHash` (bcrypt via the existing `PasswordEncoder` bean from `SecurityConfig`), `status` (PENDING/PROVISIONED/ACTIVE), `lastSeenAt`, `libraryLastSyncedAt`.
- `model/LocationRootEntity.java` — aggregate root, `Map<String, LocationEntity>`, same shape as `UserRootEntity`.
- `repository/LocationRepository.java` (+ `LocationRepositoryFileSystemImpl`, storing JSON via the already-injected Jackson `ObjectMapper` — recommend JSON over the `.oos` Java-serialization format used elsewhere, since this is a brand-new domain with no back-compat need, and JSON is inspectable for an admin provisioning table; `LocationRepositoryPostgresImpl` as a stub for pattern-consistency).
- `config/LocationProperties.java` — `@ConfigurationProperties(prefix="location")`: `repository-type`, `storage-root` (defaults under the existing root-path config), `command-timeout-ms` (used in Phase 2).
- `service/LocationService.java`/`Impl` — `registerLocation(...)` (generates locationId + one-time-shown plaintext secret, stores bcrypt hash), `receiveLibrarySync(locationId, apiKey, payload)`, `listLocations()` (picker list with derived `online` status).
- `service/LibrarySyncService.java` (slave-only, `@ConditionalOnProperty(app.mode=slave)`) — listens for the existing `ScanFileSystemForSongsEvent`, builds a new flattened `dto/LibrarySnapshotDto.java` (plain records, NOT the fragile `.oos` binary format — avoids cross-version `InvalidClassException` risk between master/slave deploys) by walking the slave's own loaded `RootFolderEntity`, and `RestClient`-POSTs it to `{master-instance-url}` — this direction is a normal outbound call since the slave has connectivity, reusing the same `RestClient` idiom as the dead `SongQueueServiceHttpClient`.
- Two sync endpoints on `controller/LocationController.java` (`@RequestMapping("/api/locations")`): `POST /{locationId}/library-sync/metadata` (JSON, cheap, run after every scan) and `POST /{locationId}/library-sync/cover-art/{sourceAlbumId}` (single-file multipart, called only when a `coverArtHash` field in the metadata differs from what master already has — avoids re-uploading unchanged art on every scan). Plus `POST /api/locations` (admin-only, master-mode) and `GET /api/locations` (public, master-mode) for the picker.
- Master stores each location's synced data under its own subtree, e.g. `{storage-root}/{locationId}/library.json` + `{storage-root}/{locationId}/cover-art/{sourceAlbumId}.jpg` — never merged into a shared tree.
- New auth: a `LocationApiKeyAuthenticationFilter` (sibling to `JwtAuthenticationFilter`) authenticates the two sync endpoints via `locationId`+`apiKey` headers, bcrypt-checked against `LocationEntity.apiKeyHash`.
- `SecurityConfig.java` gets new rules: public `GET /api/locations`, admin-only `POST /api/locations`, and the new filter wired in for the sync endpoints.
- All Phase-1 beans (`LocationService`, `LocationController`, `LibrarySyncService`) live in a new `config/LocationConfig.java` (`@Configuration`, `@ConditionalOnProperty(app.mode=master)` for master-only pieces, `slave` for `LibrarySyncService`) — so none of this exists in a standalone deployment's object graph.

## Phase 2 — Persistent connection + master-side per-location queue proxy + locationId-scoped controllers

**Highest-risk phase — nothing like this exists in the codebase today.** Recommend a focused prototype of just `SlaveCommandGateway` + one command type (`addSongToQueue`) before building the rest.

**The core problem**: only the slave can dial the master (no public IP on slave side), so master cannot `RestClient.post()` a slave the way the existing dead HTTP-client classes do. A web/mobile HTTP request on master (e.g. "add this song to location X's queue") needs a reply that only arrives asynchronously over a channel the slave opened.

**Mechanism — a second STOMP relationship, slave-as-client / master-as-server**, parallel to but separate from the existing browser-facing `/ws`:
- Extend [WebSocketConfig.java](src/main/java/com/djt/jukeanator_engine/config/WebSocketConfig.java) with a second `registry.addEndpoint("/ws-slave")` inside the existing `registerStompEndpoints` (one `@EnableWebSocketMessageBroker` config, two endpoints — confirmed this is how Spring supports it), guarded so it's only registered when `app.mode=master`. A new `StompLocationApiKeyChannelInterceptor` (mirrors `StompJwtChannelInterceptor`) reads `location-id`/`location-api-key` STOMP CONNECT headers, bcrypt-verifies via `LocationService`, and on success sets `accessor.setUser(new LocationPrincipal(locationId))`. Updates `LocationEntity.lastSeenAt` on connect; a `SessionDisconnectEvent` listener marks it offline.
- Slave subscribes to its own command destination via Spring's existing `/user/**` convention (`registry.setUserDestinationPrefix("/user")` already configured) — master sends via `convertAndSendToUser(locationId, "/queue/commands", command)`, routed automatically to that location's live session, exactly like the existing `/user/{email}/queue/credits` pattern just keyed by locationId instead of email.
- Slave pushes its own real-time events (song started/finished, queue changed) to `/app/location-events` (new `@MessageMapping` in `controller/LocationEventStompController.java`); master republishes to `/topic/location/{locationId}/queue`, `/topic/location/{locationId}/now-playing`, etc. — same `SimpMessagingTemplate.convertAndSend` idiom as `WebSocketEventBroadcaster`, parameterized by locationId so web/mobile clients only see their chosen location's events.

**Request/reply bridge** — new `service/SlaveCommandGateway.java` (master-only): `ConcurrentHashMap<String, CompletableFuture<CommandReplyDto>>` keyed by a generated `correlationId`. `sendCommandAndAwaitReply(locationId, command, timeout)` sends a `CommandEnvelope(correlationId, commandType, payload)` to the slave's command destination and returns a future that times out (default ~8-10s, `location.command-timeout-ms`) into a new `LocationOfflineException` if the slave never replies — mapped by a new handler in the existing [GlobalExceptionHandler.java](src/main/java/com/djt/jukeanator_engine/domain/common/controller/GlobalExceptionHandler.java) to a clean 503 `ApiError`, so an unreachable location fails clearly rather than hanging. The slave executes the command against its own **local, unmodified** `SongQueueService`/`SongPlayerService` and replies via `/app/location-command-reply`, which completes the correlated future.

**Per-location service resolution on master** — a lazy registry, not per-location Spring beans (locations are provisioned dynamically at runtime, incompatible with startup-fixed beans): new `service/LocationServiceRegistry.java` with `computeIfAbsent`-populated maps of `SongQueueServiceLocationProxy`/`SongLibraryServiceLocationProxy` per locationId. Important distinction: **song-library reads don't need the live connection at all** — they're served straight from Phase 1's synced JSON snapshot on master's own disk. Only song-queue/song-player *mutations* (which are inherently live, slave-owned state) go through `SlaveCommandGateway`.

**Controller refactor — "grow, don't mutate"**: locationId as a path segment (`/api/locations/{locationId}/song-queue/**`, consistent with existing nested-resource style like `/api/song-library/genres/{genreId}/albums`), via **brand-new, additive** controllers (`LocationScopedSongQueueController`, `LocationScopedSongLibraryController`, `LocationScopedSongPlayerController`) that resolve their service from `LocationServiceRegistry` — leaving `SongQueueController`/`SongLibraryController`/`SongPlayerController` **completely untouched**, so standalone-mode risk stays zero. `SecurityConfig.java` needs a parallel sibling rule for every existing rule category, scoped to `/api/locations/*/...`.

Slave side: new `client/SlaveConnectionManager.java` (`@ConditionalOnProperty(app.mode=slave)`) opens/maintains the outbound STOMP client connection to `{master-instance-url}/ws-slave` with reconnect-with-backoff, forwards received commands to the slave's existing local services, and forwards the slave's existing domain events outward via new `@EventListener`s — zero changes to the actual `SongQueueServiceImpl`/`SongPlayerServiceImpl` business logic.

## Phase 3 — Credit ledger + location-tagged charging

New append-only ledger in `domain/user/`: `model/CreditTransactionEntity.java` (`userEmail`, `locationId` nullable, signed `amount`, `type` enum, `timestamp`, optional song reference, `resultingBalance`), stored as its **own** aggregate (`CreditLedgerRootEntity`/`CreditLedgerRepository`, NOT appended onto `UserRootEntity` — avoids forcing a full user-root rewrite on every single credit spend, since `storeAggregateRoot()` already does a full rewrite per mutation today). Recommend actually implementing `CreditLedgerRepositoryPostgresImpl` (not stubbing) since per-location, per-date-range accounting queries are a real near-term need for bar-owner reporting — note Flyway is a pom dependency but **no migrations exist anywhere in the repo today**, so this would need a first baseline migration created from scratch.

`UserService.chargeCreditsForQueueAction` gets a new 3-arg overload `(email, priority, locationId)`; the existing 2-arg call site in `SongQueueController` is untouched (delegates to the new overload with `locationId=null`). Only the new `LocationScopedSongQueueController` from Phase 2 calls the 3-arg version. `UserServiceImpl.deductCredits()` gains one more side effect: append to the ledger alongside its existing balance mutation and `UserCreditsChangedEvent` publish.

Since the bill-acceptor/local walk-up flow stays untouched (per the user's decision), Phase 3 is scoped purely to web/mobile-originated, master-owned spend — no Swing/JFC UI changes needed. New admin/bar-owner-facing endpoint: `GET /api/locations/{locationId}/credit-ledger?from=&to=`, admin-role-gated.

**Refinement worth flagging to the user before building this phase**: Item 5 of the original design doc asks for accounting "at what location" a credit was used, for the bar owner — if bar-owner reporting should reflect *all* revenue at a location (not just web/mobile), consider also logging local bill-acceptor-funded plays into this same ledger as an informational `CASH_LOCAL_PLAY` transaction type (zero effect on any master balance, purely for the bar owner's combined reporting). This wasn't part of the original ask and would need the Swing UI's `CreditManager`/`JukeANatorFrame` to report play events to the local `SongQueueService`, which already fires `SongAddedToQueueEvent` — worth a quick confirmation with the user when Phase 3 starts, not blocking Phases 1-2.

## Phase 4 — Web/mobile-facing polish

Polish `GET /api/locations` response shape based on the client's actual needs (e.g. distance-from-user sorting). Round out per-location topic parity in `LocationEventStompController` if the Phase-2 event surface needs more topics (genres/popularity, matching today's standalone `WebSocketEventBroadcaster` coverage). The web/mobile client repository itself (replacing the static "Rock on Third" dropdown, subscribing to `/topic/location/{locationId}/**`) is a separate, out-of-scope repo — track it as an external dependency; the backend change alone has no visible effect until the client consumes it.

## Other risks/gaps to keep in mind

1. `SecurityConfig.java`'s literal per-path rule lists need a deliberate, itemized pass at every phase — easy to miss one, and a missed public-read rule breaks a feature (fails closed) rather than exposing one.
2. `LocalSecurityContextConfigurer` (the global `LOCAL` identity) is gated on `app.ui-enabled=true` — master must always run headless (`ui-enabled=false`); add a fail-fast check (`mode=master` + `ui-enabled=true` should error at startup) in a small new `AppModeValidator`.
3. No Flyway migrations exist anywhere despite the pom dependency — first real Postgres impl (credit ledger) needs a baseline migration written from scratch.
4. Cross-slave ID collisions are a landmine beyond just storage — any future cross-location feature (e.g. a cross-location favorites list) must key on `(locationId, albumId, songId)`, never a bare int.
5. Reconnect/backoff policy and duplicate-session handling for `SlaveConnectionManager` (what happens if a slave reconnects before its old session times out) need to be nailed down as concrete decisions before writing that class, not discovered mid-implementation.

## Verification

- Standalone-mode regression check: after each phase, run the existing test suite (`mvn test`) and manually smoke-test a `standalone`-mode instance (default `application.yml`) to confirm no behavior change — song-library browse, queue add/reorder, credit charge, and the existing browser `/ws` STOMP flows must be identical to today.
- Phase 1: spin up one instance in `master` mode and one in `slave` mode locally (or two ports), provision a location, trigger a scan on the slave, confirm the metadata JSON + cover art land under master's per-location storage root, and confirm `GET /api/locations` reflects it.
- Phase 2: prototype `SlaveCommandGateway` end-to-end for a single command (`addSongToQueue`) before building the rest — verify a web request against `/api/locations/{id}/song-queue/addSong` round-trips through the slave's real local queue and back, and verify the timeout/`LocationOfflineException` path by killing the slave's connection mid-request.
- Phase 3: verify a location-tagged charge produces a ledger entry with the correct `locationId`, and that the existing non-location 2-arg charge path still records `locationId=null` without regressing existing behavior.







WORKFLOW
========
Here's the actual workflow, grounded in what's built and verified, from deployment to a patron's phone:

## 1. Deploy the master
One instance runs with `app.mode: master`, headless (`app.ui-enabled: false`), on a public host (e.g. `jukeanator.com`). It has no song library of its own — `location.storage-root` is where synced per-location data lands.

## 2. Provision a location
An admin calls `POST /api/locations` on master (admin JWT required) with a name + coordinates:
```json
{"name": "Rock on Third", "latitude": 40.0, "longitude": -105.0}
```
Master generates a `locationId` (UUID) and a plaintext API secret, returned **once**:
```json
{"locationId": "3aab41f9-...", "apiKey": "fid7q39K...", "name": "Rock on Third"}
```
Only the bcrypt hash of that secret is ever stored — this is the credential the physical jukebox will use to authenticate itself.

## 3. Flip the physical jukebox into slave mode
On the jukebox's own `application.yml` (or startup flags), set:
```yaml
app:
  mode: slave
  master-instance-url: https://jukeanator.com
  location-id: 3aab41f9-...
  location-api-key: fid7q39K...
```
Everything else about that machine — root path, song-player, bill acceptor, JFC/Swing UI — stays exactly as it is today. This is opt-in per location; a jukebox left at `app.mode: standalone` (or with no `app.mode` set) never talks to a master at all.

## 4. The slave connects and syncs
On startup, the slave opens a persistent STOMP connection to `{master-instance-url}/ws-slave`, authenticating with its location-id/api-key. Master marks it "online" the moment that connection lands (`GET /api/locations` now shows `"online": true` for it).

Whenever the slave scans its library (manually or on schedule), it automatically pushes a metadata snapshot (genres/artists/albums/songs, no audio) plus any new/changed cover art to master. Audio files never leave the building — only the slave plays audio.

## 5. The location shows up for web/mobile
The web/mobile app calls the now-public `GET /api/locations` on master and gets a real list (replacing the old static "Rock on Third" entry) with live online/offline status.

## 6. A patron picks the location and browses
Once they pick "Rock on Third," every subsequent library call goes to `/api/locations/{locationId}/song-library/...` on master. These reads are served straight from that location's last-synced snapshot on master's own disk — no round trip to the slave, so browsing is fast even over a flaky connection.

## 7. Patron buys credits and queues a song
Credit purchase/balance is entirely master-side (`/api/users/*`). Adding a song hits `POST /api/locations/{locationId}/song-queue/addSong` on master, which:
- pushes a live command down the persistent connection to that specific slave
- the slave executes it against its own real local queue (the same queue the JFC touchscreen uses) and replies
- master charges the patron's credits and writes a ledger entry tagged with that `locationId`
- the song is now genuinely queued at the physical jukebox

If the slave's connection is down at that moment, the patron gets an immediate, clean "location offline" error — no hanging.

## 8. Bar-owner accounting
An admin can pull `GET /api/locations/{locationId}/credit-ledger?from=...&to=...` to see every credit spend tied to that location — user, song, amount, resulting balance.

## The offline guarantee, at every step
If master is unreachable — at slave startup or any time later — the physical jukebox's touchscreen, bill acceptor, local queue, and player keep working exactly as they always have. The master relationship only affects *remote* web/mobile access to that location; it's never a dependency for the location running on its own.