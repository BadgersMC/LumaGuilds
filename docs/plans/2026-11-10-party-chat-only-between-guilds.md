# Party chat only between guilds

Every new guild used to get three single-guild parties: Guild_Chat, Officer_Chat
and Leader_Chat. Guild_Chat repeated the RoseChat `guild` channel, so a player
with both on saw each message twice.

## Decisions

- Parties are for talking with other guilds. Guild, ally and mod chat stay in
  RoseChat channels (`/g chat`, `/g allychat`, `/g modchat`).
- Officer_Chat is replaced by `/g modchat`. Leader_Chat is dropped.
- Single-guild parties can no longer be made from the Java or Bedrock menus.
- At startup every active single-guild party is marked dissolved. Rows stay in
  the database. Party chat pointing at one is cleared.
- One chat at a time: guild, ally or mod chat turns party chat off, and
  switching to party chat leaves those channels.

## Requirements

REQ-207, REQ-208 and REQ-209 in `docs/requirements.md`.
