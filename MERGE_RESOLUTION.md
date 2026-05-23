# Merge Conflict Resolution Strategy: PRs #46 and #48

## Overview

PR #48 (`feat/guild-bannerman`) conflicts with the already-merged PR #46 (`fix/chat-cleanup`) in exactly 2 files. Additionally, the clean merge of `di/Modules.kt` introduced a latent duplicate DI registration that must be fixed as part of the resolution.

**Resolution principle:** Preserve ALL functionality from BOTH PRs. No feature may be dropped.

---

## File 1: `src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt`

### Conflict Location
Lines 921–936 in `registerNonClaimEvents(claimsEnabled: Boolean)`.

### What Each Side Adds
- **HEAD (PR #46):** After registering `GuildDisbandedListener`, conditionally registers `RoseChatCleanupListener` if the RoseChat plugin is enabled, with a log message.
- **pr-48 (PR #48):** Only registers `GuildDisbandedListener` with a comment mentioning bannerman displays. The bannerman listener/tick-task code (lines 892–897) was already placed **outside** the conflict by the automatic merge.

### Resolution
Keep the bannerman setup that is already present at lines 892–897, keep the `GuildDisbandedListener` registration, **and** keep the `RoseChatCleanupListener` registration from PR #46.

### Exact Replacement Text

Replace the entire conflict block (lines 921–936) with:

```kotlin
        // Close stale guild menus, clean up channels, and despawn bannerman displays when a guild is disbanded
        val guildDisbandedListener = get().get<net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener>()
        server.pluginManager.registerEvents(guildDisbandedListener, this)

        // Clean up RoseChat channels when guild status changes
        if (server.pluginManager.isPluginEnabled("RoseChat")) {
            val roseChatCleanupListener = get().get<net.lumalyte.lg.infrastructure.listeners.RoseChatCleanupListener>()
            server.pluginManager.registerEvents(roseChatCleanupListener, this)
            logColored("✓ RoseChat integration registered for chat cleanup")
        }
```

### Verification
- Lines 892–897 (bannerman renderer, listeners, tick task, sweepOrphans) must remain untouched.
- `GuildDisbandedListener` must still be registered via DI (`get().get<…>()`).
- `RoseChatCleanupListener` must still be registered conditionally.

---

## File 2: `src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/GuildDisbandedListener.kt`

### Conflict Location
The entire file is conflicted: constructor parameters differ and the event-handler body differs.

### What Each Side Adds
- **HEAD (PR #46):** Constructor takes `PartyService`. Event handler closes inventories, notifies members, and removes the guild from all parties (dissolving single-guild parties).
- **pr-48 (PR #48):** Constructor takes `BannermanListeners`. Event handler closes inventories, notifies members, and despawns bannerman displays for the guild.

### Resolution
Combine both constructor parameters and both cleanup actions in the event handler.

### Exact Replacement Text

Replace the **entire file** with:

```kotlin
package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.slf4j.LoggerFactory

/**
 * Closes any open guild menus for members still online when their guild is disbanded,
 * removes all chat channels (Parties) associated with the guild,
 * and despawns any bannerman displays attached to those members.
 */
class GuildDisbandedListener(
    private val partyService: PartyService,
    private val bannermanListeners: BannermanListeners
) : Listener {

    private val logger = LoggerFactory.getLogger(GuildDisbandedListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        // 1. Close inventories and notify online members
        event.memberIds.forEach { memberId ->
            val player = Bukkit.getPlayer(memberId) ?: return@forEach
            if (!player.isOnline) return@forEach
            player.closeInventory()
            player.sendMessage("§c✗ Your guild §f${event.guild.name} §chas been disbanded.")
        }

        // 2. Remove this guild from every party it participates in.
        //    Single/last-guild parties are dissolved; multi-guild parties survive
        //    for the remaining guilds. Uses a system removal so the cleanup is not
        //    blocked by the actor's party-management permissions.
        try {
            val parties = partyService.getActivePartiesForGuild(event.guild.id)
            parties.forEach { party ->
                if (partyService.removeGuildFromPartyAsSystem(party.id, event.guild.id) == null) {
                    logger.info("Dissolved party channel ${party.name} (${party.id}) for disbanded guild ${event.guild.name}")
                } else {
                    logger.info("Removed disbanded guild ${event.guild.name} from party ${party.name} (${party.id})")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up channels for disbanded guild ${event.guild.name}", e)
        }

        // 3. Despawn bannerman displays for every member (online or offline).
        bannermanListeners.onBannermanDisabled(event.guild.id)
    }
}
```

### Verification
- Both `PartyService` and `BannermanListeners` imports must be present.
- `LoggerFactory` import must be present (used by PR #46 party cleanup).
- All three numbered steps must exist in `onGuildDisbanded`.

---

## File 3: `src/main/kotlin/net/lumalyte/lg/di/Modules.kt` (Latent Bug from Clean Merge)

### Problem
`Modules.kt` merged without conflict markers, but it now contains **two** `single<GuildDisbandedListener>` registrations:
1. In `guildsModule()` at lines 428–431 (introduced by PR #48).
2. In `socialModule()` at lines 464–466 (introduced by PR #46).

Because Koin loads `guildsModule()` before `socialModule()`, the second registration silently overrides the first. After combining the `GuildDisbandedListener` constructor to require **both** `PartyService` and `BannermanListeners`, the `socialModule()` registration will fail at runtime if it only supplies one dependency.

### Resolution
1. **Remove** the duplicate registration from `guildsModule()`.
2. **Update** the registration in `socialModule()` to pass both dependencies.

### Exact Changes

#### Change A — Remove duplicate from `guildsModule()`

Find and delete these lines (428–431):

```kotlin
    // Guild event listeners
    single<net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener> {
        net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener(get())
    }
```

The surrounding context before deletion:
```kotlin
    single<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners> {
        net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners(get<LumaGuilds>(), get(), get(), get())
    }

    // Guild event listeners
    single<net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener> {
        net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener(get())
    }
}
```

After deletion it should read:
```kotlin
    single<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners> {
        net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners(get<LumaGuilds>(), get(), get(), get())
    }
}
```

#### Change B — Update registration in `socialModule()`

Find these lines (464–466):

```kotlin
    single<net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener> {
        net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener(get())
    }
```

Replace with:

```kotlin
    single<net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener> {
        net.lumalyte.lg.infrastructure.listeners.GuildDisbandedListener(get(), get())
    }
```

The first `get()` resolves `PartyService` (available in `socialModule()`).  
The second `get()` resolves `BannermanListeners` (registered in `guildsModule()`, visible across the shared Koin container).

### Verification
- Only **one** `single<GuildDisbandedListener>` block exists in the entire file.
- That single block is inside `socialModule()` and passes **two** `get()` arguments.
- `guildsModule()` no longer contains any `GuildDisbandedListener` registration.

---

## Summary Checklist

- [ ] `LumaGuilds.kt` conflict replaced with combined text (RoseChat + GuildDisbandedListener).
- [ ] `GuildDisbandedListener.kt` conflict replaced with combined class (both constructor params, both cleanup actions).
- [ ] `Modules.kt` duplicate `GuildDisbandedListener` registration removed from `guildsModule()`.
- [ ] `Modules.kt` `socialModule()` `GuildDisbandedListener` registration updated to pass both dependencies.
- [ ] Project compiles successfully (`./gradlew compileKotlin` or equivalent).
- [ ] No conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) remain in any file.
- [ ] `git add` all three files and commit to complete the merge.
