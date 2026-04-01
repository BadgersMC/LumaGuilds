# Hytale UI System - Discovery Summary

## What We Discovered

By decompiling HytaleServer.jar classes, we've discovered Hytale's actual native UI API structure.

### ✅ Confirmed Classes and APIs

1. **CustomUIPage** (abstract base class) - EXISTS
   - Located: `com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage`
   - Constructor: `CustomUIPage(PlayerRef, CustomPageLifetime)`
   - Abstract method: `build(Ref<EntityStore>, UICommandBuilder, UIEventBuilder, Store<EntityStore>)`
   - Methods: `handleDataEvent()`, `onDismiss()`, `rebuild()`, `sendUpdate()`, `close()`

2. **BasicCustomUIPage** (simpler alternative) - EXISTS
   - Extends CustomUIPage
   - Simplified `build(UICommandBuilder)` method - no entity refs needed
   - **Recommended for simple UIs like our guild panel**

3. **InteractiveCustomUIPage<T>** (for complex event handling) - EXISTS
   - Generic type-safe event handling
   - Requires `BuilderCodec<T>` for event data serialization

4. **CustomPageLifetime** (enum) - EXISTS
   - `CantClose` - User cannot close
   - `CanDismiss` - User can dismiss/close
   - `CanDismissOrCloseThroughInteraction` - Can close via dismiss or interaction

5. **CustomUIEventBindingType** (enum) - EXISTS
   - 24 event types discovered:
     - `Activating` (primary action/click)
     - `RightClicking`, `DoubleClicking`
     - `MouseEntered`, `MouseExited`
     - `ValueChanged`, `ElementReordered`
     - `Validating`, `Dismissing`
     - `FocusGained`, `FocusLost`
     - `KeyDown`, `MouseButtonReleased`
     - Slot-related events (for inventory-like UIs)
     - `SelectedTabChanged`

6. **UICommandBuilder** - EXISTS
   - Methods: `append()`, `set()`, `clear()`, `remove()`, `insertBefore()`
   - Supports: String, Message, boolean, int, double, arrays, objects
   - Uses BSON encoding internally

7. **UIEventBuilder** - EXISTS
   - Method: `addEventBinding(CustomUIEventBindingType, selector, EventData, locksInterface)`
   - **No helper methods like `onClick()`** - must use raw event bindings

8. **PageManager** - EXISTS
   - Component for managing player pages
   - Method: `openCustomPage(Ref<EntityStore>, Store<EntityStore>, CustomUIPage)`
   - Located: `com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager`

## How to Open a Custom UI Page

```kotlin
override fun executeSync(context: CommandContext) {
    // 1. Get player ID
    val playerId = context.sender().uuid

    // 2. Get PlayerRef from Universe
    val universe = Universe.get()
    val playerRef = universe.getPlayer(playerId) ?: return

    // 3. Get entity reference and store
    val entityRef = playerRef.reference ?: return
    val store = entityRef.store

    // 4. Get PageManager component
    val pageManager = store.getComponent(entityRef, PageManager.getComponentType()) ?: return

    // 5. Create your custom page
    val customPage = MyCustomPage(playerRef)

    // 6. Open the page
    pageManager.openCustomPage(entityRef, store, customPage)
}
```

## What We Still Need to Discover

### ❌ UI Document Paths (CRITICAL MISSING PIECE)

The UI system uses **document paths** to reference UI elements, like:
- `"hytale:ui/button"` - Button element
- `"hytale:ui/container"` - Container element
- `"hytale:ui/text"` - Text element
- `"hytale:ui/scrollView"` - Scroll view
- etc.

**We don't know what document paths exist or how to structure them.**

### How to Discover Document Paths

**Option 1: Find Documentation** (unlikely to exist publicly)
- Check Hytale official docs
- Check plugin development guides

**Option 2: Decompile Built-in UI Files**
- Look in `HytaleAssets/ui/` directory
- Find UI definition files (might be JSON, YAML, or custom format)

