---
title: Permission nodes reference
audience: admin
topic: permissions
summary: Every permission node LumaGuilds defines, what it does, and its default state.
keywords: [permissions, permission nodes, lumaguilds, vault, luckperms]
related: [installation, override]
updated: 2026-05-14
---

# Permission nodes reference

Every permission node LumaGuilds defines, what it does, and its default state.

## How it works

LumaGuilds uses two permission prefixes: `lumaguilds.guild.<action>` for player commands (creating guilds, inviting members, teleporting to homes) and `lumaguilds.admin.<action>` for administrative commands (vault rollback, bank credit, force removal). Defaults come from `plugin.yml` and are either `true` (all players), `false` (no one), or `op` (operators only).

Note that rank *permissions* (which roles can BUILD, HARVEST, CONTAINER, etc. in guild claims) are a separate system controlled in `config.yml` under `team_role_permissions` — they don't use permission nodes. See [Ranks & Permissions](../players/ranks.md) for that system.

## Player-command permissions

| Permission | Default | Grants |
|-----------|---------|--------|
| `lumaguilds.guild.create` | `true` | Create new guilds. Denying this blocks guild creation entirely. |
| `lumaguilds.guild.rename` | `true` | Rename your guild. |
| `lumaguilds.guild.sethome` | `true` | Set guild home location. |
| `lumaguilds.guild.home` | `true` | Teleport to guild home. |
| `lumaguilds.guild.ranks` | `true` | Manage guild ranks and role assignments. |
| `lumaguilds.guild.emoji` | `true` | Change guild emoji. |
| `lumaguilds.guild.mode` | `true` | Toggle guild mode (peaceful/hostile). |
| `lumaguilds.guild.disband` | `true` | Disband your guild. |
| `lumaguilds.guild.menu` | `true` | Open guild management menu. |
| `lumaguilds.guild.invite` | `true` | Invite players to guild. |
| `lumaguilds.guild.kick` | `true` | Kick players from guild. |
| `lumaguilds.guild.tag` | `true` | Change guild tag. |
| `lumaguilds.guild.description` | `true` | Change guild description. |
| `lumaguilds.guild.war` | `true` | Declare war on other guilds. |
| `lumaguilds.guild.info` | `true` | View guild information. |
| `lumaguilds.guild.history` | `true` | View player guild membership history. |
| `lumaguilds.guild.chat` | `true` | Toggle guild chat mode (messages go to guild only). |
| `lumaguilds.guild.*` | `true` | Wildcard: all guild management permissions (parent of above). |

## Claim-command permissions

| Permission | Default | Grants |
|-----------|---------|--------|
| `lumaguilds.command.claim` | `op` | Basic claim management (create, resize). |
| `lumaguilds.command.claim.addflag` | `op` | Add flags to claims. |
| `lumaguilds.command.claim.description` | `op` | Manage claim descriptions. |
| `lumaguilds.command.claim.info` | `op` | View claim information. |
| `lumaguilds.command.claim.partitions` | `op` | Manage claim partitions. |
| `lumaguilds.command.claim.remove` | `op` | Remove claims. |
| `lumaguilds.command.claim.rename` | `op` | Rename claims. |
| `lumaguilds.command.claim.trust` | `op` | Trust individual players in claims. |
| `lumaguilds.command.claim.trustall` | `op` | Trust all players in claims. |
| `lumaguilds.command.claim.trustlist` | `op` | List trusted players. |
| `lumaguilds.command.claim.untrust` | `op` | Untrust players from claims. |
| `lumaguilds.command.claim.untrustall` | `op` | Untrust all players from claims. |
| `lumaguilds.command.claim.removeflag` | `op` | Remove flags from claims. |
| `lumaguilds.command.claimlist` | `op` | List all player claims. |
| `lumaguilds.command.claimmenu` | `op` | Open claim management menu. |
| `lumaguilds.command.claimoverride` | `op` | Override claim permissions. |
| `lumaguilds.command.*` | `op` | Wildcard: all claim management permissions (parent of above). |

## Party chat and administrative permissions

