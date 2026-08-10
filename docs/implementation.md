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
forbidden: []
```

> **Status: target-state contract, not yet fully enforced.** `LayerRulesTest` enforces layer dependencies (domain ← application ← infrastructure), but Konsist's `dependsOnNothing()` only checks other *declared layers* — external packages (`org.bukkit`, `org.koin`, `co.aikar`, `net.kyori`) are not flagged. The domain currently imports `org.bukkit.event.Event` in 21 files (38 imports, mostly `domain/events/*`). Decoupling is tracked as REQ-045 / LG-1001; once merged, the forbidden list becomes executable and LayerRulesTest gains an external-package assertion.

The `domain/**` package must stay free of framework and server annotations (no Bukkit/Spigot imports, no Koin annotations, no ACF annotations, no Adventure types). When a domain model needs a port to the server, define it in `domain` and implement it in `infrastructure`.

## Authoring Conventions

- EARS requirements live in `docs/requirements.md`; tasks in `docs/tasks.md`; SPEAR state in `.claude/spear-state.json` (gitignored).
- Every task carries exactly one tag (`TDD`/`DOC`/`INFRA`), a `References:` line, and an `Evidence:` block filled with real source citations during execution.
- TDD tasks run the full cycle: spec → prove (failing test) → engine (min impl) → arch (layer check) → refine (green + close).
