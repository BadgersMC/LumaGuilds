# Hytale UI API - Actual Implementation

Based on decompiled classes from HytaleServer.jar

## Core Classes

### 1. CustomUIPage (Abstract Base Class)

```kotlin
abstract class CustomUIPage(
    protected val playerRef: PlayerRef,
    protected var lifetime: CustomPageLifetime
) {
    abstract fun build(
        entityRef: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    )

    fun handleDataEvent(entityRef: Ref<EntityStore>, store: Store<EntityStore>, eventId: String)
    fun onDismiss(entityRef: Ref<EntityStore>, store: Store<EntityStore>)

    protected fun rebuild()
    protected fun sendUpdate()
    protected fun sendUpdate(commands: UICommandBuilder)
    protected fun sendUpdate(commands: UICommandBuilder, clearFirst: Boolean)
    protected fun close()
}
```

### 2. BasicCustomUIPage (Simpler Alternative)

```kotlin
abstract class BasicCustomUIPage(
    playerRef: PlayerRef,
    lifetime: CustomPageLifetime
) : CustomUIPage(playerRef, lifetime) {

    // Implements the complex build() method, delegates to simple one
    final override fun build(
        entityRef: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        // Handles entity/store complexity internally
        build(commands)
    }

    // Simple version - just build UI with commands
    abstract fun build(commands: UICommandBuilder)
}
```

**Use BasicCustomUIPage** when you don't need entity refs or event handling!

### 3. InteractiveCustomUIPage<T> (For Complex Event Handling)

```kotlin
abstract class InteractiveCustomUIPage<T>(
    playerRef: PlayerRef,
    lifetime: CustomPageLifetime,
    protected val eventDataCodec: BuilderCodec<T>
) : CustomUIPage(playerRef, lifetime) {

    // Type-safe event handling
    abstract fun handleDataEvent(
        entityRef: Ref<EntityStore>,
        store: Store<EntityStore>,
        eventData: T
    )

    protected fun sendUpdate(
        commands: UICommandBuilder,
        events: UIEventBuilder,
        clearFirst: Boolean
    )
}
```

### 4. CustomPageLifetime (Enum)

```kotlin
enum class CustomPageLifetime {
    CantClose,                              // User cannot close at all
    CanDismiss,                             // User can dismiss/close
    CanDismissOrCloseThroughInteraction     // Can close via dismiss or interaction
}
```

### 5. CustomUIEventBindingType (Enum)

All available event types:

```kotlin
enum class CustomUIEventBindingType {
    Activating,                    // Primary action (like click)
    RightClicking,                 // Right mouse button
    DoubleClicking,                // Double click
    MouseEntered,                  // Mouse hover enter
    MouseExited,                   // Mouse hover exit
    ValueChanged,                  // Input value changed
    ElementReordered,              // Drag/drop reorder
    Validating,                    // Form validation
    Dismissing,                    // Page being dismissed
    FocusGained,                   // Element gained focus
    FocusLost,                     // Element lost focus
    KeyDown,                       // Keyboard key pressed
    MouseButtonReleased,           // Mouse button released
    SlotClicking,                  // Inventory slot clicked
    SlotDoubleClicking,            // Inventory slot double-clicked
    SlotMouseEntered,              // Mouse entered inventory slot
    SlotMouseExited,               // Mouse exited inventory slot
    DragCancelled,                 // Drag operation cancelled
    Dropped,                       // Item dropped
    SlotMouseDragCompleted,        // Completed dragging to slot
    SlotMouseDragExited,           // Drag exited slot
    SlotClickReleaseWhileDragging, // Released click while dragging
    SlotClickPressWhileDragging,   // Pressed click while dragging
    SelectedTabChanged             // Tab selection changed
}
```

## UICommandBuilder API

```kotlin
class UICommandBuilder {
    // Clear/remove elements
    fun clear(selector: String): UICommandBuilder
    fun remove(selector: String): UICommandBuilder

    // Add elements from document paths
    fun append(documentPath: String): UICommandBuilder
    fun append(selector: String, documentPath: String): UICommandBuilder
    fun appendInline(selector: String, document: String): UICommandBuilder

    // Insert elements
    fun insertBefore(selector: String, documentPath: String): UICommandBuilder
    fun insertBeforeInline(selector: String, document: String): UICommandBuilder

    // Set properties - various types
    fun <T> set(selector: String, ref: Value<T>): UICommandBuilder
    fun setNull(selector: String): UICommandBuilder
    fun set(selector: String, str: String): UICommandBuilder
    fun set(selector: String, message: Message): UICommandBuilder
    fun set(selector: String, b: Boolean): UICommandBuilder
    fun set(selector: String, n: Float): UICommandBuilder
    fun set(selector: String, n: Int): UICommandBuilder
    fun set(selector: String, n: Double): UICommandBuilder
    fun setObject(selector: String, data: Any): UICommandBuilder
    fun <T> set(selector: String, data: Array<T>): UICommandBuilder
    fun <T> set(selector: String, data: List<T>): UICommandBuilder

    fun getCommands(): Array<CustomUICommand>
}
```

