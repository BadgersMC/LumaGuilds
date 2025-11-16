# Interaction Layer Documentation

The interaction layer (`net.lumalyte.lg.interaction`) handles all player-facing interactions including commands, GUI menus, and event listeners. This is the presentation layer of the hexagonal architecture.

## Interaction Layer Overview

```mermaid
graph TB
    subgraph "Players"
        Player[Minecraft Player]
    end

    subgraph "Interaction Layer"
        Commands[Commands<br/>ACF Framework]
        Menus[GUI Menus<br/>Inventory-based]
        InteractionListeners[Interaction Listeners<br/>Bell Clicks, Tool Usage]
    end

    subgraph "Application Layer"
        Actions[Actions/Use Cases]
        Results[Result Types]
    end

    subgraph "Infrastructure Layer"
        ProtectionListeners[Protection Listeners<br/>Block Break/Place]
    end

    Player -->|/claim command| Commands
    Player -->|clicks bell| InteractionListeners
    Player -->|opens GUI| Menus

    Commands --> Actions
    Menus --> Actions
    InteractionListeners --> Actions

    Actions --> Results

    Results -.feedback.-> Commands
    Results -.update GUI.-> Menus
    Results -.messages.-> InteractionListeners

    Player -.protected by.-> ProtectionListeners

    style Commands fill:#FFB6C1
    style Menus fill:#DDA0DD
    style InteractionListeners fill:#F0E68C
    style Actions fill:#87CEEB
```

## Package Structure

```
net.lumalyte.lg.interaction
├── commands/          # Player commands (ACF)
│   ├── ClaimCommand.kt
│   ├── GuildCommand.kt
│   ├── InfoCommand.kt
│   └── ...
├── menus/             # GUI implementations
│   ├── Menu.kt
│   ├── MenuFactory.kt
│   ├── MenuNavigator.kt
│   ├── bedrock/       # Bedrock Edition forms
│   ├── common/        # Java Edition GUIs
│   ├── guild/         # Guild-specific menus
│   ├── management/    # Claim management GUIs
│   └── misc/          # Utility menus
└── listeners/         # Player interaction listeners
    ├── BellInteractionListener.kt
    ├── ClaimToolListener.kt
    └── ...
```

## Commands

Commands use the ACF (Annotation Command Framework) for elegant command handling.

### Command Architecture

```mermaid
sequenceDiagram
    participant Player
    participant ACF as ACF Framework
    participant Command as ClaimCommand
    participant Action as Application Action
    participant Result as Result Type

    Player->>ACF: /claim
    ACF->>Command: onClaim(player)
    Command->>Action: givePlayerClaimTool.execute(playerId)
    Action-->>Result: GivePlayerClaimToolResult
    Result-->>Command: Success/AlreadyHasTool/NotFound

    alt Success
        Command->>Player: "Received claim tool!"
    else AlreadyHasTool
        Command->>Player: "You already have the tool"
    else NotFound
        Command->>Player: "Error occurred"
    end
```

### ClaimCommand Example

