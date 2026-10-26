# Public guild prestige announcement

## Spec

- PA-001: When a new prestige transaction commits successfully, announce the
  guild name and resulting prestige tier once to public server chat.
- PA-002: Receipt replay, including reconnect/restart and concurrent confirmation,
  must not announce again. Rejected, rolled-back and uncertain results are silent.
- PA-003: Java and Bedrock share the application completion path. Keep existing
  private feedback, payment, reward and receipt result semantics unchanged.
- PA-004: Render configured localized gold/purple styling with untrusted guild
  names treated as text. Notification failure must not undo committed prestige.

## Architecture and delivery boundaries

Expose a separate confirmation envelope indicating a newly committed result;
retain the original repository confirm method and Applied value/equality. Only
the SQL operation that actually commits reports new success. A replay is silent,
including historical receipts. The application invokes a platform announcement
port after commit; Bukkit resolves the guild and broadcasts the localized line.

This is a best-effort, at-most-once attempt, not a durable chat outbox. A crash
between commit and announcement may lose the line; replay does not publish it.
No schema, financial policy, reward or rank changes. No merge or runtime action.

## Prove and refine

New-feature scaffolding reproduced three failures in four targeted tests:
the first SQL commit lacked new-commit metadata and neither service success path
attempted an announcement. The initial compile setup failure was separate from
this behavioral red evidence. Focused commit/replay/reopen, rejection/rollback,
concurrency and sender-failure checks then passed after implementation.

Real locale rendering tests caught Nexus stringifying nested components. The
adapter now passes a serialized text component, preserving literal guild names
and preventing them from injecting MiniMessage events. Keep the locale's named
placeholders visible to the existing contract scanner. Current Java and Bedrock
confirmation entry points both dispatch on the server thread.

The preview demonstrates success, replay and failure at a mobile-accessible Page:
<https://chatgpt.com/space/page_c94edc812cc48191be632e34db28363f>.
Local headless Chrome checked interactions and overflow at 320, 390 and 760px;
this is a simulation, not native Minecraft or authenticated mobile acceptance.

Evidence logs and preview checks are in the task-owned Chapter 2 operations
directory `operations/guilds-prestige-announcement-20261026`. Full build, native
MariaDB profile and exact-head CI results are recorded with the delivery PR.
Project EARS/state helpers are absent; this record is manual SPEAR evidence.
Canonical main inspected at 283d75f1; ongoing stack base is PR243 125e4ae3.
