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

- [x] TDD REC-001/002: dedicated rank permissions and live invitation, inbox and settings reauthorization. Existing Manage Members invitation access retained.
- [x] TDD REC-004/005/006/008/009: pure bounded policy, offer lifecycle and exact Discord invite validation.
- [x] TDD REC-005/006/007/008: durable SQLite/MariaDB applications, applicant locking, stale transitions, audit and canonical admission integration. Uncertain payments remain reserved for staff review and cannot be paid twice through another offer.
- [x] TDD REC-003/009/012: shared board/profile/applicant/recruiter/settings controller and native Java/Bedrock menus, search/filter, explicit joining confirmation and exact Discord destination confirmation. Custom banners/emojis remain public profile decoration; application answers stay private.
- [x] TDD REC-010: durable one-time prompt, personal opt-out, optional cooldown and private notification digest. Session timers do not survive a reconnect; notification polling is limited to one online player per second.
- [x] INFRA REC-011: spawn discovery integration instructions below. Proof/engine do not apply to placing documentation; runtime placement requires operator authorization.
- [x] DOC REC-012: private interactive preview updated to implemented board/profile/application/review/settings/Discord screens, Java inventory and paged Bedrock forms. Local mobile widths 320/390 have no horizontal overflow; private Page embed receipt and readback verified. Sample actions are simulated; actual phone and Minecraft client acceptance remain separate.
- [ ] TDD: full checks, architecture contracts, review/CI, reviewable source PR. No merge, deployment, restart or reload authorized in this task.

Project has SPEAR requirements/tasks/architecture documents but no project-local EARS validator or state helper. This record is the manual requirement/task/state evidence; no automated SPEAR pass is claimed.

## First slice evidence

- New policy tests initially failed compilation because the recruitment policy/model did not exist; subsequent policy/permission/architecture tests passed. This is missing-feature proof, not a historical behavioral regression claim.
- SQL tests initially failed compilation because the repository adapter did not exist. SQLite tests now cover durable decisions, concurrent duplicate/limit enforcement across repository instances, stale compare-and-set rejection, expired reapplication, uncertain admission reservations, failed writes and clock precision.
- Final `check --offline` after the Bedrock server-thread refinement: 1,786 cases, zero failures/errors, 16 existing optional skips. This is local proof, not hosted CI, native MariaDB, activation or player acceptance. A duplicate locale mapping introduced during implementation was caught and corrected by the existing locale contracts.
- Runtime DI now registers the repositories, application service, native menus and discovery listener. Additive tables will be created when this source is activated; no production/TEST instance was changed.
- Local full `check --offline`: 1,806 cases, zero failures/errors, 16 existing optional skips. Native MariaDB 11.8.3 on disposable loopback port 33484: 13 recruitment persistence/eligibility cases, zero failures/errors/skips. The process was shut down after proof. These counts are local proof, not hosted CI or player acceptance.
- Offer integration tests cover closed-guild paid admission, eligibility revocation before membership, quote/currency changes, repeated confirmation, uncertain admission and tentative membership events. Private input tests cover disconnect cleanup and stale timeout protection. Locale and architecture contracts run with the full suite.
- Invite-block compatibility reads the reviewed PR #232 `player_invite_blocks` table only when present. Native contracts prove directionality, absent-table compatibility and malformed-row fail-closed behavior. This does not merge or replace that separate PR; the combined reviewed deployment still needs acceptance.
- Public integration constructors remain compatible; recruitment-only types are internal. Private artwork stays outside the public repository. The interactive preview uses sample data and simulated actions; Java/Bedrock resource-pack and player journeys remain acceptance gates.
- Hosted build and Wiki checks passed on implementation commit `3b4259ef`. CodeRabbit skipped the draft. Codacy's public issue list reported additional style/complexity findings while its GitHub check was still in progress. Admission complexity, public data-class compatibility and boundary exception handling were reviewed; follow-up refinements and 120-column formatting are separate from a confirmed clean hosted gate. The PR remains draft until the final head's review/checks and acceptance boundaries are resolved.

## Operator discovery integration

Use `guild recruitment` as a spawn NPC's player-executed command (do not execute as console or impersonate another player). Use `/g recruitment` in a hologram's command click action, where supported. Static text can say “Find a Guild — browse guilds and apply.” No new world placements or third-party plugin configuration are changed by this PR. All three aliases `/g lfg`, `/g browse` and `/g recruitment` use the same existing `lumaguilds.guild.lfg` permission. Existing `/g list` stays available as the separate full directory.

Leaders grant Manage Recruitment to application reviewers and Edit Recruitment Settings to listing editors through the existing rank editor. Existing open flags remain the fallback until an explicit recruitment profile is saved. Joining fees remain the canonical configured fees; applying, reviewing and opening Discord charge nothing. Confirmed payment failures that cannot prove a refund remain pending for staff investigation; never instruct a player to pay again solely because membership is absent.

Player advertisements under a Looking For Guild tab remain a future option: current LFG is guild discovery, not an existing player-advertisement service. No unsolicited public posting, Discord bot automation, promoted listings or fee policy changes are included.