```kotlin
package net.lumalyte.lg.interaction.commands

@CommandAlias("claim")
@CommandPermission("lumaguilds.command.claim")
class ClaimCommand : BaseCommand(), KoinComponent {
    private val localizationProvider: LocalizationProvider by inject()
    private val givePlayerClaimTool: GivePlayerClaimTool by inject()
    private val getPartitionByPosition: GetPartitionByPosition by inject()
    private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride by inject()
    private val getClaimDetails: GetClaimDetails by inject()

    /**
     * Gives the player a claim tool.
     * Usage: /claim
     */
    @Default
    @Syntax("claim")
    fun onClaim(player: Player) {
        when (givePlayerClaimTool.execute(player.uniqueId)) {
            GivePlayerClaimToolResult.PlayerAlreadyHasTool ->
                player.sendMessage(localizationProvider.get(
                    player.uniqueId,
                    LocalizationKeys.COMMAND_CLAIM_ALREADY_HAVE_TOOL
                ))

            GivePlayerClaimToolResult.Success ->
                player.sendMessage(localizationProvider.get(
                    player.uniqueId,
                    LocalizationKeys.COMMAND_CLAIM_SUCCESS
                ))

            GivePlayerClaimToolResult.PlayerNotFound ->
                player.sendMessage(localizationProvider.get(
                    player.uniqueId,
                    LocalizationKeys.GENERAL_ERROR
                ))
        }
    }

    /**
     * Shows info about the claim at player's position.
     * Usage: /claim info
     */
    @Subcommand("info")
    @CommandPermission("lumaguilds.command.claim.info")
    fun onClaimInfo(player: Player) {
        val partition = getPartitionAtPlayer(player) ?: return
        val claim = getClaimDetails.execute(partition.claimId) ?: run {
            player.sendMessage("Claim not found!")
            return
        }

        player.sendMessage("""
            §6=== Claim Info ===
            §eName: §f${claim.name}
            §eDescription: §f${claim.description}
            §eOwner: §f${Bukkit.getOfflinePlayer(claim.playerId).name}
            §eSize: §f${getClaimBlockCount.execute(claim.id)} blocks
        """.trimIndent())
    }

    /**
     * Renames the claim at player's position.
     * Usage: /claim rename <new name>
     */
    @Subcommand("rename")
    @CommandPermission("lumaguilds.command.claim.rename")
    @Syntax("<new name>")
    fun onRename(player: Player, @Single newName: String) {
        val partition = getPartitionAtPlayer(player) ?: return

        if (!isPlayerHasClaimPermission(player, partition)) return

        when (val result = updateClaimName.execute(partition.claimId, newName)) {
            is UpdateClaimNameResult.Success ->
                player.sendMessage("§aClaim renamed to '${newName}'!")

            UpdateClaimNameResult.ClaimNotFound ->
                player.sendMessage("§cClaim not found!")

            UpdateClaimNameResult.NameAlreadyExists ->
                player.sendMessage("§cYou already have a claim with that name!")

            UpdateClaimNameResult.InvalidName ->
                player.sendMessage("§cInvalid name! Must be 1-50 characters.")
        }
    }

    // Helper methods
    private fun getPartitionAtPlayer(player: Player): Partition? {
        val partition = getPartitionByPosition.execute(
            player.location.toPosition3D(),
            player.world.uid
        )
        if (partition == null) {
            player.sendMessage(localizationProvider.get(
                player.uniqueId,
                LocalizationKeys.COMMAND_COMMON_UNKNOWN_PARTITION
            ))
        }
        return partition
    }

    private fun isPlayerHasClaimPermission(
        player: Player,
        partition: Partition
    ): Boolean {
        // Check admin override
        val overrideResult = doesPlayerHaveClaimOverride.execute(player.uniqueId)
        when (overrideResult) {
            is DoesPlayerHaveClaimOverrideResult.Success ->
                if (overrideResult.hasOverride) return true
            is DoesPlayerHaveClaimOverrideResult.StorageError ->
                return false
        }

        // Check ownership
        val claim = getClaimDetails.execute(partition.claimId) ?: return false
        if (player.uniqueId != claim.playerId) {
            player.sendMessage(localizationProvider.get(
                player.uniqueId,
                LocalizationKeys.COMMAND_COMMON_NO_CLAIM_PERMISSION
            ))
            return false
        }
        return true
    }
}
```

### Command Flow Diagram

```mermaid
flowchart TD
    Start([Player types /claim rename NewName]) --> ParseCommand[ACF Parses Command]
    ParseCommand --> CheckPerm{Has Permission?}

    CheckPerm -->|No| DenyPerm[Send Permission Error]
    CheckPerm -->|Yes| GetPartition[Get Partition at Position]

    GetPartition --> PartitionExists{Partition Exists?}
    PartitionExists -->|No| NoPartition[Send 'No claim here']
    PartitionExists -->|Yes| CheckOwnership{Is Owner or Override?}

    CheckOwnership -->|No| DenyOwner[Send 'Not your claim']
    CheckOwnership -->|Yes| ExecuteAction[Execute UpdateClaimName]

    ExecuteAction --> ProcessResult{Result Type?}

    ProcessResult -->|Success| SendSuccess[Send Success Message]
    ProcessResult -->|NameExists| SendExists[Send 'Name taken']
    ProcessResult -->|InvalidName| SendInvalid[Send 'Invalid name']
    ProcessResult -->|NotFound| SendNotFound[Send 'Claim not found']

    DenyPerm --> End([End])
    NoPartition --> End
    DenyOwner --> End
    SendSuccess --> End
    SendExists --> End
    SendInvalid --> End
    SendNotFound --> End

    style SendSuccess fill:#d4edda
    style DenyPerm fill:#f8d7da
    style DenyOwner fill:#f8d7da
    style NoPartition fill:#f8d7da
```

