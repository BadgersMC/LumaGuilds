---
title: PlaceholderAPI placeholders
audience: admin
topic: placeholderapi
summary: Every %lumaguilds_…% PAPI placeholder, what it returns, and where to use it.
keywords: [placeholderapi, papi, placeholders, tab, chat]
related: [installation, rosechat]
updated: 2026-05-14
---

# PlaceholderAPI placeholders

LumaGuilds integrates with PlaceholderAPI to expose guild information in chat, tab lists, scoreboards, and any plugin that supports PAPI.

## How it works

LumaGuilds registers a PlaceholderAPI expansion with identifier `lumaguilds`. Placeholders take the form `%lumaguilds_<key>%` and resolve in player context. The expansion provides multiple rendering variants for tag-based placeholders:

- **`%lumaguilds_guild_tag%`** — MiniMessage tags converted to legacy `§`-style color codes (safe for chat plugins that don't parse MiniMessage)
- **`%lumaguilds_guild_tag_raw%`** — Raw MiniMessage string as stored (for plugins that parse MiniMessage themselves)
- **`%lumaguilds_guild_tag_plain%`** — All formatting stripped (plain text only)

The same variants apply to `guild_display` and `guild_chat_format`.

## All placeholders

| Placeholder | Returns | Notes |
|-------------|---------|-------|
| `guild_name` | Text | Player's guild name |
| `guild_tag` | Text (MiniMessage→legacy) | Guild tag rendered to `§`-codes |
| `guild_tag_raw` | Text (MiniMessage) | Guild tag as stored (may contain `<color>`, `<bold>`, etc.) |
| `guild_tag_plain` | Text (plain) | Guild tag with formatting stripped |
| `guild_emoji` | Text | Guild emoji, converted to Nexo PAPI format (`%nexo_<name>%`) |
| `guild_level` | Integer | Current guild level |
| `guild_balance` | Integer | Virtual bank balance (coins) |
| `guild_mode` | Text | `Peaceful` or `Hostile` |
| `guild_members` | Integer | Total member count (online and offline) |
| `guild_online_members` | Integer | Members currently online |
| `guild_rank` | Text | Player's rank in the guild (Owner, Admin, Member, etc.) |
| `guild_kills` | Integer | Total guild kills (PvP) |
| `guild_deaths` | Integer | Total guild deaths (PvP) |
| `guild_kdr` | Float (2 decimals) | Kill-to-death ratio, e.g., `2.45` |
| `guild_efficiency` | Float (1 decimal + %) | Kill efficiency percentage, e.g., `62.5%` |
| `guild_wars_total` | Integer | Total wars participated in (all-time) |
| `guild_wars_active` | Integer | Active wars right now |
| `guild_age_days` | Integer | Days since guild creation |
| `guild_xp_total` | Integer | Total experience earned |
| `guild_xp_this_level` | Integer | Experience earned in current level |
| `guild_xp_next_level` | Integer | Total experience needed for next level |
| `guild_xp_to_next` | Integer | Experience remaining to level up |
| `guild_xp_progress_pct` | Float (1 decimal) | Percent progress through current level, e.g., `62.5` |
| `guild_rank_level` | Integer or blank | Player's guild rank in level leaderboard (1–25, blank if not ranked) |
| `guild_rank_balance` | Integer or blank | Player's guild rank in balance leaderboard |
| `guild_rank_activity` | Integer or blank | Player's guild rank in weekly activity leaderboard |
| `guild_rank_members` | Integer or blank | Player's guild rank in member count leaderboard |
| `guild_rank_age` | Integer or blank | Player's guild rank in age (oldest) leaderboard |
| `guild_total_count` | Integer | Total number of guilds on the server |
| `has_guild` | `true` or `false` | Whether player is in a guild |
| `guild_display` | Text | Formatted string: `[emoji] tag [level]` (all three combined) |
| `guild_chat_format` | Text | Chat-friendly format: `emoji tag` |

### Leaderboard placeholders (top 25)

Format: `%lumaguilds_top_<category>_<rank>_<field>%`

**Categories:** `level`, `balance`, `activity`, `members`, `age`

**Rank:** `1` to `25` (1 = highest). Returns blank if guild is not in top 25.

**Fields:**

- `name` — Guild name
- `tag` — Guild tag (MiniMessage→legacy `§`-codes)
- `tag_plain` — Guild tag (plain text, no formatting)
- `id` — Guild UUID
- `value` — Numeric score (level, balance, activity score, member count, or age in days)
- `level` — Guild level
- `members` — Member count
- `balance` — Virtual bank balance
- `activity` — Weekly activity score
- `age_days` — Days since creation
- `emoji` — Guild emoji (`%nexo_<name>%`)

**Examples:**

- `%lumaguilds_top_level_1_name%` — Name of the #1 guild by level
- `%lumaguilds_top_balance_3_value%` — Bank balance of #3 ranked guild
- `%lumaguilds_top_activity_2_members%` — Member count of #2 guild (by activity)

Top-N placeholders are cached for 30 seconds to reduce database load.

### Relation indicator placeholder

Two forms — pick the one that fits how your consumer plugin invokes
placeholders:

| Form | When to use |
|------|-------------|
| `%rel_lumaguilds_status%` | **Recommended for tab plugins** (NEZNAMY/TAB and ProtocolLib-based formatters). This is a PAPI *relational* placeholder; PAPI hands LumaGuilds both viewer and target, so each tab row gets the correct per-pair icon without any nested-placeholder workaround. |
| `%lumaguilds_rel_<player>_status%` | Use in chat formats where the chat plugin substitutes the target's name into the format string (e.g. RoseChat's `{sender}`). `<player>` is metavariable — replace with the actual substitution token your chat plugin uses, or a real name for `/papi parse`. |

