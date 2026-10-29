# Player-specific creation cooldown bypass

References: REQ-055. Tag: TDD.

Spec: a player holding `lumaguilds.guild.creationcooldown.bypass` can pass
creation cooldown admission without altering the stored cooldown. The permission
defaults false including operators, has no declared wildcard parent, and is checked
again for the online owner at service admission. Offline owners and revoked grants
use ordinary admission. All existing name, membership, setup, payment and database
failure checks remain. An unsupported repository fails closed for bypass admission.
No command permission, payment policy, schema, GUI or companion API changes.

Prove: retain the untested draft a56e9593 on PR18 head 076b7ad2. Add coverage for
command validation/payment failures, service authorization/revocation/offline owner,
paid service-to-SQL creation, concurrent admission, commit uncertainty, durable
attribution/cold reload, and injected history
failure after bootstrap writes. Historical red evidence is not claimed for the draft.

Engine/arch: the explicit repository operation is default-deny; the SQL transaction
still locks the creator and reads their cooldown. Only the time comparison is
conditional. Reward/progression bootstrap, creator attribution, commit uncertainty
handling and cache publication remain shared with ordinary creation. Authorization
stays in Bukkit infrastructure and the command; no Bukkit types enter domain or
application ports. Costs still use the existing reservation path.

Refine: actual commands/results are recorded below; final full-suite and exact-head
hosted results are recorded on [PR19](https://github.com/FainNeito/LumaGuilds/pull/19).
No project AGENTS.md, EARS validator or SPEAR state helpers are present; manual
requirements, task and evidence records apply. The user supplied standing agreements
through this chat on October 29. Main was fetched at e118d457; PR18 remains at
076b7ad2 with base codex/guild-prestige-release-reconcile-20261028. This bypass PR
must target PR18's head branch, codex/guild-bank-quest-recovery-20261028.

Roundtable is unavailable: no configured tool/endpoint or supported connection to
the user's local machine. It was not consulted. Pending coordination is retained
locally at /workspace/coordination/guild-cooldown-pending.md. This isolated cloud
work holds no shared-resource claims. No TEST/client/server or production access,
merge, upload, restart or activation is included.

Remaining live acceptance requires matching Roundtable/current-owner handoff,
current FainNeito state and safe reconnection verification, a pinned TEST candidate,
ordinary denial, explicit player grant, paid success, unchanged stored cooldown,
revocation/re-denial and permission cleanup readback. FainNeito was only historically
observed guildless and cooldown-blocked on TEST; no present state is assumed.


## Verification evidence

JDK 25; the pinned official RoseChat 179483128, CombatLogX API 4812e85a and
Staff API 8539bb8c were built from their workflow source pins. No companion API
was changed. The tracked Playtime API is retained. Local toolchain/proxy setup
stays outside the source tree.

- `./gradlew test --tests '*GuildCreation*' --tests '*GuildServiceCreationTest'`:
  36 tests, zero failures, errors or skips. Includes command/permission tests and
  real payment journal, inventory, creator/reward/progression and owner writes.
- `./gradlew mariaDbRewardTest -PmariaDbTestPort=33317 --tests '*GuildCreation*SQLTest'`:
  23 tests, zero failures, errors or skips on disposable cloud MariaDB 11.4.13.
  All per-test schemas were removed and the task-owned container was stopped and
  removed. No shared database or server was used.
- `check shadowJar verifyAcceptanceProbePackaging -PreleaseVersion=3.0.0-pr19-UNMERGED-TEST`
  is the full regression/packaging command; its final result is recorded on PR19.
  Local packaging checks passed. Artifacts are explicitly unmerged TEST builds.
- Wiki front matter, topic parity and `git diff --check` passed locally. Hosted
  wiki checks also passed on the initial PR head; the final head is checked again.

The initial new command fixture omitted `createdAt`, then an executed run exposed
nested MockK player-ID recording in command verification and the paid fixture.
Capture the player ID outside stubbing/verification and retain callback failure
diagnostics in the payment fixture. Corrected focused/native runs above pass;
these fixture failures are not described as product regressions. Initial hosted
head c127267a reproduced the missing-timestamp compilation failure and is superseded
by the corrected head. The original draft's engine required no further change.
CodeRabbit skips draft PRs; no independent approval is claimed. Codacy MCP tools
are unavailable in this chat. Hosted checks/review and live acceptance are distinct.

Local logs and XML snapshots are under /workspace/guild-focused2.log,
/workspace/guild-maria.log, /workspace/guild-full.log and the corresponding
/workspace/guild-*-passed-results directories. These are local evidence paths,
not published artifacts or live TEST evidence.
