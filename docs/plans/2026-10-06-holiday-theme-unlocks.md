# PR-17 holiday guild menu themes — SPEAR

REQ-121. Holiday-themed guild menu backgrounds are earned through EnthusiaHolidays
(guild goals during holiday events) and rendered by LumaGuilds. EnthusiaHolidays
keeps the permanent earned ledger and pushes each unlock or revocation here until
LumaGuilds confirms; it never edits guild menus. Contract (consumer side):
EnthusiaHolidays `docs/lumaguilds-integration.md`.

## Spec

- `GuiTheme` gains `requiresUnlock`; `HALLOWEEN` and `CHRISTMAS` require it.
  Existing progression themes are unchanged (`requiresUnlock = false`).
- Ownership ledger `guild_cosmetic_unlocks(guild_id, cosmetic_type, cosmetic_key,
  display_name, source, unlocked_at)`, primary key `(guild_id, cosmetic_type,
  cosmetic_key)`, SQLite and MariaDB, preloaded cache like the emoji grant ledger.
- `GuildCosmeticUnlocks` (ServicesManager, JDK types only):
  `unlockCosmetic(guildId, type, key, displayName, source)`,
  `revokeCosmetic(guildId, type, key)`, `getUnlockedCosmetics(guildId, type)`.
  Idempotent; false only for a missing guild or persistence failure; unknown keys
  and types are stored (forward compatibility). Type and key are normalised to
  upper case; blank or over-long values are rejected.
- `setGuiTheme` rejects a locked theme even when called outside the menu.
- Revoking an equipped `MENU_THEME` resets only its `gui_theme` to the default style (`GuiTheme.DEFAULT`).
- The selector lists every theme; locked ones show a lock and "earned through
  holiday guild goals (/holidays)" lore and cannot be applied. A failed change
  reports failure instead of claiming success.
- Guild disband deletes cosmetic ownership in the same database transaction as the guild and its other dependent state; a failed disband preserves the ledger row.

## Prove

Repository: restart persistence, idempotent upsert, delete, per-guild isolation,
normalisation. Service: unlock unknown guild → false; idempotent unlock; locked
theme availability before/after unlock; revoke resets equipped theme only when it
matches; non-holiday themes always available. GuildServiceBukkit: locked theme
rejected, owned holiday theme accepted. API: delegation, validation, missing guild.

## Arch

Entity in domain (JDK only), port in application/persistence, policy in an
application service, SQL in infrastructure, API adapter in `api` like
`GuildLookupImpl`. `LayerRulesTest` must stay green.
