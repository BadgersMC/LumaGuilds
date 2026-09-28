---
title: RoseChat integration
audience: admin
topic: rosechat
summary: Required RoseChat integration for guild and ally chat channels.
keywords: [rosechat, chat, channel, integration]
related: [installation, placeholderapi, troubleshooting]
updated: 2026-09-28
---

# RoseChat integration

RoseChat is a **hard dependency** of LumaGuilds 3.0. The server must load the compatible RoseChat build before LumaGuilds.

LumaGuilds uses RoseChat's channel API for persistent guild/ally chat routing while keeping guild membership and relation rules inside LumaGuilds.

## Managed channels

LumaGuilds integrates two channel concepts:

- `guild` — messages are scoped to the sender's guild;
- `guild-ally` — messages are scoped to the sender's guild plus active allies.

Players toggle them with `/g chat` and `/g allychat`. Quick-message commands such as `/gc` and `/gac` send to the corresponding audience without changing the player's current channel.

## Setup

1. Install the Enthusia-compatible RoseChat build in `plugins/`.
2. Install LumaGuilds.
3. Start the server normally.
4. Confirm RoseChat enables before LumaGuilds.
5. Test guild chat with two members and a non-member.
6. Test ally chat with two allied guilds and an unrelated guild.

LumaGuilds does not support running 3.0 without RoseChat present; Paper will block plugin enable when the hard dependency is missing.

## Formatting

Use LumaGuilds PlaceholderAPI values from RoseChat formatting as needed. Common values include guild tag, emoji, and combined chat-display placeholders.

See [PlaceholderAPI](placeholderapi.md) for the current placeholder list. Keep recipient filtering in the LumaGuilds/RoseChat channel integration rather than trying to reproduce guild membership rules in formatting configuration.

## Troubleshooting

If guild chat leaks to global or a toggle fails:

- verify the expected RoseChat build loaded;
- check LumaGuilds startup for channel registration/integration errors;
- restart after changing RoseChat/channel configuration;
- test `/g chat` and `/g allychat` independently;
- capture the first relevant exception from `latest.log`.

Do not use PlugMan-style hot reloads to repair channel registration.

## Party chat

Party chat is a separate LumaGuilds interaction path and should not be treated as a persistent RoseChat guild channel. Use the party documentation/configuration for its behavior.

## Related

- [Installation & config.yml](installation.md)
- [PlaceholderAPI](placeholderapi.md)
- [Troubleshooting](troubleshooting.md)
