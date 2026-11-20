---
title: LFG & Invites
audience: player
topic: lfg
summary: Manage guild invites, decline requests, find guilds via LFG, and kick or ban members.
keywords: [lfg, invite, accept, decline, kick, ban, invites]
related: [guilds, ranks]
updated: 2026-11-10
---

# LFG & Invites

Manage guild invites, decline requests, find guilds via LFG, and kick members.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g invite <player>` | `lumaguilds.guild.invite` | Invite a player to your guild. |
| `/g invites` | `lumaguilds.guild.invites` | List your pending invites. |
| `/g decline <guild>` | `lumaguilds.guild.decline` | Decline an invite from a guild. |
| `/g lfg` | `lumaguilds.guild.lfg` | Toggle "looking for guild" status. |
| `/g kick <player>` | `lumaguilds.guild.kick` | Kick a member from your guild. |
| `/g ban <player> [confirm]` | `lumaguilds.guild.ban` | Remove a player and stop them rejoining. |
| `/g unban <player>` | `lumaguilds.guild.ban` | Let a banned player join again. |

## How it works

Invites move players into guilds. A guild member with permission sends an invite; the target sees a notification and can accept or decline. On the flip side, players can broadcast "looking for guild" via `/g lfg` so guild leaders know to recruit them. Kicks remove members from your guild — they work on both online and offline players, subject to rank priority.

## Inviting a player

Run `/g invite <player>` to send them an invite:

```text
/g invite Steve
```

They receive a clickable message. Clicking accept automatically runs `/g join` for them. The invite persists until they accept or decline.

## Listing your pending invites

Use `/g invites` to see every guild that has invited you:

```text
/g invites
```

The list shows clickable accept and decline buttons for each invite.

## Accepting an invite

Click the accept button in your invites list, or manually run `/g join <guild>`:

```text
/g join Dragons
```

You're now a member of that guild. See [Guilds](guilds.md) for details.

## Declining an invite

Use `/g decline <guild>` to reject an invite:

```text
/g decline Dragons
```

The invite is removed. You can re-accept later if the guild invites you again.

## Toggling "looking for guild"

Run `/g lfg` to toggle your recruitment status:

```text
/g lfg
```

While on, your name appears in guild leaders' recruitment lists (if the server supports LFG discovery). Toggle it off once you find a guild.

## Kicking a member (online and offline)

Run `/g kick <player>` to remove them from your guild:

```text
/g kick Steve
```

This works whether they're online or offline. Your rank must outrank theirs — otherwise the kick is refused. (See [Ranks & Permissions](ranks.md) for rank hierarchy.)

## Banning a player

A ban kicks the player (if they are a member) and blocks invites, applications and join requests from them until someone unbans them. You need the Manage Members permission and, for a member, a higher rank than theirs. The guild leader can't be banned.

```text
/g ban Steve
/g ban Steve confirm
```

The first command only says what will happen; add `confirm` to do it. You can also ban from a member's page in `/g info`, and unban from **Banned players** on the same page, or with `/g unban <player>`.

## Gotchas

- Online kicks from the menu now respect rank priority too (an officer could kick a co-leader there — fixed in 2026).
- Offline kicks now respect rank priority (used to let mods kick anyone offline — fixed in 2026).
- The promote/demote confirmation menu shows the real player name even when the target is offline (used to say "Unknown Player" — fixed in 2026).
- Open vs invite-only is a guild-level setting; even with `/g lfg` on, you can't auto-join an invite-only guild.
- If a player has pending invites when they join another guild, those invites are cleared.

## Related

- [Guilds](guilds.md) — creating, joining, leaving guilds
- [Ranks & Permissions](ranks.md) — managing member ranks and kick priority
