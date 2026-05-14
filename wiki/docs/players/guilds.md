---
title: Guilds
audience: player
topic: guilds
summary: How to create, join, leave, transfer, and disband guilds.
keywords: [guilds, create, join, leave, disband, transfer]
related: [ranks, homes, alliances]
updated: 2026-05-13
---

# Guilds

How to create, join, leave, transfer, and disband guilds.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g create <name>` | `lumaguilds.guild.create` | Create a new guild. |
| `/g join <guild>` | `lumaguilds.guild.join` | Request to join a guild. |
| `/g leave` | `lumaguilds.guild.leave` | Leave your current guild. |
| `/g disband` | `lumaguilds.guild.disband` | Disband your guild (owner only). |
| `/g transfer <player>` | `lumaguilds.guild.transfer` | Transfer ownership. |
| `/g info [guild]` | `lumaguilds.guild.info` | View guild info. |
| `/g list` | `lumaguilds.guild.list` | Browse all guilds. |

## How it works

A guild is a named group of players with one owner, optional ranks, shared homes, a shared vault, and relations (allies/enemies/truces) with other guilds. You can be in one guild at a time. Guilds earn XP from member activity and level up to unlock perks like additional home slots and vault space.

## Creating a guild

Pick a name and run `/g create <name>`. Names are plain text only — max 32 characters, using letters, numbers, and the punctuation `'`, `&`, `-`. **No spaces, no color codes, no MiniMessage tags** (colors and gradients go in your *tag* instead — see the [Identity](identity.md) page). Use `_` or `CamelCase` for multi-word names. Examples:

```text
/g create WhiteLotus
/g create White_Lotus
```

You'll become the owner automatically. Your new guild starts at level 1 with one home slot and basic vault access.

## Joining a guild

For open guilds, just run `/g join <name>`. For invite-only guilds, a member with permission must run `/g invite <you>` first. You'll see a chat prompt or notification; accept it with `/g join <them>` or click the prompt button.

```text
/g join WhiteLotus
```

You can only be in one guild at a time.

## Leaving, transferring, and disbanding

Anyone can leave at any time with `/g leave`. If you're the owner, you can't leave directly — you have two options:

- **Transfer ownership:** `/g transfer <player>` hands off the guild to someone else. They become the new owner, and you become a regular member (or leave immediately after).
- **Disband:** `/g disband` dissolves the guild entirely. This is irreversible. All homes, vault, and guild data are lost.

## Browsing guilds

Use `/g list` to see all open guilds... This will be changed to show all guilds. A seperate `/guild lfg` command will take on open guild browsing.

```text
/g list
```

Use `/g info [guild]` to zoom into one guild's details — members, homes, relations, and level.

```text
/g info WhiteLotus
```

## Gotchas

- You can only be in one guild at a time. Leaving one guild to join another happens instantly.
- Disbanding is irreversible — double-check before running `/g disband`.
- The owner role is single and cannot be transferred except via `/g transfer`. If the owner goes inactive and you're a mod, you cannot promote yourself to owner. Contact staff if this is an issue.
- Guild XP comes from member activity — the more active your members are, the faster you level.

## Related

- [Ranks & Permissions](ranks.md) — set up roles and permissions
- [Homes](homes.md) — create shared teleport points
- [Alliances](alliances.md) — declare allies, enemies, and truces
