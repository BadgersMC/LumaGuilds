# Season 2 Bedrock parity audit — 2026-09-25

Status: SOURCE/AUTOMATED REMEDIATION COMPLETE; REAL BEDROCK CLIENT ACCEPTANCE PENDING.

Scope: this document is the original 2026-09-25 static source audit comparing the Java guild-menu surface against the dedicated Floodgate/Cumulus Bedrock surface. The detailed matrix/findings below are intentionally retained as the historical RED baseline and therefore describe the pre-remediation state, not the current checkout.

Remediation update — 2026-09-26: LG-1801 through LG-1811 are implemented and closed in `docs/tasks.md`. Dedicated parity contracts now cover authorization/navigation, quests, progression/Prestige, Settings, truthful Statistics, Bank safety/drill-downs, Homes/Members/Ranks, Party/LFG, Warfare/Diplomacy, and localization/truthfulness. Final automated validation passes 1,393 tests with 0 failures / 0 errors / 3 skipped, and local-only staging boots the exact validated JAR with Geyser/Floodgate/bundled Cumulus/ViaVersion. LG-1812 remains open only for the required real Bedrock-client walkthrough and independent Java/Bedrock sign-off; static/source parity alone does not close the release gate.

## Severity

- P0 — Chapter 2 feature unavailable, misleading, or authorization/economic safety differs from Java.
- P1 — substantial functional or navigation parity gap that should be fixed before release.
- P2 — UX/coverage drift that can follow the critical parity work but should be resolved before final Bedrock sign-off.
- OK / verify — implementation is materially present and uses current shared services, but still needs client validation.

## Executive summary

Bedrock is not merely unverified. It has a substantial independent menu implementation, but it has drifted across several generations of LumaGuilds.

Strong areas exist: guild listing, most relations/war management, paid guild-home activation, Chapter 2 reward purchasing, invitation statistics, canonical bank transactions, and several persisted bank settings already use shared current services.

Release blockers:

1. Weekly Guild Quests are explicitly unavailable on Bedrock.
2. Prestige has no Bedrock UI.
3. Bedrock Statistics still renders fabricated/stale values and lacks nearly the entire current Java statistics surface.
4. Bedrock Settings is missing current guild-state controls such as open/closed access and Lunar tracking; GUI theme management is absent.
5. Bedrock Bank has real transaction logic, but its deeper menus are effectively orphaned from the Bedrock main bank, and the reachable auto-deposit setting can be changed without the Java-side bank-settings authorization guard.
6. Several Bedrock confirmation flows jump directly into Java inventory menus.
7. Home-access and ally-home access management are explicitly replaced with an unavailable form.
8. LFG browser routing remains Java-only even though a Bedrock join-requirements form exists.
9. Member/rank/party flows have incomplete or divergent behavior, including a rank-edit priority hazard and an explicitly unavailable Bedrock party-permissions path.
10. Current tests have only narrow Bedrock-specific coverage; green Java/full-suite results are not evidence of broad Bedrock form parity.

## Parity matrix

