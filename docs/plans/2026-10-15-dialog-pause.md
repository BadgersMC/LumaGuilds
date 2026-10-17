# Paper dialog pause validation regression

Authoritative main freshly fetched: 283d75f1. Isolated from running TEST ba942d4e.
User error identifies Paper 26.2-128-5749698 rejecting default pause=true together
with afterAction NONE. Previous callback tests and startup checks did not exercise
real Paper dialog construction. This is a regression from the transition fix.

## Requirements and SPEAR evidence

- DP-001: WHEN any guild menu, reader or text-input dialog keeps its screen open,
  explicitly set pause=false before building, preserving afterAction NONE.
- DP-002: Preserve replacement tracking, terminal dismissal, callback guards,
  text values, navigation, fees and reward rules.
- DP-003: Verify with the real Paper build 128 implementation, not only mocks.

Prove: standalone cached Paper 128 builder reproduced the exact user exception;
operations/dialog-pause-20261015/runtime-red.log and runtime-provenance.json.
No Minecraft server was started. Engine: one shared builder configuration used
by all three dialog types. Architecture: infrastructure-only; no resource-pack
or business-rule changes. Refine: real builder probe plus complete checks.
Project EARS/state helpers absent; this file records scoped requirements/proof.

- [x] Exact exception reproduced using actual runtime builder
- [x] Shared configuration and all three consumers verified
- [x] Full check/shadowJar
- [x] Corrected artifact validated with Paper 128
- [ ] Actual player navigation acceptance

TEST correction/restart continues the existing user deployment instruction for
this transition fix. Production remains untouched. Existing interactive transition
preview remains the expected behavior; no layout or visual art changes.

Evidence: full check/shadowJar passed, 1951 cases, zero failures/errors and 16
existing skips. Actual Paper 26.2-128-5749698 builder reproduced the old exception
and accepted the compiled plugin policy with pause=false and afterAction NONE.
Compiled menu, reader and input consumers all invoke that shared policy. Logs,
pinned runtime hash and artifact hash are in operations/dialog-pause-20261015.
This establishes construction compatibility; client navigation remains an
in-game acceptance gate. Parent combined TEST stack remains local/unmerged,
with canonical PR reconciliation still outstanding.
