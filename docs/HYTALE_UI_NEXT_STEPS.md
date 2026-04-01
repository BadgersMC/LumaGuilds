# Hytale UI System - Next Steps

## What We've Done

We've created a **comprehensive Guild Control Panel skeleton** that maps out all the features we want to implement:

- ✅ Complete feature layout (16 features mapped)
- ✅ UI structure defined (`GuildControlPanel.kt`)
- ✅ Feature tracker document created
- ✅ Implementation priorities established

##  What We Need to Figure Out

The Hytale UI API is different than initially documented. We need to:

1. **Find Example UI Code**: Look for existing Hytale plugins or examples
2. **Understand CustomUIPage lifecycle**:
   - How `build()` actually works
   - How to properly use `UICommandBuilder`
   - How to handle events with `UIEventBuilder`
   - What `CustomPageLifetime` options exist

3. **Learn the UI Component System**:
   - What components are available? (button, text, container, etc.)
   - How to properly structure UI hierarchy
   - How styling/layout actually works
   - How to handle dynamic content

4. **Test Basic UI First**:
   - Create a simple "Hello World" custom page
   - Test opening it from a command
   - Learn event handling
   - Then expand to full control panel

## Recommended Approach

### Option 1: Research Hytale UI (Recommended)
1. Search for Hytale UI documentation
2. Look at HytaleServer.jar for examples
3. Find sample plugin code
4. Create a simple test UI first

### Option 2: Use Commands Instead (Temporary)
While figuring out UI, keep using commands:
- `/guild menu` opens chat-based menu
- `/guild members` - List members
- `/guild invite <player>` - Invite
- etc.

Then migrate to UI once we understand it better.

## What's Ready to Implement (No UI Required)

These features can be implemented with commands only:

1. **Invitations System**
   - `/guild invite <player>`
   - `/guild accept <guild>`
   - `/guild decline <guild>`
   - Database: `guild_invites` table

2. **Leave Guild**
   - `/guild leave`
   - Confirmation in chat
   - Simple logic

3. **Basic Member Management**
   - `/guild members` - List members
   - `/guild kick <player>`
   - `/guild promote <player>`

4. **Guild Chat**
   - `/gc <message>` - Guild chat
   - Channel system
   - Already have `HytaleChatService.kt` stub

## The Big Picture

The Guild Control Panel acts as our **implementation roadmap**. Each button/section tells us:
- What needs to be built
- What database tables are needed
- What service methods to create
- Priority order

Even without the UI working yet, we can implement all the backend logic and commands, then add the pretty GUI later!

## Next Session Goals

1. Pick ONE feature to fully implement (recommend: Invitations)
2. Build it command-based first
3. Test it works
4. Then explore Hytale UI for that one feature
5. Learn the UI system properly with a simple example

## Current Status

- ✅ Control panel structure designed
- ✅ All features mapped and documented
- ✅ Implementation priorities established
- ❌ Need to learn Hytale's actual UI API
- ✅ Can proceed with command-based implementation

The skeleton is valuable even without working UI - it's our blueprint!
