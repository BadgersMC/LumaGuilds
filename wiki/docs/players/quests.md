---
title: Weekly Guild Quests
audience: player
topic: quests
summary: Complete shared weekly guild quests, earn automatic rewards, and compete on per-quest leaderboards.
keywords: [quests, weekly quests, guild quests, leaderboard, rewards, xp]
related: [progression, guilds, war]
updated: 2026-09-28
---

# Weekly Guild Quests

Every guild receives the same weekly quest set. Your whole guild contributes to each objective, so progress belongs to the guild rather than to one player.

Open **Guild Quests** from the guild dashboard to see the active set.

## Weekly format

By default, LumaGuilds generates six quests each week. The set resets on Monday at the configured UTC reset time.

Quest objectives are generated from real server targets. They can involve actions such as breaking, placing, crafting, killing, or other supported activities, and some quests include extra conditions such as a dimension or location requirement.

The exact objectives change each week.

## Progress and completion

Every qualifying action by a guild member contributes to that guild's progress.

Each quest shows:

- a human-readable objective;
- current progress and target;
- Guild EXP and item rewards;
- time until reset;
- your guild's leaderboard position.

When your guild reaches the normal target, the quest completes automatically. There is no manual claim button. The server reconciles the reward and sends guild members a completion toast/notification.

Completing the target does **not** stop progress.

## Per-quest leaderboards

Click or tap a quest to open its leaderboard.

The leaderboard shows other guilds and their progress on that specific quest. A completed guild can keep increasing its score past the normal target, so the weekly competition can continue after the base reward has already been earned.

At weekly rollover, the highest-scoring guild for each quest can receive an additional Guild EXP reward when that payout is enabled.

This means there are two separate goals:

1. Reach the target to earn the normal quest reward.
2. Keep pushing the same objective to compete for the weekly leaderboard reward.

## Full-set bonus

Completing the entire weekly quest set can award an additional Guild EXP bonus.

The quest header shows your completed count, weekly reset timer, and whether the full-set bonus has been awarded.

## Java and Bedrock

Java players use the inventory quest menu.

Bedrock players use a native Floodgate/Cumulus form with the same quest set, progress, rewards, leaderboard information, and reset state. Quest gameplay and rewards are shared across both editions.

## Quest icons

Quest icons change with the generated action and target where possible. Servers using the Enthusia resource pack can provide custom LumaGuilds quest icons; unsupported targets fall back to standard Minecraft items.

## If progress does not move

Check the objective carefully. Generated quests can include conditions beyond the main action. For example, the correct block or mob may only count in a particular dimension or location.

If you believe a valid action is not counting, report the exact quest text and what you did to staff.

## Related

- [Progression, Quests & Prestige](progression.md)
- [Guilds](guilds.md)
- [War](war.md)

## Direct member access and spawn display configuration

All guild members may use `/g quests`, `/guild quest`, or `/guild quest menu` (permission `lumaguilds.guild.quests`, default true). This opens the existing Java/Bedrock quest menu without granting management access. Clicking a quest opens its leaderboard; Back closes the directly opened menu.

Availability reminders are controlled in progression.yml by `quests.notifications.on_join` and `quests.notifications.on_reset`, both true by default. They show only while unfinished milestone objectives remain. Completion rewards/notifications are unchanged.

For spawn holograms, use these PlaceholderAPI examples. Quest and leaderboard indexes start at 1:

- `%lumaguilds_guild_weekly_quests_available%`
- `%lumaguilds_guild_weekly_quests_time_remaining%`
- `%lumaguilds_guild_weekly_quest_1_name%`
- `%lumaguilds_guild_weekly_quest_1_description%`
- `%lumaguilds_guild_weekly_quest_1_required%`
- `%lumaguilds_guild_weekly_quest_1_reward_exp%`
- `%lumaguilds_guild_weekly_quest_1_top_1_guild%`
- `%lumaguilds_guild_weekly_quest_1_top_1_score%`
- `%lumaguilds_guild_weekly_quest_1_score%` (viewer's guild)
- `%lumaguilds_guild_weekly_quest_1_rank%` (viewer's guild)

Top entries support positions 1..100 with a 30-second cache; absent entries return empty text. Global descriptions and top entries do not require a player. Viewer score/rank need player-context hologram support. This source change does not create or alter production holograms.
