# Guild member capacity by level

Requirement REQ-190. Decision (Fain, via the 2026-11-09 handoff): new guilds
start at 10 members and gain 5 member slots every 5 levels, up to 100.
Existing guilds already above their new cap are grandfathered and lose no one.

## Today

Every guild has the same cap, `guild.max_members_per_guild` (50 by default),
because the Chapter Two reward path passes that flat number as the member
capacity. Nothing grows with level.

## New rule

| Guild level | 1-4 | 5-9 | 10-14 | ... | 85-89 | 90+ |
|---|---|---|---|---|---|---|
| Member cap | 10 | 15 | 20 | ... | 95 | 100 |

`cap = min(max, base + per_step * floor(level / step_levels))`, with config
`guild.member_capacity` (`enabled: true`, `base: 10`, `per_step: 5`,
`step_levels: 5`, `max: 100`). `guild.max_members_per_guild` stays the
server's absolute ceiling; its default rises to 100.

### Prestige

A prestige resets the level to 1. A guild that has prestiged keeps the cap of a
level 100 guild (100), so prestiging never shrinks a guild's room.

### Grandfathering

When this ships, each existing guild's member count is recorded once as its
protected floor. A guild's cap is never below its floor, so:

- nobody is removed, and nobody is ever removed because of the cap;
- a grandfathered guild can refill to its floor after members leave;
- the floor never grows; above it the level rule applies.

Guilds created later have no floor.

### Where it applies

Joining (invites, requests, open join, LFG), the LFG and recruitment listings,
the member counts shown on guild pages, the Discord profile and Getting
Started all read the same limit. The Chronicle and progression pages show it
too, instead of the flat config number.

## Out of scope

- Buying extra member slots.
- Changing any guild above its cap; that only blocks new joins.
