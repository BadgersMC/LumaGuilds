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

- The pack assets now ship in `resourcepack/enthusia-icons` (LG-1907); install them
  like the other Enthusia GUI files, then `/nexo reload all`.
- MariaDB was not exercised in this session.
- Live Paper/client validation with EnthusiaHolidays is outstanding.

## Upstream port (2026-10-06)

The work above landed first in the FainNeito fork, whose `main` was 108 commits behind
BadgersMC/LumaGuilds. It was cherry-picked onto upstream `main` and adapted to the
Enthusia redesign: the holiday themes became the design's `HALLOWEEN` and `CHRISTMAS`
styles (with seasonal icon sets), a revoked style resets to `GuiTheme.DEFAULT`, and the
requirement was renumbered REQ-121 because upstream already uses REQ-094. Tasks:
LG-1904..LG-1908. Full `test` on the port: 1,573 tests, 0 failures, 0 errors, 4 skipped.
The local RoseChat stand-in was extended with the 26.2 `ChannelMessageOptions` record
and `ChannelSettings` shapes, read from the pinned RoseChat source (not built).


## 8 October review refinement

Current authoritative main remains a15b244e8a294bf18e6dedf722462edf9faa40ae. Review continues on the existing upstream PR #208, preserving its public API and stored ownership semantics. Local EARS/state helpers were not present; the requirements, tasks and this manual evidence record remain authoritative.

Replaced earlier dependency stand-ins with actual ignored RoseChat RC-2 and CombatLogX API JARs. Focused theme/ownership/pack checks passed before and after the bounded service/SQL refinements. Native MariaDB now executes cosmetic persistence, guild insert repair and theme compare-and-set contracts using disposable randomly named schemas.

The first native run failed three guild-update assertions because historical MariaDB migrations omit physical-vault columns. Additive JDBC metadata repair adds only absent vault fields before repository loading, preserving existing values. Rerunning the same native contracts passed with zero skips. No live database, resource-pack file, deployment or client acceptance was changed. Hosted quality and full-suite verification remain pending at this record.
