# Hytale Custom UI Implementation Guide

## Overview

Hytale's Custom UI system uses a **client-server architecture** with pre-created UI files and server-side event handling. This guide provides everything needed to implement the guild control panel.

**Source**: https://hytale-docs.com/docs/api/server-internals/custom-ui

---

## Key Concepts

### Architecture
- **UI Files (.ui)**: Must exist in resources beforehand - cannot be generated at runtime
- **Location**: `src/main/resources/Common/UI/Custom/YourPlugin/`
- **Base Class**: `InteractiveCustomUIPage<T>` where T is your event data class
- **Communication**: Server sends commands via `UICommandBuilder`, receives events via `UIEventBuilder`

### Lifecycle
1. **Create**: Instantiate page with `PlayerRef`
2. **Build**: Define UI structure and event bindings in `build()`
3. **Open**: Use `player.getPageManager().openCustomPage()`
4. **Interact**: Handle events in `handleDataEvent()`
5. **Update**: Send UI updates with `sendUpdate()`
6. **Close**: User dismisses or call `this.close()`

---

## Basic Implementation

### 1. UI File Structure

**File**: `src/main/resources/Common/UI/Custom/LumaGuilds/GuildPanel.ui`

```
$C = "../Common.ui";

Group {
    Anchor: (Width: 500, Height: 400);
    Background: #1a1a2e;
    LayoutMode: Top;
    Padding: (Full: 20);

    // Header
    Label #Title {
        Text: "Guild Control Panel";
        Anchor: (Height: 40);
        Style: (FontSize: 24, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center);
    }

    Group { Anchor: (Height: 20); }

    // Guild name display
    Label #GuildName {
        Text: "Loading...";
        Anchor: (Height: 30);
        Style: (FontSize: 16, TextColor: #ffd700, HorizontalAlignment: Center);
    }

    $C.@ContentSeparator { }

    Group { Anchor: (Height: 15); }

    // Buttons
    $C.@TextButton #InviteButton {
        @Text = "Invite Player";
        Anchor: (Height: 44);
    }

    Group { Anchor: (Height: 10); }

    $C.@TextButton #ManageMembersButton {
        @Text = "Manage Members";
        Anchor: (Height: 44);
    }

    Group { Anchor: (Height: 10); }

    $C.@SecondaryTextButton #CloseButton {
        @Text = "Close";
        Anchor: (Height: 44);
    }
}
```

### 2. Page Implementation

**File**: `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/ui/GuildControlPanelPage.kt`

```kotlin
package net.lumalyte.lg.infrastructure.hytale.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.ui.builder.EventData
import net.lumalyte.lg.application.services.GuildService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildControlPanelPage(
    private val playerRef: PlayerRef
) : InteractiveCustomUIPage<GuildControlPanelPage.GuildEventData>(
    playerRef,
    CustomPageLifetime.CanDismiss,
    GuildEventData.CODEC
), KoinComponent {

    companion object {
        const val LAYOUT = "LumaGuilds/GuildPanel.ui"
    }

    private val guildService: GuildService by inject()

    override fun build(
        ref: Ref<EntityStore>,
        cmd: UICommandBuilder,
        evt: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        // Load UI layout
        cmd.append(LAYOUT)

        // Get player's guild
        val playerId = playerRef.getUuid()
        val guilds = guildService.getPlayerGuilds(playerId)

        if (guilds.isNotEmpty()) {
            val guild = guilds.first()
            cmd.set("#GuildName.Text", guild.name)
        } else {
            cmd.set("#GuildName.Text", "Not in a guild")
        }

        // Bind button events
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#InviteButton",
            EventData().append("Action", "invite"),
            false
        )

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ManageMembersButton",
            EventData().append("Action", "manage_members"),
            false
        )

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData().append("Action", "close"),
            false
        )
    }

    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: GuildEventData
    ) {
        when (data.action) {
            "invite" -> {
                // Open invite dialog
                val invitePage = GuildInviteDialogPage(playerRef)
                // TODO: Open invite dialog
            }
            "manage_members" -> {
                // Open members management
                // TODO: Implement
            }
            "close" -> {
                this.close()
            }
        }
    }

    class GuildEventData {
        companion object {
            val CODEC = com.hypixel.hytale.server.core.ui.builder.BuilderCodec.builder(
                GuildEventData::class.java
            ) { GuildEventData() }
                .append(
                    com.hypixel.hytale.server.core.ui.builder.KeyedCodec("Action", com.hypixel.hytale.server.core.codec.Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .build()
        }

        var action: String? = null
    }
}
```

