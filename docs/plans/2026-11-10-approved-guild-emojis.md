# Approved guild emojis

The guild emoji picker listed every Nexo emoji the player had permission for.
For staff and donors that meant 147 donor heads plus the chat emojis, over five
pages.

## Decisions

- `guild.emoji_choices.allowed` lists the emojis any guild can pick. No emoji
  permission is needed. If the key is missing, the 34 Redstone Labs chat
  emojis are used.
- Donor heads are never on the list, so they stay chat-only.
- `guild.emoji_choices.guild_only` maps a guild name to its own logo. Only that
  guild sees it, first in the picker. On Enthusia: BelovedOnes, Vibe,
  Everlights and Enthusiast.
- Chat use of the logos uses the existing `guild.emoji_grants`, which gives a
  guild's members the emoji's permission and removes it when they leave.
- The Pumpkin King emoji still needs the guild cosmetic unlock.
- A guild keeps an emoji that is no longer offered until it picks another.
- The pack gains picker items for the Everlights logo and the Pumpkin King, so
  no choice shows as plain paper.

## Requirements

REQ-216, REQ-217 and REQ-218 in `docs/requirements.md`.
