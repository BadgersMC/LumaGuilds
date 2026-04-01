# Hytale Native UI System - Example

## Overview
Hytale has a native UI system that's MUCH better than Minecraft's inventory hacks. You can create proper menus with buttons, text, images, and handle events.

## Key Classes

### 1. `CustomUIPage` - Your UI Page
```kotlin
abstract class CustomUIPage(playerRef: PlayerRef, lifetime: CustomPageLifetime) {
    abstract fun build(
        entityRef: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    )

    fun onDismiss(entityRef: Ref<EntityStore>, store: Store<EntityStore>)
    fun handleDataEvent(entityRef: Ref<EntityStore>, store: Store<EntityStore>, eventId: String)
}
```

### 2. `UICommandBuilder` - Build UI Elements
```kotlin
commands
    .append("root", "container")  // Add container
    .set("root.title", Message.raw("Guild Menu"))  // Set title
    .append("root", "button")  // Add button
    .set("root.button.text", "Create Guild")  // Button text
```

### 3. `UIEventBuilder` - Handle Click Events
```kotlin
events.onClick("buttonId") { event ->
    // Handle button click
}
```

## Example: Simple Guild Menu

```kotlin
package net.lumalyte.lg.infrastructure.hytale.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

class GuildMenuPage(playerRef: PlayerRef) : CustomUIPage(playerRef, CustomPageLifetime.UNTIL_DISMISSED) {

    override fun build(
        entityRef: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        // Build the UI structure
        commands
            // Create root container
            .append("root", "container")
            .set("root.layout", "vertical")
            .set("root.padding", 20)

            // Title
            .append("root", "text")
            .set("root.text.content", Message.raw("Guild Management").color("gold").bold(true))
            .set("root.text.size", 24)

            // Create Guild Button
            .append("root", "button")
            .set("root.createBtn", "createGuildBtn")
            .set("root.createBtn.text", Message.raw("Create New Guild").color("green"))
            .set("root.createBtn.width", 200)
            .set("root.createBtn.height", 40)

            // Join Guild Button
            .append("root", "button")
            .set("root.joinBtn", "joinGuildBtn")
            .set("root.joinBtn.text", Message.raw("Join Guild").color("blue"))
            .set("root.joinBtn.width", 200)
            .set("root.joinBtn.height", 40)

            // Guild List Button
            .append("root", "button")
            .set("root.listBtn", "listGuildsBtn")
            .set("root.listBtn.text", Message.raw("View All Guilds").color("yellow"))
            .set("root.listBtn.width", 200)
            .set("root.listBtn.height", 40)

            // Close Button
            .append("root", "button")
            .set("root.closeBtn", "closeBtn")
            .set("root.closeBtn.text", Message.raw("Close").color("red"))
            .set("root.closeBtn.width", 200)
            .set("root.closeBtn.height", 40)

        // Register click handlers
        events.onClick("createGuildBtn") { event ->
            handleCreateGuild(entityRef, store)
        }

        events.onClick("joinGuildBtn") { event ->
            handleJoinGuild(entityRef, store)
        }

        events.onClick("listGuildsBtn") { event ->
            handleListGuilds(entityRef, store)
        }

        events.onClick("closeBtn") { event ->
            // Close the UI
            playerRef.closeCustomPage()
        }
    }

    private fun handleCreateGuild(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Open create guild dialog or send command
        playerRef.sendMessage(Message.raw("Opening create guild dialog...").color("green"))
        // You could open another CustomUIPage here for guild creation
    }

    private fun handleJoinGuild(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        playerRef.sendMessage(Message.raw("Opening join guild dialog...").color("blue"))
    }

    private fun handleListGuilds(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        playerRef.sendMessage(Message.raw("Showing guild list...").color("yellow"))
    }

    override fun onDismiss(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Cleanup when menu is closed
        playerRef.sendMessage(Message.raw("Menu closed").color("gray"))
    }
}
```

## Opening the Menu

```kotlin
// In your command handler or interaction:
val menuPage = GuildMenuPage(playerRef)
playerRef.openCustomPage(menuPage)
```

## Registering Custom UI Interaction

```kotlin
// In your plugin setup():
OpenCustomUIInteraction.registerSimple(
    this,  // plugin
    YourInteractionClass::class.java,
    "guild_menu",
    { playerRef -> GuildMenuPage(playerRef) }
)
```

## Advanced Features

### Dynamic Content
```kotlin
override fun build(...) {
    // Get guild data
    val guild = getPlayerGuild(playerRef.uuid)

    if (guild != null) {
        commands
            .append("root", "text")
            .set("root.guildName", Message.raw("Your Guild: ${guild.name}").color("gold"))
            .append("root", "text")
            .set("root.members", Message.raw("Members: ${guild.memberCount}").color("white"))
    }
}
```

### Lists/ScrollView
```kotlin
commands
    .append("root", "scrollView")
    .set("root.scroll.height", 300)

// Add items to scroll view
guilds.forEach { guild ->
    commands
        .append("root.scroll", "container")
        .append("root.scroll.guild_${guild.id}", "text")
        .set("root.scroll.guild_${guild.id}.text", guild.name)
}
```

### Input Fields
```kotlin
commands
    .append("root", "textInput")
    .set("root.input", "guildNameInput")
    .set("root.input.placeholder", "Enter guild name...")
    .set("root.input.maxLength", 32)

events.onTextChange("guildNameInput") { newText ->
    // Handle text input
}
```

## Benefits Over Minecraft Inventories

1. ✅ **Proper UI Elements** - Buttons, text, images, not just items
2. ✅ **Event System** - onClick, onChange, etc.
3. ✅ **Declarative** - Build UI with commands, not manual slot management
4. ✅ **Flexible Layouts** - Containers, scrollviews, grids
5. ✅ **Text Input** - Real input fields, not anvil hacks
6. ✅ **Styling** - Colors, sizes, padding, alignment
7. ✅ **Persistent State** - Keep UI state between interactions

## Next Steps

1. Create `GuildMenuPage` class
2. Add command to open it: `/guild menu`
3. Implement guild creation dialog
4. Add guild member list view
5. Build guild settings page
