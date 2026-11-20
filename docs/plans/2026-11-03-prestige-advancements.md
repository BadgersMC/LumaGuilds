# Prestige history, event and member credits

Feeds the Enthusia "Guild Prestige" advancements. Production renders advancements
with the EnthusiaAdvancements pilot, whose definitions come from EnthusiaTags, so
LumaGuilds reports facts and Tags owns the achievements. Requirements: REQ-152,
REQ-153 and REQ-154 in `../requirements.md`.

## Decisions

- **No schema change for dates (REQ-152).** A prestige reuses its transaction ID
  for its canonical gold debit. `guild_gold_operations.created_at` is written
  with `System.currentTimeMillis()` inside the same database transaction as the
  receipt (`GuildGoldRepositorySQL.insertPrepared`, called from
  `applyInTransaction` within `PrestigeRepositorySQL.execute`). Joining receipts
  to the journal gives exact commit times, including prestiges made before this
  change. Server-first order is commit time, then transaction ID.
- **Event at the announcement point (REQ-153).** `GuildPrestigeService.confirm`
  already announces only when `PrestigeConfirmation.newlyCommitted` is true. The
  event is published at that point through an application port so replays stay
  silent and listener failures are contained like announcement failures.
- **Deliver to online members, ledger after dispatch (REQ-154).** EnthusiaTags
  keeps counter deltas for unloaded players only in memory
  (`RewardService.queueCounterDelta` / `pendingCounterDeltas`), so a restart can
  lose them. Credits are therefore dispatched only while the member is online and
  recorded in `guild_prestige_member_credits` afterwards. Eligibility is derived
  from durable facts (prestige history and `Member.joinedAt`), so a join after a
  restart re-derives anything not yet recorded. Configured commands must be safe
  to repeat once: the intended command is
  `enthusiatags rewards counter {uuid} guild_prestige_{tier} 1`, and a Tags
  achievement needing a counter of 1 completes once even if the counter reaches 2.
- **Eligibility uses current membership.** A member who leaves before being
  credited, or rejoins later (new `joinedAt`), is not credited for earlier
  prestiges. This is accepted and documented rather than snapshotting members.
- **Disabled by default.** Operators opt in under
  `progression.prestige.member_credits`.

## Configuration

```yaml
progression:
  prestige:
    member_credits:
      enabled: false
      min_tenure_hours: 24
      commands:
        - "enthusiatags rewards counter {uuid} guild_prestige_{tier} 1"
      first_reach_commands:
        - "enthusiatags rewards counter {uuid} guild_prestige_first_{tier} 1"
```

Placeholders: `{uuid}`, `{player}` (current name), `{tier}`.

## Boundaries

No change to the prestige transaction, fees, rewards, receipts or announcement.
No production upload, configuration change or restart is part of this work.
Native MariaDB and live TEST acceptance remain separate gates.
