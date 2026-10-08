---
title: Homes
audience: player
topic: homes
summary: Set, name, visit, and restrict access to guild homes.
keywords: [homes, sethome, removehome, ally home, teleport]
related: [ranks, alliances]
updated: 2026-10-07
---

# Homes

Set, name, visit, and restrict access to guild homes.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g sethome [name]` | `lumaguilds.guild.sethome` | Set a home at your location. |
| `/g home [name]` | `lumaguilds.guild.home` | Teleport to a guild home. |
| `/g homes` | `lumaguilds.guild.homes` | List your guild's homes. |
| `/g removehome <name>` | `lumaguilds.guild.removehome` | Remove a named home. |
| `/g setallyhome` | `lumaguilds.guild.setallyhome` | Set your ally-home. |
| `/g removeallyhome` | `lumaguilds.guild.removeallyhome` | Remove your ally-home. |

## How it works

Homes are teleport points your guild can share. Your configured base allowance and owned Chapter outpost rewards determine the available home slots. Reaching a reward's level makes it available to purchase; it does not automatically purchase or activate a home. Home access uses an explicit rank allowlist, with owner access retained. An ally-home has its own rank and inbound allied-guild allowlists.

## Unlocking, activating and moving a home

The Chapter reward purchase and home activation are separate charges when the server enables them. Review the reward menu's guild-gold price and the home activation quote before confirming. Activation prices are configurable; a server's displayed price takes precedence over examples from older guides.

Move an already activated home by setting **the same home name** at the new location. Direct relocation preserves activation and is free. Deleting the home removes its activation; creating it again can require a new activation payment. Do not delete a home simply to move it.

## Setting your default home

Stand where you want it and run `/g sethome`. This creates or overwrites the `main` home:

```text
/g sethome
```

Your guild members can now teleport there with `/g home`.

## Adding named homes

Once your guild owns the required additional home slots, you can create named homes for different purposes:

```text
/g sethome spawner
/g sethome mine
/g sethome goldfarm
```

Use `/g homes` to list all homes your guild has set.

## Visiting a home

Teleport to your main home with `/g home`. For a named home, use `/g home <name>`:

```text
/g home
/g home spawner
```

There's a short countdown — don't move or the teleport cancels. The destination must be safe (not lava, fire, or cactus right at the spot). Safe blocks like ladders, slabs, and water work fine.

## Removing a home

Use `/g removehome <name>` to delete a named home:

```text
/g removehome spawner
```

The slot opens up for a new home, but the deleted home's paid activation is removed. Setting the same existing name is the free relocation path.

## Setting up your ally-home

Your ally-home is separate from regular homes — it's a spot allied guilds can teleport to if they have permission and are on your whitelist. Stand where you want it and run:

```text
/g setallyhome
```

Only members with the "Set Ally-Home" permission can do this. Use `/g removeallyhome` to remove it.

## Restricting a home to specific ranks

Open `/g menu` → Homes → pick a home → Access. Choose which ranks can use it. By default, only the owner can access newly-created homes.

Select each rank that should have access. An empty rank allowlist does not grant every member access; owners retain access. An allied guild must also be included in the destination's inbound ally-home allowlist.

## Recently Fixed/Changed

- Nether ↔ Overworld teleports are reliable (fixed in May 2026).
- The destination block must be safe — if a protection plugin still cancels your teleport, contact staff.
- Named homes persist across server restarts (used to be wiped — fixed in May 2026).
- Ally-homes are completely separate from regular homes — your regular homes stay private to your guild.
- You need the right rank to teleport to restricted homes; mods can't override rank restrictions.

## Related

- [Ranks & Permissions](ranks.md) — set per-home rank access
- [Alliances](alliances.md) — establish allies and manage their home access
