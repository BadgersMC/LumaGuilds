# Guild Getting Started

## Spec

REQ-145: WHEN a player opens Getting Started via `/g start` or their Java/Bedrock guild menu THE SYSTEM SHALL explain creation/joining, invites, ranks, chat, home activation, quests, progression, bank and stalls, and SHALL use current membership, authority and guild state for every action. Membership loss or a removed selected guild SHALL deny access. A player without a guild SHALL receive creation guidance and directory navigation. Existing prices, rewards and permissions SHALL remain unchanged.

REQ-146: WHEN a guild is created or a new membership is added THE SYSTEM SHALL register one pending prompt per player and guild, SHALL deliver a dismissible chat prompt only while that player is online and still a member, and SHALL persist consumption atomically. Offline members SHALL receive their pending prompt after reconnecting. Existing memberships SHALL NOT receive retroactive prompts. Duplicate events, reconnects, menu closures and plugin restarts SHALL NOT reset consumed prompts. Persistence failure SHALL NOT claim a successful delivery. Manual access SHALL remain available.

Guild milestones use current member count, active homes and current level. Reading help is not counted as completing setup. Ranks/chat/quests/bank/stall entries are guidance actions, not invented task-completion metrics. Home unlock and activation are separate; existing menus supply authoritative prices and purchase confirmation. A locked home entry explains requirements and opens progression only for permitted members. No new purchase, teleport, XP or payment mutation is performed by the guide.

## SPEAR record

Current canonical main a15b244 fetched; isolated branch incorporates reviewed Guilds #216 a2bffa1 to preserve existing approved menu, permission and integration fixes. New contracts establish expected behavior; no historical red/green claim. Project-local EARS/state helpers are absent; this manual requirement/task/evidence record applies.

- [x] Spec and source/integration inspection.
- [x] Permission, stale-state, milestone and durable prompt regression proof.
- [x] Java/Bedrock implementation, focused/full build and architecture checks.
- [x] Interactive mobile preview, save/access verification and responsive browser inspection.
- [ ] Exact-head hosted check and review inspection; independent maintainer review remains required.
- [ ] Maintainer merge, canonical network release and separately authorized player acceptance.

Production unchanged; in-game testing deferred.

## Prove and refine

Clean Java 25 `clean test shadowJar --offline` completes 1,741 cases with zero failures/errors and 15 existing environment skips. All 27 new onboarding cases execute without skips: current authority/milestones, stale Java navigation, actual inventory/lore rendering, real scheduled Cumulus responses, offline/duplicate/failed invitation delivery and durable SQL state. Architecture and locale contracts also execute. The existing Market runtime contract executes against the reviewed combined Market artifact rather than reusing a skipped result.

The four SQL cases execute separately against disposable loopback MariaDB 11.8.3, port 33418, including concurrent claim, restart/duplicate registration, idempotent dismissal and injected write failure. SQLite executes the same cases in the ordinary suite. The disposable helper is shut down after validation. No production database is accessed.

The existing developer-oriented `getting-started.md` is preserved; this document records the new player-facing guide. Platform adapters remain in infrastructure/interaction, with read policy and a persistence port in application. The guide reuses existing menus and does not change purchase, teleport, bank or stall authority.

Interactive preview: https://chatgpt.com/space/page_1958f5469f048191b1c0d0c24a2b06f8 (private, same-account access verified). Browser interactions pass at 320, 390 and 736 pixels with no horizontal overflow or page errors. The preview represents the three-row Java layout, native Bedrock content/buttons, leader/member/newcomer states, home unlock routing and dismissal. Images/background are schematic and actions are simulated; actual phone and in-game acceptance are not claimed. Editable fragment and a inspected mobile screenshot are in `docs/previews/`.

For local preview inspection, use the installed visualize renderer to wrap `docs/previews/guild-getting-started.html` as `docs/previews/guild-getting-started-local.html`, then run `node docs/previews/check-getting-started.cjs` with Playwright and Chrome installed. `PLAYWRIGHT_MODULE` may select an existing Playwright installation. The generated wrapper is a local review aid, not a production artifact.
