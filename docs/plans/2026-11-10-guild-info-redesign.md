# Combined guild page and guild bans (REQ-191 to REQ-197)

Status: approved preview, building.

## Why

`/g info` and the member list were two menus. Players had to open the member
list to see who is in a guild, and officers had a third menu to promote,
demote or kick. Fain approved a single page in a phone preview on 2026-11-10
(the "Guild Info Redesign" artifact, version 5).

## What Fain decided

- Info and Members become one 6-row page. Members sit on the first screen.
- Members are sorted by rank, highest first, by default. A sort button cycles
  rank, online, name.
- Visitors see last seen. They do not see contributions or any actions.
- Tapping a member opens a member page with Promote, Demote, Kick and Ban.
- Ban is new. It kicks the player and blocks invites and join requests until
  someone unbans them.
- The guild card shows simple Yes/No lines, like the old Statistics item:
  guild home, banner, vault and guild shop. It shows only whether a home is
  set, not how many or which.

## Guild menu (REQ-198)

Fain approved a simplified guild menu on 2026-11-10 (the "Guild Menu Simplified"
mockup). Members is removed because it opened the same page as Information.

| Row | Slots |
| --- | --- |
| Top | Information 2, Progression 3, guild banner 4, Quests 5, Statistics 6 |
| Middle | Ranks 11, Economy 12, Settings 13, Guild Stalls 14, Recruitment 15 |
| Bottom | Getting Started 18, Diplomacy 21, Warfare 23, Guild Actions 26 |

Guild Actions stays alone in a corner so leave and disband are hard to misclick.

## Layout (Java)

Row 0, centred: description (2), level (3), guild card (4), prestige (5),
relations (6).

Rows 1 to 4: member heads, 36 per page.

Row 5: previous page (45), page (46), sort (47), back (49), invite (51) and
Banned players (52) for viewers with Manage Members, next page (53).

On Java, the old Members and Member Management menus now open this page, so
the dashboard and settings buttons lead here too. Bedrock keeps its forms.

Member page (6 rows): the head at 4, Promote 20, Demote 21, Kick 23, Ban 24,
back 49. A button the viewer cannot use is shown greyed (a plain grey dye)
with the reason. Visitors and members see only the head and back.

Relations page (6 rows): allies on row 1, enemies on row 2, truces on row 3,
each row starting with a label item and up to 8 guilds. With more than 8
allies or enemies, the last slot becomes "and N more", which opens the existing
relation browser. Truces have no browser and stop at 8. Clicking a guild opens
its guild page.

Icons are the Enthusia `lg_` items with vanilla fallbacks. Ban has no pack
icon yet; it uses `lg_ban` with a `BARRIER` fallback until one is drawn.

## Viewers

| Viewer | Who | Sees |
| --- | --- | --- |
| Visitor | not in this guild | ranks, online, last seen, joined |
| Member | in this guild | the above, plus bank contribution |
| Officer | has Manage Members | the above, plus actions and invite |

"Officer" here means the permission, not a rank name.

## Ban rules

- Needs Manage Members.
- The actor must outrank the target (a lower rank priority number), when the
  target is a member. The owner can never be banned.
- Banning a member removes them (recorded as KICKED) and then saves the ban.
  Banning someone who is not a member just saves the ban.
- A banned player cannot be added to that guild by any route: invite accept,
  application accept, LFG or open join. `MemberService.addMember` is the one
  check; invites and applications also refuse early so the sender gets a
  message.
- Pending invites from that guild to the player are removed when the ban is
  saved.
- If the ban list cannot be read, joining that guild is refused (fails closed)
  and the player is told to try again.
- Unban needs Manage Members. Bans have no expiry.
- Commands: `/g ban <player> [confirm]`, `/g unban <player>`.
  Without `confirm`, `/g ban` shows what will happen.
- Bans are kept when the player leaves the server. Disbanding the guild drops
  its bans.

## Kick hierarchy fix

`MemberService.removeMember` did not check that the actor outranks the target,
so the kick confirmation menu let an officer kick a co-leader (the `/g kick`
offline path checked; the online path did not). It now refuses unless the
actor's rank priority is lower than the target's. Leaving voluntarily is
unchanged.

## Guild shop line

LumaGuilds cannot see EnthusiaMarket stalls. It adds a public API,
`net.lumalyte.lg.api.GuildShopLookup`, that EnthusiaMarket registers in
Bukkit's ServicesManager. When no lookup is registered, the guild shop line is
left out rather than showing a wrong "No". EnthusiaMarket registers it in a
follow-up PR after this release.

## Bedrock

The Bedrock guild info form gets the same Yes/No lines. The Bedrock member
detail form gets a Ban button with the same rules. A Bedrock banned-players
form lists bans with an Unban button each.

## Requirements

See REQ-191 to REQ-197 in `docs/requirements.md`.

## Not in scope

- Ban reasons and timed bans.
- A pack icon for Ban (needs art).
- Per-member XP contributions; only bank contributions are tracked today.