### 3. Opening the UI

**In GuildMenuCommand**:

```kotlin
override fun executeSync(context: CommandContext) {
    if (!context.isPlayer) {
        context.sendMessage(Message.raw("Only players can use this!").color("red"))
        return
    }

    val playerId = context.sender().uuid
    val universe = com.hypixel.hytale.server.core.universe.Universe.get()
    val playerRef = universe.getPlayer(playerId)

    if (playerRef == null) {
        context.sendMessage(Message.raw("Failed to get player reference!").color("red"))
        return
    }

    // Get player component
    val world = universe.getWorld(playerRef.getWorldUuid()!!)
    if (world == null) {
        context.sendMessage(Message.raw("Failed to get world!").color("red"))
        return
    }

    val entityRef = playerRef.reference
    if (entityRef == null) {
        context.sendMessage(Message.raw("Failed to get entity reference!").color("red"))
        return
    }

    world.runOnThreadQueued {
        val player = world.getComponent(entityRef, com.hypixel.hytale.server.core.entity.entities.player.Player.getComponentType())
        if (player != null) {
            val page = GuildControlPanelPage(playerRef)
            player.getPageManager().openCustomPage(entityRef, world, page)
        }
    }
}
```

---

## Available UI Components

### Common.ui Components

Import with: `$C = "../Common.ui";`

**Buttons**:
- `$C.@TextButton` - Primary button (blue)
- `$C.@SecondaryTextButton` - Secondary button (gray)
- `$C.@CancelTextButton` - Cancel/danger button (red)

**Input Fields**:
- `$C.@TextField` - Text input
- `$C.@NumberField` - Numeric input
- `$C.@CheckBox` - Checkbox only
- `$C.@CheckBoxWithLabel` - Checkbox with label
- `$C.@DropdownBox` - Dropdown selector

**Layout**:
- `$C.@Container` - Basic container
- `$C.@DecoratedContainer` - Container with border
- `$C.@ContentSeparator` - Horizontal line
- `$C.@DefaultSpinner` - Loading spinner

---

## Event Handling

### Event Types

```kotlin
CustomUIEventBindingType.Activating        // Click or Enter
CustomUIEventBindingType.ValueChanged      // Input/slider changes
CustomUIEventBindingType.RightClicking     // Right click
CustomUIEventBindingType.DoubleClicking    // Double click
CustomUIEventBindingType.MouseEntered      // Hover over
CustomUIEventBindingType.MouseExited       // Hover off
CustomUIEventBindingType.FocusGained       // Focus received
CustomUIEventBindingType.FocusLost         // Focus lost
```

### Capturing Input Values

Use `@` prefix to capture element values:

```kotlin
evt.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#SubmitButton",
    EventData()
        .append("Action", "submit")
        .append("@PlayerName", "#PlayerNameInput.Value"),  // Captures input value
    false
)
```

### Event Data Codec

**Required structure**:
```kotlin
class MyEventData {
    companion object {
        val CODEC = BuilderCodec.builder(MyEventData::class.java) { MyEventData() }
            .append(
                KeyedCodec("Action", Codec.STRING),
                { e, v -> e.action = v },
                { e -> e.action }
            )
            .add()
            .append(
                KeyedCodec("PlayerName", Codec.STRING),
                { e, v -> e.playerName = v },
                { e -> e.playerName }
            )
            .add()
            .build()
    }

    var action: String? = null
    var playerName: String? = null
}
```

---

## UI Manipulation

### UICommandBuilder Methods

```kotlin
// Load layout
cmd.append("YourPlugin/MyPage.ui")

// Set text
cmd.set("#Title.Text", "Hello World")

// Set visibility
cmd.set("#Panel.Visible", false)

// Set numeric value
cmd.set("#Slider.Value", 0.5f)

// Clear container
cmd.clear("#ItemList")

// Append to container
cmd.append("#ItemList", "YourPlugin/ListItem.ui")
```

