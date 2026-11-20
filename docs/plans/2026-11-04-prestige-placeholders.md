# Prestige placeholders

REQ-155 and REQ-156. Builds on the prestige history from
`2026-11-03-prestige-advancements.md` so TAB, nametags, scoreboards and spawn
holograms can show a guild's prestige.

## Decisions

- **Tier from ownership, extras from history.** The tier is the authoritative
  `RewardOwnership.prestigeCount`. Latest date and server-first tiers come from
  `PrestigeHistory`. A missing reward account reads as tier 0; a failed read gives
  empty strings, matching `RewardPlaceholder` ("blank, never a fabricated zero").
- **Cache.** TAB and nametags request placeholders every refresh, so each guild's
  snapshot is cached for 30 seconds. Server-first holders (six `firstToReach`
  reads) are cached once for all guilds on the same TTL. A newly committed
  prestige invalidates the guild's snapshot and the first-holder map through the
  existing `GuildPrestigeNotifier` list, so the badge updates immediately.
- **Badges are configurable text.** `progression.prestige.badges` holds seven
  strings (index 0 = no prestige). The shipped `config.yml` uses `&`-coloured
  `✦I` to `✦VI` (gold for I-II, grey for III-IV, yellow for V, pink for VI). The
  Kotlin fallback is uncoloured, because `LocaleContractTest` keeps Kotlin sources
  free of `§` text. Once the Nexo badge glyphs are wired, operators can replace
  the entries with glyph text.
- **Leaderboard.** `top_prestige_*` sorts by tier, then current-run level and XP,
  so it doubles as the "closest to next prestige" race board. `value` is the tier.

## Boundaries

Read-only. No schema, prestige, credit or announcement change. Glyph wiring in the
resource pack is separate.
