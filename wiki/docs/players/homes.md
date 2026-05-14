---
title: Homes
audience: player
topic: homes
summary: Set, name, visit, and restrict access to guild homes.
keywords: [homes, sethome, removehome, ally home, teleport]
related: [ranks, alliances]
updated: 2026-05-13
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

Homes are teleport points your guild can share. Each guild starts with one home slot (`main`) and unlocks more as it levels up. Homes can be restricted per-rank — only members at a chosen rank or above can teleport to them. There's also a separate "ally-home" that allied guilds can reach if they have permission and are whitelisted.

## Setting your default home

Stand where you want it and run `/g sethome`. This creates or overwrites the `main` home:

```
/g sethome
```

Your guild members can now teleport there with `/g home`.

## Adding named homes

Once you unlock additional home slots (by leveling your guild), you can create named homes for different purposes:

```
/g sethome spawner
/g sethome mine
/g sethome goldfarm
```

Use `/g homes` to list all homes your guild has set.

## Visiting a home

Teleport to your main home with `/g home`. For a named home, use `/g home <name>`:

```
/g home
/g home spawner
```

There's a short countdown — don't move or the teleport cancels. The destination must be safe (not lava, fire, or cactus right at the spot). Safe blocks like ladders, slabs, and water work fine.

## Removing a home

Use `/g removehome <name>` to delete a named home:

```
/g removehome spawner
```

You'll get a confirmation prompt. The slot opens up for a new home.

## Setting up your ally-home

Your ally-home is separate from regular homes — it's a spot allied guilds can teleport to if they have permission and are on your whitelist. Stand where you want it and run:

```
/g setallyhome
```

Only members with the "Set Ally-Home" permission can do this. Use `/g removeallyhome` to remove it.

## Restricting a home to specific ranks

Open `/g menu` → Homes → pick a home → Access. Choose which ranks can use it. By default, only the owner can access newly-created homes.

Homes without rank restrictions are accessible to all guild members.

## Recently Fixed/Changed

- Nether ↔ Overworld teleports are reliable (fixed in 2026).
- The destination block must be safe — if a protection plugin still cancels your teleport, contact staff.
- Named homes persist across server restarts (used to be wiped — fixed in May 2026).
- Ally-homes are completely separate from regular homes — your regular homes stay private to your guild.
- You need the right rank to teleport to restricted homes; mods can't override rank restrictions.

## Related

- [Ranks & Permissions](ranks.md) — set per-home rank access
- [Alliances](alliances.md) — establish allies and manage their home access
