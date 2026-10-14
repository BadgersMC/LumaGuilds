# Guild recruitment delivery

Base inspected: canonical BadgersMC/LumaGuilds main 283d75f1e1016d415aa5afdfac840252f0391846.
Branch: codex/guild-recruitment. Production and TEST are untouched.

## Approved scope and EARS requirements

- REC-001: WHEN a rank is granted Manage Recruitment, THE SYSTEM SHALL permit recruitment invites and application review without granting kicks, promotions or rank editing. Existing Manage Members invite access SHALL remain compatible. Leaders retain implicit access.
- REC-002: WHEN a rank is granted Edit Recruitment Settings, THE SYSTEM SHALL permit listing/mode/requirements/Discord edits independently of application review.
- REC-003: WHEN a player opens /g lfg, /g browse or /g recruitment, THE SYSTEM SHALL open the same searchable recruitment board with profiles, slots, fees and joining methods.
- REC-004: WHEN an existing guild has no recruitment profile, THE SYSTEM SHALL interpret its open flag as Join Immediately or Closed without changing its fee.
- REC-005: WHEN recruitment is Apply In Game, THE SYSTEM SHALL accept at most one active application per player/guild and three per player, with configurable limits and seven-day default expiry.
- REC-006: WHEN a recruiter accepts an application, THE SYSTEM SHALL create a 48-hour default join offer and record the actor; it SHALL NOT enroll or charge the applicant until explicit confirmation.
- REC-007: WHEN an applicant confirms an offer, THE SYSTEM SHALL recheck identity, membership, current capacity, current fees and current eligibility before using canonical admission/payment; uncertain payment outcomes SHALL NOT be retried automatically.
- REC-008: WHEN an applicant withdraws or joins a guild, THE SYSTEM SHALL withdraw the application or close other active applications respectively; application answers SHALL be private to the applicant and authorized recruiters.
- REC-009: WHEN a guild uses Discord applications, THE SYSTEM SHALL require a valid HTTPS Discord invite and instructions, show the exact destination before leaving the game, and never infer application submission or acceptance from opening a link. Java receives a clickable link; Bedrock receives a copyable invite.
- REC-010: WHEN a player without a guild completes onboarding, THE SYSTEM SHALL send one non-interrupting recruitment prompt; optional periodic reminders SHALL respect a personal opt-out and configurable interval. Leaving a guild SHALL provide a quiet browse shortcut.
- REC-011: WHEN recruitment is deployed, THE SYSTEM SHALL support spawn NPC/hologram discovery through the same board command; placement/configuration is an operator integration, not automatic world mutation.
- REC-012: WHEN recruitment is changed, THE SYSTEM SHALL provide a responsive private clickable preview and distinguish simulation, local proof, hosted CI and in-game acceptance.

## Tasks and proof

- [x] TDD REC-001/002: dedicated permission vocabulary, Java/Bedrock editor labels and invite reauthorization on confirmation. Existing Manage Members invitation access retained. Recruitment review/settings use-case wiring remains below.
- [x] TDD REC-004/005/006/008/009: pure policy/lifecycle foundation and focused boundary tests. Not a running application/admission feature yet.
- [ ] TDD REC-005/006/007/008: durable SQLite/MariaDB applications, transitions, audit and payment/admission orchestration; restart/race/failure tests.
- [ ] TDD REC-003/009/012: shared board/profile/applicant/recruiter/settings models and Java/Bedrock menus, search/filter, safe external link confirmation.
- [ ] TDD REC-010: durable prompt/opt-out/cooldown preferences and notification lifecycle.
- [ ] INFRA REC-011: spawn discovery integration instructions. Proof/engine do not apply to placing documentation; runtime placement requires operator authorization.
- [ ] DOC REC-012: update private interactive preview to implemented screens; mobile layout and hosted delivery validation.
- [ ] TDD: full checks, architecture contracts, review/CI, reviewable source PR. No merge, deployment, restart or reload authorized in this task.

Project has SPEAR requirements/tasks/architecture documents but no project-local EARS validator or state helper. This record is the manual requirement/task/state evidence; no automated SPEAR pass is claimed.

## First slice evidence

- New policy tests initially failed compilation because the recruitment policy/model did not exist; subsequent policy/permission/architecture tests passed. This is missing-feature proof, not a historical behavioral regression claim.
- SQL tests initially failed compilation because the repository adapter did not exist. SQLite tests now cover durable decisions, concurrent duplicate/limit enforcement across repository instances, stale compare-and-set rejection, expired reapplication, uncertain admission reservations, failed writes and clock precision.
- Final `check --offline` after the Bedrock server-thread refinement: 1,786 cases, zero failures/errors, 16 existing optional skips. This is local proof, not hosted CI, native MariaDB, activation or player acceptance. A duplicate locale mapping introduced during implementation was caught and corrected by the existing locale contracts.
- SQL adapter is deliberately not registered with runtime DI yet. No recruitment tables are created in production/TEST. No new application state is treated as membership or payment authority until the admission use case is implemented and tested.
- Native MariaDB verification remains pending; portable SQL syntax and InnoDB applicant locking alone are not native-engine proof.
- Review integration with unmerged invite-block PR #232 before delivery of admission. This branch starts on main and does not import or replace that separate PR.
- Private updated preview models separate permissions and offer-confirmation, but board/inbox/settings/discovery remain simulated. Private art remains outside the public repository.

Player advertisements under a Looking For Guild tab remain a future option: current LFG is guild discovery, not an existing player-advertisement service. No unsolicited public posting, Discord bot automation, promoted listings or fee policy changes are included.