### Common Command Patterns

```mermaid
graph LR
    subgraph "Claim Commands"
        ClaimCmd[ClaimCommand]
        InfoCmd[InfoCommand]
        RenameCmd[RenameCommand]
        DescCmd[DescriptionCommand]
    end

    subgraph "Guild Commands"
        GuildCmd[GuildCommand]
        GuildCreate[GuildCreateCommand]
        GuildDisband[GuildDisbandCommand]
    end

    subgraph "Admin Commands"
        OverrideCmd[ClaimOverrideCommand]
        LumaGuildsCmd[LumaGuildsCommand]
    end

    subgraph "Helper Methods"
        GetPartition[getPartitionAtPlayer]
        CheckPerm[isPlayerHasClaimPermission]
        Localize[localizationProvider]
    end

    ClaimCmd --> GetPartition
    InfoCmd --> GetPartition
    RenameCmd --> GetPartition
    RenameCmd --> CheckPerm

    ClaimCmd --> Localize
    InfoCmd --> Localize
    GuildCmd --> Localize

    style ClaimCmd fill:#FFB6C1
    style GuildCmd fill:#DDA0DD
    style OverrideCmd fill:#F0E68C
```

## GUI Menus

Interactive inventory-based menus for claim and guild management.

### Menu Architecture

```mermaid
graph TB
    subgraph "Menu System"
        MenuInterface[Menu Interface]
        BaseMenu[BaseMenu Abstract]
        MenuFactory[MenuFactory<br/>Creates Menus]
        MenuNavigator[MenuNavigator<br/>Handles Navigation]
    end

    subgraph "Java Edition Menus"
        ClaimManagement[ClaimManagementMenu]
        PermissionMenu[PermissionManagementMenu]
        PartitionMenu[PartitionListMenu]
        GuildMenu[GuildControlPanelMenu]
    end

    subgraph "Bedrock Edition Menus"
        BedrockBase[BaseBedrockMenu]
        BedrockGuild[BedrockGuildMenu]
        BedrockClaim[BedrockClaimMenu]
    end

    MenuInterface -.implemented by.-> BaseMenu
    BaseMenu -.extended by.-> ClaimManagement
    BaseMenu -.extended by.-> PermissionMenu
    BaseMenu -.extended by.-> PartitionMenu
    BaseMenu -.extended by.-> GuildMenu

    MenuInterface -.implemented by.-> BedrockBase
    BedrockBase -.extended by.-> BedrockGuild
    BedrockBase -.extended by.-> BedrockClaim

    MenuFactory -.creates.-> ClaimManagement
    MenuFactory -.creates.-> PermissionMenu
    MenuFactory -.creates.-> BedrockGuild

    MenuNavigator -.manages.-> ClaimManagement
    MenuNavigator -.manages.-> GuildMenu

    style MenuFactory fill:#FFD700
    style ClaimManagement fill:#FFB6C1
    style GuildMenu fill:#DDA0DD
    style BedrockGuild fill:#87CEEB
```

### Menu Interface

```kotlin
package net.lumalyte.lg.interaction.menus

interface Menu {
    /**
     * Opens the menu for the specified player.
     */
    fun open(player: Player)

    /**
     * Refreshes the menu content for the player.
     */
    fun refresh(player: Player)

    /**
     * Closes the menu for the player.
     */
    fun close(player: Player)

    /**
     * Handles click events within the menu.
     */
    fun handleClick(player: Player, slot: Int, clickType: ClickType)
}
```

