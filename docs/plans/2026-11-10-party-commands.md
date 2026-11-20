# /party commands

Parties are only for talking between guilds (REQ-207). `/pc` handles party
chat; `/party` handles everything else, and opens the menus where a menu is
easier.

| Command | What it does |
| --- | --- |
| `/party` | Opens the party menu |
| `/party invite [guild]` | No guild: opens the invite page (or the create menu). With a guild: sends the invite |
| `/party accept [guild]` | Accepts that guild's request; no guild lists requests to click |
| `/party deny [guild]` | Denies that guild's request; no guild lists requests to click |
| `/party cancel [guild]` | Cancels your request to that guild; no guild lists them |
| `/party info` | Name, leader, guilds, online count, end time |
| `/party list` | Every active party between guilds |
| `/party leave` | Your guild leaves; run twice within 30 seconds |
| `/party disband` | Ends the party (leader's guild only); run twice within 30 seconds |
| `/party chat [message]` | No message: party chat on or off. With a message: sends just that |
| `/party rename <name>` | 1 to 32 characters, no spaces |
| `/party help` | Lists these |

Invite, accept, deny, cancel, leave and rename need Manage Parties. The
services check permissions again, so the command never grants more than the
menus do.

## Requirements

REQ-210 to REQ-215 in `docs/requirements.md`.
