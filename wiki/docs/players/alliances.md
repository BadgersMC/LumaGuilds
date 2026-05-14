---
title: Alliances & Diplomacy
audience: player
topic: alliances
summary: Form alliances, declare enemies, sign truces, and manage ally-home access.
keywords: [alliances, ally, enemy, truce, neutral, diplomacy]
related: [war, homes, ranks]
updated: 2026-05-13
---

# Alliances & Diplomacy

Form alliances, declare enemies, sign truces, and manage ally-home access.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g ally <guild>` | `lumaguilds.guild.ally` | Request or accept an alliance. |
| `/g enemy <guild>` | `lumaguilds.guild.enemy` | Declare another guild an enemy. |
| `/g truce <guild>` | `lumaguilds.guild.truce` | Sign a truce (non-aggression pact). |
| `/g neutral <guild>` | `lumaguilds.guild.neutral` | Clear an existing relation. |

## How it works

Every guild has *relations* with other guilds. By default, all relations are neutral. You can establish ally, enemy, or truce relations. Most relations require both sides to agree — sending `/g ally <them>` creates a *pending* request; they confirm with `/g ally <you>`. Only after both confirmations is the alliance ACTIVE. Wars come from declaring enemies and then escalating via `/g war` (see [War](war.md)).

## Requesting an alliance

Run `/g ally <other guild>` to request an alliance. Their guild will see a notification with a clickable accept prompt. Until they confirm with `/g ally <your guild>`, the request sits pending on both sides. Once confirmed, the alliance becomes ACTIVE and both guilds appear in each other's allies list.

## Accepting an alliance

When you receive an alliance request, run `/g ally <them>` from your side. The relation flips from PENDING to ACTIVE. You'll see them in your allies list and the relation indicator (🔵) appears next to their members' names in chat.

## Declaring an enemy

Use `/g enemy <guild>` to mark another guild as an enemy. This is unilateral — they don't have to agree to be marked. From here, war can be declared via `/g war`. Enemy relations escalate conflict; truces and going neutral are the typical ways to de-escalate.

## Signing a truce

A truce is a non-aggression pact between two guilds. Run `/g truce <guild>` to propose one. Both sides must confirm to make it ACTIVE. Truces commonly end an active war without going fully neutral.

## Clearing a relation

Use `/g neutral <guild>` to clear whatever relation you had — alliance, enemy mark, or truce. This is unilateral. Going neutral cancels any pending requests on both sides.

## Setting up ally-home access

Allies don't automatically reach your ally-home. You need to:

1. **Set the ally-home:** Stand at your desired location and run `/g setallyhome` (requires the "Set Ally-Home" permission).
2. **Whitelist their guild:** Open `/g menu` → Diplomacy → Ally Home Access, and add their guild to your inbound whitelist.
3. **Give your members permission:** Make sure your guild members have the "Use Ally Homes" rank permission so they can teleport to *other guilds'* ally-homes.

See [Homes](homes.md) for more detail on setting and managing ally-homes.

## Recently Fixed/Changed

- Pending alliance requests no longer auto-display as accepted (fixed in 2026 — they used to appear immediately in the allies list before the other side confirmed).
- Going neutral cancels pending requests on both sides instantly.
- Ally-home access requires BOTH the rank permission AND the whitelist entry — just having one won't work.
- Declaring an enemy is unilateral, but wars are two-sided (both guilds see the declaration).

## Related

- [War](war.md) — escalate a conflict from enemies to active war
- [Homes](homes.md) — set and manage your ally-home
- [Ranks & Permissions](ranks.md) — control who can use your ally-home
