---
title: Vault
audience: player
topic: vault
summary: Use the shared guild vault for storing items members can access together.
keywords: [vault, storage, shared chest, getvault]
related: [ranks, guilds]
updated: 2026-05-13
---

# Vault

Use the shared guild vault for storing items members can access together.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g vault` | `lumaguilds.guild.vault` | Open the guild vault. |
| `/g getvault` | `lumaguilds.guild.getvault` | Get a vault chest item. |

## How it works

The guild vault is a shared inventory all members can use, gated by rank permission. You can open it with the command, or place a special "vault chest" item somewhere and right-click it. The vault is a single shared inventory — putting items in lets your guildmates take them out, and vice versa.

## Opening the vault

Run `/g vault` from anywhere in the world (you must have the "Use Vault" rank permission). The vault inventory opens like a chest:

```text
/g vault
```

Add items by right-clicking them into the vault, or take items that other members have put there.

## Getting a vault chest item

Use `/g getvault` to get a special chest item that, when placed and right-clicked, opens the guild vault from that location:

```text
/g getvault
```

Place it in a dedicated vault room so members can access the vault without using the command. The item is tagged so it can't be repurposed.

## Sharing the vault across members

Anyone with the "Use Vault" rank permission can open the vault, regardless of where they are. Items added by any member are available to all. No permission is needed to *read* the vault — only to *open* it.

## Gotchas

- Vault chest items cannot be used in crafting (you can't craft a chest-boat, hopper, or trapped chest from one — fixed for anti-exploit).
- Vault chest items cannot be burned as furnace fuel (also fixed for anti-exploit).
- Vault access is governed by your rank — if you can't open it, check that your rank has "Use Vault" permission.

## Related

- [Ranks & Permissions](ranks.md) — vault permissions live on ranks
- [Guilds](guilds.md) — guild basics
