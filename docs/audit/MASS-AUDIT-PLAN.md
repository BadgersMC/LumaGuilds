# LumaGuilds Mass Audit — Methodology & Batch Plan

Findings-only audit driven by Hermes coordinators. **No source files are modified.**
Each coordinator audits one disjoint batch and writes a single report at
`docs/audit/<job>.md`, commits it, and pushes its branch to the `fork` remote.
The operator (Claude, locally) fetches every branch, triages false positives, and
synthesizes a consolidated master report for one docs-PR to `BadgersMC/LumaGuilds`.

## What every coordinator hunts for (priority order)

1. **Correctness** — null/async misuse, race conditions, wrong edge cases, broken
   invariants, resource leaks (unclosed `Statement`/`ResultSet`/`Connection`).
2. **Security** — SQL injection (string-concatenated queries vs parameterized),
   unsafe deserialization, permission/command bypass, secret leakage, path traversal.
3. **Layer boundaries** (hexagonal) — `domain` must import nothing from
   `application`/`infrastructure`; `application` must not import Bukkit/JDBC/frameworks.
4. **Test-coverage gaps** — name the specific untested domain/application behavior and
   the test that should exist.

Each coordinator also runs `detekt` for free quality signal and folds real hits in.

## Severity rubric

| Sev | Meaning |
|-----|---------|
| crit | Exploitable security hole or data-loss/corruption bug reachable in normal use |
| high | Logic bug that breaks a feature, or a layer violation that will rot the architecture |
| med  | Edge-case bug, resource leak under load, missing test for important logic |
| low  | Style/quality, defensive-coding nit, minor untested branch |

Report format, one section per finding:

```
### [SEV: crit|high|med|low] <relative/path>.kt:<line> — <one-line title>
**What:** <the problem, concretely>
**Why it matters:** <impact / exploit / failure mode>
**Suggested fix:** <direction, not full code>
**Confidence:** high | med | low
```

## Batches (disjoint file sets, ~573 files, 11 jobs)

Run **sequentially** — the box has one shared LumaGuilds working tree, so a new
dispatch resets it. Order below is by risk.

| Order | Job (`--job`) | Packages | Files |
|-------|---------------|----------|-------|
| 1 | `audit-infra-persistence` | `infrastructure/persistence` | 37 |
| 2 | `audit-app-actions` | `application/actions` | 66 |
| 3 | `audit-ix-commands` | `interaction/{commands,help,listeners}` | 48 |
| 4 | `audit-domain` | `domain/**` | 54 |
| 5 | `audit-app-services` | `application/{services,utilities,errors,events}` | 48 |
| 6 | `audit-infra-services` | `infrastructure/services` | 54 |
| 7 | `audit-infra-edge` | `infra/{adapters,bukkit,listeners,web,placeholders,namespaces,hidden,utilities}` | 23 |
| 8 | `audit-cross-cutting` | `config, di, common, utils, integrations` | 23 |
| 9 | `audit-app-results-ports` | `application/{results,persistence}` | 82 |
| 10 | `audit-ix-menus-a` | `interaction/menus` (first ~69) | 69 |
| 11 | `audit-ix-menus-b` | `interaction/menus` (remaining ~69) | 69 |

The exact file list for each batch is embedded in that job's coordinator prompt;
this doc is the shared methodology + rubric the prompts reference.

## Operator workflow per job

1. `dispatch.sh` with `HERMES_REPO=/opt/data/LumaGuilds`, `--job audit-<batch>`,
   `--docs-branch docs/audit-plan`, `--plan-path docs/audit/MASS-AUDIT-PLAN.md`,
   `--prompt /tmp/audit-<batch>.txt`. (Branch on box becomes `fix/audit-<batch>`.)
2. `poll.sh --job audit-<batch>` — done when `fix/audit-<batch>` appears on the fork.
3. `git fetch hermes 'refs/heads/fix/audit-<batch>:refs/heads/hermes-<batch>'`,
   read `docs/audit/<batch>.md`.
4. After all jobs: triage, dedupe, rank → `docs/audit/MASS-AUDIT-<date>.md`, one PR.
