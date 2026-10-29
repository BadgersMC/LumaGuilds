# Five reference dialog layouts

Authoritative main 283d75f1 fetched 2026-10-17; no new commits beyond the
ongoing Chronicle parent c78c9565. This isolated branch preserves that unmerged
dependency. Private companion art is in Enthusia-Pack PR2, main 6ff2abd.

## Acceptance

- REF5-001: Frame selection and saved presentation settings use full rows with
  icons, left labels/descriptions and separate right status. Frame preview is
  temporary; apply rechecks unlock/authority. Vanilla remains available.
- REF5-002: Catalog perks show configured level groups, current price/status
  and route available purchases through the existing fresh quote. Do not add
  sample XP spending, vault upgrades or branch prerequisites.
- REF5-003: Guild and quest standings show four rows, aligned values, tab
  selection, own-guild indication and valid pagination/back routes.
- REF5-004: All five use the current square, clean, recessed theme palette.
  Full row cards and optional purpose-built heading art reserve their actual
  height; missing art/unknown font metrics keep readable native content.
- REF5-005: Local native samples cover all five, frame preview/apply, toggles,
  tab/pagination and perk quote/cancel. They do not alter real guild state.
  Deliver actual captures plus a responsive private mobile gallery.

SPEAR: specify above; prove layout wrapping/status preservation and existing
permission/purchase regressions; implement only presentation adapters; retain
application/persistence rules; run canonical checks and inspect review findings.
No project EARS/state helper was found. This file is the manual task/evidence
record. No shared TEST/production activation is authorized by this task.

## Implementation and checks

Measured left/right columns wrap independently inside a single row card. Both
columns retain full text; custom fonts fall back to native readable content.
The optional 256x36 heading frame reserves four native text lines; long
translations and older packs retain the native heading. Existing menu routes,
session guards, membership checks, holiday availability, saved preferences and
fresh purchase quotes are unchanged. Vanilla selection is a separate action.
Perks group by their actual configured level and show the configured effect.
Ranking tabs and pages retain real services and guild banner item stacks.

Proof: the new column test initially failed compilation with missing
renderColumns. Focused wrapping/fallback and frame preview/authorization tests
passed after implementation. The first full check caught the optional-heading
resolver contract and an added dynamic locale call; both were corrected without
relaxing the locale baseline. Full check/shadowJar: 1,965 tests, zero failures or
errors, 16 existing optional skips. Final quest-heading refinement also passed
focused dialog/presentation/locale checks and shadowJar.

Native fixture captures use sample data and the wider complete-panel renderer;
the plugin uses adaptive row cards, so the fixture is not pixel-identical runtime
plugin acceptance. Shared TEST, real guild mutations and additional client GUI
scales remain acceptance gates. Source delivery depends on Chronicle PR234;
private artwork is delivered separately through Enthusia-Pack PR2.
