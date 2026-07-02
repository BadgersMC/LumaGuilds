---
title: Guild shops
audience: player
topic: shop
summary: How guild-owned stalls and shops work — buying, permissions, and policies.
keywords: [shop, guild shop, stall, guild stall, tariff, embargo]
related: [guilds, identity]
updated: 2026-07-02
---

# Guild shops

Guilds can own market stalls and run shops. This page explains how guild shops
differ from personal shops and what guild members can do.

## Buying a stall for your guild

1. Find an **UNOWNED** stall with a purchase sign in the market.
2. **Right-click** the purchase sign — a menu opens.
3. Choose **Guild** (instead of Personal).
4. The purchase price is charged to your **guild bank**.

You must be in a guild and have the appropriate guild rank permission.

## Guild permissions

Guild shop access is gated by LumaGuilds rank permissions — not Minecraft
permission nodes. A guild leader sets these per rank:

| Permission | What it allows |
| ---------- | -------------- |
| `MANAGE_SHOPS` | Create, edit, and delete guild shops. Access shop chests. |
| `ACCESS_SHOP_CHESTS` | Open shop container inventories. |
| `EDIT_SHOP_STOCK` | Modify items in shop chests. |
| `MODIFY_SHOP_PRICES` | Change prices on shop signs. |

## Guild rent

Guild stall rent can be paid from the **guild bank** instead of a personal
account. The config key `lumaguilds.payFrom` controls this:

- `bank`: Rent is deducted from the guild bank.
- `leader`: Rent is deducted from the guild leader's personal account.

## Trade policies

Guild leaders can set economic policies against other guilds — tariffs (extra
fees) and embargoes (full trade block). See the
[EnthusiaMarket guild stalls guide](https://badgersmc.github.io/EnthusiaMarket/players/guild-stalls/)
for details.

## Guild dissolution

If a guild is disbanded, guild-owned stalls are reverted to UNOWNED and all
shops are wiped.

## TRADE restriction

TRADE (item-for-item) shops are **not available** in guild-owned stalls.
Guild shops support BUY and SELL only.

## Related

- [EnthusiaMarket → Guild stalls](https://badgersmc.github.io/EnthusiaMarket/players/guild-stalls/)
  — tariffs, embargoes, and policy management
- [Guilds](guilds.md) — create and manage your guild
- [Identity](identity.md) — customize your guild's tag and banner (displayed on shops)
