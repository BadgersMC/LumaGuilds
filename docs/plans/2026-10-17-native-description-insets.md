# Native dialog item description insets

## Spec

WHEN a shared guild dialog places a card in an item description, reserve the
client text widget's padding before fitting the 257px card advance. Apply the
same calculation to column cards, item headings and the readable fallback.
WHEN the client lacks the Close translation key, display a readable fallback.
Preserve all callbacks, pagination, permissions, prices and persistence rules.

## Proof

The reported quest ranking screen uses a 260px item description. Minecraft 26.3
FocusableTextWidget.Builder uses 4px padding per side and subtracts it from
maxWidth; this leaves 252px, less than the 257px background advance. ItemHandler
passes description.width directly to maxWidth. The local cached Nexo pack has
the expected 256x9 card bitmap providers, so replacing those images cannot fix
this width error. Baseline inset and Close-fallback regressions both failed.

## Engine and architecture

Set the shared description width to 268px, preserving its intended 260px text
area after the native inset. Keep header/body card geometry and interaction
callbacks unchanged. Supply Close as the missing translation's fallback.
The companion pack also needs its manual dialog_spacing_v2 font tracked in the
authoritative source; it existed only as an older installed/local file. A new
canonical pack lint rule rejects that missing or incompatible font.

## Refine

Full check and shadowJar pass: 1,982 tests, zero failures/errors, 16 existing
optional-profile skips. Canonical companion pack lint/build pass with existing
unrelated warnings. Native before/after fixture uses the same row composition,
cached Nexo providers and widths 260/268; business actions are local samples.
Real shared TEST activation and server callback acceptance remain separate.
No project EARS/state helper is available; this is the manual SPEAR record.
