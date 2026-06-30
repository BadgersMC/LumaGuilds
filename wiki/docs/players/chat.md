---
title: Chat
audience: player
topic: chat
summary: Toggle guild and ally chat, and customize how your tag appears in messages.
keywords: [chat, guild chat, ally chat, tag, rosechat]
related: [identity, alliances]
updated: 2026-05-13
---

# Chat

Toggle guild and ally chat, and customize how your tag appears in messages.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g chat` | `lumaguilds.guild.chat` | Toggle guild chat on/off. |
| `/gc <message>` | `lumaguilds.guild.chat` | Send one guild msg (no toggle). |
| `/g allychat` | `lumaguilds.guild.chat` | Toggle ally chat on/off. |
| `/gac <message>` | `lumaguilds.guild.chat` | Send one ally msg (no toggle). |
| `/g modchat` | `lumaguilds.guild.chat` | Toggle mod chat on/off (moderators). |
| `/gmc <message>` | `lumaguilds.guild.chat` | Send one mod msg (no toggle). |
| `/ga [&color] <msg>` | `lumaguilds.guild.chat` | Send announcement (moderators). |

## How it works

By default your messages go to global chat. `/g chat` flips you into guild chat — anything you type goes only to your guildmates until you toggle it off. `/g allychat` does the same but the audience is all guilds you have an ACTIVE alliance with. Tags and colors render in chat via the server's chat plugin (RoseChat on EnthusiaSMP).

## Toggling guild chat

Run `/g chat` once to enter guild chat. Type your messages. Run `/g chat` again to return to global. Same command, same key — no juggling needed. A prefix or indicator in chat shows you which channel is active.

## Toggling ally chat

Use `/g allychat` to enter ally chat. Same on/off behavior as guild chat. Only members of guilds you have an ACTIVE alliance with can read your messages. Useful for coordinating with allied guilds without spamming global.

## One-shot messages (no toggle)

`/gc <message>` sends a single message to guild chat without changing your
chat channel. You stay in global (or wherever you were). Great for a quick
\"brb\" or \"meet at spawn\" to your guildmates.

`/gac <message>` does the same for ally chat — no toggle in, message, toggle
out needed.

`/gmc <message>` sends a single message to guild moderators without changing
your channel. Only moderators can send or receive mod chat messages.

`/ga [&color] <message>` sends a highlighted announcement to all guild
members. Supports color codes &0 through &9 (default &6 gold). Only
moderators with SEND_ANNOUNCEMENTS permission can use this.

## How tag formatting renders

Your guild tag (set via `/g tag`) can include MiniMessage formatting like `<gradient:#FF0000:#00FF00>Rainbow</gradient>`. RoseChat parses that and renders the colors. If you don't see colors in chat, the chat channel you're in may not be configured to format them — try `/g chat` to switch to guild chat, where formatting always renders.

## Where messages go with RoseChat

LumaGuilds hands guild chat off to RoseChat as a dedicated channel. RoseChat owns formatting, delivery, and recipients. From a player's perspective, nothing changes — `/g chat` still flips you in and out. Internally, your channel is being switched. See [Identity](identity.md) for how to set and customize your tag.

## Recently Fixed/Changed

- Switching between guild chat and ally chat preserves your original channel (e.g. global), so toggling off returns you to where you started, not to the last guild/ally channel you used.
- The first message after toggling guild chat off is no longer dropped (fixed in 2026 — there was a stale-marker bug).
- MiniMessage in tags renders correctly in chat — both colors and gradients work.
- Ally chat only reaches guilds with an ACTIVE alliance; pending or neutral relations don't count.

## Related

- [Identity](identity.md) — set your guild tag and customize your appearance
- [Alliances & Diplomacy](alliances.md) — who hears ally chat
