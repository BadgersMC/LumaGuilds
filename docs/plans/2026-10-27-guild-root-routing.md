# Guild root command routing

- Requirement: `/guild` and `/g` open a guild destination using the existing command permission: managers get the management dashboard, regular members get guild information, and guildless players get the existing recruitment destination. Explicit `/guild menu` retains its management restriction.
- Existing subcommands, including `/g leaderboard`, keep their independent routing and permissions.
- Evidence before change: on TEST `/guild` returned an unknown-command response while `/guild menu` and `/g leaderboard` opened their menus. The command class defines no default handler.
- Proof: exercise ACF registration and command dispatch with MockBukkit, including permission denial and the existing leaderboard test; verify in a real client after an authorized TEST handoff and deployment.
- Scope: source and local tests only; no GUI, economy, or RoseChat changes. No project-local SPEAR validator was identified; this note records requirements and evidence manually.

- Review correction: the dispatch regression now covers regular-member fallback and verifies explicit management remains denied; focused root and leaderboard tests pass locally.
- Missing rank records must never count as matching owner ranks. The command dispatcher test reproduced the unsafe null comparison before correction; management permissions and explicit administrator override remain valid.
