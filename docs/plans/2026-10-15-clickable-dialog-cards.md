# Clickable guild and quest dialog cards

Source authority: BadgersMC/LumaGuilds. Main freshly fetched at 283d75f1,
unchanged. Work begins at combined TEST e4b0cc4e on a new local branch.
The user's handoff requests a complete local JAR, retains CLOSE in the shared
dialog, and forbids pushing, PRs, server changes, reloads and restarts. Private
art branches and licensed assets are not touched. Referenced /home/claude patch
files are unavailable here; implementation follows the written specification.

## Requirements

- DCC-001: WHEN enabled, THE SYSTEM SHALL attach a shared guarded click event and
  fee/route hover details to recruitment card panel glyphs, spacing and text.
- DCC-002: WHEN a guild card is clickable, THE SYSTEM SHALL omit its duplicate
  Open action while retaining information-only entries, tools and navigation.
- DCC-003: WHEN disabled, THE SYSTEM SHALL retain readable cards and restore
  explicit Open actions using one shared CLICKABLE_CARDS switch.
- DCC-004: WHEN any card or action is submitted, THE SYSTEM SHALL validate actor,
  page ownership/revision/lifetime and current authorization on the server thread,
  consuming the page at most once. Stale, replayed or foreign clicks do nothing.
- DCC-005: WHEN supported Java clients open weekly quests, THE SYSTEM SHALL show
  clickable quest summaries and retain Weekly rankings, pagination and Back as
  native actions. Unsupported clients or rendering failures retain the inventory.
- DCC-006: THE SYSTEM SHALL retain CLOSE in GuildMenuDialog, existing reader/input
  policies, complete quotes, real banner items and all combined TEST features.

## Platform proof and acceptance boundaries

Minecraft's official 1.21.6 release notes explicitly support hover/click events in
dialog body components and say body clicks also evaluate after_action:
https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6
Paper's dialog API documents separate native item bodies and text descriptions:
https://docs.papermc.io/paper/dev/dialogs/

Only the description's glyph artwork/spacing/text can receive component click
events. The separate native banner/quest item image has no click-action field;
do not claim an arbitrary full rectangular hitbox or clickable native image.
Nexo styles/fonts must survive decoration. No additional texture installation is
required. CLOSE may reintroduce the previous inventory flash and must be tested.
Weekly rankings means a chooser for existing per-quest standings; this change
does not invent a new aggregate leaderboard or payout semantics.

Project EARS/state helpers are absent. This manual record follows SPEAR:
spec -> focused proof -> minimal adapter implementation -> integration -> refine.
Tests exercise new presentation/dispatch contracts; no historical red/green or
real-client acceptance is inferred. Existing docs/requirements.md, implementation
layer rules, tasks, earlier dialog plans and tests remain applicable.

## Tasks

- [x] Inspect handoff, authoritative base, API contracts and existing flows.
- [x] Establish focused failures for missing card callbacks and presentation.
- [x] Implement shared callback dispatch and card decoration with fallback.
- [x] Wire recruitment and weekly quest cards without duplicate actions.
- [x] Run focused/full checks, inspect diff and build one local combined JAR.
- [x] Deliver interactive simulation and artifact/evidence outside repo.
- [ ] In-game acceptance: card glyph/spacing/text, native-image limitation,
  tooltips, scales 1-4, long names, Escape, input, pagination/parent return,
  membership/permission changes, stale/replayed clicks, CLOSE flash and fallback.

Verification: Java 25/Paper 26.2 local `check shadowJar` passed; targeted Kotlin
formatting and `git diff --check` passed. Regression coverage includes guarded
one-shot dispatch, preserved styles, full fee tooltip, disabled-card buttons,
quest pagination, ranking chooser and authorization changes. The initial focused
failure established missing contracts at compile time, not a reproduced client
bug. Exact counts, build version, commit, checksum and client checklist are kept
beside the local JAR. Browser simulation passed navigation and responsive checks
at widths 320, 390, 760 and 1024; eight styles retain search/filter/return flows.
No hosted CI or in-game acceptance is claimed. No assets or remote operations
were included in this local code delivery.
