---
title: FAQ
audience: player
topic: faq
summary: Common questions from new players about LumaGuilds.
keywords: [faq, common questions, help, troubleshooting]
related: [walkthrough, chat, homes]
updated: 2026-05-13
---

# FAQ

## Do I have to be in a guild?

No. You can play the entire server solo. Joining a guild just unlocks the guild-specific features (shared homes, alliance/war, guild chat, progression).

## How do I leave my guild?

`/g leave`. If you're the owner, you must transfer ownership first (`/g transfer <player>`) or disband (`/g disband`).

## My guild tag isn't showing colors in chat

The colors are stored as MiniMessage, which the chat plugin converts to display colors. If they're not rendering, you may be in a chat channel that doesn't format them. Try `/g chat` to switch to guild chat — colors render there.

## Why does `/g home` say "teleport failed"?

The destination is unsafe (lava/fire/cactus right where the home was set), or a protection plugin blocked the teleport. Move the home (`/g sethome` somewhere safe) and try again.

## Can I have more than one home?

Yes. `/g sethome <name>` creates additional named homes (`shop`, `mine`, etc.). The number of slots scales with your guild level. See [Players → Homes](../players/homes.md).

## How do alliances work?

`/g ally <other guild>` sends a request. They run `/g ally <you>` back to accept. Until both have done it, you're not actually allied. See [Players → Alliances & Diplomacy](../players/alliances.md).

## I'm on Bedrock — does this all work?

Mostly. A few menus open as chat-based forms instead of inventory GUIs, and clickable chat buttons map to taps. See [Players → Bedrock differences](../players/bedrock.md).

## Where do I report a bug?

Tell staff in-game or open an issue at <https://github.com/BadgersMC/LumaGuilds/issues>.
