---
title: War
audience: player
topic: war
summary: Declare and fight wars between guilds.
keywords: [war, pvp, declare war, kills]
related: [alliances, mode, progression]
updated: 2026-05-13
---

# War

Declare and fight wars between guilds.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g war <guild>` | `lumaguilds.guild.war` | Open the war control flow. |

## How it works

War is the escalation of an enemy relation. Once two guilds are at war, kills between their members count toward war statistics and are tracked separately from normal PvP. Wars persist until one side surrenders, the other side accepts a truce, or relations are reset.

## Declaring war

First, mark the other guild as enemy using `/g enemy <them>`. Then run `/g war <them>`. A confirmation menu opens — confirm to start the war. Both guilds will see a server-wide announcement. From that point on, kills between the two warring guilds count toward war statistics.

## Ending a war

Either side can end a war by:

- **Proposing a truce:** `/g truce <them>` offers a friendly resolution; both sides must confirm.
- **Going neutral:** `/g neutral <them>` is unilateral and faster, but clears all relation data.

## How kills are tracked

Killing a member of a guild you're at war with awards your guild kill credit toward the war. The win goes to whichever guild accumulates the most kills (or however your server configures victory conditions — check with staff). Kill streaks and war statistics are displayed in your guild's war profile.

## Guild Mode and war

Your guild's Mode (peaceful/hostile) affects whether your guild can start wars at all. Peaceful guilds cannot declare war. Hostile guilds can. See [Mode](mode.md) for more detail.

## Gotchas

- You can't earn guild XP from killing a member of your own guild (anti-farm — fixed mid-2026).
- War only enables PvP between the two warring guilds — your normal PvP rules with everyone else stay unchanged.
- Pending ally requests are cancelled if you declare war on an ally.
- Both sides must be at least enemy status before a war can be declared.

## Related

- [Alliances & Diplomacy](alliances.md) — manage enemy and ally relations
- [Mode](mode.md) — peaceful vs. hostile guild alignment
- [Progression](progression.md) — how XP and kill stats work
