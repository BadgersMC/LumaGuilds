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
| `%lumaguilds_guild_tag_minimessage%` | Tag normalized to MiniMessage (for MiniMessage-only formatters like Velocitab), falls back to `<gold><name>` |
| `%lumaguilds_guild_tag_raw%` | Tag exactly as stored (may be legacy `&` codes or MiniMessage) |
| `%lumaguilds_guild_emoji%` | Nexo placeholder (`%nexo_<name>%`) — resolves only where Nexo's PAPI expansion runs (backend) |
| `%lumaguilds_guild_emoji_minimessage%` | Nexo glyph tag for MiniMessage formatters like Velocitab (rendered proxy-side via NexoProxy). Matching `:name:` values become `<glyph:name>`; null/blank returns empty; other values pass through unchanged |
| `%lumaguilds_guild_emoji_font%` | Raw MiniMessage font fragment (`<font:<glyphfont>><char></font>`) for MiniMessage consumers without Nexo glyph-tag support (UnlimitedNameTags, IC-DiscordSRV-Addon playerlist, Velocitab via PapiProxyBridge). Resolves char/font from Nexo's FontManager; falls back to `<glyph:name>` when Nexo is unavailable |
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

### Prestige

Cached for 30 seconds per guild and refreshed as soon as the guild prestiges.
Empty for players without a guild, or if the prestige data can't be read.

| Placeholder | Value |
|---|---|
| `%lumaguilds_guild_prestige%` | Prestige tier, `0` to `6` |
| `%lumaguilds_guild_prestige_roman%` | `I` to `VI`; empty at tier 0 |
| `%lumaguilds_guild_prestige_badge%` | Badge text from `progression.prestige.badges`; empty at tier 0 |
| `%lumaguilds_guild_prestige_badge_minimessage%` | The same badge as MiniMessage, for Velocitab and other MiniMessage tab lists |
| `%lumaguilds_guild_prestige_max%` | Configured lifetime maximum |
| `%lumaguilds_guild_prestige_first_tier%` | Highest tier this guild reached first on the server; `0` if none |
| `%lumaguilds_guild_prestige_last_date%` | UTC date of the latest prestige (`2026-10-24`); empty if none |

For TAB or nametags, put `%lumaguilds_guild_prestige_badge%` before the guild tag.
The default badges are coloured text (`✦I` to `✦VI`); replace them with Nexo glyph
text once the badge glyphs are in the resource pack.

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
| `%lumaguilds_guild_rank_prestige%` | Prestige tier, then level and XP |

---

## Top-N Leaderboards

Format: `%lumaguilds_top_<category>_<rank>_<field>%`

- **category** — `level` · `balance` · `activity` · `members` · `age` · `prestige`
- **rank** — `1` to `25` (1-based)
- **field** — see table below

Cached for 30 seconds. Returns `""` if the slot is empty.

| Field | Value |
|---|---|
| `name` | Guild name |
| `tag` | Formatted tag |
| `tag_mm` | Tag normalized to MiniMessage |
| `tag_plain` | Tag with formatting stripped |
| `id` | Guild UUID |
| `value` | The metric being ranked on (level / balance / activity score / members / epoch second for age / prestige tier) |
| `level` | Guild level |
| `members` | Member count |
| `balance` | Bank balance |
| `activity` | Current week activity score |
| `age_days` | Days since creation |
| `emoji` | Nexo placeholder (`%nexo_<name>%`) |
| `emoji_mm` | Nexo glyph tag — `:name:` → `<glyph:name>`; null/blank → empty; other values pass through |
| `prestige` | Prestige tier |
| `prestige_roman` | `I` to `VI`; empty at tier 0 |
| `prestige_badge` | Configured badge; empty at tier 0 |

The `prestige` category orders guilds by tier, then current-run level and XP, so it
also shows which guilds are closest to their next prestige.

### Examples

```text
%lumaguilds_top_balance_1_name%        → "DiamondDelvers"
%lumaguilds_top_balance_1_value%       → "152340"
%lumaguilds_top_level_3_tag_plain%     → "Wolves"
%lumaguilds_top_activity_2_members%    → "12"
%lumaguilds_top_age_1_age_days%        → "287"
%lumaguilds_top_prestige_1_name%       → "Vibe"
%lumaguilds_top_prestige_1_prestige_badge% → "✦VI" (coloured)
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
# Weekly guild quests

Weekly quest placeholders are read-only. Quest definitions are shared by the server; progress, completion, claim, and bonus values resolve for the viewing player's guild. Missing players/guilds and invalid quest indexes return an empty string for indexed fields and safe zero/false values for aggregate fields.

- `%lumaguilds_guild_weekly_quests_time_remaining%` — time until reset (`2d 14h 32m`, floored at zero)
- `%lumaguilds_guild_weekly_quests_count%` / `%lumaguilds_guild_weekly_quests_total%` — active quest count
- `%lumaguilds_guild_weekly_quests_completed%` — milestones completed by the player's guild
- `%lumaguilds_guild_weekly_quests_all_completed%` — whether every milestone is complete
- `%lumaguilds_guild_weekly_quests_bonus_exp%` — configured full-set Guild EXP bonus
- `%lumaguilds_guild_weekly_quests_bonus_awarded%` — whether the player's guild received that bonus

Indexed placeholders use a one-based quest number:

- `%lumaguilds_guild_weekly_quest_1_name%`
- `%lumaguilds_guild_weekly_quest_1_description%`
- `%lumaguilds_guild_weekly_quest_1_action%`
- `%lumaguilds_guild_weekly_quest_1_target%`
- `%lumaguilds_guild_weekly_quest_1_required%`
- `%lumaguilds_guild_weekly_quest_1_progress%`
- `%lumaguilds_guild_weekly_quest_1_progress_percent%`
- `%lumaguilds_guild_weekly_quest_1_completed%`
- `%lumaguilds_guild_weekly_quest_1_claimed%`
- `%lumaguilds_guild_weekly_quest_1_reward_exp%`
