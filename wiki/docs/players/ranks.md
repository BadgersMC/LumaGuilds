---
title: Ranks & Permissions
audience: player
topic: ranks
summary: Create ranks, set permissions, and manage member rank assignments.
keywords: [ranks, permissions, priority, promote, demote]
related: [guilds, homes, lfg]
updated: 2026-10-07
---

# Ranks & Permissions

Create ranks, set permissions, and manage member rank assignments.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g ranks` | `lumaguilds.guild.ranks` | Open the rank management menu. |
| `/g ranks chat on\|off` | `lumaguilds.guild.ranks` + guild settings permission | Show or hide rank labels for everyone in your guild chat. |
| `/g menu` | `lumaguilds.guild.menu` | Open the guild control panel. |

## How it works

Every member is assigned a rank. Ranks have a *priority* (0 = owner, higher numbers = lower rank) and a set of *permissions*. To do anything privileged — invite, kick, manage homes, declare war — your rank needs the matching permission. You can only manage members and ranks below your own priority. You cannot change your own assignment, grant an owner rank through normal promotion, or delegate permissions your rank lacks. Ownership changes use the separate ownership-transfer action.

## Opening the rank menu

Use `/g ranks` or `/g menu` → Ranks to access rank management. From there you can create, delete, rename, reorder, and set permissions on ranks.

```text
/g ranks
```

## Creating a rank

In the rank menu, click "Create". Pick a name and assign permissions. New ranks default to the lowest priority — use the reorder buttons to lift them up.

Example: Create a "Moderator" rank with permission to invite and kick members.

## Rank colors and guild chat

When creating or renaming a rank, put color codes in its name: `&aFounder` for green, `&6&lModerator` for bold gold, or `&#55ff99Member` for a hex color. Names may contain 1–24 visible letters, numbers, spaces, underscores, or hyphens; color codes do not count toward that limit. Changing colors keeps the rank's permissions, member assignments, and claim access.

Guild leaders and ranks with **Manage Guild Settings** can show or hide rank labels for the entire guild with `/g ranks chat on` or `/g ranks chat off`. Java players can also use **Guild Chat Ranks** in rank management; Bedrock players can use the toggle in guild settings. Labels are shown by default, and the setting survives server restarts. It changes guild chat only.

## Setting permissions on a rank

Open the rank in the menu and go to Permissions. Toggle each permission to enable or disable it. Permissions are grouped by area:

- **Members:** invite, kick, promote, demote
- **Homes:** create, set, remove, access (restricted homes)
- **Vault:** deposit, withdraw, manage inventory
- **War:** declare, accept, end
- **Banner:** set guild banner/tag colors
- And more.

Only give permissions you trust members to use safely.

## Reordering rank priority

In the rank menu, use the ▲ / ▼ buttons next to each rank to move it up or down. Higher priority = more trust. The owner rank (priority 0) can't move, and you can't move a rank above your own priority.

```text
Owner (priority 0)
  ▼
Moderator (priority 1)
  ▼
Member (priority 2)
```

## Promoting and demoting members

Open `/g menu` → Members. Click a member and select Promote or Demote. Both online and offline members can be managed.

```text
/g menu
```

The system will confirm the action before applying it. You can't promote someone above your own rank, and you can't demote the owner.

## Gotchas

- Mods with "Manage Members" can no longer manage co-owners or promote themselves above their rank (fixed mid-2026).
- Offline kicks now respect priority — you can't kick someone above you just because they're offline.
- Promote/demote confirmations show the real player name even when the target is offline (used to show "Unknown Player").
- The owner role is fixed at priority 0 and has all permissions automatically. You can't create a rank equal to the owner.
- If a member's rank is deleted, they're moved to the next-lowest rank.

## Related

- [Guilds](guilds.md) — create and manage your guild
- [Homes](homes.md) — set per-home rank access
- [LFG](lfg.md) — manage members and bans


Permission categories now open individual toggles during rank creation and editing.
Opening a category does not grant its permissions. You cannot grant permissions
you do not currently hold. Lower priority numbers rank higher; priority zero is
the owner rank. Claims-related permissions are hidden when claims are disabled.