| Permission | Default | Grants |
|-----------|---------|--------|
| `lumaguilds.partychat` | `op` | Use party chat commands (`/pc`). |
| `lumaguilds.partychat.*` | `op` | Wildcard: all party chat permissions. |
| `lumaguilds.admin` | `op` | Administrative access to LumaGuilds and admin commands. Includes `/lumaguilds override`. |
| `bellclaims.admin` | `op` | Allows access to admin override mode (legacy, included in `lumaguilds.admin`). |

## Specialist administrative permissions

| Permission | Default | Grants |
|-----------|---------|--------|
| `lumaguilds.admin.vault.rollback` | `op` | List and restore vault backups with `/vaultrollback`. |
| `lumaguilds.admin.bank.credit` | `op` | Manually credit a player's Vault balance with `/bankcredit`. |
| `lumaguilds.admin.vault.remove` | `op` | Forcibly remove a guild's vault with `/removevault`. |
| `lumaguilds.bedrock.cache.stats` | `op` | View Bedrock form cache statistics with `/bedrockcachestats`. |
| `lumaguilds.bedrock.cache.clear` | `op` | Clear Bedrock form cache. |

## Rank permissions vs node permissions

LumaGuilds has two distinct permission systems:

1. **Node permissions** (this page) — grant access to *commands* via permission plugins (LuckPerms, PermissionsEx, etc.). These control who can run `/g create`, `/claim`, `/lumaguilds override`, etc.

2. **Rank permissions** — control what guild members *at a specific rank* can do *within guild claims* (e.g., "Members can HARVEST and VIEW, but not BUILD"). These are configured in `config.yml` under `team_role_permissions` and apply only to players inside their own guild's claims.

Example: A player might have `lumaguilds.command.claim: true` (node permission) but still be blocked from building in a claim because their guild rank (Member) doesn't have the BUILD permission. See [Ranks & Permissions](../players/ranks.md) for rank permission details.

## Recommended permission setups

### LuckPerms — Default Player

Grant basic guild creation and management to all players:

```
/lp group default permission set lumaguilds.guild.create true
/lp group default permission set lumaguilds.guild.menu true
/lp group default permission set lumaguilds.guild.invite true
/lp group default permission set lumaguilds.guild.kick true
/lp group default permission set lumaguilds.guild.home true
/lp group default permission set lumaguilds.guild.sethome true
/lp group default permission set lumaguilds.guild.ranks true
/lp group default permission set lumaguilds.guild.emoji true
/lp group default permission set lumaguilds.guild.mode true
/lp group default permission set lumaguilds.guild.disband true
/lp group default permission set lumaguilds.guild.chat true
/lp group default permission set lumaguilds.guild.info true
/lp group default permission set lumaguilds.guild.war true
/lp group default permission set lumaguilds.guild.tag true
/lp group default permission set lumaguilds.guild.description true
/lp group default permission set lumaguilds.guild.history true
```

Or use the wildcard:

```
/lp group default permission set lumaguilds.guild.* true
```

### LuckPerms — Staff

Grant staff access to claims, party chat, and admin commands:

```
/lp group staff permission set lumaguilds.guild.* true
/lp group staff permission set lumaguilds.command.* true
/lp group staff permission set lumaguilds.partychat.* true
/lp group staff permission set lumaguilds.admin true
/lp group staff permission set lumaguilds.admin.vault.rollback true
/lp group staff permission set lumaguilds.admin.bank.credit true
/lp group staff permission set lumaguilds.admin.vault.remove true
```

## Discrepancies

PERMISSIONS.md (at repo root) and plugin.yml are in sync as of 2026-05-14. Both define the same node structure and defaults.

## Gotchas

- **`.create` denial blocks all guilds:** Denying `lumaguilds.guild.create` completely prevents new guild creation. Don't accidentally set it to `false` unless you want to lock guilds.
- **`.admin.override` is powerful:** The `lumaguilds.admin` permission grants `/lumaguilds override` mode, which bypasses all rank and guild membership checks. Only grant to trusted administrators; override access is logged but not audited per-action.
- **Revoking nodes doesn't kick players:** Removing a permission doesn't kick players from commands they're already using. They're only blocked on the next command execution.
- **Party chat defaults to `op`:** If you want all players to use `/pc`, explicitly grant `lumaguilds.partychat` to your default group.

## Related

- [Installation & config.yml](installation.md) — install LumaGuilds and configure all settings
- [Override mode guide](override.md) — using admin override for debugging and emergency access
