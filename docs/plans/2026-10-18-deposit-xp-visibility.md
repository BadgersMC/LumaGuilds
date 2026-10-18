# Deposit and quest XP visibility

## Spec

- XP-VIEW-1: WHEN a positive XP award commits without a level-up, THE SYSTEM SHALL refresh the cached guild progression used by menus and placeholders.
- XP-VIEW-2: WHEN ordinary raw-gold bank growth awards XP through the Chapter Two service, THE SYSTEM SHALL publish the committed progression without awarding XP again.
- XP-VIEW-3: WHEN a quest reward is retried, THE SYSTEM SHALL retain its stable transaction identity and SHALL NOT grant duplicate XP. Deposit caps, high-water anti-farming, AFK eligibility and reward amounts SHALL remain unchanged.

## Prove and boundaries

Authoritative main `283d75f1` fetched and inspected before creating the isolated `codex/guild-deposit-xp-20261018` worktree. The active TEST dialog branch is preserved separately. No project AGENTS, EARS/state helpers or general verification file is present; this is the manual SPEAR requirement/task/evidence record.

Reported behavior: deposit quest completes without visible reward XP; normal deposit XP is also missing. Source: the SQL writer updates persisted progression independently of the menu repository cache. `awardUncappedSystemExperienceOnce` and `levelFromAward` refresh only on level-up. Chapter Two bank growth bypasses those refresh paths completely. No matching post-deposit/quest errors appeared in the inspected current TEST startup log; this is source diagnosis, not a live database balance audit.

Add regressions before the smallest cache-publication change. Keep all writes, reward identities, source policies and domain rules intact. No server upload/reload/restart or compensating historical XP grants are included.

## Engine and architecture

Every successful progression facade award refreshes cached progression, including awards without a level-up. An already-paid quest receipt also refreshes state without replaying a level event or adding XP. Chapter Two awards publish through a deferred application callback wired to the existing committed-XP platform adapter; resolving it only after the award avoids an eager dependency cycle. The adapter catches and logs publication failures so a committed award is not reported as a failed payment. The original five-argument JVM constructor remains available.

Database-backed proof exposed a second fault: `DbRow.getString` casts numeric epoch timestamps to String before the timestamp parser can handle them. The reader now converts the raw numeric/text value before parsing. Refresh and preload use the same mapper; null last-level-up remains null. A concurrent progression cache supports the existing background award and menu-reader paths. SQL award transactions, receipt identities, caps, high-water accounting and reward values are unchanged. No schema migration is needed.

## Prove and refine evidence

- Red: both new no-level-up cache publication assertions failed on untouched main. The real SQL/cache fixture then reproduced the numeric timestamp ClassCastException while proving that XP had already committed to the database.
- Green: 32 focused cases cover quest rewards, non-level bank activity, Chapter Two commit publication, timestamp formats, SQL cap/high-water rules and existing quest settlement. Real SQL/cache tests cover a 1-XP bank growth award, a 500-XP quest reward, duplicate receipt recovery and withdrawal/redeposit without new XP.
- Java 25.0.3 canonical `check shadowJar --offline` passed: 1,775 tests, zero failures/errors, 16 existing optional-profile skips. Architecture/localization checks included. The only supported compile profile is the existing Paper 26.2 API; no new platform API was introduced. The existing TEST client/runtime is 26.3; corrected live acceptance remains pending.
- JVM inspection confirms that the old five-argument ChapterTwoGuildAwardService constructor remains present. Existing policy, persistence and integration regressions pass. Project EARS/state tools remain absent; this manual record is the SPEAR evidence.
- Local artifact: `3.0.0-deposit-xp-UNMERGED-TEST`, 25,318,039 bytes, SHA-256 `df56c7cf1ff8cb9e01e6f6afb13e9f9726f7d0635f5731098edb18a63a426e94`. This standalone-main test build does not contain the separate unmerged dialog stack and must not replace that shared TEST build without reconciliation.
- Hosted CI/review and live TEST acceptance are separate gates. No merge, monorepo pin update, server operation or historical XP adjustment occurred.
