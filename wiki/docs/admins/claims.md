---
title: Claims (built-in)
audience: admin
topic: claims
summary: LumaGuilds includes a full claims subsystem (forked from Bell Claims). Toggle it on/off and map guild ranks to claim permissions.
keywords: [claims, bell claims, fork, built-in, permissions, guild ranks]
related: [installation, override, permissions]
updated: 2026-05-14
---

# Claims (built-in)

LumaGuilds is a fork of [Bell Claims](https://github.com/Mizarc/bell-claims) with the guild system layered on top. The claims subsystem ships **inside** the plugin — there is no separate claims plugin to install or integrate with. You can run LumaGuilds with claims enabled (guilds + claims, the default) or disabled (guilds only).

## How it works

LumaGuilds reads `claims_enabled` from `config.yml` at startup (default: `true`). The flag controls whether the claims subsystem is wired up at all:

- **`claims_enabled: true`** — Every claim-related command, listener, GUI, and database table is registered. Players can run `/claim`, claim chunks, manage trust, etc. Guild ranks map to in-claim permissions (see below).
- **`claims_enabled: false`** — None of the claims surface is registered. Players cannot use claim commands; no claim listeners fire. LumaGuilds runs as a pure guilds plugin. Use this mode if you're running a different claims/protection plugin on the server.

The flag is read **once at startup**. Toggling it requires a restart.

## Guild ranks and claim permissions

When `claims_enabled: true`, guild membership grants automatic claim permissions on claims owned by guild members. A player's *rank* in a guild that owns a claim determines what they can do inside it, without needing to be explicitly trusted on each claim.

The default mapping (configurable in `config.yml` under `team_role_permissions`):

| Rank | Permissions |
|------|-------------|
| Owner | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, DETONATE, EVENT, SLEEP, VIEW |
| Co-Owner | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, DETONATE, EVENT, SLEEP, VIEW |
| Admin | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, VIEW |
| Mod | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, VIEW |
| Member | HARVEST, CONTAINER, VIEW |
| (non-member) | VIEW only |

These are resolved at runtime by `GuildRolePermissionResolver`, which caches per-player results and invalidates the cache when a player's rank changes (with a short delay — see Gotchas).

## Customizing the rank-to-permission map

Edit `team_role_permissions` in `plugins/LumaGuilds/config.yml`:

```yaml
team_role_permissions:
  # Cache invalidation delay in seconds when role changes occur.
  cache_invalidation_delay_seconds: 5

  # Default permissions for ranks that don't have explicit mappings below.
  default_permissions:
    - "VIEW"

  # Per-rank permission grants.
  roles:
    Owner:
      - "BUILD"
      - "HARVEST"
      - "CONTAINER"
      # ... see installation.md for the complete default list

    Member:
      - "HARVEST"
      - "VIEW"    # Remove CONTAINER if you don't want members opening guild chests.
```

Restart the server to apply changes.

## Running with claims disabled

If you want guilds *only*, set:

```yaml
claims_enabled: false
```

and restart. After that:

- `/claim`, `/claims`, and related commands no longer register.
- Claim listeners (chunk protection, visualiser, etc.) don't run.
- The `GuildRolePermissionResolver` bean is not bound in Koin (this is intentional and load-bearing — see the recovery section below).
- All `/g …` commands continue to work as normal.
- The SQLite schema still contains claims tables, but they're untouched. If you later flip back to `claims_enabled: true`, any old data is still there.

This is the right mode for servers that run a different claims/protection plugin alongside LumaGuilds.

## Recovery: `/lumaguilds override` crash on claims-disabled servers

In versions prior to commit `10357f3`, running `/lumaguilds override` on a server with `claims_enabled: false` would throw `NoDefinitionFoundException`.

**Why:** `GuildRolePermissionResolver` is only registered when `claims_enabled: true`. Earlier code paths injected it eagerly via Koin's `by inject()`, which crashes if the bean is missing.

**Fix:** the resolver is now resolved lazily via `getKoin().getOrNull()`, and dependent code skips the work cleanly when the resolver is absent.

If you see this exception on `/lumaguilds override`, **upgrade the plugin**. There's no config workaround.

## Gotchas

- **`claims_enabled` is startup-only.** Toggling it requires a restart — there's no `/reload`-style live toggle.
- **Cache invalidation has a small delay.** When a player's rank changes, the claim-permission cache invalidates after `cache_invalidation_delay_seconds` (default 5). For a few seconds the player may still act with their old rank. Tune the delay if you have frequent rank changes.
- **Admin override bypasses claim permissions.** While `/lumaguilds override` is active, the admin bypasses all rank-derived claim checks. See [`/lumaguilds override`](override.md).
- **Disabling claims does not delete claim data.** Toggling `claims_enabled: false` leaves the SQLite claim tables intact. Re-enabling later restores everything. If you want a true wipe, drop the relevant tables (back up first).
- **Running another claims plugin alongside LumaGuilds:** set `claims_enabled: false`. Running two claims systems with overlapping protection rules produces unpredictable behavior — one will win events the other expected to handle.

## Related

- [Installation & config.yml](installation.md) — claim distance, visualiser, harvesting, and `team_role_permissions` defaults
- [/lumaguilds override and recovery](override.md) — bypass behavior with claims
- [Permission nodes reference](permissions.md) — `lumaguilds.claim.*` command permissions
