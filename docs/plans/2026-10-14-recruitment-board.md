# Recruitment board presentation

Authoritative main fetched before edits: 283d75f1; ongoing feature base ab40a920.
Continue in the existing isolated codex/nexo-dialog-theme checkout. No deployment,
reload, restart, new PR or production change is authorized by this task.

- REC-029: WHEN Java players browse recruitment, THE SYSTEM SHALL display real
  banner cards with complete admission/fee summaries, optional selected-theme
  panel artwork and working search/category controls on that screen.
- REC-030: WHEN a player submits board search, THE SYSTEM SHALL validate the
  category, trim and bound the query to 64 characters, reset result pagination,
  and retain the existing session/revision/authorization guards.
- REC-031: WHEN board artwork is incomplete, THE SYSTEM SHALL keep the entire
  functional native board readable without unresolved glyphs. Artwork SHALL
  occupy body lines only and SHALL NOT masquerade as clickable controls.

The native client lays out body cards before input widgets and action rows.
The reference's tabs above an input above independently clickable banner cards
cannot be promised by this API. This implementation uses a category control and
explicit guild-opening buttons. No global vanilla widget texture replacement.
Custom panel line spacing is a resource-pack prototype requiring real-client
acceptance at different GUI scales. The interactive preview must depict that
adapted layout, not claim the browser concept is a Minecraft screenshot.

SPEAR helpers/EARS validator are absent. Manual requirement, task and evidence
record applies. Presentation adapters own artwork; service, fee, membership and
payment semantics are unchanged.

- [x] Prove search bounds/category validation, pagination, direct navigation and
  complete card text under missing artwork.
- [x] Implement native board controls and additive private panel assets.
- [x] Run targeted checks, full check/build and inspect the resulting diff.
- [x] Prepare an accurate interactive preview, recording client-acceptance limits.

Proof: the initial targeted build failed because GuildDialogSearch did not exist.
The final local `check shadowJar` passes 1,874 cases with zero failures/errors and
16 existing optional skips. Seven added cases cover invalid/bounded/blank queries,
category submission, pagination reset, retained advanced filters, direct board
navigation, complete long-fee panel text, banner fallback and incomplete/invalid
Nexo card assets. Existing dialog-session and architecture contracts pass.
Localization references were corrected after the first full run caught dynamic
keys; no baseline was relaxed. Changed files pass formatting and local complexity
checks. The private bundle adds 21 panel images, one glyph-definition file and a
dedicated spacing font to the previously prepared theme bundle. No licensed
texture bytes or Minecraft font bytes are tracked in Git.

The preview uses the prepared panel textures and bitmap text. Search, activity,
profile return, viewer navigation and empty states pass at 320/390/760/1024 pixels
across seven styles and Vanilla without script errors or horizontal overflow.
Its guild banners are representative sample art; the plugin resolves persisted
guild banners with their real patterns. This is a responsive simulation, not a
Minecraft screenshot. Native scrolling, line wrapping and negative font spacing
still require TEST client acceptance after separately authorized installation.

Delivery remains an unmerged standalone review artifact. A TEST deployment must
preserve the separate combined TEST branch's quest/stall/invite-block changes.
Hosted checks and maintainer review are separate from the local evidence above.
