# LG-1202 — Developer handoff

Prepared 2026-09-17 from main `8037101` (merged PR #143).
Branch: `codex/lg-1202-reward-handoff`. This draft prepares the next developer;
it does not implement runtime rewards. The operator approved the linked catalog
and its prices on 2026-09-17. LG-1209 is
marked complete per operator confirmation; LG-1202 is complete as a DOC task.

## Read first — committed sources of truth

1. `docs/requirements.md`: REQ-050, then REQ-049, REQ-054, REQ-056 and
   REQ-090 through REQ-093 for progression, homes, chapters, gold and prestige.
2. `docs/superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md`:
   later prestige/gold decisions supersede conflicting older designs.
3. `docs/superpowers/specs/2026-08-27-chapter-2-progression-revamp-design.md`:
   progression/economic context, subject to the later spec.
4. `docs/implementation.md` and `docs/tech-stack.md`: relevant architecture,
   configuration and dependency sections; follow layer boundaries.
5. `docs/tasks.md`: LG-1202 is a DOC task. Elo, chapter rollover, gold costs
   and prestige implementation are separate work, not automatic scope additions.

Historical context: `docs/design/prestige.md` and
`docs/superpowers/plans/2026-08-28-chapter-2-permanent-progression.md`.
Their older permanent-level wording must not override current-run resets
and permanent-asset rules in REQ-056/093.

## Approved constraints

- Complete level 1–100 catalog. Every level has a visible benefit/event.
  Ordinary non-checkpoint levels advance bank capacity; every fifth level
  unlocks a purchasable numeric perk, every tenth a purchasable major perk.
- Unlocking a purchase is not granting the perk. Checkpoint purchases cost
  raw gold from the guild bank. Ordinary level benefits are automatic.
- No free gold as filler rewards: the operator rejected currency inflation.
- Reuse supported infrastructure. Unsupported perk ideas require separate
  approval; do not promise them as existing features.
- Fixed configurable membership capacity, default 50, independent of level
  and prestige. It must not fall when a guild resets its level.
- Levels 101–200 are seasonal Elo presentation, not XP reward tiers.
- Purchased home capacity and activated locations are permanent. Capacity
  and paid home activation are distinct (REQ-054).
- Prestige ships disabled. Defaults: maximum three lifetime prestiges,
  fees 10,000 / 20,000 / 30,000 gold, one purchased eligible non-permanent
  perk retained and one extra permanent home-capacity unit per prestige.
- Prestige resets run level/XP and other non-permanent perks, not gold
  remainder, items, homes, membership, Elo, source caps or weekly quest state.
  Wars block prestige; post-fee gold must fit post-prestige bank capacity.
- Weekly quest XP bypasses fixed guild-wide per-source caps. No per-player
  cap or combined daily guild XP cap; do not rebalance this incidentally.

## Deliverables / next developer checklist

- Accepted checkpoint (2026-09-17): [approved 100-level catalog](2026-09-17-lg-1202-reward-catalog-proposal.md).
  Includes source inventory, approved prices/effects, arithmetic, retention rules,
  community copy and later implementation gates. The operator approved all six decisions;
  LG-1202 is complete as documentation. No runtime/config changes or deployment.

- [x] Inventory existing reward effects and configuration/registry consumers;
  record real code references before proposing unsupported mechanics.
- [x] Produce a canonical table with exactly one row per level 1–100:
  automatic benefit, purchasable perk ID/name, exact effect, gold price,
  prerequisite, prestige/rollover persistence, prestige eligibility and
  existing implementation reference.
- [x] Resolve level 1 initial capacity and overlapping fifth/tenth-level
  rewards explicitly with the operator; do not assume whether they stack.
- [x] State incremental versus absolute effects, cumulative bank capacity,
  maximum home capacity and total purchase cost for a full run.
- [x] Explain retained-perk stacking/rebuy rules, permanent homes and
  post-prestige capacity; flag undecided behavior for approval.
- [x] Label proposed numbers separately from approved requirements. Get
  final prices, capacity increments and named perks approved before coding.
- [x] Write a concise community-facing explanation matching the table.
- [ ] If adding machine-readable config/catalog, test level coverage,
  uniqueness, checkpoint rules, valid prices/effects and persistence first.
- [ ] Follow SPEAR for implementation: requirement -> failing test -> minimal
  code -> architecture checks -> full verification. Update specs first if
  an approved decision changes their contract.
- [x] Record evidence in tasks.md and mark LG-1202 complete only after acceptance.

## Setup and verification

Use JDK 21 and the Gradle wrapper. Local ignored dependencies are listed in
build.gradle.kts: `libs/RoseChat-RC-2.jar`, `libs/EnthusiaPlaytime-api.jar`,
and `libs/CombatLogX-api.jar`. Consult workflow setup and obtain required API
JARs from their source projects/operator; do not commit private binaries,
live credentials or production databases.

Build deployable artifacts with `./gradlew test shadowJar`, not plain jar.
Local version defaults to 2.1.0; supply `-PreleaseVersion=...` if desired.

This is docs-only preparation: tests were not rerun in this new worktree.
Historical baseline: 922 tests passed and shadowJar succeeded before the
banking merge; release testing/packaging on main also succeeded. This is
not a claim of new-worktree verification or LG-1202 implementation.

Local workspace: `D:/BadgersMC-Dev/LumaGuilds/.worktrees/lg-1202-reward-handoff`.
Other developers can check out the branch normally; this path is not required.
No production deployment, local server changes or DB mutations are authorized
as part of this handoff.
