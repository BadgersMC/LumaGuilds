# Guild-shop XP delivery

## Spec (REQ-121)

WHEN an outside customer completes a paid EnthusiaMarket SELL purchase from a guild-owned stall, THE SYSTEM SHALL award that stall's owning guild one configurable XP award (default 5), bounded by a guild UTC-day cap (500), buyer/guild UTC-day cap (50), and buyer/guild cooldown (300 seconds) shared across shops. Guild members, free trades, BUY shops, barter, and failed or compensated purchases SHALL award zero. Quantity and price SHALL NOT multiply XP. BUY-shop resale is a separate trade, not a refund.

WHEN a completed sale is redelivered, THE SYSTEM SHALL atomically consume its durable ID once with its caps and progression update. Eligibility and prestige identity SHALL be captured before payment. Pending sales from a previous prestige run SHALL NOT award XP to a new run. Purchased rewards SHALL remain untouched. Permanent consumption records SHALL outlive the ordinary XP-history retention policy.

## Architecture / acceptance

Guilds owns policy, membership snapshot, progression identity, caps and idempotence. Market owns the completed-sale journal and replay. A JDK-only ServicesManager API avoids a dependency cycle. Policy is snapshotted before payment; retries retain that quote. Guilds reuses its existing SQL XP engine in the same transaction. Progression cache refresh and level events follow committed awards.

Vault and inventories have no shared SQL transaction or durable payment receipts. A crash between item/payment effects and the Market COMPLETED journal write is ambiguous. PREPARED records never replay automatically; staff must investigate the trade evidence. Completed journal records retry after transient errors or acknowledgement loss. If preparation is unavailable, the purchase is refused before any side effect, rather than silently losing its reward. Personal/free trades remain unaffected.

## SPEAR state

- Spec: approved user policy above; Guilds base a15b244, Market main 14351db and stacked API dependency 322933c (PRs #197/#198).
- Prove: new SQL/trade boundary tests exercise own membership at sale time, acknowledgement loss, retries, cooldown/day boundaries, caps, prestige, missing accounts, concurrent awards, history retention and injected write/rollback failures. The first full Guilds run exposed a zero-cap engine rejection and the missing icon-registry entry; both were fixed. Initial compilation attempts also needed the existing untracked RoseChat/CombatLogX API dependencies. Those setup errors are not behavioral proof or historical red/green evidence.
- Engine: durable quote/consumption tables, atomic guild/buyer caps and cooldown, shared existing XP engine, JDK-only API, generic-source bypass guard, refreshed progression/level caches and localized source presentation implemented.
- Arch: shared guild coordinator serializes against prestige, SQLite reserves a writer before reading; MariaDB resolves immutable sale identity before its transaction to avoid stale repeatable-read snapshots. Platform adapters remain in infrastructure. Failed rollback disposes the connection rather than restoring autocommit.
- Refine/local: clean Java 25 / Paper 26.2 `test shadowJar` passed 1,561 tests, zero failures/errors, four skips. Follow-up shop/config tests passed after removing an arbitrary cooldown ceiling; the actual companion-artifact classloader contract executed successfully in Market. Market has its own full-suite/Detekt evidence record.
- Hosted CI and review for the final published head: pending. Disposable loopback MariaDB 11.8.3 passed all seven native XP contracts, including concurrency, UTC caps, prestige and transactional rollback; this is local database proof, not staging/player acceptance. The earlier 2460158 GitHub build passed with only workflow deprecation annotations; Codacy was still running.
- Project-local EARS/state helpers are absent; this file and docs/tasks.md are the manual state/evidence record.
- Hosted CI, merged companion build, staging and player acceptance remain separate; no production changes authorized.


## Operator / release gates

Both companion PRs must be reviewed and merged. Market's current CI runtime pin is Guilds 3.0.17, which lacks this API. Its CI reports a warning and a skipped new runtime contract; that is not hosted proof of the XP integration. Update the released companion version and SHA-256 after the Guilds API is merged/released, then require the runtime contract to execute. Build clean merged commits through the canonical combined build/pins before any upload. Activate the new Guilds companion before the new Market build. An unavailable API refuses paid guild SELL purchases before side effects with an explicit retry message; personal, free, BUY and barter paths are unaffected.

Guilds settings are `progression.shop_xp.{enabled,xp_per_sale,guild_daily_cap,buyer_daily_cap,pair_cooldown_seconds}`. Zero award disables awards; zero cap awards nothing; negative or overflowing values fail explicitly. Quotes keep their captured policy across configuration changes. No XP boost multiplies shop awards.

For uncertain Market trades, inspect `guild_sale_xp_journal` by ID/shop/buyer and compare inventory/payment evidence. Only COMPLETED rows replay, ordered by sale time/ID, at most 100 every ten seconds. PREPARED means ambiguous/unconfirmed; ABORTED means known trade failure; ACKNOWLEDGED records the Guilds terminal outcome. Do not blindly change PREPARED to COMPLETED. Durable IDs, quote outcomes, caps and cooldowns survive restarts and XP audit-history cleanup. No user-facing refund or return was added.

Local unmerged review artifact: `build/libs/LumaGuilds-3.0.17-shop-xp-review.jar`; SHA-256 `de2472bd9201021a6f21fceb79180687a8977a4360db2359968b9d1e2b161d0f`. This is not a production artifact or deployment approval.

The optional native test target is fixed to loopback and a disposable test database. Gradle tracks its configured port and reruns configured native checks rather than reusing skipped or stale external-database results.

## Review refinement (7 October)

The SQL completion path is split into terminal-denial, award and buyer-reservation steps, retaining one transaction. Preparation takes one internal immutable sale identity. Internal-only implementation types remain internal; display names retain descriptive test output. Transaction rollback now also covers Errors, with a regression proving rollback on AssertionError and disposal after failed rollback. Clean Java 25/Paper 26.2 test + shadowJar passed 1,562 tests, zero failures/errors and four unrelated skips. All seven native MariaDB contracts executed on disposable loopback MariaDB 11.8.3. These results supersede the previous local count and review-artifact hash above.

Revised unmerged review artifact SHA-256: b09c321bd6e15be9db1f7363606c6a04bc33ad2318724d87684124be8fd9947d. Hosted checks for the revised head, the released companion pin and staging remain separate gates; production was unchanged.
