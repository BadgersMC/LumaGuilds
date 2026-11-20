# Prestige and disbanding

Requirements REQ-162 and REQ-163. Decided with the operator on 2026-11-08.

## Rules

| Event | Guild's tier, perks, cosmetics | Prestige history | Server-firsts | Members' advancements and tags |
|---|---|---|---|---|
| Disband | Deleted, for good | Kept, plus the guild's name and tier at disband | Stay with the disbanded guild | Kept |
| Rename | Kept (stored by guild id) | Kept; shows the current name | Kept | Kept |
| Re-found under the same name | A new guild at tier 0 | None | None | Unaffected |
| Member leaves, joins another prestiged guild | Not applicable | Not applicable | Not applicable | Credited only for prestiges they were in the guild for at least 24 hours before (REQ-154) |

There is no guild merge in LumaGuilds, so nothing transfers between guilds.

## What changed

- **Warning (REQ-162).** The Java and Bedrock disband confirmations show the
  guild's prestige tier, that it and its permanent rewards are lost for good,
  that members keep their advancements and tags, and that server-firsts stay
  in the history under the guild's name. `/g disband` used to disband at once;
  for a prestiged guild it now opens the confirmation instead. Unprestiged
  guilds keep the old flow. If the tier cannot be read, the warning still
  shows, without the tier.
- **Name kept (REQ-163).** Disbanding a prestiged guild writes its id, name,
  prestige count and disband time to `guild_prestige_tombstones` (schema
  version 50) inside the disband transaction, before the reward account is
  deleted. `DisbandedPrestigeGuilds` reads it back, for the future Hall of
  Prestige and website to show "Everlights (disbanded)".
- Server-firsts already stayed with a disbanded guild, because prestige
  receipts and the gold journal are never deleted; that is now the documented
  rule.
