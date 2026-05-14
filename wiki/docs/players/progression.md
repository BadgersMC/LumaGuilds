---
title: Progression & Levels
audience: player
topic: progression
summary: Earn guild XP from member activity, level up, and unlock perks.
keywords: [progression, levels, xp, leveling, perks]
related: [guilds, war, homes]
updated: 2026-05-13
---

# Progression & Levels

Earn guild XP from member activity, level up, and unlock perks.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g info` | `lumaguilds.guild.info` | See your guild's current level and XP. |

## How it works

Your guild earns XP from what its members do — mining, killing mobs, completing certain activities. XP accumulates and your guild levels up automatically when it crosses thresholds. Each level unlocks something: more member slots, more home slots, access to advanced features (like declaring war), better leaderboard placement. You don't need to manage XP — just play, and your guild progresses.

## Earning XP (sources)

XP comes from typical Minecraft activity by guild members: breaking blocks, killing mobs, certain PvP kills. Most sources are passive — playing the game progresses your guild. There are no explicit "quests" to grind.

## What levels unlock

Higher levels unlock additional member capacity, named home slots, advanced features (declaring war, certain rank permissions), and emoji options. Run `/g info` to see your current level and what's available at the next level.

## Checking your guild's level

Run `/g info` — it shows:
- Current level
- Current XP
- XP needed for the next level
- Member count

The leaderboard (in-game `/g list` and the web leaderboard) ranks guilds by level and XP.

## Gotchas

- Killing a member of your own guild no longer awards XP (anti-farm — fixed mid-2026).
- XP earned in the seconds before you quit, the server shuts down, or a database flush fails is no longer lost (fixed mid-2026 — it now retries on the next flush cycle).
- Large mining runs no longer overwhelm the server (block XP is now batched — fixed mid-2026).
- Old guilds that displayed "Level 1" despite huge XP totals had their levels corrected on the May 2026 patch — `/g info` should now show the real level.

## Related

- [Guilds](guilds.md) — guild basics
- [War](war.md) — declaring war requires a minimum guild level
- [Homes](homes.md) — unlocking home slots through leveling
