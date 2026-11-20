# Simplified guild statistics (REQ-199 to REQ-202)

Status: approved mockup ("Guild Statistics Simplified", 2026-11-10), building.

## The bug

Nothing saved player kills. `KillService.recordKill` had no caller, and its
SQLite-only repository never got guild IDs, so every kill-based button read 0:
Kill Statistics, K/D Analysis, Performance, Top Killers, Recent Activity,
combat Achievements, Rivalries and Guild Comparison.

The weekly activity counters are no substitute: `weekly_activity` keys on
`guild_id` alone, so each new week replaces the last. No kill history exists
anywhere, so the new log starts empty on release day.

## The fix

A new kill log, `guild_kill_log`, created by its repository on SQLite and
MariaDB. Each player kill saves killer, victim, both guilds (when each is in
exactly one guild), weapon and time. It is skipped when:

- the killer killed themselves or there is no player killer,
- neither player is in a guild,
- both are in the same guild,
- the same killer killed the same victim less than 10 minutes ago (farming).

`KillService` is re-implemented on top of the log, so the placeholders, the
Discord guild profile and the achievement milestones that already read it start
working without changes.

## The menu

3 rows. Top row: Combat 2, Wars 3, Recent Activity 4, Rivalries 5,
Achievements 6. Middle row: Top Killers 12, Top Contributors 13, Top Inviters
14. Back 22.

- **Combat** merges Kill Statistics, K/D Analysis, Performance and Period
  Statistics: kills, deaths and K/D, then kills this week, in the last 30 days
  and all time.
- **Recent Activity** lists the last 15 kills and deaths involving the guild.
- **Rivalries** lists the guilds this guild has fought most, by kills both ways.
- **Top Killers** lists the top 10 members by kills.
- Wars, Achievements, Top Contributors and Top Inviters keep their pages.

Removed: Member Statistics (the guild page shows members), Guild Comparison
(cross-guild ranking belongs to leaderboards), Performance's invented
efficiency score, and Refresh (opening the menu always loads fresh numbers).

Bedrock gets the same entries.
