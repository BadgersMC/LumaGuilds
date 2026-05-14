---
title: Shop integration
audience: player
topic: shop
summary: Mark shops as guild-owned so the guild gets credit and visibility.
keywords: [shop, setshop, guild shop, integration]
related: [guilds, identity]
updated: 2026-05-13
---

# Shop integration

Mark shops as guild-owned so the guild gets credit and visibility.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g setshop` | `lumaguilds.guild.setshop` | Mark your current shop as guild-owned. |

## How it works

LumaGuilds integrates with the server's shop plugin. When a guild member runs `/g setshop` at one of their own shops, that shop is tagged as guild-owned. Guild-owned shops display the guild name, tag, and banner to customers, and the guild gets visibility in shop listings (if your server supports them).

## Marking a shop as guild-owned

First, set up your shop normally with the shop plugin. Then stand near it (or look at it, depending on plugin convention) and run `/g setshop`:

```
/g setshop
```

The shop is now linked to your guild.

## How guild shops appear to other players

Customers browsing your shop see your guild's tag and (if configured) banner displayed with the shop. The shop may appear in guild-shop listings if your server has them enabled. Note: shop transactions don't automatically credit guild XP — that's configured separately by the server admin.

## Removing a guild-owned shop

If you need to unlink a shop from your guild, you'll typically need to reset it using the shop plugin's own tools, then re-run `/g setshop` with a different shop (or contact staff for guidance).

## Gotchas

- You must own the shop personally with the shop plugin before `/g setshop` can mark it for the guild.
- Shop ownership stays with you — only the guild link changes.
- If you leave the guild, the link may need to be reapplied by a member who owns the shop.
- Only works with the supported shop plugin on this server — check with staff if it isn't behaving.

## Related

- [Guilds](guilds.md) — create and manage your guild
- [Identity](identity.md) — customize your guild's tag and banner (displayed on shops)
