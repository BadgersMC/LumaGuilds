---
title: Geyser/Floodgate behavior
audience: admin
topic: geyser
summary: How LumaGuilds detects Bedrock players and routes them through native Floodgate/Cumulus forms.
keywords: [geyser, floodgate, bedrock, forms, cross-play]
related: [installation, troubleshooting, upgrade-3.0]
updated: 2026-09-28
---

# Geyser/Floodgate behavior

LumaGuilds detects Bedrock players through Floodgate and routes them to native Bedrock forms. Java players continue to use inventory GUIs.

The business logic is shared: menu type changes by platform, but guild state, permissions, progression, quests, banking, and war rules are the same.

## Requirements

For Bedrock support, install and configure:

1. **Geyser** — protocol translation for Bedrock clients.
2. **Floodgate** — Bedrock identity detection and form delivery.

LumaGuilds uses the Cumulus form API exposed by the Geyser/Floodgate ecosystem. Admins do not normally install a separate "Cumulus plugin" for LumaGuilds.

Restart the server after adding or changing Geyser/Floodgate. Do not hot-reload these plugins.

## Menu routing

For each menu request, LumaGuilds checks:

1. `bedrock.bedrock_menus_enabled` is enabled.
2. The player is detected as a Bedrock/Floodgate player.
3. The Cumulus form API is available.

If all three are true, the Bedrock form is used. If not, LumaGuilds falls back to the Java menu path.

Current configuration:

```yaml
bedrock:
  bedrock_menus_enabled: true
  force_bedrock_menus: false
  fallback_to_java_menus: true
  fallback_on_floodgate_unavailable: true
  fallback_on_cumulus_unavailable: true
```

`force_bedrock_menus` is primarily a testing/debugging option. Leave it false in normal production use.

## Current Bedrock coverage

LumaGuilds 3.0 has dedicated Bedrock flows across the main guild system, including:

- guild dashboard and settings;
- members, ranks, invites, kicks, promotion, and permissions;
- homes and ally-home access;
- bank, security, budgets, automation, statistics, and transaction history;
- relations, diplomacy, war declarations, and war management;
- weekly guild quests and per-quest leaderboards;
- Chapter progression and prestige;
- LFG, parties, tags, emojis, descriptions, statistics, and confirmations;
- built-in claim management flows.

This is a parity target, not a pixel-for-pixel UI match. Bedrock uses buttons, toggles, dropdowns, and text fields where Java uses inventory items and slots.

## Verifying the integration

1. Join with a real Bedrock client through Geyser.
2. Open `/g menu`.
3. Confirm a Floodgate form opens instead of a Java inventory GUI.
4. Open **Weekly Quests** and **Progression**.
5. Verify quest leaderboard navigation and the progression/prestige information render.
6. Test a home teleport and one write action such as a settings or rank change on a staging guild.

## Troubleshooting

If Bedrock players receive Java menus, check these in order:

- Floodgate is loaded and the player is actually recognized by Floodgate.
- `bedrock_menus_enabled` is true.
- the Cumulus classes are available at runtime;
- the server log does not show a platform-detection exception;
- the menu is not being forced down a fallback path after an earlier form error.

If one specific form fails while others work, capture the exact menu/action and the server stack trace. That is usually a menu-specific issue rather than a Geyser installation problem.

## Related

- [Installation & config.yml](installation.md)
- [Upgrading to LumaGuilds 3.0](upgrade-3-0.md)
- [Troubleshooting](troubleshooting.md)
