# Guildless leaderboard routing

Requirement: When a player with leaderboard permission runs `/g leaderboard` or `/guild leaderboard`, the server shall open the public rankings even if the player has no guild. Guild members retain their guild theme, own-guild highlight, and membership revalidation. Guildless players use the default theme. Both Java and Bedrock screens must retain pagination, guild links, and safe navigation.

Existing TEST observation: a guildless player was rejected with the no-guild message, despite the command being intended as a direct leaderboard shortcut. Test-only, client acceptance, and PR review are separate gates. Project-local EARS/state helpers are absent.

Local verification: command alias and permission tests, default-theme dialog and sorting tests, and Java/Bedrock factory selection tests passed. The full local suite reported 2,335 tests, zero failures or errors, and 88 skips. Bedrock client interaction and in-game acceptance remain unverified.
