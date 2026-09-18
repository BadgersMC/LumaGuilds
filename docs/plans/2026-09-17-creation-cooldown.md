# LG-1207 guild creation cooldown — SPEAR

REQ-055: deletion less than seven days after creation blocks the original creator
for fifteen days from deletion. Both windows are configurable; the seven-day
boundary is exclusive and creation is allowed exactly at expiry.

Persist immutable creator identity with the guild insertion. Lock the creator's
cooldown row during creation admission and during deletion. Guild deletion and
cooldown update must commit together; rollback must preserve both. Ownership
transfers never change the creator. Ordinary cleanup/failed-creation rollback must
not impose a deletion cooldown. Repeated deletion cannot extend a settled cooldown.
Never shorten an existing cooldown when another deletion or configuration changes.

Existing guilds have no reliable original creator field. Do not infer one from
the current owner or deleting administrator. Preserve their behavior without a
retroactive penalty; creator tracking begins for guilds created through this path.

Prove boundaries, custom durations, negative-clock rejection, failed writes,
restart, immutable attribution and serialized concurrent admission. Integrate the
repository admission/deletion methods, service and localized command preflight;
run the full regression and SQLite/MariaDB contracts.
