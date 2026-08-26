# LumaGuilds — Implementation Guide

## Layer Dependency Rules

Layers, from most stable to most volatile:

- **domain** — pure guild/claim/bank/war/rank models, invariants, and ports. Depends on nothing outside `net.lumalyte.lg.domain` and the Kotlin stdlib.
- **application** — use cases / application services orchestrating domain objects. Depends on `domain` only.
- **infrastructure** — Bukkit adapters, persistence (MariaDB/IDB), external integrations (Nexo, RoseChat, LiteBans, Geyser/Floodgate, PlaceholderAPI, Vault, CombatLogX). Depends on `application` and `domain`.

Precedence: **domain <- application <- infrastructure**. Never the reverse. Enforcement: `src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt` (Konsist).

Packages outside the three layers (`api/`, `common/`, `config/`, `di/`, `integrations/`, `interaction/`, `utils/`) are not asserted by LayerRulesTest; `interaction/` (commands + menus) may depend on any of the three layers plus `common/`/`utils/`.

## Forbidden Domain Annotations

```yaml
forbidden:
  - org.bukkit
  - org.koin
  - co.aikar
  - net.kyori
```

> **Status: executable contract.** `LayerRulesTest` enforces both layer dependencies (domain ← application ← infrastructure) and the external-package prefixes listed above. The remaining violations are removed by REQ-045 / LG-1001.

The `domain/**` package must stay free of framework and server annotations (no Bukkit/Spigot imports, no Koin annotations, no ACF annotations, no Adventure types). When a domain model needs a port to the server, define it in `domain` and implement it in `infrastructure`.

### Public Guild Event Migration

Public guild events remain synchronous, non-cancellable Bukkit events with unchanged constructors and payload properties. External plugins must replace `net.lumalyte.lg.domain.events` imports with `net.lumalyte.lg.api.events` and recompile.

| Old package | New package |
|---|---|
| `net.lumalyte.lg.domain.events.GuildBankDepositEvent` | `net.lumalyte.lg.api.events.GuildBankDepositEvent` |
| `net.lumalyte.lg.domain.events.GuildBannerChangedEvent` | `net.lumalyte.lg.api.events.GuildBannerChangedEvent` |
| `net.lumalyte.lg.domain.events.GuildBannerSetEvent` | `net.lumalyte.lg.api.events.GuildBannerSetEvent` |
| `net.lumalyte.lg.domain.events.GuildCreatedEvent` | `net.lumalyte.lg.api.events.GuildCreatedEvent` |
| `net.lumalyte.lg.domain.events.GuildDisbandedEvent` | `net.lumalyte.lg.api.events.GuildDisbandedEvent` |
| `net.lumalyte.lg.domain.events.GuildHomeSetEvent` | `net.lumalyte.lg.api.events.GuildHomeSetEvent` |
| `net.lumalyte.lg.domain.events.GuildLeaderboardRankChangeEvent` | `net.lumalyte.lg.api.events.GuildLeaderboardRankChangeEvent` |
| `net.lumalyte.lg.domain.events.GuildLevelUpEvent` | `net.lumalyte.lg.api.events.GuildLevelUpEvent` |
| `net.lumalyte.lg.domain.events.GuildMemberJoinEvent` | `net.lumalyte.lg.api.events.GuildMemberJoinEvent` |
| `net.lumalyte.lg.domain.events.GuildMemberRemovedEvent` | `net.lumalyte.lg.api.events.GuildMemberRemovedEvent` |
| `net.lumalyte.lg.domain.events.GuildOwnershipTransferEvent` | `net.lumalyte.lg.api.events.GuildOwnershipTransferEvent` |
| `net.lumalyte.lg.domain.events.GuildRelationChangeEvent` | `net.lumalyte.lg.api.events.GuildRelationChangeEvent` |
| `net.lumalyte.lg.domain.events.GuildTrackingChangedEvent` | `net.lumalyte.lg.api.events.GuildTrackingChangedEvent` |
| `net.lumalyte.lg.domain.events.GuildVaultPlacedEvent` | `net.lumalyte.lg.api.events.GuildVaultPlacedEvent` |
| `net.lumalyte.lg.domain.events.GuildWarDeclaredEvent` | `net.lumalyte.lg.api.events.GuildWarDeclaredEvent` |
| `net.lumalyte.lg.domain.events.GuildWarEndEvent` | `net.lumalyte.lg.api.events.GuildWarEndEvent` |
| `net.lumalyte.lg.domain.events.GuildWarKillEvent` | `net.lumalyte.lg.api.events.GuildWarKillEvent` |

The audited migration moved 17 Bukkit events to `api.events` and three Bukkit-backed state types out of `domain.entities` into the infrastructure-owned vault subsystem. `LayerRulesTest` enforces both layer direction and the documented external forbidden-prefix list.

## Authoring Conventions

- EARS requirements live in `docs/requirements.md`; tasks in `docs/tasks.md`; SPEAR state in `.claude/spear-state.json` (gitignored).
- Every task carries exactly one tag (`TDD`/`DOC`/`INFRA`), a `References:` line, and an `Evidence:` block filled with real source citations during execution.
- TDD tasks run the full cycle: spec → prove (failing test) → engine (min impl) → arch (layer check) → refine (green + close).

## Weekly Guild Quests (PR-16)

The domain owns typed quest definitions, conditions, provenance policies, validation results, and pure generation rules. The application layer owns the active-week lifecycle, progress evaluation, atomic claims, completion bonuses, and leaderboard payout orchestration through repository and Guild EXP ports. Infrastructure translates Bukkit/domain events into quest progress facts, persists active sets/progress/block provenance in SQLite, schedules reset catch-up, and supplies PlaceholderAPI adapters. Interaction renders the existing ChestGUI menu without mutating state except through the claim use case.

One `WeeklyQuestSet` is shared server-wide for a stable reset-period ID. `GuildQuestProgress` is keyed by `(week_id, quest_id, guild_id)`. Location metadata never becomes an implicit condition: it is consulted only when validating an explicitly generated location condition. Reward flags and reset processing are persisted/idempotent; leaderboard payouts run before expired progress is cleared.