### ClaimManagementMenu Example

```kotlin
package net.lumalyte.lg.interaction.menus.management

class ClaimManagementMenu(
    private val claim: Claim,
    private val getClaimBlockCount: GetClaimBlockCount,
    private val getClaimPermissions: GetClaimPermissions,
    private val menuNavigator: MenuNavigator
) : Menu {

    override fun open(player: Player) {
        val inventory = Bukkit.createInventory(
            null,
            54,
            "§6Manage: ${claim.name}"
        )

        // Claim info display
        inventory.setItem(4, ItemStack(Material.valueOf(claim.icon)).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§6${claim.name}")
                lore = listOf(
                    "§7${claim.description}",
                    "",
                    "§eBlocks: §f${getClaimBlockCount.execute(claim.id)}",
                    "§eCreated: §f${claim.creationTime}"
                )
            }
        })

        // Manage permissions button
        inventory.setItem(20, ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§aManage Permissions")
                lore = listOf(
                    "§7Grant or revoke permissions",
                    "§7to other players",
                    "",
                    "§eClick to open"
                )
            }
        })

        // Manage partitions button
        inventory.setItem(22, ItemStack(Material.MAP).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§aManage Partitions")
                lore = listOf(
                    "§7View and modify claim areas",
                    "",
                    "§eClick to open"
                )
            }
        })

        // Claim flags button
        inventory.setItem(24, ItemStack(Material.BANNER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§aManage Flags")
                lore = listOf(
                    "§7Toggle claim protection flags",
                    "",
                    "§eClick to open"
                )
            }
        })

        // Transfer claim button
        inventory.setItem(40, ItemStack(Material.ENDER_PEARL).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§6Transfer Claim")
                lore = listOf(
                    "§7Transfer ownership to another player",
                    "",
                    "§eClick to transfer"
                )
            }
        })

        // Delete claim button
        inventory.setItem(49, ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§cDelete Claim")
                lore = listOf(
                    "§7Permanently remove this claim",
                    "§c§lWarning: This cannot be undone!",
                    "",
                    "§eClick to delete"
                )
            }
        })

        player.openInventory(inventory)
    }

    override fun handleClick(player: Player, slot: Int, clickType: ClickType) {
        when (slot) {
            20 -> {
                // Open permission management menu
                menuNavigator.navigateTo(
                    player,
                    PermissionManagementMenu(claim, /* ... */)
                )
            }
            22 -> {
                // Open partition list menu
                menuNavigator.navigateTo(
                    player,
                    PartitionListMenu(claim, /* ... */)
                )
            }
            24 -> {
                // Open flags menu
                menuNavigator.navigateTo(
                    player,
                    FlagManagementMenu(claim, /* ... */)
                )
            }
            40 -> {
                // Open transfer menu
                menuNavigator.navigateTo(
                    player,
                    TransferClaimMenu(claim, /* ... */)
                )
            }
            49 -> {
                // Open delete confirmation
                menuNavigator.navigateTo(
                    player,
                    ConfirmDeleteMenu(claim, /* ... */)
                )
            }
        }
    }

    override fun refresh(player: Player) {
        open(player)  // Recreate inventory with updated data
    }

    override fun close(player: Player) {
        player.closeInventory()
    }
}
```

### Menu Navigation Flow

```mermaid
sequenceDiagram
    participant Player
    participant Bell as Bell Block
    participant Listener as BellInteractionListener
    participant MenuFactory
    participant ClaimMenu as ClaimManagementMenu
    participant PermMenu as PermissionMenu
    participant Action as GrantPermissionAction

    Player->>Bell: Shift + Right Click
    Bell->>Listener: PlayerInteractEvent
    Listener->>MenuFactory: createClaimMenu(claim)
    MenuFactory-->>ClaimMenu: new ClaimManagementMenu(claim)
    ClaimMenu->>Player: open(inventory)

    Player->>ClaimMenu: Click 'Manage Permissions'
    ClaimMenu->>PermMenu: open(player)
    PermMenu->>Player: show permission GUI

    Player->>PermMenu: Click player + permission
    PermMenu->>Action: grantPermission.execute(...)
    Action-->>PermMenu: Result.Success
    PermMenu->>PermMenu: refresh()
    PermMenu->>Player: updated GUI

    Player->>PermMenu: Click 'Back'
    PermMenu->>ClaimMenu: navigateBack()
    ClaimMenu->>Player: open(inventory)
```

