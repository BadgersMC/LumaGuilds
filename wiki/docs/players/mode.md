---
title: Mode (Peaceful / Hostile)
audience: player
topic: mode
summary: Switch your guild between Peaceful and Hostile mode to control PvP and conflict behavior.
keywords: [mode, peaceful, hostile, pvp]
related: [war, alliances]
updated: 2026-05-13
---

# Mode (Peaceful / Hostile)

Switch your guild between Peaceful and Hostile mode to control PvP and conflict behavior.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g mode` | `lumaguilds.guild.mode` | Open the mode selection menu. |

## How it works

Every guild is either in **Peaceful** or **Hostile** mode. Peaceful guilds opt out of guild-vs-guild PvP and can't be enemies or be at war. Hostile guilds can be enemied and warred. Switching modes is reversible but may have cooldowns or restrictions configured by the server.

## Switching to Peaceful

Run `/g mode`, then click the Peaceful option in the menu. Confirm the switch:

```
/g mode
[Click: Peaceful]
[Confirm]
```

Your guild's enemy relations and pending war declarations clear. Future enemy and war attempts will be blocked while you're peaceful.

## Switching to Hostile

Run `/g mode`, then click Hostile in the menu and confirm:

```
/g mode
[Click: Hostile]
[Confirm]
```

Now your guild can be enemied, declare wars, and engage in guild-vs-guild PvP.

## Understanding each mode

**Peaceful:** Protects you from being declared an enemy and from being warred. Your members can still PvP individually under server-wide PvP rules, but those kills don't count toward guild war stats. You also can't declare yourself enemies or declare wars.

**Hostile:** The default — fully engaged in the guild relationship system. You can be enemied, declare wars, declare other guilds as enemies, and PvP counts toward guild war progression.

## Gotchas

- The Mode menu options work properly after the recent fix (clicking Peaceful/Hostile used to throw errors — fixed in 2026).
- Going Peaceful while at war may immediately end the war (server-dependent).
- Some advanced features (declaring war, marking enemies) are gated to Hostile mode only.

## Related

- [War](war.md) — wage war with other guilds in Hostile mode
- [Alliances](alliances.md) — establish peace and cooperation with allies
