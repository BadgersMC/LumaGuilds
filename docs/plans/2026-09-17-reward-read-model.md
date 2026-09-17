# LG-1214 reward read model — SPEAR

The approved Chapter 2 catalog must replace level-only ownership assumptions.
Read current run level and reward ownership from one database snapshot, then
resolve bank, homes, fixed membership, cooldown and fee effects together. Missing,
corrupt or failed reads must remain unavailable; they must not grant defaults,
initialize ownership, change saved homes or fall back to legacy benefits.

Configuration is reloadable and defaults to disabled. Disabled reads perform no
reward database access and preserve the legacy path. Enabling the read model is
not a migration: only explicitly initialized guild accounts can resolve benefits.
No automatic initialization or production migration is part of this checkpoint.

Prove consistent SQL snapshots, fresh reads after purchases, missing/corrupt state,
disabled access, configuration changes and agreement between benefit consumers.
Then implement the shared service, wire gold and progression/member consumers,
run architecture, regression and both SQL dialect checks. Menus, purchase actions,
rollout/migration and lifecycle remain separate dependencies within LG-1214 and
the existing Chapter 2 tasks; do not mark the whole integration task complete.
