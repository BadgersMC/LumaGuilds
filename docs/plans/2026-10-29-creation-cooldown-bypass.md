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

Refine: actual commands/results and exact-head hosted checks will be recorded here.
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
