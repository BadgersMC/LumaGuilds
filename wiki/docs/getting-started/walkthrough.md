---
title: Your first 30 minutes
audience: player
topic: walkthrough
summary: A guided tour for new players — spawn to working guild in seven steps.
keywords: [walkthrough, tutorial, onboarding, first time, getting started]
related: [welcome, guilds, homes, chat]
updated: 2026-05-13
---

# Your first 30 minutes

A guided tour for new players. Follow it top-to-bottom and you'll end up in a working guild with a tag, a home, chat configured, and a clear next move.

## 1. Spawn and orientation

When you first join, you'll spawn at the world spawn. Look around. Note:

- Chat is global by default. Anything you type goes to everyone.
- You don't need a guild to play, but a lot of the social and PvE/PvP features run through guilds.
- Type `/g help` any time to get a clickable in-game help menu.

## 2. Joining or creating a guild

You have three options:

**Join an existing guild** — ask in chat, or browse leaderboards with `/g list`. If a guild is open, you can join directly. If it's invite-only, ask a member to `/g invite` you.

**Create your own** — pick a name (plain text, max 32 chars, letters/numbers and the punctuation `'`, `&`, `-`) and run:

```
/g create <name>
```

Example: `/g create WhiteLotus` or `/g create White_Lotus` — **no spaces, no color codes**.

**Hold off for now** — you don't have to. You can come back later. Don't feel pressured to immediately create or join a guild.

See [Players → Guilds](../players/guilds.md) for the full reference.

## 3. Setting your tag and home

Once you're in a guild, two things make it feel like home.

**Tag** — fancy formatting that appears next to your name in chat. Use MiniMessage:

```
/g tag <gradient:#FF6A00:#FF1F00>Lotus</gradient>
```

Or run `/g tag` with no arguments to open a visual editor. Here are some tools to help you make a tag that stands out:

- [Birdflop RGB / MiniMessage gradient picker](https://www.birdflop.com/resources/rgb/) — visual gradient builder, copy MiniMessage straight into `/g tag` Note that we only support MiniMessage. Use magic color codes at your own risk.
- [fsymbols small-caps generator](https://fsymbols.com/generators/smallcaps/) — turn `Lotus` into `ʟᴏᴛᴜs` and similar styles
- [ETC Gamer Minecraft symbols & emojis](https://etcgamer.com/minecraft-symbols-emojis/) — extra glyphs that render in chat

**Home** — a teleport point your guild can share. Stand where you want it and run:

```
/g sethome
```

That sets the default home. You can have multiple — `/g sethome shop`, `/g sethome mine`. Teleport with `/g home` or `/g home <name>`. See [Players → Homes](../players/homes.md).

## 4. Inviting a friend

```
/g invite <player>
```

They run `/g accept <yourguild>` (or click the chat prompt). Done.

Need more control? You can lock invites to specific ranks, or set up "open" mode so anyone can join. See [Players → LFG & Invites](../players/lfg.md).

## 5. Chat basics

Two toggles you should know:

- `/g chat` — flips your messages into guild chat. Type it again to switch back to global. Same word, same key, easy to remember.
- `/g allychat` — same idea, but to all allied guilds.

Tags and colors only appear in chat if the chat plugin renders them — they do on this server. See [Players → Chat](../players/chat.md) for the details.

## 6. What XP and levels do

Your guild earns XP from what its members do: mining, killing mobs, exploring. XP turns into levels. Levels unlock:

- Bigger member caps.
- More named home slots.
- Access to advanced features (alliances, war declarations).
- Bragging rights on the leaderboard.

You don't have to micromanage it — just play. See [Players → Progression & Levels](../players/progression.md).

## 7. Where to go next

If you got this far you're functional. Pick one:

- **Decorate your guild** — set a description (`/g desc`), pick a banner (`/g menu` → Banner).
- **Make a friend or enemy** — `/g info <other guild>`, then `/g ally <them>` or `/g enemy <them>`. See [Players → Alliances & Diplomacy](../players/alliances.md).
- **Read the [How do I…? index](../players/how-do-i.md)** — every common task with a deep link to the right page.

That's the tour. The rest of the wiki is reference — come back as needed.
