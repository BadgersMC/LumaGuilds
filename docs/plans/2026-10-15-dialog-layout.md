# Dialog layout refinement after TEST screenshot

Authoritative main 283d75f1 freshly fetched. Isolated branch descends from the
81a4399b TEST candidate; prior composite work stays unmerged. User screenshot
confirms aligned frames, but exposes duplicate heading, detached native item,
unlabelled profile fields, and raw fractional-second timestamp.

## Requirements (SPEAR)

- DL-001: WHEN showing a dialog, render its title once through native dialog
  chrome, with no repeated framed heading or decorative divider before content.
- DL-002: WHEN a themed item has card text, render the actual item beside a compact
  heading above the full-width card, never centred beside the whole paragraph.
- DL-003: WHEN showing a recruitment profile, label descriptive sections and
  display UTC listing timestamps to the minute; preserve exact fee/currency,
  fields, permissions, and join/Discord confirmation behavior.
- DL-004: Preserve native action buttons, keyboard focus, callbacks, CLOSE and
  search response behavior. These controls remain gray; Nexo glyphs do not skin
  native button surfaces individually. Do not promise a one-to-one CSS mockup.

Proof: client screenshot and focused regression. Engine: infrastructure renderer
layout plus recruitment presentation only. Architecture: no domain/payment or
reward rules change; no new private pack assets required. Refine: focused and
full checks, interactive simulation and client acceptance still required.
Project EARS/state helpers absent; this scoped record holds task evidence.

- [x] Regression proof
- [x] Renderer and profile presentation
- [x] Full checks and local artifact
- [x] Interactive preview and delivery limits
- [ ] Actual Minecraft acceptance

No server mutation or restart is authorized by this refinement request alone.

Evidence: layout-red.log records the generic subtitle regression failing against
81a4399b. Full check/shadowJar passed: 1945 tests, zero failures/errors, 16 existing
skips. New timestamp formatting requires real Instant fixtures rather than a
relaxed mock. Localization scanner remains zero missing/unused keys; the dynamic
call baseline falls from 55 to 54 because the conditional generic subtitle call
was removed. Changed Kotlin files formatted; diff whitespace check passes.
Preview navigation checked at 320/390/760/1024px, zero overflow or script errors.
Private Page embed saved/read back; actual signed-in mobile rendering and client
Minecraft rendering remain unverified. No server changed. PR delivery remains
pending reconciliation of this combined local TEST stack with canonical main.
