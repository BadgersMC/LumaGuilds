# Guild root command routing

- Requirement: `/guild` and `/g` open the same destination as `/guild menu`, retaining its command and guild-management permission checks; guildless players receive its existing recruitment destination.
- Existing subcommands, including `/g leaderboard`, keep their independent routing and permissions.
- Evidence before change: on TEST `/guild` returned an unknown-command response while `/guild menu` and `/g leaderboard` opened their menus. The command class defines no default handler.
- Proof: exercise ACF registration and command dispatch with MockBukkit, including permission denial and the existing leaderboard test; verify in a real client after an authorized TEST handoff and deployment.
- Scope: source and local tests only; no GUI, economy, or RoseChat changes. No project-local SPEAR validator was identified; this note records requirements and evidence manually.
