# Long-term guild advancements

Goal: give guilds advancements that take months to earn (REQ-224 to REQ-226).

## Approach

- Most new advancements reuse activity keys the plugin already tracks, so they are new
  rows in `guild-achievements.json` chained after the current ones on the same tree row.
- Two new guild-wide keys, checked on join and each minute like level and playtime:
  `GUILD_MEMBER_COUNT` (current members) and `GUILD_AGE_DAYS` (whole days since creation).
- Remove the 1,000-hour ceiling on combined playtime; the inspector text uses each node's goal.

## Notes

- Collective counters (blocks, mobs, crops, smelting, sales) stay personal records credited
  to online members, as today. Playtime, members, age and level read the guild's real totals.
- Vanguard Status moves to the end of the level chain (25, 50, 75, then 100).

## Guild XP rewards (REQ-227, REQ-228)

- Each node gets a `guildXp` amount: TASK 500, GOAL 2,500, CHALLENGE 10,000, top tiers 50,000; joke/penalty nodes and the root pay 0.
- Payment reuses `awardUncappedSystemExperienceOnce` with transaction ID `nameUUIDFromBytes("lumaguilds:advancement-xp:<guild>:<node>")`; the XP ledger rejects duplicates, so the first member to complete a node pays and nobody else does.
- New source `GUILD_ADVANCEMENT`, excluded from weekend and dated boosts like quest and admin XP.
- Guild-wide nodes (level, play hours, members, age) are paid from the guild's totals each minute, cached in memory per guild, so guilds that already qualify are paid once at rollout.
- Supersedes the closed prototype #25 (no IP checks or outbox; the XP ledger is the receipt).