| Domain | Java Season 2 baseline | Current Bedrock state | Severity |
| --- | --- | --- | --- |
| Main dashboard | 10 primary sections plus authoritative guild summary | Flat control-panel list; all major sections except Quests, plus legacy top-level actions; no equivalent summary | P1 |
| Weekly quests | Six shared quests, progress, claim state, rewards, leaderboard/time state | MenuFactory returns BedrockAccessUnavailableMenu | P0 |
| Prestige | Level-100 overview, eligibility, retained-perk selection, quote, confirmation, retry-safe result | No Prestige reference in Bedrock menu package | P0 |
| Progression | Current run, source-cap grid, reward catalog, Prestige, rank/Season 2 presentation | Chapter 2 reward catalog exists and purchases work; no Prestige; legacy PerkType fallback remains | P1 |
| Settings | description/tag/banner/emoji/theme/homes/open state/Lunar tracking/members/mode | Settings form edits name/description/mode only; some appearance/home actions live elsewhere; open state, Lunar tracking and theme absent | P0/P1 |
| Guild Bank main | canonical balance, transactions, fee-aware actions, history/stats/automation/contributions hierarchy | real deposit/withdraw and persisted auto-deposit toggle, but no navigation to deeper Bedrock bank forms | P1 |
| Bank settings authorization | permission-gated management | reachable auto-deposit change lacks MANAGE_BANK_SETTINGS guard; automation/budget/security forms themselves have no authorization dependency | P0 |
| Bank history | real history with search/type/member/date filters and paging | last 15 transactions only; no filters/search/paging | P1 |
| Bank statistics/contributions | richer analytics and data-backed drill-downs | aggregate stats and top-10 contributions exist, but are shallow and currently unreachable from main Bedrock bank | P1 |
| Statistics | kills/wars/members/performance plus supported detail views and explicit empty/unavailable states | only overview/activity/invitations/economy/territory; contains fabricated XP/activity/territory values | P0 |
| Guild info | richer guild overview and linked current systems | real members/relations/war kill progress; materially present but narrower | OK / verify |
| Guild list | bounded paging and four sort modes | same service-bound paging/sorts are present | OK / verify |
| LFG | Java LFG browser plus join requirements | Bedrock join-requirements exists, but browser/return path is Java-only | P1 |
| Members | management, per-member flows, moderation/penalties | invite/kick/promote/demote present; member-list detailed action path still TODO; no equivalent strike/player-moderation flow found | P1 |
| Ranks | full current rank state/permission management and rank presentation | overlapping editors; one is broad, one simplified; rank-list detail path incomplete | P1 |
| Homes | paid activation, teleport/remove, access controls | paid activation and use checks are current; per-rank home access and ally-home access explicitly unavailable; new homes auto-name homeN | P1 |
| Diplomacy | requests/allies/enemies/truce/enemy flows | substantial dedicated Bedrock implementation | OK / verify |
| Warfare | durable declarations, active/history/stats, wagers/objectives/peace | substantial current WarService usage; declaration objective surface is narrower than Java | P1 / verify |
| Parties | current create/request/moderation/settings flows | large Bedrock implementation, but party permissions explicitly report unavailable | P1 |
| Localization | all visible player text localized | mostly localized; at least one hardcoded player-facing party-permissions unavailable message remains | P1 |
| Navigation | platform-specific forms remain within platform flow | several Bedrock confirmation classes directly construct Java guild menus | P1 |
| Tests | Java Season 2 contracts plus full suite | only a handful of dedicated Bedrock tests plus wiring tests; broad form/authorization/parity behavior is unguarded | P0 release-process gap |

## Detailed findings

### BPAR-001 — Weekly Guild Quests are intentionally disabled on Bedrock — P0

MenuFactory createGuildQuestsMenu checks for Bedrock and returns BedrockAccessUnavailableMenu for weekly guild quests. There is no BedrockGuildQuestsMenu.

Bedrock players therefore cannot inspect the six shared quests, understand generated objective text, track guild progress, claim milestone rewards, inspect full-set bonus state, or see reset/leaderboard state.

Required fix: dedicated Cumulus quest browser using the same quest read/claim services as Java. Do not duplicate quest generation or reward logic in the form layer.

### BPAR-002 — Prestige is Java-only — P0

Java GuildProgressionMenu uses GuildPrestigeService to expose overview, max-count/fee state, retained-perk selection, quote creation and confirmation through GuildPrestigeConfirmationMenu.

The Bedrock menu package contains no Prestige reference and no confirmation form.

Required fix: Bedrock Prestige overview plus eligible retained-perk selection plus modal confirmation backed by GuildPrestigeService. Show prestige count / 6, next fee, disabled/unavailable/max states and the same rejection semantics as Java.

### BPAR-003 — Bedrock progression mixes Chapter 2 and legacy models — P1

