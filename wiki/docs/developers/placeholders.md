---
title: Placeholders
audience: dev
topic: placeholders
summary: How the PlaceholderAPI expansion resolves guild, Chapter, quest, reward, and leaderboard values.
keywords: [placeholderapi, placeholders, expansion]
related: [api-reference, infrastructure]
updated: 2026-09-28
---

# Placeholders (internal)

The PlaceholderAPI expansion is implemented by `LumaGuildsExpansion` in the infrastructure layer.

Resolution is grouped by identifier families rather than one giant data object.

Current families include:

- basic guild identity/state such as `guild_name`, tags, emoji, level, XP, balances, and rank;
- `chapter_*` values for Chapter lifecycle/progression state;
- `guild_weekly_*` values for weekly quest state;
- `guild_reward_*` values for Chapter reward ownership/state;
- `source_*` values for progression-source usage/caps;
- `top_*` leaderboard values;
- seasonal Elo/rank eligibility values.

Player-scoped placeholders resolve the player's guild first and return an empty/safe value when no guild exists.

When adding a placeholder, update both the expansion tests and the admin-facing [PlaceholderAPI page](../admins/placeholderapi.md).
