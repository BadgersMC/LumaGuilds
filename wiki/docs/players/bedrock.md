---
title: Bedrock differences
audience: player
topic: bedrock
summary: How LumaGuilds presents the same guild systems to Bedrock players through Geyser/Floodgate forms.
keywords: [bedrock, geyser, floodgate, cross-play, forms]
related: [quests, progression, homes, chat, ranks]
updated: 2026-09-28
---

# Bedrock differences

LumaGuilds uses Geyser/Floodgate to identify Bedrock players and presents native Bedrock forms instead of Java inventory menus.

The underlying guild data and rules are shared. A Java member and a Bedrock member contribute to the same guild, quests, progression, bank, homes, relations, and wars.

## What is the same

Bedrock players can use the same guild command surface and participate in the same systems, including:

- guild creation, invites, membership, and ranks;
- homes and ally-home access;
- banking, vaults, budgets, and transaction views;
- alliances, enemies, truces, and wars;
- weekly guild quests and per-quest leaderboards;
- Chapter progression, reward state, and prestige;
- guild statistics, LFG, parties, tags, emojis, and settings.

## What looks different

Java uses inventory-style menus with items and slots. Bedrock uses Floodgate/Cumulus forms with buttons, text fields, toggles, and dropdowns.

That means the interaction can look different even when it performs the same action. For example:

- a Java item click becomes a Bedrock form button;
- a Java paginated inventory becomes a paginated form;
- a Java confirmation item becomes a modal confirmation;
- text editing uses a Bedrock input field instead of an inventory/anvil-style interaction.

## Weekly quests and progression

Bedrock has native forms for both **Weekly Quests** and **Progression**.

The quest form shows the active objectives, progress, Guild EXP/item rewards, reset state, full-set bonus state, and leaderboard access. Clicking a quest opens the same per-quest guild competition available to Java players.

The progression form exposes Chapter information and the prestige flow when prestige is enabled.

See [Weekly Guild Quests](quests.md) and [Progression, Rewards & Prestige](progression.md).

## Toasts and notifications

LumaGuilds uses toast-style notifications for important lifecycle events such as quest completion and war state changes. The notification system is designed to work for the network's Bedrock path as well as Java, with chat/sound fallback where needed.

## If a form does not open

Try the command again once. If the problem persists, report it to staff with:

- your Bedrock username;
- the exact command or menu button;
- which form you expected;
- whether other guild forms still open.

A form failure should not require changing guild data manually.

## Related

- [Weekly Guild Quests](quests.md)
- [Progression, Rewards & Prestige](progression.md)
- [Homes](homes.md)
- [Chat](chat.md)
- [Ranks & Permissions](ranks.md)
