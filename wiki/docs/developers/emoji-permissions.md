---
title: Emoji permissions
audience: dev
topic: emoji-permissions
summary: How configured guild emoji grants are reconciled with membership and guild lifecycle.
keywords: [emoji, permissions, reconciliation]
related: [infrastructure, application]
updated: 2026-09-28
---

# Emoji permissions

Guild emoji permission grants are reconciled by `GuildEmojiGrantService`.

The service maps configured guild names to grant identifiers, then delegates membership changes to the application-layer `GuildEmojiGrantReconciler`.

Important entry points:

- `reconcileAll()` — reconcile all configured guild grants;
- `reconcileMember(playerId, guildId)` — apply the correct grant after membership/join changes;
- `removeMember(...)` — remove membership-derived access;
- `reconcileGuild(guildId)` — refresh a guild after identity/config changes;
- `removeGuild(guildId)` — clean grants when a guild disappears.

The normal `/lumaguilds reload` path also reconciles emoji permissions.

Keep grant resolution deterministic and keyed from configuration; do not hide permission mutations in menu code.
