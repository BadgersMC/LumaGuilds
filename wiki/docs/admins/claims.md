---
title: Claims integration
audience: admin
topic: claims
summary: Hook LumaGuilds into the server's claims plugin so guild membership grants claim permissions.
keywords: [claims, integration, permissions]
related: [installation, override, permissions]
updated: 2026-05-14
---

# Claims integration

Optionally integrate LumaGuilds with a server claims plugin to grant guild members automatic permissions on claims owned by the guild. Guild ranks (Owner, Co-Owner, Admin, Mod, Member) map to claim permissions (BUILD, HARVEST, CONTAINER, etc.) via the config.

## How it works

When `claims_enabled: true` in `config.yml`, LumaGuilds loads the `guildClaimsIntegrationModule`, which registers the `GuildRolePermissionResolver` service. This service:

1. Listens for claim permission checks when a player tries to interact with a claimed block.
2. Looks up the claim's guild owner (if any).
3. Checks the player's rank in that guild.
4. Maps the rank to a set of claim permissions from the config.
5. Caches the result for performance.

When `claims_enabled: false`, the integration module is not loaded, and the `GuildRolePermissionResolver` bean is absent from the dependency injection container. Code that depends on it must resolve it lazily (via `getKoin().getOrNull()`) to avoid a `NoDefinitionFoundException` crash.

## Supported claims plugins

Currently, LumaGuilds integrates with one claims plugin:

- **AxKoth Claims** — A custom claims plugin used on BadgersMC servers.

If you're using a different claims plugin (e.g., GriefPrevention, WorldGuard, Claims), the integration will not work out of the box. File an issue or contact the developers if you need support for another plugin.

## Setup

Enable the claims integration in `plugins/LumaGuilds/config.yml`:

```yaml
# Enable or disable the entire claims system
claims_enabled: true
```

When `claims_enabled: true`, LumaGuilds assumes the claims plugin is installed and will call its API. If the plugin is not installed or not compatible, you may see error logs but the server should not crash.

**Restart required:** Yes. The integration is loaded at startup; toggling the config without restarting will have no effect.

## What guild membership grants

When a player is in a guild that owns a claim, the player's rank determines what they can do in that claim:

| Rank | Permissions |
|------|-------------|
| Owner | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, DETONATE, EVENT, SLEEP, VIEW |
| Co-Owner | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, DETONATE, EVENT, SLEEP, VIEW |
| Admin | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, VIEW |
| Mod | BUILD, HARVEST, CONTAINER, DISPLAY, VEHICLE, SIGN, VIEW |
| Member | HARVEST, CONTAINER, VIEW |
| Default (not in guild) | VIEW |

These mappings come from the `team_role_permissions` block in `config.yml`. You can customize them:

```yaml
team_role_permissions:
  # Cache invalidation delay in seconds when role changes occur
  cache_invalidation_delay_seconds: 5
  
  # Default permissions for roles that don't have explicit mappings
  default_permissions:
    - "VIEW"
  
  # Role to permission mappings
  roles:
    Owner:
      - "BUILD"
      - "HARVEST"
      - "CONTAINER"
      # ... (see installation.md for full list)
```

Edit the `roles` section to change what each rank can do. For example, to prevent Members from opening containers in guild claims:

```yaml
    Member:
      - "HARVEST"
      - "VIEW"  # Removed CONTAINER
```

Then restart the server for changes to take effect.

## Disabling the integration

To disable the claims integration without uninstalling the claims plugin:

```yaml
claims_enabled: false
```

Restart the server. The `GuildRolePermissionResolver` will no longer be loaded, and guild membership will not grant any automatic claim permissions. Existing permissions granted to players are NOT revoked — the claims plugin owns its own data and will continue to enforce those permissions if they exist in the database.

To revoke all guild-based claim permissions, you must contact the claims plugin admin or manually delete the permissions from the claims database.

## Recovery: Koin crash on /lumaguilds override when claims disabled

In versions prior to commit `3699222`, the `/lumaguilds override` command would crash with a `NoDefinitionFoundException` if you disabled claims integration.

**Why:** `GuildRolePermissionResolver` was only registered in `guildClaimsIntegrationModule`. When you disabled claims, the bean was missing, but `LumaGuildsCommand` tried to inject it eagerly using `by inject()`. The container threw an exception instead of finding a fallback.

**Fix (v0.5.1+):** `LumaGuildsCommand` now resolves `GuildRolePermissionResolver` lazily:
```kotlin
private val guildRolePermissionResolver: GuildRolePermissionResolver?
    get() = getKoin().getOrNull()
```

It then safely invalidates the cache only if the resolver is present:
```kotlin
guildRolePermissionResolver?.invalidatePlayerCache(sender.uniqueId)
```

If you see `NoDefinitionFoundException` when running `/lumaguilds override` on a claims-disabled server, you are running an older version. **Upgrade to v0.5.1 or later.**

## Gotchas

- **Disabling after enabling:** If you had claims enabled, granted permissions to players, and then disabled `claims_enabled`, those permissions remain in the database and the claims plugin will continue to enforce them. Toggling the config flag does not revoke anything — the claims plugin owns claim permission data.
- **Switching claims plugins:** If you replace the claims plugin (e.g., AxKoth → a different plugin), you must perform a clean database migration. Existing claims data will not transfer, and you will lose all grant history.
- **Admin override bypasses checks:** While admin override is active (via `/lumaguilds override`), the player bypasses all guild membership and claim permission checks, including those granted by guild rank. See [`/lumaguilds override`](override.md) for details.
- **Cache invalidation:** When a player's guild rank changes, the permission cache is invalidated after a 5-second delay (controlled by `cache_invalidation_delay_seconds`). This means rank changes may not take effect immediately in claims; the player might be able to act with the old rank for a few seconds. Increase this delay if you have frequent role changes and need more safety margin; decrease it if performance is critical.
- **No fallback on plugin unavailable:** If the claims plugin crashes or is unloaded, claim permission checks may behave unexpectedly. LumaGuilds does not fall back to a safe mode; it expects the claims plugin to be running. Always ensure both plugins are healthy before using the integration.

## Related

- [Installation & config.yml](installation.md) — core claims and team_role_permissions settings
- [/lumaguilds override](override.md) — admin override and its interaction with claim permissions
- [Permission nodes reference](permissions.md) — player command permissions