## UIEventBuilder API

```kotlin
class UIEventBuilder {
    fun addEventBinding(
        type: CustomUIEventBindingType,
        selector: String
    ): UIEventBuilder

    fun addEventBinding(
        type: CustomUIEventBindingType,
        selector: String,
        locksInterface: Boolean
    ): UIEventBuilder

    fun addEventBinding(
        type: CustomUIEventBindingType,
        selector: String,
        data: EventData
    ): UIEventBuilder

    fun addEventBinding(
        type: CustomUIEventBindingType,
        selector: String,
        data: EventData?,
        locksInterface: Boolean
    ): UIEventBuilder

    fun getEvents(): Array<CustomUIEventBinding>
}
```

## Opening Custom Pages

### From Command Context

```kotlin
override fun executeSync(context: CommandContext) {
    // Check if sender is a player
    if (!context.isPlayer()) {
        context.sendMessage(Message.raw("Only players can use this command").color("red"))
        return
    }

    // Get player entity reference
    val entityRef = context.senderAsPlayerRef()

    // Get the player entity from the store
    entityRef.accessor { entity ->
        // Get PageManager component
        val pageManager = entity.get(PageManager.getComponentType())

        // Create your custom page
        val customPage = MyCustomPage(entity.get(PlayerRef.getComponentType()))

        // Open the page
        pageManager.openCustomPage(entityRef, entity.store, customPage)
    }
}
```

## Example: Simple Guild Menu (Using BasicCustomUIPage)

```kotlin
package net.lumalyte.lg.infrastructure.hytale.ui

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef

class SimpleGuildMenu(playerRef: PlayerRef) :
    BasicCustomUIPage(playerRef, CustomPageLifetime.CanDismiss) {

    override fun build(commands: UICommandBuilder) {
        commands
            // Build your UI using document paths
            .append("root", "hytale:ui/container")
            .set("root.title", Message.raw("Guild Menu").color("gold"))
            .append("root", "hytale:ui/button")
            .set("root.button.text", Message.raw("Create Guild"))
    }
}
```

## Example: Complex Interactive Page

```kotlin
class GuildControlPanel(playerRef: PlayerRef) :
    InteractiveCustomUIPage<MyEventData>(
        playerRef,
        CustomPageLifetime.CanDismiss,
        MyEventData.CODEC
    ) {

    override fun build(
        entityRef: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        // Build UI
        commands
            .append("root", "hytale:ui/container")
            .append("root", "hytale:ui/button")
            .set("root.myButton", "myButtonId")

        // Register events
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#myButtonId",
            MyEventData("button_clicked"),
            false  // locksInterface
        )
    }

    override fun handleDataEvent(
        entityRef: Ref<EntityStore>,
        store: Store<EntityStore>,
        eventData: MyEventData
    ) {
        // Handle the event
        when (eventData.action) {
            "button_clicked" -> {
                // Do something
                playerRef.sendMessage(Message.raw("Button clicked!"))
            }
        }
    }
}
```

## Key Differences from Initial Assumptions

1. ✅ **CustomUIPage exists** - But also has BasicCustomUIPage for simpler cases
2. ✅ **CustomPageLifetime exists** - With 3 options, not just UNTIL_DISMISSED
3. ❌ **No onClick() helper** - Must use `addEventBinding(CustomUIEventBindingType.Activating, ...)`
4. ❌ **No playerRef.openCustomPage()** - Must use PageManager component
5. ✅ **Document paths** - UI uses document paths like "hytale:ui/button", not direct construction
6. ❌ **No CommandContext.asPlayer()** - Use `context.senderAsPlayerRef()` instead

## Next Steps

1. Find out what document paths are available (hytale:ui/button, hytale:ui/container, etc.)
2. Understand the UI document structure (how to reference elements)
3. Implement GuildControlPanel using BasicCustomUIPage
4. Test opening the page from GuildMenuCommand
