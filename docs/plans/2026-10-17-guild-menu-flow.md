# Guild GUI and dialog journeys

Authoritative main 283d75f1 fetched before this isolated branch. Ongoing
reference-layout parent 92fdc5e3 (PR235) retained; Chronicle PR234 remains its
dependency. No working changes overwritten. No deployment or merge authorized.

## Spec

- FLOW-001: WHEN opening a dialog from an inventory, close that inventory on
  the next server tick before presenting the dialog; retain a guarded Back route.
- FLOW-002: WHEN Close or Escape is used, return to gameplay and invalidate
  pending callbacks. Back alone restores the prior screen and its browsing state.
- FLOW-003: WHEN a recruitment detail is opened, Back restores its actual entry
  screen/page, including management previews, inboxes and filtered browsing.
- FLOW-004: WHEN a perk is selected from Chronicle or Perks, use the same fresh
  benefit details and quote/confirmation flow; preserve prices and permissions.
- FLOW-005: WHEN presentation preferences change, other guild views read the
  current saved theme. Preview never persists before Apply.
- FLOW-006: WHEN an old callback fires after another menu or gameplay dismissal,
  it neither spends nor reopens a menu. Preserve unavailable/failed/denied states.

## Prove and delivery boundaries

Source audit maps dashboard, recruitment, settings, progression, quests, Chronicle
and purchases below. Add regressions before implementation; do not invent prior
red evidence. No project EARS/state helper or authoritative SPEAR skill was found;
this is the manual requirement/task/evidence record. Follow spec, prove, engine,
architecture and refinement with the existing test/build tools.

Keep dashboard, bank/vault, homes, members/ranks and other quick operations as
inventory GUIs. Recruitment and information-heavy progression/quest/settings
views use dialogs on supported Java clients. Older Java and Bedrock retain
their existing presentations and permissions. No new progression mechanics.

## Route map

| Entry | Destination | Back destination |
| --- | --- | --- |
| Dashboard GUI | Recruitment board/tools | Same Dashboard instance |
| Board/tools/inbox | Guild/application details | Captured entry and page |
| Dashboard GUI | Settings dialog | Dashboard GUI |
| Settings | Frame picker or detailed settings GUI | Settings dialog |
| Dashboard GUI | Progression | Dashboard GUI |
| Progression | Chronicle or Perks | Progression |
| Either reward browser | Shared benefit details and quote | Origin browser/state |
| Dashboard/Progression | Quests | Origin GUI/dialog |
| Quest | Detail, rankings, selected quest tabs | Exact previous screen |

## Acceptance tasks

- [x] Navigation/session regressions and meaningful failure paths.
- [x] Shared detail/quote routes and refreshed theme contract.
- [x] Full local checks and reviewable canonical PR #236.
- [x] Responsive click-through preview and local Minecraft transition captures.
- [x] Record simulation versus actual plugin/client proof boundaries.
- [ ] Shared TEST player journeys after separately authorized activation.

## Engine and architecture

The inventory hub and its item operations remain intact. A shared presentation
boundary queues inventory-to-dialog changes until the next tick, closes with
OPEN_NEW, and rechecks the source inventory, navigation generation and caller
authorization. Opening another container or manually closing the source cancels
the queued presentation. Plugin closes for chat input preserve the generation.

Back is an ordinary action. Close is the native exit action (also used by Escape)
and invalidates shared navigation. Dialog presentation revisions reject callbacks
from a different reader, form or menu, even when its navigation token is unchanged.
Expired pages retain a separate dismissal callback without authorizing business
actions. Recruitment uses visit history rather than fixed parent guesses.

Perks opens Chronicle's benefit detail with a return callback to the same catalog
page. The existing quote/confirmation services still own permission checks,
prices, atomic spending and results. Presentation adapters reread saved themes on
return; no reward, quest settlement, persistence schema or pack contract changed.

## Proof and refinement evidence

- Initial new lifecycle regressions failed compilation before the new boundary
  existed (operations/guild-menu-flow-red.log). Subsequent failures identified
  outdated Back assertions and fresh-guild mock fixtures; they were corrected.
- The locale dynamic-call baseline tightened from 54 to 53 after replacing the
  conditional recruitment Back/Close lookup with literal Back.
- Added owner/replay/expired Close checks; queued handoff cancellation and failure
  checks; plugin-versus-player inventory close checks; repeated recruitment visit
  unwinding; stale fallback purchase rejection; same-instance navigator return;
  shared benefit return and fresh-theme checks. Existing quest and catalog checks
  retain reward-rule and fallback coverage.
- The responsive navigation simulation passed 114 interactions at 320, 390 and
  736 pixels, with loaded pack icons, no script errors and no horizontal overflow.
  It depicts only the chosen journeys; its inventory hub is schematic and its
  business actions are samples.
- Dawn Java 26.3 local datapack: Settings -> Frame picker -> Back restores
  Settings; Close returns to gameplay; Escape from the picker also returns to
  gameplay. Native F2 captures retain pixels and show body/footer separately in
  the private preview. The local fixtures use native show_dialog/after_action
  close, whereas the plugin uses guarded server callbacks/afterAction NONE.
  This proves native controls/layout, not the plugin's actual packet sequence.
- Preview: https://chatgpt.com/space/page_757a3f06bc64819192d699a4f3991d0b
  The private Page was saved and read back. Its browser link reaches ChatGPT
  sign-in; phone-width content was tested locally, but the signed-out browser
  could not verify the authenticated mobile Page viewer.

Full `check shadowJar --offline --no-daemon` passed with Java 25.0.3: 1,980
tests, zero failures/errors, 16 existing optional-profile skips. Local artifact
version is 3.0.0; SHA-256 and source commit are in the delivery receipt. Shared TEST activation, real GUI-to-dialog packet acceptance,
Bedrock runtime acceptance, canonical merge and monorepo pin advancement remain
separate gates. No production or shared TEST files were modified.
