---
title: Geyser/Floodgate behavior
audience: admin
topic: geyser
summary: How LumaGuilds renders menus and dispatches teleports for Bedrock players via Geyser/Floodgate.
keywords: [geyser, floodgate, bedrock, forms, cross-play]
related: [installation, troubleshooting]
updated: 2026-05-14
---

# Geyser/Floodgate behavior

LumaGuilds detects Bedrock players and automatically adapts the menu experience from inventory GUIs to text-based Floodgate forms.

## How it works

Geyser translates Bedrock protocol to Java; Floodgate extends that with player detection and the form API. LumaGuilds uses the `FloodgateApi.isFloodgatePlayer(uuid)` method to detect Bedrock players and routes them through Bedrock-flavored menus (chat-based Floodgate forms) instead of inventory GUIs. All commands work identically for Bedrock and Java players—the difference is only the visual menu layer.

**The detection flow:**
1. Player joins the server via Geyser.
2. Floodgate assigns a prefixed UUID (typically `.<username>`).
3. When the player opens a guild menu (e.g., `/guild menu`), LumaGuilds checks `isBedrockPlayer()`.
4. If Bedrock, a Floodgate form is rendered; if Java, an inventory GUI.
5. Form responses are captured by the Cumulus form handler and routed to the same business logic as Java menus.

## What renders differently on Bedrock

The following guild/claims menus have Bedrock-optimized form variants:

| Bedrock Form | What the player sees |
|---|---|
| `BedrockGuildSelectionMenu` | Text form listing available guilds to join |
| `BedrockGuildHomeMenu` | Text form to select and teleport to guild homes |
| `BedrockGuildMemberListMenu` | Text form listing guild members with ranks |
| `BedrockGuildBankMenu` | Text form for virtual/physical bank access |
| `BedrockGuildModeMenu` | Text form to switch between peaceful/hostile modes |
| `BedrockGuildWarManagementMenu` | Text form to view and manage active wars |
| `BedrockClaimManagementMenu` | Text form for land claim creation and editing |
| `BedrockConfirmationMenu` | Text form for yes/no confirmations |
| `BedrockRankCreationMenu` | Text form for custom rank creation |
| `BedrockGuildPartyManagementMenu` | Text form to create and manage parties |
| `BedrockDescriptionEditorMenu` | Text input form for editing guild descriptions |

Additionally, there are 40+ other Bedrock menu variants (for tags, emojis, permissions, etc.). All provide equivalent functionality to their Java counterparts but as form dialogs instead of inventory slots.

## Setup

1. **Install Geyser-Bukkit** from [Modrinth](https://modrinth.com/plugin/geyser). Drop the JAR into `plugins/`.

2. **Install Floodgate**. Geyser includes Floodgate by default in modern versions; verify:
   ```
   /version Floodgate
   ```
   You should see Floodgate loaded.

3. **Optional: Install Cumulus** for enhanced form stability. Drop the JAR into `plugins/`. LumaGuilds works without it but falls back gracefully.

4. **Restart the server.** Geyser and Floodgate will auto-generate configs.

5. **Configure Geyser's `config.yml`** (in `plugins/Geyser-Bukkit/`):
   - Ensure the remote address (MCPE server IP) and port are correct.
   - Set `send-floodgate-data: true` to enable Floodgate integration.
   - LumaGuilds requires no Geyser-specific config. Defaults work fine.

6. **No LumaGuilds config changes needed.** Bedrock detection and menu routing are built-in and enabled by default via `config.yml`:
   ```yaml
   bedrock:
     bedrock_menus_enabled: true
     fallback_to_java_menus: true
   ```

## Verifying the integration

1. **Join with a Bedrock client.** Use Geyser (on the same server port) or any Bedrock Edition client connecting to a Java server running Geyser.

2. **Check the console.** On player join, you should see:
   ```
   [GeyserMC] <player> has connected to the server via Geyser
   ```

3. **Open the guild menu.** Type `/guild menu` (or `/g menu`).
   - **Java players:** See a multi-slot inventory GUI with clickable items.
   - **Bedrock players:** See a text form with button options (no inventory).

4. **Confirm Floodgate prefix.** In the player list, Bedrock players appear with a `.` prefix (e.g., `.Steve` instead of `Steve`). This is normal and indicates Floodgate is working.

5. **Test a command.** Use `/guild home` to teleport. Bedrock players should see a confirmation form; Java players see the standard system message.

## Cross-dimensional teleports

As of commit `5b8bf3a` (PR #39), all teleport callbacks are dispatched on the main thread—even when called from Bedrock's Netty thread. This ensures:

- Bedrock home teleports work reliably across dimensions.
- The plugin does NOT spawn threads directly for Bukkit API calls.
- Exception handling is thread-safe and logs detailed coordinates if a teleport fails.

**If you see `Asynchronous teleport!` errors or stack traces containing "Bukkit API called on wrong thread" after a teleport:**

1. Upgrade LumaGuilds to the latest version (commit `5b8bf3a` or later).
2. Check the server logs for the exact error; contact support with the full stack trace.

## Gotchas

- **Floodgate must be installed even on Bedrock-only servers** — LumaGuilds uses Floodgate to detect the Bedrock client, not just for forms.

- **Some advanced menu features fall back to simpler input on Bedrock.** For example, drag-and-drop banner color editing on Java menus becomes text-based hex input on Bedrock. This is intentional and maintains functional parity.

- **Java Edition players joining via Geyser-Reborn or GeyserMC's Java port are NOT detected as Bedrock.** They get Java GUIs. This is correct behavior—only actual Bedrock Edition clients (Mobile, Console, Windows 10/11 Edition) are routed through Bedrock menus.

- **Form button limits.** Bedrock forms have a practical limit of ~8 buttons. Menus with more options (e.g., member lists, leaderboards) paginate or truncate gracefully. If a menu seems cut off, use back/next buttons to navigate.

- **Cumulus is optional but recommended.** Without it, form timeouts fall back to the old behavior. With Cumulus, forms are more stable and have better error recovery. Both work; Cumulus is just more robust.

## Related

- [Installation & config.yml](installation.md) — configure bedrock menus in config
- [Troubleshooting](troubleshooting.md) — debug Bedrock/Geyser issues
