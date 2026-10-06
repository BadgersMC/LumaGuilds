# PR-17 holiday theme unlocks verification

SPEAR specification: `2026-10-06-holiday-theme-unlocks.md` (REQ-121).

The RED run failed test compilation with 38 unresolved references because the
ledger, service, API and `GuiTheme.requiresUnlock` did not exist. The implementation
adds the entity, port, SQLite/MariaDB ledger, the application service, the public
`GuildCosmeticUnlocks` API (registered in ServicesManager), the `setGuiTheme` gate and
the locked-theme selector. The first targeted run failed `LocaleContractTest` because
the selector chose its locale key dynamically; it now uses literal keys.

## Results

- Full `test`: 1017 tests, zero failures/errors/skips (17 new).
- `shadowJar`: successful, `build/libs/LumaGuilds-2.1.0.jar`, which contains the API classes.
- `git diff --check`: clean.

## Local environment adjustments

CI builds `libs/RoseChat-RC-2.jar` and `libs/CombatLogX-api.jar` from pinned
third-party commits. This session did not build third-party code. It compiled against
small local stand-ins for the handful of RoseChat/CombatLogX types LumaGuilds
references. These are gitignored, never committed and not bundled. The artifact built
here is therefore for verification only. CI must rerun with the real jars. No
production dependency, mock behavior or assertion was changed.

## Boundaries and remaining work

- LG-1805: `guild_bg_halloween_<rows>_row` and `guild_bg_christmas_<rows>_row`
  Nexo glyphs/textures belong in the server's resource pack. Until they exist, the
  themes unlock and apply, but the menu background glyph will not render.
- MariaDB was not exercised in this session.
- Live Paper/client validation with EnthusiaHolidays is outstanding.