BedrockGuildProgressionInfoMenu immediately returns the Chapter 2 reward catalog whenever GuildRewardRead.Available is returned. That catalog does use current reward entitlements and GuildRewardPurchaseService.

Gaps:
- no Prestige integration;
- alternative form still contains legacy PerkType presentation;
- source usage is a long text block from pool names rather than the intentional Java Season 2 presentation;
- no equivalent rank/season/Prestige state;
- no segmentation strategy if source count expands.

Required fix: treat Bedrock progression as a current Chapter 2 surface, not a compatibility wrapper around two generations of progression UI.

### BPAR-004 — Current Settings state is inaccessible from Bedrock — P0/P1

Java GuildSettingsMenu exposes GUI Theme, open/closed guild access, Lunar tracking, homes/members/mode and appearance/identity controls.

BedrockGuildSettingsMenu currently handles name, description and mode. Tag/banner/emoji/homes are reachable elsewhere from the flat control panel, but open/closed access, Lunar tracking and GUI theme are not represented.

Required fix: add current-state controls with the same permission checks and refresh semantics as Java.

### BPAR-005 — Bedrock Statistics contains fabricated values — P0

BedrockGuildStatisticsMenu only depends on MemberService, BankService and InvitationStatisticsService. It does not consume the Java statistics dependencies for kills, wars and leaderboards.

Concrete misleading values:
- experience is hardcoded to 0/800;
- activity status is always Active and last activity is derived from the latest member join time;
- territory uses claims = 0, area = 0, power = 1.

Real invitation pagination and bank aggregates are mixed with those fabricated values.

It also lacks supported Java surfaces for kill stats, war stats, performance, top killers, top contributors, K/D, recent activity, periodic statistics, rivalry, achievements and guild comparison.

Required fix: remove every fabricated field first. Rebuild the Bedrock statistics hierarchy on the same services/data states as Java and use explicit no-data/unavailable states.

### BPAR-006 — Bedrock Bank submenus exist but are not connected — P1

Dedicated classes exist for Statistics, Member Contributions, Transaction History, Security, Automation and Budget. MenuFactory can construct all of them.

BedrockGuildBankMenu contains no navigation to any of them. References to those factory methods come from the Java bank menus, while the Bedrock main form only handles deposit, withdrawal and auto-deposit.

Required fix: create a Bedrock bank navigation shell or a second-level form exposing authorized History, Statistics, Contributions, Automation, Budget and Security.

### BPAR-007 — Bedrock bank-management authorization is weaker than Java — P0

The reachable BedrockGuildBankMenu validates canDeposit and canWithdraw for money movement. The same form can change scheduledDepositsEnabled through saveAutoDeposit without an equivalent bank-settings permission check.

Standalone automation, budget and security Bedrock forms inject repositories/editors and localization but no guild/rank authorization service.

Required fix: one shared bank-management authorization check before rendering mutable controls and again before persistence. Add negative tests for ordinary members.

### BPAR-008 — Bedrock bank drill-downs are functionally behind Java — P1

Even after navigation is added:
- history shows only the most recent 15 records and has no search/type/member/date filters or paging;
- bank statistics exposes only a simple aggregate;
- contributions truncate to top 10;
- automation lacks the Java next-run/countdown/status presentation and coherent hierarchy.

Required fix: consume the same current services and states as Java rather than preserving simplified legacy forms.

### BPAR-009 — Bedrock forms leak back into Java inventory menus — P1

Examples found:
- BedrockGuildInviteConfirmationMenu opens Java GuildMemberManagementMenu;
- BedrockGuildKickConfirmationMenu opens Java GuildMemberManagementMenu / GuildKickMenu;
- BedrockGuildMemberRankConfirmationMenu opens Java GuildMemberManagementMenu / GuildMemberRankMenu.

There are also reflective Java fallbacks in old Bedrock code.

