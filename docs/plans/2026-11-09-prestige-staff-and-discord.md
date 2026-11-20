# Prestige staff tools and Discord post

Requirements REQ-184 to REQ-188. Decisions (Fain, 2026-11-09): staff can
inspect, set a guild's prestige, grant a free prestige and re-run member
rewards; prestiges post to Discord through a webhook.

## Staff commands

All under `/lumaguilds prestige`, permission `lumaguilds.admin.prestige`
(child of `lumaguilds.admin`). A guild is its name or id. Commands that change
anything first show a preview and only act when repeated with `confirm` at the
end. Every change is written to the server log with the staff member's name.

| Command | Does |
|---|---|
| `info <guild>` | Prestige count, level, kept rewards, each applied prestige (tier, date, kept reward, by whom), and any staff adjustments |
| `set <guild> <count> [confirm]` | Lowers the prestige count. Each prestige keeps exactly one reward, so the kept rewards of the removed tiers go too (newest first; the preview names them). Level, XP and gold stay. Raising is done with `grant`. Recorded as an adjustment |
| `grant <guild> <reward> [confirm]` | A real prestige with no fee and no level 100 requirement: level resets as normal, the reward is kept, and announcements, fanfare, member credits and the Discord post all run. Without `<reward>` the preview lists the choices |
| `rerun <guild> [tier] [confirm]` | Forgets which members were already credited for that guild's prestiges (all, or one tier) and credits the online members again; offline members get it on their next join |

Notes:

- `set` is for corrections. It does not change prestige history, so `/g info`
  history and "first to reach" stay as they were; perks, badges and the Hall
  follow the new count. The count must always match the kept rewards (one per
  prestige), which is why raising goes through `grant`.
- `rerun` can credit someone twice if the original command did work; the
  preview says so.

## Discord post

When a prestige is committed (by a guild or a staff grant), LumaGuilds posts
one embed to a Discord webhook:

- Title: `<Guild> reached Prestige IV`, coloured by tier.
- Body: who prestiged, the reward they kept, and "First guild to reach
  Prestige IV!" when it is the server's first.
- Footer: the server name from config; timestamp: the commit time.

Config `progression.prestige.discord`: `enabled: false`, `webhook_url: ""`,
`username: "Enthusia"`, `avatar_url: ""`. The post runs off the main thread; a
failed or slow request is logged once and never affects the prestige.
