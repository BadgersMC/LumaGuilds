# Guild Prestige System — Design (LG-1208)

> Status: **theorized / agreed** (Aug 12) — feeds REQ-056 contract.
> Prestige is the endgame loop on top of the 0–200 level rebalance (LG-1201/LG-1202).
> It is also an **economy sink**: established guilds are rich, and prestige makes them
> spend gold at scale, helping balance inflation.

---

## 1. Core loop

A guild that reaches **level 200** (the cap, once LG-1201/LG-1202 make it real) may
**prestige**: its level resets to 1, XP to 0, and it gains one permanent unlock from
the tier table below. Prestige is repeatable — each cycle increments the prestige
tier (P1, P2, P3, …).

> ⚠️ Baseline: today `ProgressionConfig.maxLevel` (default 30) is **never enforced at
> runtime**, so levels are unbounded. Prestige *requires* the enforced cap to exist
> first. The 100→200 curve is the priority; prestige rides on top of it.

## 2. What resets vs. what survives

| Resets on prestige | Survives prestige |
|---|---|
| Level → 1 | Bank balance & vault contents |
| XP → 0 | Claims & territories |
| Level-gated *capacity* (homes beyond base, member cap extras, etc.) | Members & relations (allies, enemies, truces) |
|  | Gold (in bank/vault) |
|  | **Activated** homes (REQ-054 rule: paid features are never confiscated) |
|  | **All previous prestige unlocks** (permanent by definition) |

## 3. Tier table — one significant unlock per tier

| Tier | Permanent unlock | Cosmetic |
|---|---|---|
| **P1** | **+1 home slot, effective at ALL levels** (never level-gated, never reset — applies even at level 1 after reset) | Prestige emoji + title |
| **P2** | **+1 alliance slot** (cap raised beyond level-gated max) | Title accent |
| **P3** | **+10% permanent XP** (stacks with fast-track; re-levels get faster) | Banner accent |
| **P4** | **+1 war slot** (more concurrent wars) | Title upgrade |
| **P5** | **+5 member capacity** | Banner accent |
| **P6+** | Cosmetic-only — unique titles/banner colors per tier | Escalating cosmetics |

Power is hard-bounded at P5; P6+ is pure flex.

**P1 home slot applies to all levels** (operator decision): unlike level-gated homes
(125/150/175/200), the prestige home slot is usable at every level — it is a
permanent asset, not a level entitlement. Activation of *any* home still costs gold
(REQ-054).

## 4. Double requirement: level AND gold (operator decision)

Every prestige tier requires **both**:

1. **Level requirement** — reaching cap 200 is a *sub-requirement*: it gates access
   to the prestige action. Level alone unlocks nothing by itself.
2. **Gold cost** — the actual unlock is **paid for in raw gold**, escalating per tier.

> Rationale: established guilds are bigger and richer; prestige is the primary
> **economy sink**. Each unlock spends gold, tier after tier, pulling currency out
> of circulation. Costs must grow fast enough to matter for the richest guilds.

**Cost model (contract):**
```
cost(tier) = round(prestige.cost_base * prestige.cost_multiplier^(tier-1))
```
- `cost_base` (config, default: 2,500,000 raw gold) — P1 cost
- `cost_multiplier` (config, default: 2.0) — each tier costs double the previous
  (P1: 2.5M, P2: 5M, P3: 10M, P4: 20M, P5: 40M, …)

Gold is **deducted and consumed** (paid to the void — not into any guild's bank) at
the moment prestige is confirmed.

## 5. Anti-farm & pacing

- **Hold-at-cap cooldown:** a guild must hold level 200 for **7 consecutive days**
  (`prestige.hold_cap_days`, config) before prestiging — prevents level-farm → instant
  prestige cycling.
- **Fast-track:** while re-leveling after prestige, the guild gains **+25% XP**
  (`prestige.relevel_xp_multiplier`, config) so the 1→200 re-climb takes roughly half
  the original time. This is what makes prestige *feel* worth it. (Flat multiplier —
  does not change the REQ-049 curve shape or anti-stunting rule.)
- **Tier pacing:** because cost doubles per tier, natural pacing emerges — P1 is
  attainable by an established guild, P5+ is a long-term goal. No additional
  time-gates beyond the 7-day hold.

## 6. Display & leaderboard

- **Prestige tier shown in Roman numerals** alongside level:
  - `/g info`: `Level 132 · Prestige III`
  - Guild chat tag: `[P III]` prefix (or emoji + numeral, operator-configurable)
- **Emoji:** each tier P1–P5 earns its prestige emoji (granted via the LG-1104
  config-driven emoji system, not manual perms); P6+ reuses the P5 emoji with
  escalating titles.
- **Placeholder:** new PlaceholderAPI expansion `%lumaguilds_guild_prestige%`
  (returns Roman numeral, e.g. `III`; `0`/empty for no prestige) in
  `LumaGuildsExpansion` — same pattern as `guild_level`.
- **Leaderboard (LG-1503):** prestige is visible in the guild list GUI —
  emoji + Roman numeral next to the guild name. Sorting: level leaderboard ranks
  by **(prestige tier, level)** so a Prestige III guild at level 50 outranks a
  Prestige I guild at level 132; a dedicated prestige sort mode can be added later.
- **Spawn banners (LG-1502):** prestige emoji/tier shown on banner lines when the
  guild places.

## 7. Interactions & edge cases

- **REQ-049 (anti-stunting):** unaffected — prestige XP bonus is a flat multiplier,
  curve shape and per-source caps unchanged.
- **REQ-051 (level-gated homes):** independent — level homes remain gated at
  125/150/175/200; prestige P1 home is a separate permanent slot usable at all levels.
- **REQ-054 (gold costs):** shared philosophy — level grants capacity/access, gold
  activates. Prestige applies the same double requirement.
- **Level-loss reconciliation (REQ-053):** a prestige never *loses* XP from penalties
  retroactively; penalties apply to the current level/climb like normal.
- **Guild disband:** prestige unlocks are lost with the guild (they are guild-level,
  not player-level). Prestige tier is not transferable.
- **War slots (P4):** interacts with `maxWarsForGuild()` progression slots
  (PR-4) — P4 adds a flat +1 on top of the progression-based max.
- **Discord roles (LG-1506):** prestige tier may later map to its own Discord role;
  deferred until LG-1506 lands.

## 8. Config surface (proposed)

```yaml
prestige:
  enabled: true
  hold_cap_days: 7
  relevel_xp_multiplier: 1.25
  cost_base: 2500000
  cost_multiplier: 2.0
  unlocks:
    p1_home_slot: true        # +1 home slot, all levels
    p2_alliance_slot: true    # +1 alliance slot
    p3_xp_multiplier: 1.10    # +10% permanent XP
    p4_war_slot: true         # +1 war slot
    p5_member_capacity: 5     # +5 members
```

## 9. Open items (post-design)

- Exact prestige emoji set + title strings (operator content pass)
- Whether P6+ cosmetic escalation is finite or endless (default: finite table of ~10)
- Leaderboard sort: confirm (prestige, level) composite as the default ordering
