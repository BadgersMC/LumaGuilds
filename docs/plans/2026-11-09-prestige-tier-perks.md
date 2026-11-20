# Prestige tier perks and allied-home access

Requirements REQ-179 to REQ-182. Decisions (Fain, 2026-11-09), from the Discord
"Prestige Suggestions" thread: practical rewards unlock permanently at prestige
tiers; guild-owned stalls get a cap of 1 with +1 from prestige; allied-home
access control is for every guild, not just prestiged ones.

## Tier perks (permanent, by current prestige tier)

| Tier (default) | Perk | Config |
|---|---|---|
| II | A second allied-home slot | `second_ally_home_tier` |
| III | One more guild-owned market stall (cap 1 becomes 2) | `extra_stall_tier` |

- The perk follows the guild's current prestige tier from the cached standing
  (30 s). A failed read means no perk, never a crash.
- The prestige confirmation lists the perk the new tier starts. The second
  allied home joins that list when it ships.
- Other plugins read the stall bonus from the public `GuildPrestigePerkLookup`
  service (Bukkit ServicesManager).

## Delivery

1. LumaGuilds `feature/prestige-tier-perks`: config, Quick Travel (REQ-179),
   stall bonus API (REQ-181 LumaGuilds side), confirmation lines.
2. EnthusiaMarket `feature/guild-stall-cap`: the cap (REQ-181, Market REQ-399).
3. LumaGuilds follow-up: second allied home (REQ-180) and allied-home access
   (REQ-182); both change how allied homes are stored.

Quicker home teleports were first planned as a Prestige I perk. Fain moved
them into Quick Travel instead (2026-11-09): see "Quick Travel" below.

## Quick Travel (every guild, bought as a level reward)

Quick Travel 1-5 (Lv10-50, multiplier 0.9 to 0.5) used to shorten only the
cooldown between `/g home` and `/g allyhome` uses. With the default 5-second
cooldown that saved at most 2.5 seconds, and the Java and Bedrock home menus
skipped the cooldown entirely. Now (REQ-179):

| Quick Travel | none | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| Countdown | 5 s | 4 s | 4 s | 3 s | 3 s | 2 s |
| Cooldown | 100% | 90% | 80% | 70% | 60% | 50% |

- One cooldown for commands and menus, kept in `TeleportationService`.
- War-banner teleports keep the plain 5-second countdown and no cooldown.
- `guild.home_teleport_warmup_seconds` is still unused (the base countdown
  stays 5 s); the base cooldown is `guild.home_teleport_cooldown_seconds`.

## Guild stall cap (EnthusiaMarket)

- Guild-owned stalls are unlimited today (only solo players have limit groups).
- New: a guild may own at most `1 + bonus` stalls, where the bonus comes from
  LumaGuilds (1 at Prestige III by default). Market config
  `guild-stall-limit: 1` (`-1` keeps today's unlimited behaviour).
- Guilds already above the cap keep every stall they own; they just can't buy
  another until they are under it. Staff bypass still applies.

## Second allied home (Prestige II)

- Today a guild has one allied home (`allyHome`) with an allow-list of allied
  guilds. Prestige II adds a second slot; both share the same access rules.
- Set, move, remove and teleport work for both slots on Java and Bedrock; a
  guild that drops below tier II (disband only, since tiers never go down)
  keeps nothing extra.

## Allied-home access control (every guild)

The host guild decides who may use its allied homes, on one page (Java and
Bedrock):

1. **Which allies** (exists today): toggle each allied guild.
2. **Which of their ranks**: for each allowed ally, pick the ranks of that
   guild that may use it. Default: every rank that has "Use ally homes" in
   its own guild (today's behaviour).
3. **Blocked players**: add or remove individual players; a blocked player
   can never use the host's allied homes, whatever their rank.

Visitors still need "Use ally homes" in their own guild. Click-time and
countdown-end checks use the current rules (REQ-141 behaviour).

## Out of scope

- Prestige perks beyond these three.

## As built (part 3)

- Storage: `ally_home_visitor_ranks`, `ally_home_blocked_players`,
  `ally_home_second_homes` (schema v51), loaded into memory at start and written
  through. Disband removes a guild's rows.
- Commands: `/g setallyhome [2]`, `/g removeallyhome [2]` (removing works even
  without the perk), `/g allyhome <guild> [2]`. The home menu lists a second
  home as "<guild> #2".
- Access page (Java): allies in rows 1-2 (left-click allow/deny, right-click
  their page), blocked players in row 3 (click to unblock). Each ally's page:
  their ranks (click to allow/deny, "Allow every rank" resets) and their
  members (click to block/unblock).
- Access page (Bedrock): one button per ally opens a form with the ally toggle,
  rank toggles and member block toggles; "Blocked players" lists blocks to untick.
- Rank picks and blocks apply to every visitor, the ally's owner included; the
  visitor still needs Use ally homes in their own guild. The check runs again
  when the countdown ends.

## Outbound picks per ally (REQ-183, Fain 2026-11-09)

"I might want members to have access to a certain ally home but not another."
On each ally's page the guild also picks which of its OWN ranks may use that
ally's homes. No pick: the rank's Use ally homes permission decides (as
before). The owner always keeps access. Stored in `ally_home_outbound_ranks`.

## Allied Homes page (Fain, 2026-11-09: "I can't see ally homes in the menu at all")

Allied homes could only be set by command, and the Homes menu hid the whole
feature unless the guild owned Allied Waypoints (Lv50). Now managers always see
the door: locked (with how to unlock) without the reward, otherwise it opens
**Allied Homes**: Allied Home 1 and 2 (left-click set/move here, right-click
remove; 2 locked below Prestige II), the access page, and a how-it-works note.
Bedrock's access form gets the same set/move/remove buttons and a locked message.
Setting, moving and removing allied homes now needs Manage home (was Manage
members), matching guild homes and the access page (Fain, 2026-11-09).
