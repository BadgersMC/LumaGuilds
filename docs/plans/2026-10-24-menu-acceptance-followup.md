# Guilds remaining menu and acceptance work

## Spec

- GM-001: WHEN a dialog is opened directly with no parent, its exit SHALL say Close; nested leaderboard navigation SHALL retain Back and restore its actual parent.
- GM-002: WHEN a projected advancement tree is available and selected, the dialog SHALL close and invalidate its callbacks before displaying keybind-aware instructions. If unavailable, the authoritative dialog SHALL remain usable.
- GM-003: Milestone branch links SHALL retain complete titles and details. Known long advancement-tree action labels SHALL fit the existing native controls without changing hitboxes.
- GM-004: All existing economy, replay, persistence, permissions, provider lifecycle and native fallback contracts SHALL remain intact. New source/CI proof is not live acceptance.

## Prove and engine

Current canonical main283d75f1 was fetched and inspected; continue clean owned PR241 headb5659c73, preserving core PR240 and creation PR242. Add focused regressions for direct/nested exits, full milestone titles, available/unavailable tree handoff and stale callbacks. Record the actual pre-change failures before implementation. No project-local EARS/state helpers exist; this is the manual SPEAR record.

The parent-context regression first failed compilation on the missing read-only query. Initial full validation passed2272 cases with four existing skips and84 MariaDB cases with none, after fixing a missing disposable fixture account (49 authentication failures, no product failures). Review then exercised the unavailable-renderer action through the real transition dispatcher; that regression failed because no replacement dialog suppressed the terminal auto-dismiss. Refreshing the same authoritative dialog in that branch corrects the observed failure. Final full validation must include this correction. Native acceptance is still separate.

## Architecture and refine

Navigation remains in the menu layer and read-only stack inspection does not alter existing transitions. Optional tree availability remains supplied by the infrastructure runtime. The domain reward/persistence engine is unchanged. Run focused tests, full configured suite, pinned Market and explicit native MariaDB profiles, canonical build and exact-head review/CI. Inspect all actionable annotations.

## Native acceptance and coordination

User authorizes TEST deployment and own-client testing once ZoneTitles is ready. SHARED1118 requests explicit handoff; no runtime/client claim exists yet. Prepare exact paired PackPR9/source/artifact pins, backups and rollback before a bounded live request. Do not use silence or previous released windows. Use disposable fixtures for payments, rewards, saved goals and prestige; preserve the user's real guild funds/progression and global ranks. No production writes or merges authorized. Record each acceptance row separately and retain unresolved outcomes. Deliver an updated private interactive preview and native captures only after their respective checks occur.
