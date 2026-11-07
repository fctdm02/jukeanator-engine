# Pricing: Cash, Card Reader and Web/Mobile UI

This document explains how a song is priced through each way of paying, why the prices are set the way they are, and what changed in the three-phase pricing cleanup.

## The pricing rule

A song should cost the least with cash at the jukebox, more with the jukebox's card reader, and the most through the Web/Mobile UI:

- **Cash** (bill acceptor) has no processing fees.
- **The card reader** (Nayax VPOS Touch) has a monthly service fee plus a fee on every transaction.
- **The Web/Mobile UI** goes through Braintree. Its standard US rate is about 2.59% plus $0.49 per transaction (confirm against your Braintree agreement). The fixed $0.49 is a large share of a small purchase: about 7% of a $7 package, but under 2% of a $28 one.

## Credits are not the same everywhere

A "credit" buys different amounts in different places, so compare **songs**, not credits:

- **At the jukebox**, a normal play costs **1 credit**.
- **On the Web/Mobile UI**, every cost is the jukebox cost multiplied by `user-interface.web-cost-multiplier` (default `2`), so a normal play costs **2 credits**.
- **Web credits work at every location.** They are bought on the master instance and are not tied to any one location. Each location's own `web-cost-multiplier` decides what a song costs there.

## Cost per normal song, with the defaults

**Jukebox, cash** (`credits-per-dollar: 3`, bonuses 3 at $5 and 10 at $10):
- $1 buys 3 songs, $0.33 each
- $5 buys 18 songs, $0.28 each
- $10 buys 40 songs, $0.25 each

**Jukebox, card reader** (`credit-card-cost-multiplier: 2`):
- $5 buys 9 songs, $0.56 each
- $10 buys 20 songs, $0.50 each
- $15 buys 29 songs, $0.52 each
- $20 buys 40 songs, $0.50 each

**Web/Mobile UI** (`add-funds.packages`, `web-cost-multiplier: 2`):
- $7 buys 14 credits: 7 songs, $1.00 each
- $14 buys 32 credits: 16 songs, $0.88 each
- $28 buys 72 credits: 36 songs, $0.78 each

Every web package costs more per song than every card-reader amount, and the card reader costs more than cash. The web premium is largest on the smallest package, where Braintree's fixed fee is the largest share of the price.

## Settings

All of these are in `application.yml`. On a jukebox, the running copy is `C:\kiosk\config\application.yml`. New settings are copied into that file automatically at startup (see `ExternalConfigMerger`), with the default values shown here.

- `user-interface.credits-per-dollar`, `five-dollar-bonus-credits`, `ten-dollar-bonus-credits`: the jukebox's cash pricing.
- `user-interface.credit-card-cost-multiplier` (new, default `2`): card-reader credits = cash credits ÷ this value, rounded up. Decimals are allowed, for example `1.5`. Jukebox only, never synced to master.
- `user-interface.web-cost-multiplier` (default `2`): Web/Mobile UI cost = jukebox cost × this value. Synced from each slave to master, per location.
- `user-interface.display-currency-for-cost`: now applies to the jukebox screen only. The Web/Mobile UI always shows credits.
- `add-funds.packages` (new): the Web/Mobile UI's credit packages. Set them on the instance that takes web payments (master, or standalone). One package per line:

```yaml
add-funds:
  packages:
    - { id: pkg-28, price-usd: 28.00, credits: 48, bonus-credits: 24, badge: Best Value }
    - { id: pkg-14, price-usd: 14.00, credits: 24, bonus-credits: 8 }
    - { id: pkg-7, price-usd: 7.00, credits: 12, bonus-credits: 2 }
```

When changing prices, keep two things true. The startup check (Phase 2) warns when either one breaks:
1. Each package's total (`credits + bonus-credits`) divides evenly by the web song cost (`web-cost-multiplier`).
2. Every package costs more per song than every card-reader amount.

---

## Phase 1: a separate card-reader multiplier

### Why
Card-reader credits used to be calculated as cash credits ÷ `web-cost-multiplier`. That tied two unrelated prices together: raising the web premium to 3 would also have made the card reader 50% more expensive, with no way to change one without the other.

### What changed
- **New setting** `user-interface.credit-card-cost-multiplier`:
  - A `BigDecimal` that defaults to `2`, so prices are unchanged.
  - Lives in `JukeANatorUserInterfaceProperties`.
  - Must be greater than 0. `CreditManager` refuses to start otherwise.
- **New class `domain/user/service/KioskCreditMath`.** It holds the jukebox's tier math (`addTierDollar`), cash credits, card credits, and the reader's price buttons (`CARD_READER_PRICE_DOLLARS` = 5, 10, 15, 20). It doesn't depend on Swing, so both `CreditManager` and the startup check use the same formulas.
- **`CreditManager`** now takes the card multiplier in place of `webCostMultiplier`, and delegates its tier math to `KioskCreditMath`. Card credits are rounded up with `BigDecimal` (`RoundingMode.CEILING`), so a decimal multiplier never suffers floating-point error.
- **`JukeANatorFrame`** passes the new setting to `CreditManager` and no longer reads `web-cost-multiplier` at all.
- **No database or master change.** Card credits exist only on the jukebox, and the ledger records card dollars, not credits.

### Example
With `credit-card-cost-multiplier: 1.5`, the card reader gives $5 = 12, $10 = 27, $15 = 39 and $20 = 54 songs.

---

## Phase 2: configurable web packages with even totals

### Why
- The packages were hard-coded in `UserServiceImpl.getCreditPackages()`, so changing a price meant a code change and a release.
- Two of them stranded a credit. $7 bought 13 credits and $14 bought 31, so with a song costing 2 credits, one credit was always left over that couldn't buy a normal play.

