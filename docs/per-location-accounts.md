# Per-Location Accounts: Credits, Song Plays, Playlists and Mobile Revenue

Status: Implemented (schema migration `V2__per_location_accounts.sql`).

This document describes the refactor that ties a Web/Mobile patron's credits, song plays and playlists to one location, and that records every Add Funds purchase in that location's financial ledger. It supersedes the parts of [financial-ledger-refactor-and-sync.md](financial-ledger-refactor-and-sync.md), [pricing.md](pricing.md) and [WebPaymentsWithBraintree.md](WebPaymentsWithBraintree.md) that describe one global credit balance or value mobile revenue from credits spent.

## Why

The Web/Mobile UI was first modelled on AMI: one credit balance, one play history and one set of playlists per user, usable at every location. That model does not fit JukeANator:

- **There is no central song library.** Each location plays its own licensed copies, and a song is identified only by `(locationId, albumId, songId)`. A playlist or a recent play built at one bar means nothing at another.
- **App store review.** Apple and Google exempt payments for physical services from in-app purchase (Apple guideline 3.1.3(e)). Credits that buy song plays on one physical jukebox at one venue fit that exemption far more clearly than a balance that works anywhere, which a reviewer may treat as virtual currency.
- **Bar-owner accounting.** A purchase made for a location is that location's revenue, recorded once at the moment it is paid, exactly like a swipe on the jukebox's own card reader. Valuing mobile revenue from credits spent counted bonus credits as money and deferred the revenue until the songs were played.

## What a patron sees

- **The selected location is remembered** in the browser (`localStorage`), so the balance and Add Funds always refer to it.
- **Add Funds needs a location.** With none selected, the tab reads "Select a location to add funds. Credits are bought for one location and can be used only there." With one selected, a notice above the packages reads "Credits for *Location*. They can be used only at this location.", and the packages listed are that location's own.
- **The header balance is the balance at the selected location.** Switching location reloads the balance, the home page (recent plays and favorites), and the playlists.
- **My Account** shows the balance at the selected location, plus a line for every other location where the patron still has credits.
- **The receipt email** names the location ("these credits can be used only at this location") and shows the new balance at that location.
- **Live updates** (`/user/queue/credits` and `/user/queue/recent-plays`) now carry a `locationId`. The page ignores updates for any location other than the selected one.

A new location account starts with 0 credits and an empty My Favorites playlist. It is created the first time the signed-in patron opens that location's home page, buys credits, plays a song, or uses a playlist there.

## Object model

### `UserLocationAccountEntity` (new)

The `(user, location)` association, in `domain/user/model`, table `user_location_account`. It holds everything that used to hang directly off the user:

- `numCredits`: the balance, usable only at this location.
- `songPlayHistory` (`user_song_play_history`), most recent first.
- `playlists` (`user_playlist`), including this location's My Favorites. Playlist names are unique per account.
- `songCreditUsages` (`user_song_credit_usage`): credits spent here.
- `addFundsTransactions` (`user_add_funds_transaction`): purchases made for this location. `resultingBalance` is the balance at this location.

Every song or ledger entry added to an account must carry the account's own location id. Anything else is rejected with `IllegalArgumentException`, so a song from one bar can never land in another bar's history or playlist.

### `UserEntity`

The user remains the aggregate root inside `UserRootEntity`. It no longer has `numCredits`, a play history, playlists or ledgers. Instead it has `locationAccounts` and these methods:

- `getLocationAccountNullIfNotExists(locationId)`
- `getOrCreateLocationAccount(locationId)`
- `getNumCredits(locationId)`: 0 where the user has no account.
- `changeLocationId(old, new)`: re-tags an account and its children when master corrects a slave's location id.

Search history stays on the user, because it is not tied to a location's songs. Closing an account (`UserRootEntity.closeUser`) clears the history and playlists in every location account but keeps every balance-affecting record.

### Re-parented children

- `PlaylistEntity` belongs to a `UserLocationAccountEntity` instead of a user. `getOwner()` and `getLocationId()` are derived from that account.
- `UserSongCreditUsageEntity` belongs to the location account. It keeps its own `locationId` for reporting and for the slave mirror.
- `UserAddFundsTransactionEntity` belongs to the location account and gains `locationId`.

### Per-location credit packages

