---
title: Progression, Quests & Prestige
audience: player
topic: progression
summary: Earn guild XP, complete weekly guild quests, unlock Chapter rewards,
and use prestige when enabled.
keywords: [progression, levels, xp, chapter, quests, weekly quests, rewards,
prestige]
related: [guilds, war, homes]
updated: 2026-10-07
---

# Progression, Quests & Prestige

LumaGuilds 3.0 turns guild progression into a full Chapter system. Normal play
still earns guild XP, but weekly guild quests, reward unlocks, purchases,
seasonal war rating, and optional prestige now sit on top of permanent
progression.

## Where to find it

Open your guild dashboard and use **Progression** for your Chapter level, XP,
unlocked rewards, reward purchases, and prestige status. Use **Guild Quests**
for the current weekly quest set.

Java players use inventory menus. Bedrock players get native forms with the same
progression and quest information.

## Checking your guild's level

Open **Progression** from the guild dashboard to see the current level, accepted
XP, unlock state, reward catalog, and prestige status. The guild information and
leaderboard views also expose progression summaries where relevant.

## Earning guild XP

Guild XP can come from configured gameplay sources such as mining, combat,
crafting, and other tracked activity. The server applies the Chapter progression
rules and configured per-source limits before XP is accepted.

Weekly guild quests provide another major source of XP. Quest objectives are
generated from real server targets and can include additional conditions, so the
exact set changes from week to week.

## Weekly guild quests

Each weekly quest shows:

- the objective and any conditions;
- your guild's current progress;
- the completion target;
- Guild EXP and item rewards;
- the guild leaderboard for that quest;
- the weekly reset timer.

Quest completion is automatic. When the target is reached, the completion reward
is reconciled by the server and guild members receive a completion
toast/notification.

A guild may continue making leaderboard progress after completing the normal
target. That extra progress is used for the per-quest weekly competition, so
completing a quest does not remove your guild from the race for the top score.

## Quest leaderboards

Open a quest to view other guilds and their progress on that specific objective.
The weekly winner can receive additional Guild EXP when that reward is enabled
by server configuration.

This leaderboard score is separate from whether your guild has already completed
the quest's normal reward target.

## Chapter rewards

Progression unlocks rewards as your guild advances. Some rewards are permanent
unlocks; others use the Chapter reward-purchase flow and may require guild
resources before they become active.

The Progression menu shows whether a reward is locked, available, already owned,
or unavailable and gives the price and requirements before a purchase is
confirmed.

## Prestige

Prestige is server-configurable and may be disabled. When enabled and your guild
meets the requirements, the Progression menu shows the current prestige count,
fee, available retained-perk choices, and confirmation flow.

Prestige is intentionally explicit: the menu shows what will be retained and
what the next prestige costs before anything is committed.

## Guild Discord roles

When Discord role synchronization is enabled, a guild earns its role at the
configured minimum guild level (default 50). A completed prestige preserves
eligibility even though the current-run level resets. Role creation also
requires the configured Discord integration and its role-management permissions;
reaching the level alone does not guarantee successful Discord delivery.

## Seasonal war rating

Rated wars also feed a seasonal Elo-style rating. This is separate from
permanent guild XP: Chapter progression represents long-term guild development,
while seasonal rating represents current competitive war performance.

War menus show seasonal rating information where relevant.

## Reliability

Chapter 2 progression and rewards use durable database state. Reward
reconciliation, weekly payouts, Chapter rollover, and migration state are
designed to recover after a restart instead of relying only on in-memory claim
flags.

## Related

- [Guilds](guilds.md) — guild basics
- [War](war.md) — rated wars and seasonal competition
- [Homes](homes.md) — guild home access and unlocks
