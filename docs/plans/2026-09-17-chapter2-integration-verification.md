# Chapter 2 integration and XP boost verification — 2026-09-17

Scope: LG-1214 read-model checkpoint and LG-1204 scheduled increased XP. This
continues the operator-approved catalog and atomic purchase work on the fork.

## SPEAR evidence

- Spec: `2026-09-17-reward-read-model.md` records consistent SQL reads, unavailable
  semantics, disabled rollout and consumer behavior. `2026-09-17-xp-boost.md`
  records UTC boundaries, rounding, applicable sources, reload and fixed caps.
- Prove: new read-service, consumer and placeholder tests failed compilation on
  missing APIs before their implementations. The boost tests likewise failed on
  missing boost types and the shared service integration. A later regression
  produced an assertion failure when Chapter 2 level-up notifications still
  advertised legacy perks as granted; the gated path now returns no automatic
  perk grants while preserving ordinary level-up events.
- Engine: domain-owned immutable boost schedule and typed reward read states;
  application reward resolver and pre-reservation XP multiplier; SQL snapshot
  adapter, reloadable configuration and lazy dependency registration; gold,
  progression/home/member consumers, Java/Bedrock views and placeholders.
- Arch: layer checks verify domain independence and infrastructure direction.
  UI code renders the resolver's offers and does not calculate entitlement rules.
- Refine: service tests cover disabled storage access, ownership-derived effects,
  live configuration changes and unavailable/corrupt reads. SQL contracts cover
  restart and fresh ownership reads. XP contracts cover inclusive/exclusive
  boundaries, fractional rounding, source selection, invalid configuration,
  rejection ordering, overflow, partial final caps and durable duplicate handling.

## Final verification

```text
gradlew.bat test shadowJar mariaDbRewardTest -PmariaDbTestPort=33371 --console=plain
BUILD SUCCESSFUL in 1m 53s
test: 977 tests, 0 failures, 0 errors, 0 skipped
mariaDbRewardTest: 26 tests, 0 failures, 0 errors, 0 skipped
```

Counts come from the final JUnit XML reports. Full regression includes dependency
graph, layer and localization contracts. The first full run exposed two locale
contract failures from interpolated reward-status keys; explicit localized enum
mapping fixed them without relaxing the baseline. The final Shadow artifact is
`build/libs/LumaGuilds-2.1.0.jar`.

MariaDB 11.4.5 ran on disposable loopback port 33371, using a fresh randomly named
schema per test, following the setup in the atomic purchase verification. Its
26 contracts comprise eight ownership, thirteen purchase, three read-model and
two XP-boost/cap tests. Test schemas are cleaned up by their fixtures; the disposable
database process is shut down after verification.

## Operator configuration

Keep `progression.chapter_two_rewards_enabled: false` until migration, account
initialization and purchase actions are complete. Turning it on is not migration.
Missing state blocks benefit reads instead of silently granting legacy benefits.
The catalog displays all 30 offers but purchase actions are not enabled yet.

Increased XP is independently disabled by default. To schedule a period, configure
`progression.xp_boost.enabled`, `starts_at`, `ends_at` (ISO UTC instants) and
`multiplier` (finite and at least one). Optional `sources` contains exact
`ExperienceSource` names. Omitting it includes non-admin sources, including weekly
quests. The end is exclusive; caps remain fixed and accepted XP is rounded down
after multiplying the complete award. Invalid enabled configuration is rejected.

Reward placeholders share the read model:

- `%lumaguilds_guild_reward_state%`: disabled, unavailable or available.
- `%lumaguilds_guild_reward_bank_capacity%`, `guild_reward_home_capacity`,
  `guild_reward_member_capacity`: effective capacities.
- `guild_reward_home_cooldown_multiplier`, `guild_reward_withdrawal_fee_multiplier`,
  `guild_reward_ally_homes`: effective purchased benefits.
- `guild_reward_offer_<reward-id>`: LOCKED, AVAILABLE, PURCHASED, PERMANENT or
  NO_IMPROVEMENT. Unknown IDs and unavailable numeric/effect values are blank.

All names above use the `%lumaguilds_...%` wrapper. Existing progression placeholders
retain their previous names and semantics.

## Remaining work

LG-1214 remains open for purchase actions, migration readiness and live UI testing.
Chapter lifecycle/migration, paid home activation, bounded prestige, seasonal Elo
and creation cooldown remain their own Chapter 2 tasks. No production database or
server was changed, and mocked UI/local database checks are not live-client proof.
