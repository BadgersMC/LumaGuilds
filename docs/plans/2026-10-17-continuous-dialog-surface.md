# Continuous guild dialog surface

## Spec

SURFACE-001: Render a continuous theme-coloured background behind the heading,
tabs, native item bodies and their gaps, ending before native inputs/actions.
SURFACE-002: Preserve the exact native body objects, banners, callback events,
buttons, input fields, navigation guards and business rules.
SURFACE-003: Use each guild inventory's existing palette; Vanilla and missing or
unmeasurable pack/font data retain the readable native presentation.
SURFACE-004: Fit the background to measured wrapped content, including native
4px text insets and 10px body gaps. Never cover controls with an oversized crop.

## Prove

The deployed renderer shades individual cards only. Its referenceBody explicitly
omits the surrounding surface; the approved native preview uses a whole panel.
The native 26.3 client handlers confirm text height = 9px per row + 8px padding,
item height = max(item height, description height), and body spacing = 10px.
Keep backgrounds in the first body so native item rendering remains above them.
Use repeated, offset 9px bitmap strips rather than fixed-size screen crops.

## Tasks and evidence

- [x] Regression: wrapped height, native item insets, control clearance and fallback.
- [x] Shared renderer and reproducible seven-theme pack generation.
- [x] Full checks, pack lint/build, native screenshots and click-through preview.
- [x] Reviewable source/pack changes prepared for PR236/PR2; no activation in this request.

The profile's spear-paper-brownfield skill applies. No project EARS/state helpers
are available; this is the manual SPEAR record.

## Refinement evidence

The initial new regressions failed compilation before the surface boundary existed.
All five final surface regressions pass.
The final full `check shadowJar --offline --no-daemon` passes: 1,987 tests,
zero failures/errors, 16 existing optional-profile skips. All seven source
palettes generate reproducibly; canonical pack lint/build pass with existing
unrelated warnings. The pack lint also rejects missing/incompatible strip fonts.

Native Dawn 26.3 uses components exported directly by GuildDialogSurface and
GuildDialogCards, with source-pack bitmap/spacing providers and native item
widgets. Recruitment, management, rankings and wrapped text render as one panel.
All seven themes were opened and captured. A 55-line scrolling case retains its
background with the header offscreen and ends before the native action buttons.
Native show_dialog sample links navigate only fixture state; actual guarded
server callbacks and shared TEST player journeys remain a separate acceptance
step. The renderer preserves body objects, banners, inputs and action builders.

The capture gallery passes 36 interactions across 320, 390 and 736px with loaded
images, correct selection/captions, no script errors or horizontal overflow.
No shared TEST or production deployment, restart, merge or monorepo pin change
is authorized by this implementation request.
