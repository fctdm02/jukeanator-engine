# Financial ledger: entity-model refactor + slave→master mirroring

Status: Implemented.

## Context

The original ask was to extend the master/slave integration tests to cover the financial ledger:
local cash, local credit-card, and mobile transactions. Research found mobile transactions already
behave correctly (master-only, correctly location-tagged), but no slave→master mirroring exists at
all for local cash/credit-card — each instance's ledger is a JVM-local singleton, never seen by
master. The decision was to build that mirroring for real, not just document the gap.

Before implementing, a review of the first draft of this plan found its data model "incomplete and
misleading": it conflated two genuinely different kinds of transaction — **a user obtaining song
credits with real money** (financial) vs. **a user spending already-owned song credits to queue a
song** (song-play/usage) — and asked for two structural changes before mirroring is built on top.
Both are incorporated below; the original mirroring feature is still in scope, now built against
the corrected model.

## Design decisions (judgment calls made explicit)

1. **`CreditTransactionEntryDto`** (the nested-under-`UserDto` JSON persistence shape, distinct
   from the API-facing `CreditTransactionDto`) is renamed to `UserSongCreditUsageEntryDto` too,
   for the same disambiguation reason as the other renames below, even though it wasn't named
   explicitly in the request.
2. **`UserAddFundsTransactionEntity` captures the Braintree reconciliation fields** —
   `packageId`, `creditsAwarded`, `bonusCredits`, `amountUsd`, `paymentSource`,
   `paymentTransactionId` (Braintree's own transaction id), `timestamp`, `resultingBalance`. This
   is more than the bare minimum ("credits were added"), but the goal is a "rock solid" ledger,
   and the payment-gateway transaction id is exactly what makes a disputed charge or a support
   ticket reconcilable against Braintree's own dashboard later. No `locationId` — Add Funds
   credits aren't location-coupled; a user can spend them at any location.
3. **`AbstractLocationTransactionEntity` uses real JPA `@Inheritance(strategy = SINGLE_TABLE)`**
   with a `transaction_type` discriminator column. This is the first use of that pattern in the
   codebase (the only other `@Inheritance` use at the time, on `BackgroundMusicSongEntity`, was
   `TABLE_PER_CLASS` — no shared table, no discriminator; it has since been moved to the same
   `SINGLE_TABLE` pattern, in `song_background_music` with a `type` discriminator of `REGULAR` /
   `SMART` — and `song_library`'s multi-type-
   single-table needs are handled by an entirely separate flat-mapping-class pattern, not JPA
   inheritance, for reasons specific to its tree structure). Standard single-table inheritance is
   the right fit here because `LocalCashTransactionEntity`/`LocalCreditTransactionEntity` are flat
   records, not a tree — `song_library`'s heavier machinery would be solving a problem that
   doesn't exist for these two.
4. **Table-name singularization is scoped to only the tables this change creates or renames**
   (`user_add_funds_transaction`, `user_song_credit_usage`, `location_transaction`) — not a
   sweep renaming every existing plural table (`local_cash_transactions`, `song_queue_entries` (since renamed to `song_queue`),
   `background_music_songs` (since consolidated into `song_background_music`), etc.), which would be a large, separately-scoped, higher-risk change.
5. **Pre-production schema convention is reused**: like the earlier "consolidate 17 migrations
   into V1" commit, there's no live production data to preserve, so this shipped first as its own
   `V2` migration dropping `local_cash_transactions`/`local_credit_transactions`/
   `mobile_transactions` and creating their replacements directly, rather than writing
   data-migrating `ALTER`/`INSERT SELECT` statements. That `V2` was subsequently folded back into
   the `V1` baseline (same rationale: no production data means no need to preserve migration
   history as separate steps), so the schema now reflects Parts A and B as part of a single
   `V1__init_schema.sql`.

## Part A — User transaction model refactor

**The core split**: today, `UserServiceImpl.addFunds()` calls a private `addCredits(...)` helper
that appends a `CreditTransactionEntity` with `type=PURCHASE`; `handleSongAddedToQueueEvent`/
`chargeCreditsForQueueAction` call a private `deductCredits(...)` helper appending
`type=QUEUE_ADD`/`QUEUE_ACTION` (`ADMIN_ADJUSTMENT` is declared but dead — no call site anywhere).
These become two genuinely separate entities:

- **`UserAddFundsTransactionEntity`** (new file, `domain/user/model/`) — the Add-Funds/Braintree
  event. `addCredits(...)` (renamed `recordAddFundsTransaction` — private, zero external callers)
  now appends this instead of a `CreditTransactionEntity`. Table `user_add_funds_transaction`,
  same `AbstractPersistentEntity` base (so `version`/`dateAdded`/`dateUpdated` come for free,
  exactly like every other entity) plus a `@ManyToOne` back-ref to `UserEntity`, mirroring
  `CreditTransactionEntity`'s existing shape.
- **`UserSongCreditUsageEntity`** (renamed from `CreditTransactionEntity`) — `deductCredits(...)`
  keeps building this, now typed `UserSongCreditUsageType` (renamed from `CreditTransactionType`,
  `PURCHASE` removed — its only call site is gone since Add Funds no longer uses this type at
  all). Table `mobile_transactions` → `user_song_credit_usage`.
- **`UserEntity`**: `transactions` (`Set<CreditTransactionEntity>`) → `userSongCreditUsages`
  (`Set<UserSongCreditUsageEntity>`), same `@OneToMany(mappedBy="user", cascade=ALL,
  orphanRemoval=true, fetch=EAGER)` mapping; new `userAddFundsTransactions`
  (`Set<UserAddFundsTransactionEntity>`) added alongside it, identically mapped.
- **`UserRepositoryJpaImpl.storeAggregateRoot`**: the existing placeholder-id-then-persist dance
  it already does for `transactions` (JPQL to find which transaction ids are already persisted,
  `persist()` on the rest after nulling their collection-size-based placeholder id) gets renamed
  in place for `userSongCreditUsages`, and the exact same pattern is duplicated for
  `userAddFundsTransactions`.
- **Filesystem round-trip**: `UserDto`/`UserMapper` get a new `userAddFundsTransactions` field/
  mapping methods alongside the renamed `userSongCreditUsages` ones, following
  `CreditTransactionEntryDto`'s existing shape (renamed `UserSongCreditUsageEntryDto`) as the
  template for a new sibling `UserAddFundsTransactionEntryDto`.
- **API-facing DTO**: `CreditTransactionDto` → `UserSongCreditUsageDto`.
  `getCreditLedgerForLocation` (interface + impl, unchanged method name/endpoint) now returns this
  renamed type, built from `userSongCreditUsages` instead of `transactions`.
- **Rename footprint**: `FinancialLedgerServiceImpl` (`computeMobileTotal`'s import + usage),
  `LocationController` (`getCreditLedger` endpoint's import + return type), `UserService`/
  `UserServiceImpl`, `UserEntity`, `UserMapper`, `UserRepositoryJpaImpl`. Test files needing the
  same rename: `FinancialLedgerServiceTest.java`, `LocationControllerTest.java`,
  `UserServiceTest.java`. Swing UI has zero references — no UI changes needed.
- Behavior-preserving check: `computeMobileTotal` already filters to `t.amount() < 0` (spends
  only), so `PURCHASE` rows (positive amount) were already excluded from the split-period mobile
  total — removing the type doesn't change that computation's output, only cleans up its input
  type.

## Part B — Local cash/credit-card entity merge

- **`AbstractLocationTransactionEntity`** (new file, `domain/financialledger/model/`) — the common
  base: `amountDollars`, `timestamp`, `locationId`, plus `sourceTransactionId` (needed by Part C's
  mirroring/idempotency below — lives on the shared base since both cash and card get mirrored the
  same way). Declared `@Entity @Table(name = "location_transaction")
  @Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name =
  "transaction_type")`, extending `AbstractPersistentEntity`.
- `LocalCashTransactionEntity`/`LocalCreditTransactionEntity` become thin subclasses —
  `@DiscriminatorValue("CASH")`/`@DiscriminatorValue("CREDIT_CARD")`, constructors only, no
  duplicated fields.
- **`FinancialLedgerRootEntity`** keeps its existing two-accessor shape
  (`getLocalCashTransactions()`/`getLocalCreditCardTransactionsSince()` etc.) — minimal-risk
  choice: `FinancialLedgerServiceImpl.computeTotals()` keeps iterating them exactly as it does
  today, so the merge is invisible above the repository layer.
- **`FinancialLedgerRepositoryJpaImpl`**: the existing `select ... from LocalCashTransactionEntity`
  / `from LocalCreditTransactionEntity` JPQL queries need no change — under `SINGLE_TABLE`
  inheritance, JPA automatically scopes a query against a concrete subtype to that subtype's
  discriminator value. The two native insert methods (`insertNewCashTransaction`/
  `insertNewCreditCardTransaction`), byte-for-byte identical apart from table/column literals,
  collapse into one `insertNewLocationTransaction(AbstractLocationTransactionEntity, String
  discriminatorValue)` helper, called twice.
- The `V1` baseline creates `location_transaction` directly (singular; `id` PK, `transaction_type`,
  `amount_dollars`, `timestamp`, `location_id`, `source_transaction_id`, audit columns, `CREATE
  INDEX ix_location_transaction_location ON location_transaction (location_id)` so a central DBA
  can filter by location) — no `local_cash_transactions`/`local_credit_transactions` tables ever
  exist in the baseline schema.
- Tests needing updates for the new table/discriminator:
  `FinancialLedgerRepositoryJpaImplTest.java`, `FinancialLedgerRepositoryFileSystemImplTest.java`.
- `AdminPanel.java`'s Financial Ledger table only ever touches `FinancialLedgerService`'s DTOs
  (`getAllPeriods()`), never the entity classes directly — no Swing UI changes needed.

## Part C — Slave→master mirroring, built on the refactored model

- Slave: `FinancialLedgerServiceImpl.recordLocalCashCredit`/`recordLocalCreditCardCredit` publish a
  new `LocalFinancialTransactionRecordedEvent` (kind, locationId, sourceTransactionId=the
  just-minted id, amountDollars, timestamp) after a successful local store. New
  `@ConditionalOnProperty(app.mode=slave)` listener `FinancialLedgerSyncService` (new file,
  modeled directly on `LibrarySyncService`: background daemon executor, catch-`Throwable`+log.warn,
  best-effort/no retry — same durability tradeoff `LibrarySyncService` already accepts) POSTs it to
  master via `RestClient`, same `location-id`/`location-api-key` headers.
- Master: new `FinancialLedgerSyncController` (`domain/financialledger/controller/`,
  `@ConditionalOnProperty(app.mode=master)`) exposes
  `POST /api/locations/{locationId}/financial-ledger/local-cash` and `.../local-credit-card`.
  `SecurityConfig` gets `/api/locations/*/financial-ledger/**` added to the existing
  `hasRole("LOCATION")` matcher, right beside `/api/locations/*/library-sync/**`.
  `FinancialLedgerService` gains `receiveLocalCashSync`/`receiveLocalCreditCardSync`, each
  re-verifying `locationId`+`apiKey` via an injected `LocationService.verifyApiKey` (same
  defense-in-depth double-check `LocationServiceImpl.requireValidLocation` already does — the
  security filter alone only proves *some* location's credentials were valid, not that they match
  *this* path's locationId), then idempotently inserting via a new
  `FinancialLedgerRootEntity.hasLocationTransactionFromSource(locationId, sourceTransactionId)`
  in-memory check (no new repository query methods needed — the root is already fully loaded).
- `FinancialLedgerConfig`'s service bean method gains `ApplicationEventPublisher`/`LocationService`
  params (both already exist as beans app-wide, nothing new to wire).
- New wire DTO `LocalTransactionSyncDto(sourceTransactionId, amountDollars, timestamp)`, distinct
  from the existing filesystem-persistence `LocalTransactionDto` (which gains the
  `sourceTransactionId` field too, for filesystem-repository parity).
- `docs/multi-tenant-mode.md` gets a short addendum — it currently states local cash/card is
  untouched by the multi-tenant feature; that line goes stale once this ships.

## Tests

- **`domain/financialledger/service/MasterSlaveFinancialLedgerIntegrationTest.java`** (new, live
  MySQL, `app.mode=master`, `RANDOM_PORT`+`TestRestTemplate`, same shape as the existing
  `MasterSlaveLibrarySyncIntegrationTest`): two locations provisioned; cash+card mirror POSTs over
  real HTTP land in `location_transaction` tagged by `location_id`+`transaction_type`, isolated per
  location; a retried POST (same `sourceTransactionId`) is a no-op (idempotency); wrong API key is
  rejected and persists nothing; a registered user's Add-Funds event lands in
  `user_add_funds_transaction` with no `location_id`; the same user's song-credit spend at two
  different locations lands in `user_song_credit_usage` correctly isolated per location via
  `getCreditLedgerForLocation`, proving Add Funds and location-scoped usage are now cleanly
  separated where before they were commingled under one ambiguous `CreditTransaction` concept.
- **`domain/financialledger/service/FinancialLedgerSyncServiceTest.java`** (new, mock-based unit
  test, mirrors `LocationServiceTest`'s style): verifies the slave-side event listener builds and
  sends the right HTTP request shape without a live master.
- Update the existing tests identified above (`FinancialLedgerServiceTest`, `LocationControllerTest`,
  `UserServiceTest`, `FinancialLedgerRepositoryJpaImplTest`,
  `FinancialLedgerRepositoryFileSystemImplTest`) for the renamed types/table.

## Verification

1. `./mvnw -q -o test-compile` after each meaningful chunk (the rename touches ~15 files; compiling
   incrementally catches missed references early).
2. `./mvnw -q -o test -Dtest=MasterSlaveFinancialLedgerIntegrationTest` against local MySQL, run
   twice in a row to confirm idempotent fixture naming.
3. `./mvnw -q -o test -Dtest=FinancialLedgerSyncServiceTest`.
4. `./mvnw -q -o test -Dtest=FinancialLedgerServiceTest,LocationControllerTest,UserServiceTest,FinancialLedgerRepositoryJpaImplTest,FinancialLedgerRepositoryFileSystemImplTest`
   to confirm the renamed-type updates didn't break existing coverage.
5. Full `./mvnw -q -o test` regression pass.