### Menu Types Diagram

```mermaid
graph TB
    subgraph "Claim Menus"
        ClaimList[ClaimListMenu<br/>Player's Claims]
        ClaimMgmt[ClaimManagementMenu<br/>Main Control Panel]
        PermMgmt[PermissionManagementMenu<br/>Trust Players]
        PartitionList[PartitionListMenu<br/>View Areas]
        FlagMgmt[FlagManagementMenu<br/>Toggle Protection]
    end

    subgraph "Guild Menus"
        GuildPanel[GuildControlPanelMenu<br/>Main Guild UI]
        GuildMembers[MemberManagementMenu<br/>Manage Members]
        GuildBank[GuildBankMenu<br/>Bank Operations]
        GuildWars[GuildWarsMenu<br/>War Management]
    end

    subgraph "Misc Menus"
        Confirm[ConfirmationMenu<br/>Yes/No Dialogs]
        PlayerSelect[PlayerSelectionMenu<br/>Choose Player]
    end

    ClaimList -->|select claim| ClaimMgmt
    ClaimMgmt -->|permissions| PermMgmt
    ClaimMgmt -->|partitions| PartitionList
    ClaimMgmt -->|flags| FlagMgmt

    GuildPanel -->|members| GuildMembers
    GuildPanel -->|bank| GuildBank
    GuildPanel -->|wars| GuildWars

    ClaimMgmt -->|delete| Confirm
    PermMgmt -->|select player| PlayerSelect

    style ClaimMgmt fill:#FFB6C1
    style GuildPanel fill:#DDA0DD
    style Confirm fill:#F0E68C
```

## Interaction Listeners

Handle player interactions with claim tools and bells.

### BellInteractionListener

```kotlin
package net.lumalyte.lg.infrastructure.listeners

class BellInteractionListener(
    private val getClaimAtPosition: GetClaimAtPosition,
    private val menuFactory: MenuFactory,
    private val registerClaimMenuOpening: RegisterClaimMenuOpening
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractBell(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.clickedBlock?.type != Material.BELL) return
        if (!event.player.isSneaking) return

        event.isCancelled = true

        val player = event.player
        val bellPosition = event.clickedBlock!!.location.toPosition3D()

        // Get claim at bell position
        val result = getClaimAtPosition.execute(
            bellPosition,
            player.world.uid
        )

        when (result) {
            is GetClaimAtPositionResult.Success -> {
                // Register menu opening to prevent protection
                registerClaimMenuOpening.execute(player.uniqueId)

                // Open claim management menu
                val menu = menuFactory.createClaimManagementMenu(result.claim)
                menu.open(player)
            }
            is GetClaimAtPositionResult.NotFound -> {
                // Open claim creation menu
                val menu = menuFactory.createClaimCreationMenu(bellPosition)
                menu.open(player)
            }
        }
    }
}
```

### Interaction Flow Diagram

```mermaid
flowchart TD
    Start([Player Shift+Right-Click Bell]) --> CheckAction{Action Type?}
    CheckAction -->|Not Right Click| Ignore1[Ignore Event]
    CheckAction -->|Right Click| CheckBlock{Block Type?}

    CheckBlock -->|Not Bell| Ignore2[Ignore Event]
    CheckBlock -->|Bell| CheckSneak{Is Sneaking?}

    CheckSneak -->|No| Ignore3[Ignore Event]
    CheckSneak -->|Yes| CancelEvent[Cancel Event]

    CancelEvent --> GetClaim[Get Claim at Position]
    GetClaim --> ClaimExists{Claim Exists?}

    ClaimExists -->|Yes| CheckOwner{Is Owner?}
    ClaimExists -->|No| OpenCreate[Open Claim Creation Menu]

    CheckOwner -->|Yes| OpenManage[Open Claim Management Menu]
    CheckOwner -->|No| ShowInfo[Show Claim Info]

    OpenCreate --> RegisterMenu[Register Menu Opening]
    OpenManage --> RegisterMenu

    RegisterMenu --> End([End])
    ShowInfo --> End
    Ignore1 --> End
    Ignore2 --> End
    Ignore3 --> End

    style OpenCreate fill:#d4edda
    style OpenManage fill:#d4edda
    style Ignore1 fill:#f8d7da
    style Ignore2 fill:#f8d7da
    style Ignore3 fill:#f8d7da
```

