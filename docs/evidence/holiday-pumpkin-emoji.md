# Earned Halloween guild emoji — 2026-10-11

## Spec and prove
REQ-148; LG-2013. Canonical BadgersMC/LumaGuilds main bfa6931 was fetched before isolated work. Main checks actor permission only and rejects the Pumpkin King tag glyph because Nexo does not mark it as an emoji. The exact existing glyph is pumpkin_king_icon, U+A0A0, minecraft:default; other seasonal tag glyphs remain excluded. No historical red run is claimed. Project EARS/state helpers are absent; manual requirements/tasks/evidence are maintained.

## Engine and architecture
Ownership uses existing string-based GuildCosmeticUnlocks ledger (EMOJI:PUMPKIN_KING), without API ABI or schema changes. Domain constants identify the entitlement, application service checks ownership, and Nexo adapter resolves the exact non-emoji glyph. Existing Java selector, command completion/command and authoritative GuildService save paths use guild ownership; Bedrock forms retain their existing save path. MANAGE_EMOJI authority remains enforced by GuildService. Wildcard personal permissions do not bypass holiday guild ownership. Revocation uses a conditional emoji-only SQL update and preserves unrelated guild fields; cache changes only if the expected emoji remains equipped. No guild menu redesign.

## Refine and validation
Java 25 canonical `gradlew.bat clean test shadowJar --no-daemon` passed; follow-up `test shadowJar` after the SQL regression passed. Final report: 1,646 tests, zero failures/errors, 14 existing optional/environment skips. Regression tests cover locked/wrong guild despite wildcard permission, owned guild, exact glyph/font rendering, rejected non-emoji aliases/assets, revoke/reset behavior and actual SQLite conditional persistence. Existing architecture/integration graph tests execute in full suite. MariaDB fixture was not run in this task; conditional SQL uses the same supported portable syntax. Build uses the existing local companion API JARs and pinned Nexo dependency; no companion API signature is changed.

Preview: https://enthusia-holiday-rewards-preview.awareyak.chatgpt.site/guild-pumpkin/ ; exact glyph artwork with simulated unlock/equip/revoke and mobile controls. Browser checks cover locked/earned, authority, save/revoke and 390px layout without horizontal overflow. Native Minecraft rendering and resource-pack acceptance remain unverified.

Requires companion EnthusiaHolidays REQ-GUILD-EMOJI-001 plus event YAML EMOJI unlock. No TEST/production deployment, restart or merge performed. Hosted exact-head checks remain separate.
