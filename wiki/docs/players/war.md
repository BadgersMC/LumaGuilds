---
title: War
audience: player
topic: war
summary: Declare, accept, fight, and resolve wars between guilds, including seasonal rating and lifecycle notifications.
keywords: [war, pvp, declare war, kills, seasonal elo, rating, toast]
related: [alliances, mode, progression]
updated: 2026-09-28
---

# War

Wars are persisted guild-vs-guild conflicts with explicit declaration, acceptance, objectives, resolution, and notifications.

## Declaring war

Use your guild's war controls to select an eligible guild and configure the declaration. Peaceful/hostile mode, guild permissions, cooldowns, and other server rules are checked before the declaration can proceed.

The receiving guild gets the declaration through its war UI and can respond through the acceptance flow. A war becomes active only when its lifecycle reaches the active state; a sent declaration is not treated as an already-resolved fight.

## Objectives and victory

Wars can track kill objectives and configured global kill targets. When a victory condition is reached, the server persists the result and resolves the war instead of relying on a transient chat event.

War statistics and the management UI show the active state and progress. Expired wars are also recovered and reconciled after restarts.

## Seasonal rating

Eligible rated wars affect the current Chapter's seasonal Elo-style rating. The war declaration and management menus show rating information so guilds can see the competitive context before and during a rated war.

Seasonal rating is separate from permanent guild progression. See [Progression](progression.md) for the distinction between Chapter XP and seasonal competition.

## Notifications

War lifecycle events use persistent notifications. Guild members receive toast-style notifications plus chat detail for events such as:

- declaration sent or received;
- war accepted;
- victory;
- defeat.

Unread lifecycle notifications can be replayed when a member logs in, so a restart or an offline player does not silently lose the result.

Guild disbands are also announced to affected allies and enemies.

## Ending a war

Resolution state is stored durably. On startup, LumaGuilds reconciles active or expired wars and pending victory/defeat notification state so a crash between gameplay resolution and notification delivery does not duplicate or lose the outcome.

## Guild mode and diplomacy

Peaceful guilds cannot participate in war in the same way as hostile guilds. Relation state, ally/enemy rules, and mode restrictions are validated by the server before a declaration is accepted.

See [Mode](mode.md) and [Alliances & Diplomacy](alliances.md) for those rules.

## Related

- [Alliances & Diplomacy](alliances.md)
- [Mode](mode.md)
- [Progression, Quests & Prestige](progression.md)
