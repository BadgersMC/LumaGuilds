# Chapter 2 Progression Source Audit — 2026-10-05

## Scope

Audited every enabled Chapter 2 XP source from producer/event classification through the permanent XP award service and source-usage read model. The audit was prompted by reports that Elder Guardian kills and advancements did not move progression bars.

## Source producer matrix

| Source / pool | Producer | Classification / qualification | Status |
| --- | --- | --- | --- |
| `BANK_DEPOSIT` | `BankServiceBukkit` → `ChapterTwoGuildAwardService` | Net-new guild gold, 100 gold per unit | Wired; separate live award investigation remains open |
| `QUALIFIED_RECRUIT` | `QualifiedRecruitScheduler` | Retention-qualified membership stint | Wired |
| `PRE_CAP_WAR_WIN` | `WarServiceBukkit` | Qualifying pre-cap war victory | Wired |
| `PLAYER_KILL` | `PlayerDeathEvent` | Enemy/non-guildmate player kill | Wired |
| `MOB_KILL` | `EntityDeathEvent` | Entity must be in configured `normal_mobs` | Wired, intentionally target-limited |
| `CROP_BREAK` | harvest / block break events | Mature natural crop only | Wired |
| `BLOCK_BREAK` | `BlockBreakEvent` | Configured natural common block; player-placed provenance excluded | Wired |
| `BLOCK_PLACE` | `BlockPlaceEvent` | Configured common-place target | Wired |
| `SMELTING` | `FurnaceExtractEvent` | Extracted item count | Wired |
| `BREWING` | brewing inventory take | Finished potion, one-time item marker | Wired |
| `FISHING` | `PlayerFishEvent` | `CAUGHT_FISH` | Wired |
| `ENCHANTING` | `EnchantItemEvent` | Successful enchant event | Wired |
| `EXPLORATION_MILESTONE` | `PlayerAdvancementDoneEvent` + `GuildExplorationMilestoneEvent` | Vanilla `minecraft:adventure/*` or explicit external completion | Wired; EnthusiaAdvancements publishes newly-completed custom nodes through the public event |
| `ORE` tier sources | `BlockBreakEvent` | Natural supported ore/deepslate ore; player-placed provenance excluded | Wired |
| `CRAFTING` tier sources | Paper `ItemCraftedEvent` | Configured common/utility/equipment/rare targets | Wired |
| `ENDER_DRAGON_KILL` | `EntityDeathEvent` | Ender Dragon | Wired |
| `WITHER_KILL` | `EntityDeathEvent` | Wither | Wired |
| `ELDER_GUARDIAN_KILL` | `EntityDeathEvent` | Elder Guardian | Wired; listener acceptance test added |
| `WARDEN_KILL` | `EntityDeathEvent` | Warden | Wired |
| `WEEKLY_ACTIVITY` | `QuestRewardSinkBukkit` | Trusted quest reward | Wired, uncapped |
| `ADMIN_BONUS` | `/lumaguilds xp give` | Admin-only manual grant | Wired, uncapped |

## Eligibility and cap behavior

All actor-attributed activity flows through `PermanentExperienceService`. Creative/spectator events are rejected before award, and EnthusiaPlaytime `AFK` or `SUSPICIOUS` actors receive zero XP and consume zero cap as required by REQ-089. Capped source usage is persisted atomically by pool and period.

Daily and weekly cap windows are UTC. UI text must therefore describe the configured period rather than imply the player's local calendar day/week.

## Confirmed display/read-model defects

The Java progression menu previously hid weekly-source XP from its header, labeled every capped source as `Today`, and rendered a level-100 guild as a 0% / empty level bar because XP-to-next is zero at the permanent cap. Unlimited sources also displayed a fabricated `Tracked: 0 XP` because uncapped sources do not have cap-usage rows.

The audit patch makes the header period-aware, labels source cards by daily/weekly period, renders permanent level cap explicitly, and describes uncapped sources as unlimited without inventing a tracked value.

## Advancement integration

`EXPLORATION_MILESTONE` deliberately accepts only Bukkit `PlayerAdvancementDoneEvent` keys under `minecraft:adventure/*`. Other vanilla tabs are not progression milestones.

EnthusiaAdvancements projects UltimateAdvancementAPI nodes directly, so they do not emit Bukkit `PlayerAdvancementDoneEvent`. The integration now uses the explicit public `GuildExplorationMilestoneEvent`: EnthusiaAdvancements publishes it only when a custom node crosses from incomplete to complete, while LumaGuilds retains guild membership, game-mode, AFK/suspicious and cap enforcement.

## Generic mob scope

The shipped `normal_mobs` target set is currently `ZOMBIE`, `SKELETON`, `CREEPER`, `SPIDER`, and `ENDERMAN`. Other ordinary mobs do not award generic `MOB_KILL` XP unless added by configuration. Bosses remain separately classified regardless of that target set.

## Regression coverage added

- Listener acceptance: Elder Guardian death reaches `ELDER_GUARDIAN_KILL` with one unit.
- Listener acceptance: vanilla Adventure advancement reaches `EXPLORATION_MILESTONE`.
- Listener acceptance: non-Adventure vanilla advancement is not treated as exploration.
- Source-policy assertions cover bank, brewing, exploration and all four weekly bosses.
- Enabled-source coverage contract fails if a future enabled Chapter 2 source is added without a declared producer path.
- External integration acceptance: `GuildExplorationMilestoneEvent` reaches the same `EXPLORATION_MILESTONE` award path.