- Each location sells its own Add Funds packages. On a slave, `add-funds.packages` now describes that slave's location. The slave sends the list to master with its pricing config (`LocationPricingConfigDto.creditPackages`), each time it connects to master. After changing the packages, restart the slave so master receives them.
- Master validates the list (`CreditPackageCatalog.validate`) and stores it in `location_credit_package` (`LocationCreditPackageEntity`; for a filesystem master, `JukeANator_LocationCreditPackages.json`). An invalid list is logged and ignored, and the location keeps its previous packages. A slave that sends no list at all (an older build) also leaves the stored packages unchanged.
- `PricingService.resolveCreditPackages(locationId)` decides what a location sells:
  - On master, the location's pushed packages. When a location has pushed none, or is unknown, master's own `add-funds.packages` are used.
  - On a standalone or slave instance, the instance's own packages.

### Mobile purchases in the financial ledger

- **New `LocalMobileTransactionEntity`**, discriminator `MOBILE_WEB`, in the existing single-table `location_transaction`, next to `CASH` and `CREDIT_CARD`. Besides the shared fields it records `userEmail`, `paymentReference` (the Braintree transaction id), `packageCode`, `creditsAwarded` and `bonusCredits`.
- **`amount` replaces `amountDollars`.** `AbstractLocationTransactionEntity.amount` is a `BigDecimal` (column `amount DECIMAL(12,2)`), so a package need not be priced in whole dollars. Cash and card still post 1.00 per bill or pulse, and `getAmountDollars()` remains for them.
- `FinancialLedgerRootEntity` has a fourth list, `localMobileTransactions`. The filesystem repository keeps it in its own file, `JukeANator_MobileTransactions.json`, so an existing ledger file loads unchanged and simply has no purchases yet.

## Add Funds, step by step (master)

`UserServiceImpl.addFunds(email, AddFundsRequest(locationId, packageId, paymentMethodNonce))`:

1. Resolve the location: the request's `locationId`, otherwise the instance's own location. Master has no location of its own, so there a request with no `locationId` is refused with **402** (`PaymentException`). On master the location must also exist.
2. Find the package among that location's packages, and check the nonce.
3. Charge the card through `PaymentGateway`.
4. Credit the patron's account at that location (creating it if needed), record the `UserAddFundsTransactionEntity`, and store the user root.
5. Record the purchase in the location's ledger: `FinancialLedgerService.recordMobilePurchase(...)` writes one `MOBILE_WEB` row for the full package price and stores the ledger root.
6. If step 4 or step 5 fails, the balance and the transaction are rolled back, a location account created by this purchase is removed again, and the charge is voided (`PaymentGateway.voidCharge`).
7. Broadcast the new balance (`UserCreditsChangedEvent`, with the location id), and publish `PurchaseCompletedEvent` for the receipt email.

The response (`AddFundsResponseDto`) includes `locationId` and `locationName`.

Spending is unchanged apart from its scope: `deductCredits`, the affordability checks and the queue charges all work on the account at the location where the song is queued, and an unaffordable add still returns **402**.

## Master and slave

- **Purchases, master to slave.** Recording a purchase publishes `MobilePurchaseRecordedEvent`. On master, `MobileTransactionSlaveNotifier` sends it to the location's slave as a `recordMobileTransaction` command over `/ws-slave`. The slave stores it through `FinancialLedgerService.receiveMobileTransaction`, with `sourceTransactionId` set to master's row id. That makes the mirror idempotent and keeps it out of the slave's own push to master.
- **Catch-up pull.** A push the slave missed is recovered by `GET /api/locations/{locationId}/financial-ledger/mobile-transactions?since=...`, which `FinancialLedgerSyncService` calls on every sweep before the credit-spend pull, using the same time window as that pull.
- **Credit spends stay mirrored, for audit only.** `location_mobile_credit_usage` is still filled on the slave, but it no longer affects any split total.
- **Location id corrections.** When master corrects a slave's location id, `LocationRepositoryJpaImpl.changeLocationId` also re-keys `location_credit_package`, `user_add_funds_transaction` and `user_location_account`.

## Split totals

A period's **Mobile** total is now the sum of the `MOBILE_WEB` purchases in that period at the location, on master and on the slave alike. This mirrors how card-reader revenue is counted. It replaces the earlier rule of credits spent divided by the location's `credits-per-dollar`.