### Updating UI After Changes

```kotlin
override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: EventData) {
    // Process event

    // Update UI
    val cmd = UICommandBuilder()
    cmd.set("#StatusText.Text", "Updated!")
    this.sendUpdate(cmd, false)
}
```

---

## Layout System

### Layout Modes

```
LayoutMode: Top;      // Vertical stacking (top to bottom)
LayoutMode: Left;     // Horizontal arrangement (left to right)
LayoutMode: Center;   // Center children
```

### Sizing

```
// Fixed size
Anchor: (Width: 400, Height: 300);

// Flexible size
FlexWeight: 1;  // Takes available space proportionally

// Height only (full width)
Anchor: (Height: 44);
```

### Padding and Spacing

```
// Uniform padding
Padding: (Full: 20);

// Directional padding
Padding: (Left: 10, Right: 10, Top: 5, Bottom: 5);

// Spacer groups
Group { Anchor: (Height: 20); }  // Vertical space
Group { Anchor: (Width: 20); }   // Horizontal space
```

---

## Best Practices

### 1. UI File Syntax

✅ **Correct**:
```
Label #Title {
    Text: "Hello World";
    Anchor: (Height: 30);
    Style: (FontSize: 16, TextColor: #ffffff);
}
```

❌ **Incorrect**:
```
Label #Title {
    Text: Hello World        // Missing quotes
    Anchor: (Height: 30)     // Missing semicolon
    Style: (FontSize: 16)    // Missing semicolon
}
```

### 2. Performance

- **Batch updates**: Combine multiple `cmd.set()` calls before `sendUpdate()`
- **Minimize rebuilds**: Only update changed elements
- **Avoid frequent updates**: Debounce rapid changes

### 3. Threading

- UI event handlers run on network threads
- Use `world.runOnThreadQueued { }` for entity operations
- Don't block UI event handlers with long operations

### 4. Dynamic Lists

When appending templates to containers:

❌ **Wrong**:
```kotlin
cmd.append("#Container", "Template.ui")
cmd.set("#Container[0] #Button.Text", "Hello")  // Template becomes the element
```

✅ **Correct**:
```kotlin
cmd.append("#Container", "Template.ui")
cmd.set("#Container[0].Text", "Hello")  // Direct access
```

### 5. Error Handling

**Common errors**:
- "Failed to load CustomUI documents" = Syntax error in .ui file
- "Failed to apply CustomUI event bindings" = Element ID doesn't exist
- "Selected element not found" = Incorrect selector for dynamic element

**Debug tips**:
- Check semicolons after every property
- Verify element IDs match exactly (case-sensitive)
- Ensure quotes around all string values

---

## Complete Form Example

### UI File: Invite Dialog

```
$C = "../Common.ui";

Group {
    Anchor: (Width: 450, Height: 250);
    Background: #1a1a2e;
    LayoutMode: Top;
    Padding: (Full: 20);

    // Title
    Label {
        Text: "Invite Player to Guild";
        Anchor: (Height: 30);
        Style: (FontSize: 18, TextColor: #ffffff, RenderBold: true);
    }

    Group { Anchor: (Height: 15); }

    // Instructions
    Label {
        Text: "Enter the player's username:";
        Anchor: (Height: 20);
        Style: (FontSize: 14, TextColor: #cccccc);
    }

    Group { Anchor: (Height: 10); }

    // Input field
    $C.@TextField #PlayerNameInput {
        PlaceholderText: "PlayerName";
        Anchor: (Height: 40);
    }

    Group { Anchor: (Height: 10); }

    // Status message
    Label #StatusMessage {
        Text: "";
        Anchor: (Height: 20);
        Style: (FontSize: 12, TextColor: #ff6b6b, HorizontalAlignment: Center);
    }

    // Spacer to push buttons to bottom
    Group { FlexWeight: 1; }

    // Button row
    Group {
        LayoutMode: Left;
        Anchor: (Height: 44);

        $C.@TextButton #InviteBtn {
            @Text = "Send Invite";
            FlexWeight: 1;
        }

        Group { Anchor: (Width: 10); }

        $C.@CancelTextButton #CancelBtn {
            @Text = "Cancel";
            FlexWeight: 1;
        }
    }
}
```

