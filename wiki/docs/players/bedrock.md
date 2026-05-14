---
title: Bedrock differences
audience: player
topic: bedrock
summary: Where LumaGuilds behaves differently on Bedrock Edition via Geyser/Floodgate.
keywords: [bedrock, geyser, floodgate, cross-play]
related: [homes, chat, ranks]
updated: 2026-05-13
---

# Bedrock differences

Where LumaGuilds behaves differently on Bedrock Edition via Geyser/Floodgate.

## Quick reference

All guild commands work identically on Bedrock and Java. There are no Bedrock-specific commands — the differences are visual and UX-focused only.

## How it works

The server supports Bedrock players via Geyser (protocol translation) and Floodgate (Bedrock Forms Menus). All LumaGuilds features work on Bedrock: commands, vaults, homes, alliances, menus, and chat.

The only differences are visual. Some Java menus open as native GUI-based forms on Bedrock, and clickable chat buttons have backup commands (such as `/g ally <them>`). Behavior is otherwise identical.

## What looks different in menus

Some Java menus (the guild member rank confirmation, the ally-home access editor, the home selection on `/g homes`) open as Floodgate form dialogs on Bedrock instead of inventory GUIs. The functionality is identical — pick options, confirm, done. The UX is slightly different, but you get the same result.

Example: On Java, `/g homes` opens an inventory menu. On Bedrock, it opens a form dialog with the same homes listed and the same buttons to teleport.

## Known limitations

A few advanced menu features fall back to simpler chat menus or text input on Bedrock. The affected features are:

- **Guild banner editing:** Drag-and-drop banner placement is unavailable; Bedrock players use a text-based alternative.
- **Multi-page selectors:** Complex paginated menus (large member lists, extended alliance directories) render as a simplified flat list without prev/next page buttons.
- **Inventory-based GUI interactions:** Item-moving workflows (vault sorting, manual rank reordering) may show as command prompts instead of drag-and-drop interfaces.
- **Item renaming UIs:** Custom anvil-style renaming menus fall back to plain text input.

The substance is preserved; the UX is plainer. If you find something that doesn't render correctly, report it to staff so it can be added to the known-issues list.

## Teleports and cross-dimensional travel

All teleports (including cross-dimensional home teleports from the Overworld to the Nether, or to the End) are dispatched safely on the main thread for Bedrock players. Floodgate threading quirks are handled correctly — you won't see "teleport rejected" or timeout errors (fixed in v2.4.0, commit 7a3f2e1, 2026-05-01).

## Recently Fixed/Changed

Nothing for now!

## Related

- [Homes](homes.md) — set and teleport to guild homes
- [Chat](chat.md) — guild chat and messaging
- [Ranks & Permissions](ranks.md) — guild rank system and permissions
