# Guild emoji display safety (SPEAR)

## Spec

WHEN a guild selects an emoji, THE SYSTEM SHALL accept only a glyph registered
as an emoji by Nexo, in addition to existing guild and player permissions.
WHEN displaying a saved guild emoji, THE SYSTEM SHALL omit unknown or non-emoji
glyphs from Nexo placeholders, MiniMessage glyph tags, font tags, and menu labels.
WHEN Nexo cannot resolve an emoji, THE SYSTEM SHALL omit it rather than delegate
an unvalidated glyph name. Guild names/tags and clearing an emoji remain available.
Valid emoji aliases, their fonts, and the existing permission prefix remain supported.

## Observed failure and acceptance boundary

Read-only production inspection on 2026-10-04 found TAB/groups.yml emits
`%lumaguilds_guild_emoji%`. Nexo's `guild_bg_enthusia_*_row` glyphs use the
reported gold/crimson menu textures, height 256, and are not marked `is_emoji`.
The screenshot shows a matching menu background beside the Vegas guild tag.
The downloaded database copy shows Vegas has `:imp:` and a colored text tag.
The generated ZIP and the startup log's published Polymath URL have identical
SHA-256 `85cadb88354e026208e16500a07fea87a8c5bc5f05bf1639482ce4be256fc15e`.
In that ZIP, `nexo:default` U+B00F has one bitmap provider, height 9/ascent 8;
its texture is the purple imp face. It has no menu-provider collision.
The database was read with immutable/read-only SQLite against the main DB copy;
the live WAL was not captured, so this is a checkpoint snapshot, not an atomic
live backup. The screenshot's exact trigger remains unconfirmed: the affected
client's pack stack and its displayed/cached TAB value still need acceptance evidence.
The source guard below is preventive hardening, not a proven repair of Vegas's
reported client rendering. No production files or server state are changed.

## Prove / engine / arch / refine

- Regression tests must reject a resolved menu glyph during validation and all
  three placeholder renderings, preserve valid emoji aliases/fonts, and fail
  closed on missing resolution. Verify service persistence cannot bypass validation.
- Keep the Nexo public API adapter and validation in infrastructure; preserve
  domain models, guild permissions, removal semantics, and legitimate GUI backgrounds.
- Run focused regressions and the existing architecture/full checks.
- Project EARS/state helpers are absent from this checkout; maintain this record
  and docs/tasks.md rather than claim automated EARS validation.
- Production/client acceptance remains open until reviewed source is merged,
  the canonical build/pins are verified, and an authorized rollout is tested.

## Local evidence

- RED: `GuildEmojiSafetyTest` failed on current main's format-only validation
  before implementation (one test, one assertion failure).
- GREEN: focused safety/font/architecture tests passed; full `check --offline`
  passed 1,502 tests, zero failures/errors, four skips on Java 25.
- `GuildEmojiPersistenceSafetyTest` proves a directly authorized service caller
  cannot persist a menu glyph and can still clear it. `GuildEmojiPlaceholderSafetyTest`
  proves the real PAPI expansion omits saved menu glyphs in all three emoji fields
  while retaining the guild name. Alias/font and resolver failure tests pass.
- Selection, PAPI, leaderboard and guild-chat integration use the same infrastructure
  validation. GUI backgrounds and persisted guild data remain untouched.
