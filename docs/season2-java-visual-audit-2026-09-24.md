# Season 2 Java Visual Audit — 2026-09-24

Status: **follow-up required**. This records the first human Java-client walkthrough after the second-pass Season 2 UI/resource deployment to local staging.

## Test context

- Environment: local staging only; no production changes.
- Client: Java Edition.
- Data: imported/old guild database, so persistence anomalies must be reproduced against a copy of current live data before being classified as migration artifacts or current-code defects.
- The walkthrough temporarily used guild override/join flows, including Vibe (23 members, rank-1 guild context) and Test.
- This document records observed behavior and product direction; it does not claim Bedrock validation.

## Dashboard / home

Observed on Vibe:
- Dashboard summary showed 23 members, rank 4 and guild balance 0 while using the guild's custom banner.
- Economy/Bank showed a guild balance of 850 for the same guild.
- Therefore the dashboard balance is inconsistent with the destination menu and must use the same authoritative balance source.
- Product direction: keep the personal guild summary useful at a glance, but consider replacing low-value/redundant fields with more meaningful guild information rather than simply adding more text.

## Guild Information

- The Information item appeared inert while temporarily overridden into Vibe.
- After joining Test, Information opened normally and exposed members, banner, allies, truces, wars, statistics, progression and guild-overview data.
- Because behavior changed with membership/override context, reproduce authorization/override handling before labeling this solely a GUI bug.

## Disband / stale guild data

- Test was still joinable even though it was believed to have been disbanded previously.
- Test displayed an unknown owner. The likely interpretation during the walkthrough was that the unknown UUID was the former owner, but this is not yet proven.
- A member represented as Unknown Player could be kicked successfully.
- Required follow-up: verify successful disband cleanup, orphaned owner/member rows, and migration handling. Add a pre-cutover report instead of silently carrying stale guild records into Chapter 2.

## Members and ranks

- Members menu looked good in the walkthrough.
- Rank browsing worked, although the walkthrough account did not have rank-management permission in that guild.
- Multiple rank/perk entries expose missing localization keys.
- Existing perk/item descriptions are legacy text, too verbose, visually dense and sometimes taller than practical screen space.
- Direction: preserve the information and behavior but rewrite hover lore into concise, scannable descriptions and cover every visible rank/perk key with localization tests.

## Weekly quests

Core quest behavior appeared to be functioning, but generated text and condition selection need a player-facing pass.

Observed examples:
- `Fish Any` with `100 any above Y96` reads like generator output. Preferred intent: `Catch any fish` (or equivalent natural wording).
- `Enchant Items Stone Spear` with `90 stone spear within 50 blocks of Z0` likely means enchant 90 Stone Spears, but the text is calculator-like and the Z=0 corridor condition is not meaningful for enchanting.
- `Deposit Bank Coins` must use Enthusia's real guild-bank currency wording: deposit Gold Ore to the Guild Bank, with the amount expressed as Gold Ore rather than generic coins.

Quest-generation direction:
- Coordinate/highway conditions are useful for activities where place matters (for example block placement, mob/player combat and other world activity), but should be filtered by action semantics.
- Expand the shared weekly set from three quests to **six**.
- Rework menu placement so all six read as a deliberate grid rather than three sparsely separated cards.

## Guild Bank

The bank should **keep its depth and information**, not be gutted. The problem is discoverability, unfinished controls and presentation.

Observed issues:
- Transaction History reported no transactions, but clicking deeper exposed statistics/filter controls with missing language keys.
- Several history/filter controls appeared inert, making it unclear whether the feature is empty or unwired.
- Automation exposes scheduled deposits, auto rewards, alerts, recurring payments, active automation state and interest/accrual information.
- Several configuration buttons still say `coming soon` or do not function.
- Member Contributions and Bank Statistics are desired features and should remain, but need verified real data and working navigation.
- The next-interest/accrual timer is useful and should tick live from the real persisted schedule.
- Current automation/status text is information-dense enough to overwhelm players.

Direction:
- Keep the information and intended functionality.
- Add purpose-specific Season 2 icons.
- Simplify grouping, navigation and labels.
- Complete localization.
- Every interactive-looking control must work; otherwise render it as explicit read-only status until functionality exists.

## Settings

- Settings presentation looked acceptable overall.
- Change requested: Lunar tracking should initialize **disabled by default**.

## Progression

- Progression received strong visual approval in the walkthrough: the menu was described as clear, intuitive and easy to understand from icons without hovering every item.
- Keep the current presentation direction.
- Prestige currently says it requires level 25. Correct requirement is **level 100**.
- Prestige also says `coming in a future update`; Chapter 2 release must expose the real production-ready prestige flow rather than future-update copy.
- Existing operator enable/disable policy remains governed by REQ-093 unless separately changed.

## Diplomacy

- Diplomacy presentation looked good.
- No redesign requested from this walkthrough.

## Warfare / party

- The Warfare/Party presentation looked good.
- Chapter 2 must not inherit residual legacy war state. Add an explicit audited admin cleanup operation for active wars, incoming declarations, outgoing declarations and peace-agreement state.
- Vibe appeared to have roughly 12 active/long-running wars in the imported data while Quick Stats reported zero.
- This mismatch may involve stale imported data, current statistics wiring, or both; inspect a copy of the live database before release.

## Statistics

The Statistics menu needs a full Season 2 functional/presentation pass.

Observed:
- It still largely uses vanilla Minecraft icons and appears close to the legacy presentation.
- Remove the obsolete `Export Statistics` CSV button; the old export path is no longer supported.
- Kill Trends and Periodic Statistics expose missing localization.
- Rivalry Statistics and Guild Achievements appeared empty for the tested guild.
- Top Killers did not appear to return data.
- Top Contributors appeared inert.
- K/D Analysis and other legacy drill-downs are not trusted until verified.

Direction:
- Replace vanilla placeholders with intentional custom icons.
- Fix all localization.
- Validate every retained statistic against persisted data.
- Distinguish a legitimate empty result from an unwired feature.
- Hide/remove dead views rather than ship buttons that do nothing.

## Chapter 2 live-data readiness gate

Before production migration, run the readiness process against a **copy** of the current live database and report:
- orphaned/disbanded guild rows and missing/unknown owners;
- residual active wars, declarations and peace state;
- guild dashboard/economy balance consistency against canonical guild gold;
- representative warfare statistics and general Statistics-menu source data;
- cleanup actions required before migration and the before/after result.

Do not use the old staging snapshot as proof that live data is healthy.

## Release status after this walkthrough

- `LG-S2-UI` remains **partial**.
- A second Java-client walkthrough is required after these follow-ups are implemented.
- Bedrock remains independently unverified in the current staging runtime.