**Option 3: Reverse Engineer from Built-in Plugins**
- Look at `PrefabPage.class` implementation
- Look at `ImageImportPage.class` implementation
- Extract the document paths they use

**Option 4: Trial and Error**
- Try common paths like:
  - `"hytale:ui/button"`
  - `"hytale:ui/container"`
  - `"hytale:ui/panel"`
  - `"hytale:ui/text"`
- Check server logs for errors about invalid paths

**Option 5: Ask Hytale Community**
- Hytale Discord server
- Plugin development forums
- Other plugin developers

## Current Implementation Status

### ✅ What's Done

1. **API Documentation Created**
   - `docs/HYTALE_UI_API_ACTUAL.md` - Complete API reference
   - All classes and methods documented
   - Examples provided

2. **Control Panel Skeleton**
   - `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/ui/GuildControlPanel.kt`
   - Uses `BasicCustomUIPage`
   - Placeholder `build()` method with example structure
   - Commented with all 16 planned features

3. **Guild Menu Command**
   - `/guild menu` command registered
   - Currently sends informative placeholder message
   - Shows UI API discovery progress
   - Ready to implement once document paths are known

4. **Feature Tracker Updated**
   - `docs/FEATURE_IMPLEMENTATION_TRACKER.md`
   - Phase 6-10 priorities documented
   - All 16 features mapped

### ❌ What's Blocked

1. **Can't build actual UI** - Need document paths
2. **Can't test UI system** - Need document paths
3. **Can't implement features** - Need working UI first

## Next Steps

### Immediate (Critical Path)

1. **Discover UI Document Paths**
   - Try decompiling built-in page implementations
   - Check Assets.zip for UI definition files
   - Trial and error with common path names

2. **Test Simple UI**
   - Create "Hello World" test page
   - Verify document paths work
   - Test opening page from command

3. **Implement GuildControlPanel**
   - Build button grid for 16 features
   - Add event bindings
   - Test in-game

### Short Term (Once UI Works)

4. **Implement Phase 6 Features** (High Priority)
   - Invitations System
   - Members Management
   - Leave Guild
   - Guild Chat Settings

## Alternative Approach

If UI document paths remain elusive, we can:

1. **Use Commands First**
   - Implement all features with commands (like Phase 5 trust system)
   - `/guild members` - List members
   - `/guild invite <player>` - Send invite
   - `/guild leave` - Leave guild
   - etc.

2. **Add UI Wrapper Later**
   - Once document paths are discovered
   - Build UI on top of existing command logic
   - Commands remain as alternative interface

This is documented in `docs/HYTALE_UI_NEXT_STEPS.md`

## Files Created/Modified

### Documentation
- `docs/HYTALE_UI_API_ACTUAL.md` - Complete API reference
- `docs/HYTALE_UI_DISCOVERY_SUMMARY.md` - This file
- `docs/HYTALE_UI_EXAMPLE.md` - Initial assumptions (some incorrect)

### Code
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/ui/GuildControlPanel.kt` - Skeleton
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/commands/GuildMenuCommand.kt` - Placeholder
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/commands/GuildCommand.kt` - Added menu subcommand

### Build
- Plugin builds successfully (`./gradlew build -x test`)
- Ready to deploy and test placeholder command

## Testing the Placeholder

1. Build: `./gradlew build -x test`
2. Deploy to test server
3. In-game: `/guild menu`
4. Should see informative message about UI API discovery status

## Key Takeaways

✅ **Good News:**
- Hytale's native UI system is real and powerful
- API structure is clean and well-designed
- Much better than Minecraft's inventory hacks
- We've mapped the entire API

❌ **Challenge:**
- Document paths are the missing piece
- Without them, we can't build any UI
- Need to discover through decompilation or experimentation

🎯 **Recommendation:**
- Focus on discovering document paths as top priority
- OR implement features with commands first (safer fallback)
- UI is a nice-to-have, not a must-have for functionality
