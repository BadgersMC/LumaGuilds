---
title: Tags, Banners & Identity
audience: player
topic: identity
summary: Set your guild's tag, description, banner, and emoji to give it personality.
keywords: [tag, banner, description, emoji, identity, rename, minimessage]
related: [chat, guilds]
updated: 2026-05-13
---

# Tags, Banners & Identity

Set your guild's tag, description, banner, and emoji to give it personality.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g tag [text]` | `lumaguilds.guild.tag` | Set or open the tag editor. |
| `/g desc <text>` | `lumaguilds.guild.desc` | Set your guild description. |
| `/g rename <name>` | `lumaguilds.guild.rename` | Rename your guild. |
| `/g emoji` | `lumaguilds.guild.emoji` | Pick a guild emoji. |

## How it works

Four pieces give your guild personality: the *name* (plain text, what shows in `/g list`), the *tag* (fancy formatted prefix shown next to members' names in chat), the *description* (a short blurb in your info card), the *banner* (a Minecraft banner pattern), and the *emoji* (an icon shown in menus). Tags use MiniMessage formatting and can include colors and gradients.

## Setting a guild tag (CLI)

Run `/g tag <text>` directly with MiniMessage formatting. Examples:

```
/g tag <red>FireGuild</red>
/g tag <gradient:#FF0000:#00FF00>Rainbow</gradient>
/g tag <bold><blue>ELITE</blue></bold>
```

Max 32 visible characters (formatting tags don't count toward the limit).

## Setting a guild tag (menu)

Run `/g tag` with no arguments to open the visual Tag Editor. You can type your tag, preview formatting, clear it, and save. Same validation as the CLI.

## Setting a description

Use `/g desc <text>` — appears on your guild's info card and in directory listings. Keep it short and descriptive:

```
/g desc A relaxed community for builders and redstoners
```

## Choosing an emoji

Run `/g emoji` to open an emoji picker. The chosen emoji appears in menus, leaderboards, and some chat contexts. Emoji selection is gated by your guild level — more options unlock as you level up.

## Renaming your guild

Use `/g rename <name>` (owner-only typically — check your rank's permissions). Same rules as creation: max 32 plain-text chars, no MiniMessage:

```
/g rename MyGuild
```

Renaming doesn't change your tag — those are independent.

## Setting a banner

Open `/g menu` → Banner. Click to enter the banner selection menu, where you can drag your custom banner design into the menu, and show off your desgins. The banner is shown in your info card, alliance menus, and on guild-owned shops. 

## Gotchas

- The `/g tag` CLI accepts MiniMessage formatting (used to be stricter than the menu — fixed in 2026).
- The "Clear" button in the tag editor now properly clears the tag (used to silently undo on save).
- The Guild Banner menu renders correctly (used to open empty — fixed).
- The Glass Pane can no longer be removed from the menu (sorry toxik)
- If your colored tag isn't rendering in chat, you may be in a chat channel that doesn't format MiniMessage — try `/g chat` to switch to guild chat.

## Related

- [Chat](chat.md) — where your tag is rendered
- [Guilds](guilds.md) — name vs tag distinction