Required fix: all Bedrock success/cancel/back paths must use MenuFactory/Bedrock forms, never direct Java menu construction.

### BPAR-010 — Home access controls are explicitly unavailable — P1

MenuFactory replaces both Java access surfaces with BedrockAccessUnavailableMenu:
- per-rank home whitelist;
- inbound ally-home access whitelist.

The core Bedrock home menu otherwise uses current paid activation and permission logic.

Required fix: implement Cumulus forms for both access-control surfaces.

### BPAR-011 — LFG navigation is split across platforms — P1

A dedicated BedrockJoinRequirementsMenu exists and uses the current LfgService/payment state.

MenuFactory createLfgBrowserMenu is unconditionally Java, and the Bedrock join-requirements return path points back to the Java browser.

Required fix: add a Bedrock LFG browser and keep the complete LFG journey inside forms.

### BPAR-012 — Member-management detail/moderation parity is incomplete — P1

The basic Bedrock management form has invite/kick/promote/demote.

BedrockGuildMemberListMenu still has a TODO for detailed member selection/actions. No equivalent Bedrock strike/player-moderation surface was found, and several confirmation paths jump to Java menus.

Required fix: inventory every Java member action and expose the same authorized action set through a selected-member Bedrock form.

### BPAR-013 — Rank management has overlapping implementations and a priority hazard — P1

There are two different Bedrock rank-edit approaches:
1. BedrockGuildRankManagementMenu dynamically exposes the full RankPermission enum.
2. BedrockRankCreationMenu / BedrockRankEditMenu expose a much smaller fixed set of toggles.

Available permissions therefore depend on the route used to open the editor.

BedrockGuildRankManagementMenu also computes a new priority from the maximum existing priority and its edit path writes that computed priority, risking rank-order changes when merely editing a rank.

BedrockGuildRankListMenu still has a compatibility path that shows details in chat and goes back.

Required fix: converge to one rank editor, preserve rank identity/priority unless deliberately reordered, and expose the same prefix/permission semantics as Java.

### BPAR-014 — Party permissions are explicitly unavailable and hardcoded — P1

BedrockGuildPartyManagementMenu openPartyPermissionsMenu sends a hardcoded player message stating that party permissions configuration is not available in Bedrock.

Required fix: implement the current settings flow or hide the control until implemented; no hardcoded player-facing fallback.

### BPAR-015 — Warfare is one of the stronger Bedrock areas, but not fully equivalent — P1 / verify

The Bedrock war implementation uses current WarService APIs for active wars, declarations, accept/reject/cancel, wagers, history/statistics, kill progress and peace proposals.

Remaining static difference: Bedrock declaration exposes claims/territory and kill objective toggles, while Java has a broader separate objective-selection surface including time-survival configuration.

Required fix: compare all supported ObjectiveType/configuration options and fill only missing controls. Keep war state logic shared.

### BPAR-016 — Diplomacy and guild list are comparatively healthy — OK / verify

BedrockGuildRelationsMenu is a substantial dedicated implementation with request/alliance/truce/enemy flows. Guild Info uses a dedicated Bedrock relation browser.

BedrockGuildListMenu uses the bounded GuildListService getPage contract and exposes the four current sort keys with paging.

These need real-client verification, not first-wave rewrites.

### BPAR-017 — Bedrock Guild Info uses real relation/war data but is narrower — OK / verify

The form consumes current GuildService, MemberService, RelationService and WarService, including active-war kill progress and expandable ally/enemy browsing.

No equivalent fabricated values were found in the inspected sections.

### BPAR-018 — Bedrock Home core economics are current — OK with missing access UI

BedrockGuildHomeMenu uses current available home slots, canUseHome, GuildCostService.activateHome and explicit applied/rejected/configuration/payment-review outcomes.

Main gaps are access-management parity and UX; new homes are automatically named homeN.

### BPAR-019 — Navigation/state helper is only partially implemented — P1

