---
title: Interaction layer
audience: dev
topic: interaction
summary: Commands, Java inventory menus, Bedrock forms, help, and player-facing routing.
keywords: [interaction, commands, menus, bedrock, ui]
related: [architecture, application]
updated: 2026-09-28
---

# Interaction layer

Location: `src/main/kotlin/net/lumalyte/lg/interaction/`.

This is the player/admin presentation edge of LumaGuilds.

It contains:

- ACF/Bukkit command handlers;
- Java InventoryFramework menus;
- native Bedrock Floodgate/Cumulus forms;
- menu navigation/factory logic;
- help topics and chat-facing interaction code.

`MenuFactory` chooses Java or Bedrock presentation based on configuration, platform detection, and Cumulus availability.

Keep interaction code thin: render state, collect input, enforce presentation-level authorization, then call services. Shared rules should live in application/domain so Java and Bedrock cannot drift into different gameplay behavior.