Both forms return the same icon set:

| Icon | Meaning |
|------|---------|
| `🔴` | Enemy (guilds at war) |
| `🔵` | Ally (guild alliance active) |
| `🟢` | Teammate (same guild) |
| `⚪` | Truce (temporary truce active) |
| (blank) | Neutral, no relation, target offline, or either player not in a guild |

**Notes:**

- Only renders for **active** relations. Pending ally requests and expired truces return blank.
- Same guild always returns `🟢`.
- For `%lumaguilds_rel_<player>_status%`, the target must be online — name lookup goes through `Bukkit.getPlayer(name)`.
- For `%rel_lumaguilds_status%`, PAPI supplies the target directly — no name lookup needed; works as long as the consumer plugin has a target Player in context.

## Where to use them

### Tab list (TAB plugin, DeluxeMenus)

Use `guild_tag`, `guild_emoji`, `guild_level`, `guild_display`:

```yaml
# TAB header
header:
  - "Welcome to %lumaguilds_guild_name%!"
  - "Rank: %lumaguilds_guild_rank% | Level: %lumaguilds_guild_level%"
```

### Chat format (RoseChat, EssentialsChat)

Use `guild_tag`, `guild_chat_format`, `guild_emoji`:

```yaml
# RoseChat channel config
message-format: "%lumaguilds_guild_chat_format% %player_displayname% ⋙ {message}"
```

### Scoreboards (FeatherBoard, TAB Scoreboard)

Use `guild_name`, `guild_rank`, `guild_level`, `guild_balance`, `guild_kdr`:

```yaml
# TAB scoreboard lines
lines:
  - "&eGuild: &f%lumaguilds_guild_name%"
  - "&eRank: &f%lumaguilds_guild_rank%"
  - "&eLevel: &f%lumaguilds_guild_level%"
  - "&eBalance: &f$%lumaguilds_guild_balance%"
  - "&eK/D: &f%lumaguilds_guild_kdr%"
```

### Holograms (Holographic Displays)

