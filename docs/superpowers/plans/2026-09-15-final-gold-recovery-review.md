# Final PR 143 Recovery Review Implementation Plan

> Execute inline using the SPEAR test-first cycle and superpowers:executing-plans. Do not deploy or mark the PR ready.

**Goal:** Close review findings 4015429385, 4015429435, and 4015429487 without guessing external outcomes.

**Spec:** REQ-092 and its paid-admission clarification in `docs/requirements.md`.

**Architecture:** Keep money mutation in GuildGoldRepositorySQL. Store external-attempt evidence before external effects. Physical reservations retain exact serialized item receipts and lifecycle state. Banner purchases persist a payment/delivery identity independently of a menu instance. Recovery is conservative: unknown effects remain held; no timeout implies a refund or a successful delivery.

## Tasks

- [x] Pending-operation evidence and recovery: extend GuildGoldRepository/GuildGoldSchema/GuildGoldRepositorySQL; test abandoned preparation, started-but-unknown effects, known rejected effects, confirmed credit after restart, and replay. READY preparations can be atomically rejected after the cutoff; beginExternal must fail after that rejection. Only confirmed normal deposits may finish automatically; paid admission still requires confirmed membership completion. Schedule recovery on the server thread.
- [x] Physical commit: replace Boolean with Committed/NotConsumed/Unknown. Persist reservation identity and exact stacks before removal, then removal/restore/commit phases. Use BALANCE_APPLIED until consumption is confirmed. Roll back the canonical credit before returning items only for proven nonconsumption; interrupted restoration remains held. Test crash/restart and same-ID retries.
- [x] Banner purchase: persist one active purchase per player/guild; reuse its transaction ID and immutable amount/banner on retry. Record delivery intent before inventory mutation; never redeliver an uncertain attempt. Route guild-bank payment through the canonical service and remove ID-less physical mutation overloads. Test restart between debit and purchase marker, and delivery uncertainty.
- [x] Full test/build (920 passing, zero skipped) and ownership scan. The backup-restoration balance write remains a separate LG-1209 gate; no live MariaDB verification claim.
- [ ] Push verified changes, reply individually to CodeRabbit with @coderabbitai, and resolve the three verified findings.

## Safety invariants

- Legacy PREPARED rows without attempt evidence are unknown, not safely abandoned.
- No external effect may begin unless its persisted transition succeeds.
- A crash during an external effect is not evidence of success or failure.
- Recovery never grants paid membership without the admission completion proof.
- Preserve physical-item metadata; do not recreate a generic denomination in place of the original stacks.
- Payment replay and item-delivery replay are distinct; an idempotent debit alone cannot prevent duplicate banners.
