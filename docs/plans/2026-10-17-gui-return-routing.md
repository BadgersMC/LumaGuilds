# Inventory GUI return routing

## Spec

- GUI-RETURN-1: When a named parent return is selected, the navigator shall restore the nearest existing parent instance and discard descendants. The following Back shall reach that parent's predecessor, not the discarded child.
- GUI-RETURN-2: When no parent exists (direct entry), the return shall replace the current screen with the safe parent fallback rather than retaining the child behind it.
- GUI-RETURN-3: Return navigation shall invalidate outstanding asynchronous navigation tokens and preserve ordinary forward navigation and dialog behavior.

## Prove / engine / architecture / refine

Observed source failure: Enemy Guilds Back pushes Relations onto `[Enemies, Relations, Dashboard]`, producing `[Relations, Enemies, Relations, Dashboard]`; Relations Back then reopens Enemies. Seven diplomacy returns, five bank returns and emoji selection returns share this forward-for-backward pattern.

The correction belongs in the interaction navigator and explicit inventory return adapters, not the dialog renderer or domain/payment code. Project EARS/state helper scripts are absent; this record tracks the scaled SPEAR evidence. Tests, review and client acceptance are recorded below as they occur. No deployment or restart is authorized by this bug report.

## Evidence

- Canonical `origin/main` fetched and inspected at `283d75f1`; clean ongoing PR236 worktree at `97b965e5` before these edits. Existing dialog-stack changes preserved.
- Red: the actual diplomacy return-button callback reproduced the extra history entry; the next Back reopened the child instead of the dashboard.
- Green: all seven real diplomacy return callbacks complete parent then dashboard. Navigator regressions cover preserved state, discarding multiple descendants, current-parent refresh, direct/empty entry fallback, closing and async token invalidation. A first test-fixture run needed the existing Bukkit dialog-capability check mocked before exercising final Close.
- `check shadowJar --offline` on Java 25.0.3 passed: 1,991 tests, zero failures/errors, 16 existing optional-profile skips. Existing architecture/localization checks included. Diff whitespace check passed.
- Explicit parent returns corrected in diplomacy, bank, ranks, guild-mode, war and emoji selection. Forward navigation and native callbacks retain their existing authorization and business operations.
- Responsive routing simulation passes 24 journeys at 320/390/736px. It is a navigation schematic, not a Minecraft screenshot or proof of real server packet ordering. Actual corrected TEST/player acceptance is pending activation; this turn changes no server.