Use any placeholder. Update interval depends on holo plugin:

```yaml
hologram:
  - "Guild: %lumaguilds_guild_name%"
  - "Level: %lumaguilds_guild_level%"
  - "Members: %lumaguilds_guild_members%"
```

### Player list (TAB / tab-formatting plugins)

Use the PAPI **relational** placeholder `%rel_lumaguilds_status%`. PAPI hands
the tab plugin both the viewer and the row's player, and LumaGuilds returns
the icon for that specific pair. No nested-placeholder substitution needed.

```yaml
# tab.yml (NEZNAMY/TAB style)
tabsuffix: "%lumaguilds_guild_tag% %rel_lumaguilds_status%"
tagsuffix: "%lumaguilds_guild_tag% %rel_lumaguilds_status%"
```

For each row in the tab list, PAPI calls the relational variant of the
LumaGuilds expansion with `(viewer, target)` from the row context. The icon
is computed per pair, so each row in the tab list gets the correct relation
indicator. No `%player_name%` workaround is required.

This is the recommended syntax for tab plugins. The older
`%lumaguilds_rel_<player>_status%` form (target supplied as part of the
placeholder name) still works but requires the consumer to substitute the
target's name into the placeholder string — see the chat example below.

### Chat format with relation status

For chat, the chat plugin substitutes the sender's name into the format
string before PAPI resolves. With RoseChat that's the `{sender}` token:

```yaml
# RoseChat channel format (sketch)
format: "[%lumaguilds_guild_tag%]%lumaguilds_rel_{sender}_status% {sender} > {message}"
```

RoseChat replaces `{sender}` with the speaker's name; PAPI resolves the
outer placeholder against the viewer's guild. Result for a viewer at war
with Steve's guild:

```text
[Knights]🔴 Steve > hello
```

## Gotchas

**Tab plugins: use `%rel_lumaguilds_status%`, not `%lumaguilds_rel_<player>_status%`.**
TAB-style plugins don't reliably do nested PAPI resolution, so attempts like
`%lumaguilds_rel_%player_name%_status%` will fall apart and render the
literal placeholder string in the tab list. The relational form
`%rel_lumaguilds_status%` is the correct invocation for those contexts —
PAPI passes both viewer and target directly.

**`<player>` in `%lumaguilds_rel_<player>_status%` is metavariable, not literal.**
When this doc writes `<player>`, it means *"the target player's name goes
here"*. **Do not put the literal string `<player>` in your config.** Where
the substitution comes from:

- **Chat plugins (RoseChat, etc.)** — the chat plugin's sender token
  (e.g. RoseChat's `{sender}`): `%lumaguilds_rel_{sender}_status%`.
- **Direct lookups (custom plugins / `/papi parse`)** — substitute the actual
  player's name verbatim: `/papi parse Bob %lumaguilds_rel_Steve_status%`
  returns Bob's relation to Steve.

**MiniMessage tags leak in non-MiniMessage plugins:** If you use `%lumaguilds_guild_tag_raw%` in a plugin that doesn't parse MiniMessage (like vanilla TAB), you'll see raw tags like `<color:#FF5733>Elite</color>` in chat. Use `%lumaguilds_guild_tag%` (the legacy variant) instead — it converts MiniMessage to `§`-codes automatically.

**Relation indicator returns empty, not a space:** Neutral relations return a completely blank string (not a space). If you want consistent spacing, add a space yourself in the surrounding format.

**Leaderboard placeholders are read-only:** Top-N placeholders cannot be reordered or filtered; they always return top 25 in the hardcoded order (level, balance, activity, members, age).

**Player must be online for relation checks:** the relation placeholder requires the target player to be online at the time of resolution. Offline players return blank.

## Related

- [Installation & config.yml](installation.md) — install LumaGuilds and configure it
- [RoseChat integration](rosechat.md) — set up guild chat with RoseChat
