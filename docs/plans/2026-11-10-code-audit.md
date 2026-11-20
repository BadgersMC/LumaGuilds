# Code audit, 2026-11-10

Scope: `main` at `8206532` plus the open prestige branches (#45, #46). About
143,000 lines of Kotlin in 1,046 files; there is no static-analysis tool in the
build, so this was pattern scans plus reading the flagged code.

## Checked and found sound

- **SQL injection.** Every interpolated SQL fragment is a constant, an enum
  mapping or a placeholder list; values are bound.
- **Rank privilege escalation.** `RankServiceBukkit` checks `canEditRank`,
  `canGrant`, rank priority and ownership transfer on every write.
- **XP and quest farming through cancelled events.** Progression and quest
  listeners run at MONITOR or HIGHEST with `ignoreCancelled = true`.
- **Console command dispatch.** LuckPerms emoji grants validate the node
  against a pattern and refuse off-thread; prestige credits substitute only a
  UUID, a player name and a tier.
- **Command permissions.** Every `/g` subcommand has a permission except
  `info`, `notifications` and the public `strikes` view, which are meant to be
  open; `strikes punish` is gated.

## Found and fixed in this change

1. **Allied Waypoints could be skipped by command.** The Homes menu required
   the level 50 reward on both guilds; `/g setallyhome` and `/g allyhome` did
   not check it at all. The check now lives in the guild service, so commands,
   Java menus and Bedrock forms agree.
2. **An ally with only a second allied home was not listed** in the Homes menu
   or tab completion, because the list was built from first homes.
3. **Staff prestige grants skipped the war and frozen-gold guards.** REQ-186
   waives only the fee and the level requirement; the other guards are back.
4. **`/lumaguilds prestige` tab completion read the database on every
   keypress.** Facts are now kept for five seconds per guild.
5. **The `prestige set` preview did not say a home slot goes with each removed
   tier.** It does now.

## Known limits, not changed

- The allied-home ally page shows at most 9 of a guild's ranks per row on
  Java, and the Bedrock form lists at most 40 members to block.
- 51 `catch (_: Exception)` blocks and 40 `printStackTrace()` calls remain.
  The ones read were deliberate fallbacks; they were not all reviewed.
- Not audited: the gold and vault duplication paths, war wager settlement,
  cross-server concurrency, and anything only a live client can show.
