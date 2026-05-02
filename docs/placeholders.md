# LumaGuilds Placeholders

PlaceholderAPI expansion identifier: `lumaguilds`

All placeholders are read-only and safe to use in chat plugins, scoreboards, holograms, and tab plugins. Top-N rankings are cached for 30 seconds.

---

## Player's Guild

Resolve to `""` (empty) if the player has no guild — except `has_guild`, which returns `false`.

| Placeholder | Value |
|---|---|
| `%lumaguilds_has_guild%` | `true` / `false` |
| `%lumaguilds_guild_name%` | Guild name |
| `%lumaguilds_guild_tag%` | Formatted tag, falls back to `§6<name>` |
| `%lumaguilds_guild_emoji%` | Nexo placeholder (`%nexo_<name>%`) |
| `%lumaguilds_guild_level%` | Current level |
| `%lumaguilds_guild_balance%` | Bank balance |
| `%lumaguilds_guild_mode%` | `Peaceful` / `Hostile` |
| `%lumaguilds_guild_members%` | Member count |
| `%lumaguilds_guild_online_members%` | Members currently online |
| `%lumaguilds_guild_rank%` | Player's rank inside the guild |
| `%lumaguilds_guild_age_days%` | Days since guild creation |
| `%lumaguilds_guild_display%` | Composed `emoji tag [level]` |
| `%lumaguilds_guild_chat_format%` | `emoji tag` for chat plugins |

### Combat

| Placeholder | Value |
|---|---|
| `%lumaguilds_guild_kills%` | Total guild kills |
| `%lumaguilds_guild_deaths%` | Total guild deaths |
| `%lumaguilds_guild_kdr%` | Kill/death ratio (e.g. `1.42`) |
| `%lumaguilds_guild_efficiency%` | Kills as % of total combat actions (e.g. `58.7%`) |
| `%lumaguilds_guild_wars_total%` | All wars participated in |
| `%lumaguilds_guild_wars_active%` | Currently active wars |

### Progression Detail

| Placeholder | Value |
|---|---|
| `%lumaguilds_guild_xp_total%` | Lifetime XP |
| `%lumaguilds_guild_xp_this_level%` | XP earned within the current level |
| `%lumaguilds_guild_xp_next_level%` | XP needed to complete the current level |
| `%lumaguilds_guild_xp_to_next%` | XP remaining to level up |
| `%lumaguilds_guild_xp_progress_pct%` | Percent progress (e.g. `62.5`) |

---

## Player's Guild Rank in Leaderboards

Returns the 1-based rank of the player's guild in the top 25, or `""` if not ranked.

| Placeholder | Ranking metric |
|---|---|
| `%lumaguilds_guild_rank_level%` | Guild level (XP tie-breaks) |
| `%lumaguilds_guild_rank_balance%` | Bank balance |
| `%lumaguilds_guild_rank_activity%` | Current week activity score |
| `%lumaguilds_guild_rank_members%` | Member count |
| `%lumaguilds_guild_rank_age%` | Oldest first |

---

## Top-N Leaderboards

Format: `%lumaguilds_top_<category>_<rank>_<field>%`

- **category** — `level` · `balance` · `activity` · `members` · `age`
- **rank** — `1` to `25` (1-based)
- **field** — see table below

Cached for 30 seconds. Returns `""` if the slot is empty.

| Field | Value |
|---|---|
| `name` | Guild name |
| `tag` | Formatted tag |
| `tag_plain` | Tag with formatting stripped |
| `id` | Guild UUID |
| `value` | The metric being ranked on (level / balance / activity score / members / epoch second for age) |
| `level` | Guild level |
| `members` | Member count |
| `balance` | Bank balance |
| `activity` | Current week activity score |
| `age_days` | Days since creation |
| `emoji` | Nexo placeholder |

### Examples

```text
%lumaguilds_top_balance_1_name%        → "DiamondDelvers"
%lumaguilds_top_balance_1_value%       → "152340"
%lumaguilds_top_level_3_tag_plain%     → "Wolves"
%lumaguilds_top_activity_2_members%    → "12"
%lumaguilds_top_age_1_age_days%        → "287"
```

---

## Global

| Placeholder | Value |
|---|---|
| `%lumaguilds_guild_total_count%` | Total guilds on the server |

---

## Relations (Relational Placeholder)

`%rel_lumaguilds_rel_<otherPlayerName>_status%`

| Output | Meaning |
|---|---|
| `🟢` | Same guild |
| `🔵` | Allied guild |
| `🔴` | Enemy / at war |
| `⚪` | Truce |
| (empty) | Neutral or other player offline / no guild |

---

## Notes

- Top-N rankings are computed live from `GuildService`, `BankService`, `LeaderboardRepository`, and `ProgressionRepository`. The 30-second cache prevents PAPI tick storms on busy scoreboards.
- The same data is exposed over HTTP via the public web API (`/api/leaderboards/guilds?type=<category>`).
- Service errors are swallowed and resolve to safe defaults (`0`, `""`, `0.0`) so PAPI never propagates exceptions to chat or scoreboards.