## Localization

Multi-language support using localization keys.

```kotlin
package net.lumalyte.lg.application.utilities

class LocalizationProvider(
    private val playerStateRepository: PlayerStateRepository
) {
    private val translations = mutableMapOf<String, MutableMap<String, String>>()

    init {
        loadTranslations()
    }

    fun get(playerId: UUID, key: LocalizationKeys): String {
        val playerLang = playerStateRepository.getLanguage(playerId) ?: "en_US"
        return translations[playerLang]?.get(key.key) ?: key.default
    }

    private fun loadTranslations() {
        // Load from files
        translations["en_US"] = mutableMapOf(
            "command.claim.success" to "§aYou received the claim tool!",
            "command.claim.already_have_tool" to "§cYou already have the claim tool!",
            "command.common.no_permission" to "§cYou don't have permission to do that!"
        )
    }
}

enum class LocalizationKeys(val key: String, val default: String) {
    COMMAND_CLAIM_SUCCESS("command.claim.success", "You received the claim tool!"),
    COMMAND_CLAIM_ALREADY_HAVE_TOOL("command.claim.already_have_tool", "You already have the claim tool!"),
    COMMAND_COMMON_NO_PERMISSION("command.common.no_permission", "You don't have permission!"),
    // ... more keys
}
```

## Best Practices

### 1. Always Use Result Types
Never throw exceptions for business logic failures in commands:

```kotlin
// Good: Handle all result cases
when (val result = createClaim.execute(/* ... */)) {
    is CreateClaimResult.Success -> player.sendMessage("Success!")
    CreateClaimResult.LimitExceeded -> player.sendMessage("Limit exceeded!")
    // ... handle all cases
}

// Bad: Let exceptions bubble up
try {
    val claim = createClaimUnsafe(/* ... */)
    player.sendMessage("Success!")
} catch (e: Exception) {
    player.sendMessage("Error!")
}
```

### 2. Use Localization
Always use localization keys for messages:

```kotlin
// Good: Localized
player.sendMessage(localizationProvider.get(
    player.uniqueId,
    LocalizationKeys.COMMAND_CLAIM_SUCCESS
))

// Bad: Hardcoded
player.sendMessage("§aYou received the claim tool!")
```

### 3. Validate Early
Check permissions and preconditions before opening menus:

```kotlin
// Good: Check first
if (!player.hasPermission("lumaguilds.command.claim")) {
    player.sendMessage("No permission!")
    return
}
val partition = getPartitionAtPlayer(player) ?: return
openMenu(player, partition)

// Bad: Check after expensive operations
val partition = getPartitionAtPlayer(player) ?: return
loadAllData()
if (!player.hasPermission("lumaguilds.command.claim")) return
```

### 4. Cancel Events Appropriately
Only cancel events when you handle them:

```kotlin
@EventHandler
fun onInteract(event: PlayerInteractEvent) {
    if (event.clickedBlock?.type != Material.BELL) return  // Don't cancel
    if (!event.player.isSneaking) return  // Don't cancel

    event.isCancelled = true  // Now cancel - we're handling it
    // Handle bell interaction...
}
```

## Related Documentation

- [Application Layer](./application.md) - Actions used by commands
- [Infrastructure Layer](./infrastructure.md) - Event listeners and adapters
- [Getting Started](./getting-started.md) - Creating custom commands
- [Master Diagram](./master-diagram.md) - Full system flow