BedrockMenuNavigator contains explicit compatibility comments for forward/state and step navigation; state-preservation integration is not actually implemented in those helper paths.

Required fix: centralize form navigation/back-stack/state behavior before adding more multi-step flows such as Prestige and detailed Statistics.

### BPAR-020 — Bedrock test coverage is too narrow for release parity — P0 release-process gap

Dedicated Bedrock tests found are primarily:
- bank settings editor;
- claim permission editor;
- edit-tool controller;
- localization;
- MenuFactory join requirements.

Additional wiring tests touch descriptions, relation browsers, invitation statistics, guild list and reward purchasing.

There are no broad behavior contracts for the Bedrock control panel, current Settings parity, Statistics truthfulness, bank navigation/authorization, Quests, Prestige, home access, member moderation or broad war-form parity.

Required fix: add platform-parity contract tests before implementing the missing forms.

## Existing Bedrock strengths to preserve

Do not rewrite working shared behavior merely to make forms look like Java.

- Chapter 2 reward purchase confirmation delegates to GuildRewardPurchaseService.
- Guild-home activation delegates to GuildCostService.
- Main bank transactions delegate to BankService, including ambiguous-withdrawal handling.
- Guild list delegates to bounded GuildListService.
- Relations and war forms already use current services extensively.
- Invitation statistics are real and paginated.
- Bedrock descriptions preserve visible Discord URLs as plain text.
- Toast delivery is service-level rather than menu-level and should remain shared.

## Recommended implementation order

### Phase A — parity contracts and navigation safety

1. Add a Bedrock parity contract test covering required Dashboard destinations.
2. Eliminate direct Bedrock to Java menu construction.
3. Add shared authorization helpers for mutable bank/settings/rank actions.
4. Finish BedrockMenuNavigator state/back-stack behavior needed by multi-step forms.
5. Add tests proving ordinary members cannot mutate management settings.

### Phase B — Chapter 2 blockers

1. BedrockGuildQuestsMenu.
2. Bedrock Prestige selection and confirmation.
3. Current Settings controls: open/closed, Lunar tracking, GUI theme as appropriate.
4. Rebuild Progression around the current Chapter 2 model.

### Phase C — truthful data surfaces

1. Delete fabricated Bedrock Statistics values.
2. Add kill/war/member/performance overview from the same services as Java.
3. Add supported detail views with explicit empty/unavailable states.
4. Connect Bank History / Statistics / Contributions / Automation / Budget / Security into a coherent Bedrock hierarchy.
5. Add history filters/paging and bank live-state presentation where supported.

### Phase D — management parity

1. HomeAccess and AllyHomeAccess forms.
2. Selected-member management and moderation/penalties.
3. Converge rank creation/edit management and preserve priority.
4. Party permissions/settings.
5. Bedrock LFG browser.

### Phase E — war/diplomacy parity polish

1. Objective-type/configuration parity.
2. Declaration/acceptance detail parity.
3. Verify relations requests, peace, wager and war-history flows against representative persisted data.

### Phase F — runtime acceptance

Provision local staging with compatible Geyser plus Floodgate/Cumulus support and run a real Bedrock client matrix:
- Dashboard navigation;
- Quests claim flow;
- reward purchase plus Prestige;
- Settings mutations and denials;
- Bank physical and Vault-backed paths where providers are available;
- home activation/access;
- member/rank management;
- relations/war declaration/accept/reject/peace;
- Statistics real/no-data states;
- LFG browser/join/return flow;
- form close/back/timeout/reconnect behavior.

Java acceptance and Bedrock acceptance must remain reported independently.

## Release conclusion

The Bedrock implementation is not a total rewrite, but it is not Chapter 2 release-equivalent today.

The fastest safe path is to keep shared services and replace stale form-layer seams. The largest missing features are Quests, Prestige, current Settings and truthful Statistics. The most important safety fix before exposing more existing Bedrock forms is bank/settings authorization and removal of direct Java-menu fallbacks.
