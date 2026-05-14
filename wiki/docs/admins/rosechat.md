---
title: RoseChat integration
audience: admin
topic: rosechat
summary: Hook guild chat into RoseChat as a managed channel — formatting, recipients, and toggle behavior.
keywords: [rosechat, chat, channel, integration]
related: [installation, placeholderapi, troubleshooting]
updated: 2026-05-14
---

# RoseChat integration

When RoseChat is installed, LumaGuilds hands guild chat off to RoseChat via a channel hook. RoseChat owns formatting, delivery, and recipient resolution. Players use `/g chat` to toggle between guild chat and their normal channel.

## How it works

LumaGuilds detects RoseChat at startup and registers two managed channels: `guild` (guild-only) and `guild-ally` (allied guilds). When a player runs `/g chat`:

1. **First toggle:** Switch the player's RoseChat channel to `guild`. Messages now only reach guild members.
2. **Second toggle:** Restore their previous channel (e.g., global). Guild chat is off.

RoseChat owns the actual chat rendering, permission checks, and message delivery. LumaGuilds only switches the channel; it doesn't intercept chat events anymore.

### Why hand off?

Two plugins listening to `AsyncChatEvent` cause race conditions: duplicate messages, dropped chat, or both. Single-owner (RoseChat) is the only clean model. Before this integration (commit `046f8bf`), LumaGuilds intercepted the event directly and competed with other chat plugins.

## Setup

1. **Download RoseChat** and drop it into `plugins/`. LumaGuilds requires **RoseChat RC-2** or later.

2. **Restart the server.** LumaGuilds detects RoseChat and auto-registers the two channels (`guild` and `guild-ally`). No config flag is needed.

3. **Verify the integration** by testing a placeholder:

   ```bash
   /papi parse <player> %lumaguilds_guild_tag%
   ```

   Should return the guild's tag formatted in MiniMessage syntax (e.g., `<color:#FF5733>Elite</color>`). If empty, check server logs for PAPI errors.

4. **Test in-game:**
   - `/g chat` to toggle guild chat on
   - Type a message — only guild members should see it
   - `/g chat` again to toggle off

## Customizing the guild channel format

RoseChat channel configuration goes in `plugins/RoseChat/channels.yml`. Add or update the `guild` channel section like this:

```yaml
channels:
  guild:
    display-name: "Guild Chat"
    # Message format: uses PAPI placeholders and MiniMessage tags
    message-format: "%lumaguilds_guild_emoji% %lumaguilds_guild_tag% %player_displayname% ⋙ {message}"
    
    # Recipient resolution (LumaGuilds channel hook handles this)
    # Only members of the player's guild see messages
    
    # Optional: customize how the channel appears in /g chat output
    abbreviation: "G"
    color: "#FFB400"
    
  guild-ally:
    display-name: "Ally Chat"
    message-format: "<color:#00FF00>%lumaguilds_guild_tag%</color> %player_displayname% ⋙ {message}"
    abbreviation: "A"
    color: "#00FF00"
```

**Key placeholders:**

- `%lumaguilds_guild_emoji%` — Guild emoji (converted to Nexo format)
- `%lumaguilds_guild_tag%` — Guild tag (MiniMessage-formatted)
- `%lumaguilds_guild_chat_format%` — Combo of emoji + tag (usually simpler)
- `%player_displayname%` — Player name (from PlaceholderAPI)

The actual recipient filtering (who sees the message) is handled by LumaGuilds' channel hook; RoseChat just renders and delivers.

## Verifying the integration

1. **Player A and Player B** are in different guilds.

2. **Player A** runs `/g chat` (toggle ON).

3. **Player A** types: `Hello guild!`

4. **Expected result:**
   - Only members of Player A's guild see the message.
   - Player B does not see it.

5. **If the message leaks to global:**
   - Check [Troubleshooting → Chat leaks](#gotchas).
   - Verify RoseChat's `guild` channel is configured in `channels.yml`.
   - Confirm LumaGuilds detected RoseChat (check console at startup for registration logs).

## Ally chat

`/g allychat` toggles the player into the `guild-ally` RoseChat channel. Same toggle mechanism as `/g chat`:

- **First toggle:** Switch to `guild-ally`. Only allied guild members (guilds with active ally relations) see messages.
- **Second toggle:** Return to previous channel.

Ally relations are managed in-game with `/g relation` commands. Messages only reach members of guilds that have an active ally link.

## Party chat

Party chat **is NOT** hooked into RoseChat. It uses the legacy chat-claim system (metadata-based message interception). This is intentional—parties are short-lived (24-hour default), dynamic groups that don't map cleanly to RoseChat's persistent channel model. Party chat always routes through LumaGuilds' internal listener, independent of RoseChat.

See [Party system configuration](installation.md#party-system-configuration) for chat format options.

## Gotchas

**Without RoseChat installed:** LumaGuilds falls back to the legacy `AsyncChatEvent` path. Guild chat works fine, but you lose RoseChat's formatting niceties and must be careful about other chat plugins interfering.

**Party chat always uses the legacy path:** Even with RoseChat enabled, party chat uses the old message-claim system. This is by design.

**Chat-claim metadata can rarely go stale:** When a player toggles `/g chat` off, LumaGuilds clears the stale chat-claim metadata. Older versions had a bug where metadata lingered and caused chat to leak. This was fixed in commit `a09429d`; if you're on an old build and see leaks, upgrade.

**Channel IDs must match:** The toggle logic looks for channels named `guild` and `guild-ally` (exact IDs in `channels.yml`). If you rename them, the toggle will fail with `Guild channel 'guild' is not configured`. Update the channel names in RoseChat or change the hardcoded IDs in LumaGuilds (not recommended).

## Related

- [Installation & config.yml](installation.md) — install LumaGuilds and RoseChat
- [PlaceholderAPI placeholders](placeholderapi.md) — format guild tags and emoji in channels
- [Troubleshooting](troubleshooting.md) — debug chat leaks and other issues
