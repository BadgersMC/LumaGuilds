# Guild shop XP startup registration

## Spec

When Guilds enables after persistence and Koin initialization, publish the existing
GuildShopXpApi v2 provider through Bukkit ServicesManager, even when shop XP is
disabled. Market must still be able to prepare/complete non-awarding transactions.
Keep all receipt, payment, cap and exclusion semantics. Disable removes services.

## Prove and boundaries

Current canonical main: 283d75f1e1016d415aa5afdfac840252f0391846.
The deployed integrated source ee2064af contains GuildShopXpProvider but no startup
registration. Stall permission synchronization only exercises GuildLookup and does
not prove purchase or XP availability. Record a failing startup wiring regression
before implementation, then exercise real Bukkit service discovery and the real
provider with repository/progression boundaries controlled by the test.

## Engine and architecture

Use the existing provider and Koin repositories/services in the infrastructure
startup adapter. No new public API, dependency, schema, policy or UI changes.
Build a separate integrated candidate retaining ee2064af's dialogs and XP fixes.

## Refine and delivery

Project EARS/state helpers are absent; maintain this manual record and tasks.
Local tests, hosted checks, TEST activation and live purchases are distinct gates.
No production action or historical XP compensation is authorized by this record.

## Evidence

- Startup wiring test failed on main because onEnable did not publish the service.
  An initial missing-local-library compile attempt is not regression evidence.
- Real MockBukkit ServicesManager discovers v2 and removes it with its owning plugin.
- Actual released Market 1.0.53 gateway discovers that real provider and routes
  prepare, complete, duplicate complete and abort; progression refresh occurs once.
  Repository calls are controlled in this adapter test; existing SQL tests cover
  durable receipts, rollback, caps and exclusions. This is not a live shop purchase.
- Canonical Java 25 / Paper 26.2 check and shadowJar passed with MARKET_RUNTIME_JAR
  set, including both paired registration tests without skips. Optional external
  database profiles remain separately skipped.
- TEST activation and player purchase/balance/stock acceptance remain pending.