### What changed
- **New `domain/user/config/AddFundsProperties`** (`add-funds.packages`). Each entry has `id`, `price-usd`, `credits`, `bonus-credits` and an optional `badge`. With no packages configured, the built-in defaults apply.
- **New `domain/user/service/CreditPackageCatalog`:**
  - `DEFAULT_PACKAGES`, the new built-in list.
  - `validate()`, which stops startup on an empty list, or a package with a blank or duplicate id, a missing or zero price, zero credits, or negative bonus credits.
- **`UserServiceImpl`** now sells whatever `setCreditPackages()` was given. `AppConfig` sets it from `add-funds.packages` when the bean is created.
- **New package contents.** The total of each package is now even:
  - $7: 12 + 1 → **12 + 2** = 14 credits
  - $14: 24 + 7 → **24 + 8** = 32 credits
  - $28: 48 + 24 = 72 credits (unchanged)
- **Package ids are unchanged** (`pkg-7`, `pkg-14`, `pkg-28`). Each purchase record (`UserAddFundsTransactionEntity`) already stores the credits, bonus and price at the time of sale, so past purchases stay accurate when a package's contents change. Keeping the ids also means a browser that loaded the Add Funds screen before an update can still complete its purchase.
- **New startup check, `domain/user/service/PricingStartupCheck`** (an `ApplicationRunner` registered in `AppConfig`). It only logs and never stops startup:
  - It logs each package's price, credits, songs and price per song.
  - If card processing is enabled, it also logs each card-reader amount's songs and price per song.
  - It warns when a package's total doesn't divide evenly by the web song cost.
  - It warns when any web package costs no more per song than the most expensive card-reader amount.
  - It warns when `credit-card-cost-multiplier` is below 1, which would make the card reader cheaper than cash.
- **Why the check reads settings directly.** It uses this instance's own `user-interface.*` settings rather than calling `PricingService`, because it runs at startup before any user is signed in, and service calls require a signed-in principal (see `ServiceSecurityAspect`). On master it therefore checks against master's own `web-cost-multiplier`, not each location's synced value. Keep `web-cost-multiplier` the same at every location: since web credits work everywhere, no single set of package totals divides evenly at every location otherwise.
- **The YAML puts one package per line** (`- { id: ..., ... }`). `ExternalConfigMerger` treats every indented `key:` line as a setting, and the one-line form keeps it from misreading the list entries.

---

## Phase 3: credits, not dollars, on the Web/Mobile UI

### Why
- The Add Funds screen showed "48 Credits +24 BONUS" without saying that the package totals 72 credits.
- The web balance was converted to dollars at the jukebox's cash rate (credits ÷ `credits-per-dollar`), even though web credits buy songs at half that rate. Right after a $28 purchase, the balance read $24.00. With `display-currency-for-cost` on, web song costs were converted the same way: a 2-credit song showed as $0.67, although the patron paid $0.78–$1.00 for it.
- A dollar balance can also suggest that the credits have a cash value, for example that unused credits could be refunded.

### What changed
- **Add Funds packages** (`app.js`, `renderAddFundsContent`) are labelled with their total credits (credits + bonus):
  - large: **72 Credits**
  - the "+24 BONUS CREDITS" tag, kept for packages that have a bonus

  The card shows neither a song count nor a price per song. Priority plays and queue changes cost more credits than a normal play, so either figure would overstate what a package buys. The per-song figures (for normal plays) are still in the startup log (Phase 2), for the operator.
- **The balance** shows credits only, for the same reason (no song count):
  - header: "Credits: 72"
  - My Account: "72 Credits"
  - after a purchase, "New Balance" uses the same header text
- **Costs and shortfalls** on the Web/Mobile UI always show credits ("4cr", "ADD 2 CREDITS"). `display-currency-for-cost` no longer changes the web, only the jukebox screen.
- **The server no longer sends a dollar balance.** `balanceUsd` was removed from `UserProfileDto` (`GET /api/users/me`) and `AddFundsResponseDto` (`POST /api/users/add-funds`), along with the code in `UserServiceImpl` that calculated it.
- **The Nayax reader's price titles** stay in credits for the same reason (a priority play at the jukebox costs more than 1 credit): the startup log prints `9 CREDITS;20 CREDITS;29 CREDITS;40 CREDITS` to paste into Nayax Core's `Pulse Title 1-6`, and the suggested welcome message in [credit-card-reader.md](credit-card-reader.md) is `TAP AMOUNT FOR SONG CREDITS`.

---

## Not changed

- **What the bar owner is paid for web plays.** The financial ledger still values web credits spent at a location at that location's cash rate (credits ÷ `credits-per-dollar`), the kiosk-equivalent value of what was played there, whatever the patron paid for the credits.
- **The jukebox screen.** Its CARD line (`5$=9cr | 10$=20cr | 20$=40cr`) and its `display-currency-for-cost` behaviour are unchanged.

## Tests

- **`CreditManagerTest`:** the new multiplier, including a decimal multiplier and rejecting zero.
- **New `CreditPackageCatalogTest`, `AddFundsPropertiesTest` and `PricingStartupCheckTest`.**
- **`UserServiceTest`:** configured packages and validation.
- **Web/Mobile and ledger tests updated for the new package totals and the removed `balanceUsd`:**
  - `WebPatronPlayLifecycleTest`. With 14 credits, the patron's first package now buys one more play, so the bar's mobile revenue in that scenario is $6.67 instead of $6.00.
  - `WebUiPatronLifecycleTest`
  - `WebUiMasterPatronLifecycleTest`
  - `WebUiMasterConnectedSlaveLifecycleTest`
  - `MasterSlaveFinancialLedgerIntegrationTest`
  - `UserControllerTest`
