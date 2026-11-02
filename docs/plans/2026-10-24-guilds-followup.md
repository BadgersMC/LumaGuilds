# Guilds remaining acceptance followup

## Spec

- GF-001: WHEN the leaderboard opens directly, its application Close control SHALL use the same measurable themed artwork as other controls, with a server-localized label. Minecraft's client-translated global footer and unknown-font safety fallback SHALL remain intact.
- GF-002: Existing Back/Close hitboxes, callbacks, parent restoration and locale-file preservation SHALL remain unchanged.
- GF-003: Live payment, XP, multiplayer goal persistence and prestige validation SHALL use explicitly authorized disposable participants and fixtures, never the user's existing guild finances or prestige. Source/database proof and native acceptance SHALL be reported separately.
- GF-004: Native theme checks SHALL preview without saving changes to the guild theme. Bedrock source contracts SHALL not be reported as real-device acceptance.
- GF-005: The explicit native MariaDB reward profile SHALL discover prestige and Chronicle goal contracts. Prestige SHALL preserve purchased home capacity, saved home locations, paid activations and unused legacy credits after database reopen; its committed receipt SHALL replay without another charge after reopen.
- GF-006: The Chronicle prestige action SHALL fit its two-column control using a concise localized label; the complete earning-guide title and details SHALL remain in the destination screen.
- GF-007: WHEN prestige commits on MariaDB, its progression timestamp SHALL use the native timestamp type; SQLite SHALL retain epoch milliseconds. Failed transactions SHALL roll back the debit and ownership changes.

## Prove and engine

Continue clean owned PR241 source4d9c344b after fetching authoritative main283d75f1 unchanged. Prior native capture `direct-leaderboard-close` proves the translated application control falls back to gray while neighboring controls are themed. Add a regression that measures the rendered direct control across every theme; demonstrate failure before replacing only that label with a server-localized text label. No project-local EARS/state helpers are present; this manual record supplies requirements and evidence.

## Architecture and refine

Resolve localized application text before width measurement; do not teach the shared metric engine to guess client-side translations. Preserve the global translated native footer and unsupported-font fallback. Run focused regressions, full suite and native MariaDB contracts with the actual pinned Market binary, then canonical build and exact-head CI review. Runtime deployment requires a fresh Roundtable claim and exact companion/preimage guards; previous window1123 was released1124. No production or merge authorization.

## Acceptance evidence

Evidence directory: `operations/guilds-followup-20261024`. Source, CI, loaded artifact, delivered pack and native client checks remain distinct. The current TestBridge is observational and has no Guilds fixture capability; no reflection, SQL or fake-player workaround may be used to mutate shared TEST. Explicit participant authorization and an approved fixture path are prerequisites for those live acceptance rows.

The newly selected native MariaDB prestige contracts first exposed a test seed using epoch milliseconds against TIMESTAMP columns. After making that seed dialect-aware, four successful-prestige contracts still failed with PrestigeResult.Failed (`prestige-maria-red.log`, 8 tests/4 failures). Inspection found the same incompatible binding in the actual reset of guild_progression.last_updated. The infrastructure adapter now binds java.sql.Timestamp for MariaDB and preserves setLong for SQLite. Existing rollback, replay and rejection contracts exercise the transaction boundary. New home/reopen contracts were added as coverage, without claiming historical product failures for them.

The human explicitly authorized disposable accounts/guilds and selected FainNoir. Shared claims1143 and1145 cover the bounded current window; they do not authorize modifying Enthusiast or production. Native multiplayer/finance/device acceptance remains pending until executed with real participants.