### Kotlin Implementation

```kotlin
class GuildInviteDialogPage(
    private val playerRef: PlayerRef
) : InteractiveCustomUIPage<GuildInviteDialogPage.InviteEventData>(
    playerRef,
    CustomPageLifetime.CanDismiss,
    InviteEventData.CODEC
), KoinComponent {

    private val invitationService: InvitationService by inject()
    private val guildService: GuildService by inject()

    override fun build(ref: Ref<EntityStore>, cmd: UICommandBuilder, evt: UIEventBuilder, store: Store<EntityStore>) {
        cmd.append("LumaGuilds/InviteDialog.ui")

        // Bind invite button with input capture
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#InviteBtn",
            EventData()
                .append("Action", "invite")
                .append("@PlayerName", "#PlayerNameInput.Value"),
            false
        )

        // Bind cancel button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelBtn",
            EventData().append("Action", "cancel"),
            false
        )
    }

    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: InviteEventData) {
        when (data.action) {
            "invite" -> {
                val playerName = data.playerName
                if (playerName.isNullOrBlank()) {
                    showError("Please enter a player name")
                    return
                }

                // Get inviter's guild
                val playerId = playerRef.getUuid()
                val guilds = guildService.getPlayerGuilds(playerId)

                if (guilds.isEmpty()) {
                    showError("You are not in a guild!")
                    return
                }

                val guild = guilds.first()

                // Look up target player
                val universe = com.hypixel.hytale.server.core.universe.Universe.get()
                val targetPlayerRef = universe.getPlayerByUsername(
                    playerName,
                    com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE
                )

                if (targetPlayerRef == null) {
                    showError("Player '$playerName' not found!")
                    return
                }

                // Send invitation
                val success = invitationService.sendInvitation(
                    guildId = guild.id,
                    invitedPlayerId = targetPlayerRef.getUuid()!!,
                    inviterPlayerId = playerId
                )

                if (success) {
                    // Play sound and close dialog
                    net.lumalyte.lg.infrastructure.hytale.sounds.GuildSounds.playInvitationSent(playerRef)
                    this.close()
                } else {
                    showError("Failed to send invitation")
                }
            }
            "cancel" -> {
                this.close()
            }
        }
    }

    private fun showError(message: String) {
        val cmd = UICommandBuilder()
        cmd.set("#StatusMessage.Text", message)
        this.sendUpdate(cmd, false)
    }

    class InviteEventData {
        companion object {
            val CODEC = BuilderCodec.builder(InviteEventData::class.java) { InviteEventData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .append(
                    KeyedCodec("PlayerName", Codec.STRING),
                    { e, v -> e.playerName = v },
                    { e -> e.playerName }
                )
                .add()
                .build()
        }

        var action: String? = null
        var playerName: String? = null
    }
}
```

---

## Next Steps

### Phase 1: Simple Test UI
1. Create basic "Hello World" UI
2. Test opening from command
3. Verify button clicks work
4. Confirm event handling

### Phase 2: Guild Panel
1. Implement guild control panel UI file
2. Create GuildControlPanelPage class
3. Add button event handlers
4. Test with real guild data

### Phase 3: Sub-Dialogs
1. Create invite dialog UI
2. Create member management UI
3. Create settings UI
4. Link from main panel

### Phase 4: Polish
1. Add loading states
2. Implement error messages
3. Add confirmation dialogs
4. Improve styling

---

## Resources

- **Official Documentation**: https://hytale-docs.com/docs/api/server-internals/custom-ui
- **Common.ui Components**: See Hytale server resources
- **Example UI Files**: Check server assets for reference implementations

---

## Troubleshooting

### UI Won't Load
- Check .ui file syntax (semicolons, quotes)
- Verify file path in resources folder
- Check console for "Failed to load CustomUI documents"

### Events Not Firing
- Verify element ID matches exactly
- Check event binding type is correct
- Ensure EventData codec fields match keys

### UI Not Updating
- Call `sendUpdate()` after changes
- Check threading - use `runOnThreadQueued` for entity access
- Verify UICommandBuilder selectors are correct

### Styling Issues
- Use hex colors: `#ffffff` not `white`
- Check padding/anchor values
- Verify LayoutMode is set correctly
