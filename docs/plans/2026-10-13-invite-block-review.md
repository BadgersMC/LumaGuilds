# Invite block PR review

Spec: REQ-150/151 and user-provided invite-block document. Canonical main fetched at283d75f1 and PR #232 fork head db53a274 inspected. Isolated codex/invite-blocks-pr-review contains only invite work. Preserve prior local quest/stall work outside this PR.

Prove: local integration regressions reproduced raw invite counts/presence and malformed UUID loading, then passed after corrections (local source5176e7f). Port those tested refinements plus routing/viewer coverage. Original PR Codacy detail reports136 new findings (summary first100); formatting, effective visibility/public docs, complexity, excessive returns/deep nesting, and migration magic values will be addressed without disabling quality rules.

Engine/arch: SQL repository loads blocks once, publishes cache only after durable write, serializes writes per instance and fails closed on incomplete load. Application ports remain independent of platform; Bukkit listeners/API wiring preserve current contracts and memberships. No GUI changes, so visual preview not applicable.

Verification pending: focused/full Java25 Paper26.2 tests, exact-head GitHub checks and review. No EARS/state helpers or generic verification.md present; manual traceability used. TEST currently runs earlier local combined artifact; later PR refinement is distinct and will not be deployed this turn. No production changes or merge. Multi-server cache invalidation and real player/native MariaDB acceptance remain outside local proof.

## Local validation

Ported correctness/routing/viewer regressions from tested local integration. Original malformed load and invite-count cases failed before correction; no historical red/green claim for inherited tests. Added SQLite trigger-constraint case to verify failed writes cannot be mistaken for duplicate-key success. SQL duplicate acceptance now verifies that the exact block row exists. Repository mutations remain serialized per instance.

Ktlint1.0.1 targeted new-file formatting at120 columns passed using an external operation-only init script; project quality configurations untouched. Full Java25/Paper26.2 offline check shadowJar passed; results recorded in operation evidence. SQLite load/reopen/idempotency/write failure/schema create/repair, real ACF block/unblock/list permission routing, memory-only viewer filtering and JDK API delegation exercised. Native MariaDB and player/cross-server cache acceptance remain separate. LocaleContractTest initially rejected a dynamic message-key refactor; restored literal keys and full contract passes.

Hosted exact-head checks pending after publication. TEST remains on prior local combined5176e7f build; no further server operations or production changes.