For example, two $7 purchases at a bar make that bar's Mobile total $14.00, however many of the credits have been played so far. Credits bought for one bar can never be played at another, so no revenue crosses between locations.

**Known limit.** A purchase that reaches a slave only after the operator has finalized the period it falls in (the slave was offline) is stored, but it is not counted in any split, and the slave logs a warning. Master's `location_transaction` rows remain the authoritative record for reconciling such a case.

## Endpoints

On master, each of these requires `locationId`. A standalone or slave instance falls back to its own location when `locationId` is omitted.

- `GET /api/users/credit-packages?locationId=`
- `POST /api/users/add-funds`, body `{ locationId, packageId, paymentMethodNonce }`
- `GET /api/users/me`: `balances` is a list of `{ locationId, locationName, numCredits }`, replacing the single `numCredits`
- The home page, playlist and favorites endpoints under `/api/users/...` take `?locationId=`. The home page response gains `numCredits`, the balance at that location.

A missing location on master is refused with **400**, "Please select a location first."

## Database migration (V2)

`src/main/resources/db/migration/mysql/V2__per_location_accounts.sql`. V1 is not edited.

1. **Clears the test patron data.** It deletes all rows from `user_song_credit_usage`, `user_add_funds_transaction`, `user_playlist_song`, `user_playlist`, `user_song_play_history` and `location_mobile_credit_usage`. User accounts themselves, cash and card ledger rows, and split periods are kept. Production held only a few test accounts, whose balances were refunded or zeroed outside the app.
2. **Creates `user_location_account`**: `user_id`, `location_id` and `num_credits`, unique on `(user_id, location_id)`. `location_id` has no foreign key to `location`, because standalone and slave instances have no location row for themselves.
3. **Drops `user_account.num_credits`.**
4. **Re-parents the patron tables onto the account.** `user_playlist` (whose `owner` column is dropped, and which becomes unique on account and name), `user_song_play_history` (primary key: account and `play_order`), `user_song_credit_usage` and `user_add_funds_transaction` (which also gains `location_id`) all reference `user_location_account_id` instead of `user_id`.
5. **Changes `location_transaction`.** `amount_dollars` becomes `amount DECIMAL(12,2)`, with existing values copied. It gains `user_email`, `payment_reference`, `package_code`, `credits_awarded` and `bonus_credits`, and an index on `(location_id, timestamp)`.
6. **Creates `location_credit_package`**, unique on `(location_id, package_code)`, with a foreign key to `location` that follows id corrections (`ON UPDATE CASCADE`).

Before deploying to production, refund or void any real purchases on the test accounts in the Braintree Control Panel. The migration removes their records.

The filesystem (JSON) repositories need no conversion step. A user file from before this change, which has a global balance and no location accounts, cannot be read by the new mapper and is set aside like any other unreadable file. Purchases and per-location packages live in their own new files.

## Tests

- **Model:** `UserEntityTest`, `UserRootEntityTest`, `UserEntityPlaceholderIdentityTest`, `PlaylistEntityTest` and `UserMapperVerificationTest`. They check that balances, plays and playlists stay separate per location, that a song or ledger entry from another location is refused, and that the user file round-trips with its accounts.
- **Services and controllers:**
  - `UserServiceTest`: one full-price ledger row per purchase, a void when the ledger write fails, per-location packages and balances.
  - `UserControllerTest`.
  - `FinancialLedgerServiceTest`: purchase-based totals, plus recording, receiving, the catch-up pull and the purge.
  - `PricingServiceImplTest` (new).
  - `LocationServiceTest`: packages arriving with the pricing config.
- **Integration:**
  - `WebPatronPlayLifecycleTest`, rewritten for the purchase-based rule.
  - `MasterSlaveFinancialLedgerIntegrationTest`: `MOBILE_WEB` rows and the purchase catch-up pull.
  - The three `WebUi*LifecycleTest` classes, `MasterSlaveGeoFenceIntegrationTest` and `UserServiceJpaConcurrencyTest`.
- **Repositories:**
  - `LocationRepositoryJpaImplTest`: storing credit packages, and location id correction of the new tables.
  - `FinancialLedgerRepositoryJpaImplTest` and `FinancialLedgerRepositoryFileSystemImplTest`: purchases.
  - The two `UserRepositoryJpa*Test` classes: accounts and their children.

The database tests need the local MySQL test database with V2 applied.
