# Dialog transition flicker correction

Current main fetched: 283d75f1. Isolated branch from running TEST source 04d6e653.
The client report is regression evidence: native dialog CLOSE restores its prior
non-dialog screen before the deferred server callback supplies the next dialog.
Official Paper 26.2 DialogAfterAction docs confirm CLOSE and NONE semantics.

## Requirements and SPEAR evidence

- FT-001: WHEN navigating between guild dialogs, keep the current dialog visible
  while the guarded callback is queued; do not emit an intermediate close packet.
- FT-002: WHEN a guarded callback shows a replacement dialog, do not clear it.
- FT-003: WHEN a callback ends the dialog flow or opens an inventory, explicitly
  dismiss the old dialog after the action. Preserve owner/revision/lifetime and
  reauthorization guards, failure handling and platform fallback.
- FT-004: Apply the same transition contract to menu, reader and text-input dialogs.

Spec: direct client failure and Paper enum contract. Prove: packet-close ordering
regressions plus existing callback/session/recruitment navigation tests. Engine:
NONE plus a weak per-player presentation revision, explicit terminal dismissal.
Architecture: infrastructure-only adapter, no fees/admission/rewards changed.
Refine: complete checks and interactive transition simulation; actual client
acceptance remains required. Project EARS/state helpers are absent.

No TEST deployment, restart or production action is implied by this fix.

- [x] Callback and transition checks
- [x] Full check/shadowJar
- [x] Interactive preview
- [ ] Actual client acceptance

Verification: full check/shadowJar passed, 1950 tests, zero failures/errors and
16 existing skips. Compiled menu/reader/input bytecode inspected: NONE and
presentation tracking in all three. Existing callback owner, replay, expiry,
permission recheck and failure tests passed. No historical red test was invented;
the user's client report and documented CLOSE behavior establish the regression.
Preview checked at 320/390/760px with delayed responses and explicit inventory
return, zero script errors/overflow; private Page embed saved and read back.
Browser simulation does not prove Minecraft packet ordering or client rendering.
All code remains local/unmerged on the combined TEST stack; canonical PR delivery
requires reconciling the parent feature branches. No server changed.

Paper contract: https://jd.papermc.io/paper/26.2/io/papermc/paper/registry/data/dialog/DialogBase.DialogAfterAction.html
