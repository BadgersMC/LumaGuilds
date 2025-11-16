# Master System Diagram

This document provides comprehensive visual maps of the entire LumaGuilds plugin, showing how all components interact across all layers.

## Complete System Architecture

```mermaid
graph TB
    subgraph "Player Interface"
        Player[Minecraft Player]
    end

    subgraph "Interaction Layer (Presentation)"
        Commands[Commands<br/>ClaimCommand<br/>GuildCommand<br/>AdminCommands]
        Menus[GUI Menus<br/>ClaimManagement<br/>GuildPanel<br/>Permissions]
        InteractionListeners[Interaction Listeners<br/>BellClickListener<br/>ClaimToolListener]
    end

    subgraph "Application Layer (Use Cases)"
        ClaimActions[Claim Actions<br/>CreateClaim<br/>UpdateClaimName<br/>GrantPermission]
        GuildActions[Guild Actions<br/>CreateGuild<br/>AddMember<br/>DeclareWar]
        PlayerActions[Player Actions<br/>GiveTool<br/>Visualize<br/>ToggleOverride]
        Results[Result Types<br/>Sealed Classes]
    end

    subgraph "Domain Layer (Core Business Logic)"
        ClaimEntities[Claim Entities<br/>Claim<br/>Partition]
        GuildEntities[Guild Entities<br/>Guild<br/>Member<br/>War]
        ValueObjects[Value Objects<br/>Position3D<br/>Area]
        DomainEvents[Domain Events]
    end

    subgraph "Infrastructure Layer (External Integrations)"
        Repositories[SQLite Repositories<br/>ClaimRepo<br/>GuildRepo<br/>PartitionRepo]
        BukkitAdapters[Bukkit Adapters<br/>LocationAdapter<br/>ItemStackAdapter]
        ProtectionListeners[Protection Listeners<br/>BlockBreak<br/>BlockPlace<br/>PvP]
        Services[Services<br/>ChunkCache<br/>Scheduler<br/>Localization]
    end

    subgraph "External Systems"
        Database[(SQLite<br/>Database)]
        BukkitAPI[Bukkit/Paper API]
        Vault[Vault Plugin]
        PlaceholderAPI[PlaceholderAPI]
        WorldGuard[WorldGuard]
    end

    %% Player to Interaction
    Player -->|/claim command| Commands
    Player -->|shift+click bell| InteractionListeners
    Player -->|opens GUI| Menus
    Player -.protected by.-> ProtectionListeners

    %% Interaction to Application
    Commands --> ClaimActions
    Commands --> GuildActions
    Commands --> PlayerActions
    Menus --> ClaimActions
    Menus --> GuildActions
    InteractionListeners --> ClaimActions
    InteractionListeners --> PlayerActions

    %% Application to Domain
    ClaimActions --> ClaimEntities
    ClaimActions --> ValueObjects
    GuildActions --> GuildEntities
    GuildActions --> ValueObjects
    PlayerActions --> ClaimEntities

    %% Application to Results
    ClaimActions --> Results
    GuildActions --> Results
    PlayerActions --> Results

    Results -.feedback.-> Commands
    Results -.update.-> Menus

    %% Domain Events
    ClaimEntities -.emits.-> DomainEvents
    GuildEntities -.emits.-> DomainEvents

    %% Application to Infrastructure
    ClaimActions --> Repositories
    GuildActions --> Repositories
    PlayerActions --> Services

    %% Infrastructure to External
    Repositories --> Database
    BukkitAdapters --> BukkitAPI
    ProtectionListeners --> BukkitAPI
    Services --> BukkitAPI

    %% External Integrations
    Services -.uses.-> Vault
    Services -.uses.-> PlaceholderAPI
    ProtectionListeners -.checks.-> WorldGuard

    style Player fill:#FF6B6B
    style Commands fill:#FFB6C1
    style Menus fill:#DDA0DD
    style ClaimActions fill:#87CEEB
    style GuildActions fill:#87CEEB
    style ClaimEntities fill:#90EE90
    style GuildEntities fill:#90EE90
    style ValueObjects fill:#FFD700
    style Repositories fill:#F0E68C
    style Database fill:#D3D3D3
```

## End-to-End: Creating a Claim

Complete flow from player command to database storage.

```mermaid
sequenceDiagram
    autonumber
    participant Player
    participant Bell as Bell Block
    participant Listener as BellInteractionListener
    participant Menu as ClaimCreationMenu
    participant Command as ClaimCommand
    participant Action as CreateClaimAction
    participant Domain as Claim Entity
    participant ClaimRepo as ClaimRepository
    participant PartitionRepo as PartitionRepository
    participant Cache as In-Memory Cache
    participant DB as SQLite Database
    participant Event as Bukkit Event Bus

    Player->>Bell: Shift + Right Click
    Bell->>Listener: PlayerInteractEvent
    Listener->>Listener: Check is Bell & Sneaking
    Listener->>Action: getClaimAtPosition.execute()
    Action-->>Listener: NotFound

    Listener->>Menu: open(ClaimCreationMenu)
    Menu->>Player: Display GUI

    Player->>Menu: Enter name "MyBase"
    Menu->>Menu: Validate input
    Menu->>Command: Execute create command

    Command->>Action: createClaim.execute(playerId, "MyBase", position, worldId)

    Action->>ClaimRepo: getByPlayer(playerId)
    ClaimRepo->>Cache: claims[playerId]
    Cache-->>ClaimRepo: List<Claim>
    ClaimRepo-->>Action: existingClaims (2)

    Action->>Action: Check limit (2 < 5) ✓
    Action->>Action: Validate name ✓
    Action->>ClaimRepo: getByName(playerId, "MyBase")
    ClaimRepo-->>Action: null (no duplicate)

    Action->>Action: Generate Area (15x15)
    Action->>Action: Check world border ✓

    Action->>Domain: new Claim(...)
    Domain->>Domain: Validate in init block
    Domain-->>Action: Claim instance

    Action->>Domain: new Partition(claimId, area)
    Domain-->>Action: Partition instance

    Action->>ClaimRepo: add(claim)
    ClaimRepo->>Cache: claims[claim.id] = claim
    ClaimRepo->>DB: INSERT INTO claims...
    DB-->>ClaimRepo: Success

    Action->>PartitionRepo: add(partition)
    PartitionRepo->>DB: INSERT INTO partitions...
    DB-->>PartitionRepo: Success

    Action->>Event: ClaimCreatedEvent(claim, player)
    Event-->>OtherPlugins: Event fired

    Action-->>Command: CreateClaimResult.Success(claim)
    Command->>Player: "§aClaim 'MyBase' created!"
    Command->>Menu: close()
    Menu-->>Player: Close GUI

    Player->>Player: Receives feedback
```

## End-to-End: Permission Check (Block Break)

Complete protection flow.

```mermaid
sequenceDiagram
    autonumber
    participant Player
    participant Block
    participant BukkitEvent as Bukkit Event System
    participant ProtectionListener as ClaimProtectionListener
    participant Action as IsPlayerActionAllowed
    participant GetClaim as GetClaimAtPosition
    participant ClaimRepo as ClaimRepository
    participant PermRepo as ClaimPermissionRepository
    participant PlayerAccessRepo as PlayerAccessRepository

    Player->>Block: Left click to break
    Block->>BukkitEvent: BlockBreakEvent
    BukkitEvent->>ProtectionListener: @EventHandler

    ProtectionListener->>Action: isPlayerActionAllowed.execute(playerId, position, worldId, BREAK)

    Action->>Action: Check if has override
    Action-->>Action: No override

    Action->>GetClaim: execute(position, worldId)
    GetClaim->>ClaimRepo: Find claim at position
    ClaimRepo-->>GetClaim: Claim found
    GetClaim-->>Action: Success(claim)

    Action->>Action: Check if owner
    Action-->>Action: Not owner

    Action->>PermRepo: getClaimWidePermissions(claimId)
    PermRepo-->>Action: [BUILD, INTERACT] (no BREAK)

    Action->>PlayerAccessRepo: getPlayerPermissions(claimId, playerId)
    PlayerAccessRepo-->>Action: [] (no permissions)

    Action-->>ProtectionListener: Denied(claim)

    ProtectionListener->>BukkitEvent: event.isCancelled = true
    ProtectionListener->>Player: "§cYou don't have permission to break blocks in ${claim.name}!"

    BukkitEvent-->>Block: Event cancelled
    Block-->>Player: Block not broken
```

## Guild System Complete Flow

```mermaid
graph TB
    subgraph "Guild Creation Flow"
        GC1[Player: /guild create Dragons]
        GC2[GuildCommand]
        GC3[CreateGuild Action]
        GC4[Guild Entity Created]
        GC5[GuildRepository.add]
        GC6[MemberRepository.add]
        GC7[Database INSERT]
        GC8[GuildCreatedEvent]
    end

    subgraph "Guild Member Flow"
        GM1[Member: /guild join Dragons]
        GM2[GuildCommand]
        GM3[JoinGuild Action]
        GM4[Check Invitation]
        GM5[Member Entity Created]
        GM6[MemberRepository.add]
        GM7[GuildMemberJoinedEvent]
    end

    subgraph "Guild War Flow"
        GW1[Leader: /guild declarewar Lions]
        GW2[GuildCommand]
        GW3[DeclareWar Action]
        GW4[Check Conditions]
        GW5[War Entity Created]
        GW6[WarRepository.add]
        GW7[WarDeclaredEvent]
        GW8[Server Broadcast]
    end

    GC1 --> GC2 --> GC3 --> GC4 --> GC5 --> GC6 --> GC7 --> GC8
    GM1 --> GM2 --> GM3 --> GM4 --> GM5 --> GM6 --> GM7
    GW1 --> GW2 --> GW3 --> GW4 --> GW5 --> GW6 --> GW7 --> GW8

    style GC4 fill:#90EE90
    style GM5 fill:#90EE90
    style GW5 fill:#90EE90
    style GC7 fill:#FFD700
    style GW8 fill:#FF6B6B
```

## Data Flow Architecture

```mermaid
graph LR
    subgraph "Input Sources"
        PlayerCmd[Player Commands]
        PlayerGUI[Player GUIs]
        BukkitEvents[Minecraft Events]
        ExternalAPI[External Plugin API]
    end

    subgraph "Processing Pipeline"
        Validation[Input Validation]
        Actions[Action Execution]
        BusinessLogic[Domain Business Logic]
        Persistence[Data Persistence]
    end

    subgraph "Data Storage"
        Cache[In-Memory Cache<br/>Hot Data]
        Database[(SQLite Database<br/>Cold Storage)]
    end

    subgraph "Output Channels"
        PlayerFeedback[Player Messages]
        GUIUpdates[GUI Refreshes]
        EventBus[Event Notifications]
        ExternalNotify[External Plugin Events]
    end

    PlayerCmd --> Validation
    PlayerGUI --> Validation
    BukkitEvents --> Validation
    ExternalAPI --> Validation

    Validation --> Actions
    Actions --> BusinessLogic
    BusinessLogic --> Persistence

    Persistence --> Cache
    Persistence --> Database

    Cache -.fast reads.-> Actions
    Database -.long-term storage.-> Cache

    Actions --> PlayerFeedback
    Actions --> GUIUpdates
    Actions --> EventBus
    EventBus --> ExternalNotify

    style Cache fill:#FFD700
    style Database fill:#D3D3D3
    style BusinessLogic fill:#90EE90
    style Actions fill:#87CEEB
```

## Component Dependency Graph

```mermaid
graph TB
    subgraph "Layer 1: Domain (Zero Dependencies)"
        D1[Claim]
        D2[Guild]
        D3[Partition]
        D4[Position3D]
        D5[Area]
    end

    subgraph "Layer 2: Application (Depends on Domain)"
        A1[CreateClaim]
        A2[GrantPermission]
        A3[CreateGuild]
        A4[DeclareWar]
        A5[ClaimRepository Interface]
        A6[GuildRepository Interface]
    end

    subgraph "Layer 3: Infrastructure (Implements Application)"
        I1[ClaimRepositorySQLite]
        I2[GuildRepositorySQLite]
        I3[BukkitLocationAdapter]
        I4[ClaimProtectionListener]
        I5[ChunkCacheService]
    end

    subgraph "Layer 4: Interaction (Uses Application)"
        UI1[ClaimCommand]
        UI2[GuildCommand]
        UI3[ClaimManagementMenu]
        UI4[BellInteractionListener]
    end

    %% Application depends on Domain
    A1 --> D1
    A1 --> D3
    A1 --> D4
    A2 --> D1
    A3 --> D2
    A4 --> D2

    %% Infrastructure implements Application
    I1 -.implements.-> A5
    I2 -.implements.-> A6
    I4 --> A1
    I4 --> A2

    %% Interaction uses Application
    UI1 --> A1
    UI1 --> A2
    UI2 --> A3
    UI2 --> A4
    UI3 --> A1
    UI3 --> A2
    UI4 --> A1

    style D1 fill:#90EE90
    style D2 fill:#90EE90
    style A1 fill:#87CEEB
    style A3 fill:#87CEEB
    style I1 fill:#FFD700
    style UI1 fill:#FFB6C1
```

## Claim Permission System Map

```mermaid
graph TB
    Start([Player attempts action]) --> HasOverride{Has Admin Override?}

    HasOverride -->|Yes| Allow1[✓ Allow Action]
    HasOverride -->|No| InClaim{Is in Claim?}

    InClaim -->|No| Allow2[✓ Allow Action]
    InClaim -->|Yes| IsOwner{Is Claim Owner?}

    IsOwner -->|Yes| Allow3[✓ Allow Action]
    IsOwner -->|No| CheckGuild{Is Guild Claim?}

    CheckGuild -->|Yes| InGuild{Is in Same Guild?}
    CheckGuild -->|No| CheckClaimWide

    InGuild -->|Yes| Allow4[✓ Allow Action]
    InGuild -->|No| CheckClaimWide

    CheckClaimWide{Has Claim-Wide Permission?} -->|Yes| Allow5[✓ Allow Action]
    CheckClaimWide -->|No| CheckPlayer{Has Player Permission?}

    CheckPlayer -->|Yes| Allow6[✓ Allow Action]
    CheckPlayer -->|No| Deny[✗ Deny Action]

    Allow1 --> End([Action Proceeds])
    Allow2 --> End
    Allow3 --> End
    Allow4 --> End
    Allow5 --> End
    Allow6 --> End
    Deny --> Cancel([Cancel Event & Notify Player])

    style Allow1 fill:#d4edda
    style Allow2 fill:#d4edda
    style Allow3 fill:#d4edda
    style Allow4 fill:#d4edda
    style Allow5 fill:#d4edda
    style Allow6 fill:#d4edda
    style Deny fill:#f8d7da
    style Cancel fill:#f8d7da
```

## Database Schema Relationships

```mermaid
erDiagram
    CLAIMS ||--o{ PARTITIONS : contains
    CLAIMS ||--o{ CLAIM_PERMISSIONS : has
    CLAIMS ||--o{ PLAYER_ACCESS : grants
    CLAIMS ||--o{ CLAIM_FLAGS : has
    CLAIMS }o--o| GUILDS : "owned by"

    GUILDS ||--o{ MEMBERS : has
    GUILDS ||--o{ WARS_ATTACKER : "attacks in"
    GUILDS ||--o{ WARS_DEFENDER : "defends in"
    GUILDS ||--o{ GUILD_HOMES : has
    GUILDS ||--o{ AUDIT_RECORDS : tracks

    PLAYERS ||--o{ CLAIMS : owns
    PLAYERS ||--o{ MEMBERS : "member of"
    PLAYERS ||--o{ PLAYER_STATE : has
    PLAYERS ||--o{ PLAYER_ACCESS : "has access via"
    PLAYERS ||--o{ PARTY : leads

    PARTY ||--o{ PARTY_MEMBERS : has
    PARTY }o--o| GUILDS : "optionally guild-only"

    WARS }o--|| GUILDS : attacker
    WARS }o--|| GUILDS : defender
    WARS ||--o{ KILLS : tracks

    CLAIMS {
        uuid id PK
        uuid world_id
        uuid owner_id FK
        uuid team_id FK
        bigint creation_time
        varchar name
        varchar description
        int position_x
        int position_y
        int position_z
        varchar icon
    }

    PARTITIONS {
        uuid id PK
        uuid claim_id FK
        int min_x
        int min_z
        int max_x
        int max_z
    }

    GUILDS {
        uuid id PK
        varchar name
        varchar banner
        varchar emoji
        varchar tag
        int level
        int bank_balance
        varchar mode
        bigint created_at
    }

    MEMBERS {
        uuid guild_id FK
        uuid player_id FK
        bigint joined_at
        int contributed_money
    }

    WARS {
        uuid id PK
        uuid attacker_guild_id FK
        uuid defender_guild_id FK
        bigint declared_at
        int wager
        int kills_to_win
    }
```

## Plugin Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Loading: Server Start

    Loading --> InitializingDB: Load Config
    InitializingDB --> MigratingSchema: Connect to SQLite
    MigratingSchema --> InitializingDI: Run Migrations

    InitializingDI --> RegisteringRepos: Setup Koin Modules
    RegisteringRepos --> RegisteringActions: Wire Repositories
    RegisteringActions --> RegisteringCommands: Wire Actions
    RegisteringCommands --> RegisteringListeners: Wire Commands
    RegisteringListeners --> PreloadingData: Wire Event Listeners

    PreloadingData --> Ready: Load Claims into Cache

    Ready --> Processing: Handle Events
    Processing --> Processing: Normal Operation

    Processing --> Saving: Server Stop Signal
    Saving --> DisablingListeners: Flush Cache to DB
    DisablingListeners --> ClosingDB: Unregister Events
    ClosingDB --> [*]: Shutdown Complete

    Ready --> Reloading: /lumaguilds reload
    Reloading --> InitializingDB: Clear State
```

## Cache Strategy

```mermaid
graph TB
    subgraph "Request Flow"
        Request[Action Request]
    end

    subgraph "Cache Layers"
        L1[L1: In-Memory Map<br/>Claims, Guilds]
        L2[L2: Chunk Cache<br/>ClaimId by Chunk]
        L3[(L3: SQLite Database<br/>Persistent Storage)]
    end

    subgraph "Cache Operations"
        Hit[Cache Hit<br/>Return Immediately]
        Miss[Cache Miss<br/>Query Database]
        Update[Write-Through<br/>Update Both]
        Invalidate[Invalidate<br/>Clear Stale Data]
    end

    Request --> L1
    L1 -->|Found| Hit
    L1 -->|Not Found| L2
    L2 -->|Found| Hit
    L2 -->|Not Found| Miss

    Miss --> L3
    L3 --> L1
    L3 --> L2

    Update --> L1
    Update --> L3

    Invalidate --> L1
    Invalidate --> L2

    style Hit fill:#d4edda
    style Miss fill:#fff3cd
    style L1 fill:#FFD700
    style L3 fill:#D3D3D3
```

## Complete Feature Map

```mermaid
mindmap
  root((LumaGuilds))
    Claims
      Creation
        Bell Placement
        Name & Icon
        Initial Area
      Management
        Permissions
          Player-Specific
          Claim-Wide
        Partitions
          Create
          Resize
          Remove
        Flags
          PvP
          Mob Spawning
          Explosions
        Transfer
          Request
          Accept
      Protection
        Block Break/Place
        Entity Damage
        Container Access
        Button/Lever Use
    Guilds
      Core
        Creation
        Disbanding
        Leveling
      Members
        Invite System
        Rank Management
        Permissions
        Contribution Tracking
      Bank
        Deposits
        Withdrawals
        Interest
        Guild Vault Chest
      Wars
        Declaration
        Objectives
        Kill Tracking
        Wagers
        Peace Agreements
      Claims
        Convert Personal to Guild
        Guild-Owned Territory
    Parties
      Creation
        Public Parties
        Guild-Only Parties
      Management
        Invite Players
        Leader Transfer
        Disband
    Visualization
      Borders
        Edge Highlighting
        Corner Markers
      Tools
        Claim Tool
        Move Tool
      Modes
        Always On
        Tool Only
    Integration
      PlaceholderAPI
      Vault Economy
      WorldGuard
      External Plugins
```

## Summary

This master diagram document provides:

1. **Complete System Architecture** - All layers and their interactions
2. **End-to-End Flows** - Real user scenarios from start to finish
3. **Component Dependencies** - How modules depend on each other
4. **Data Flow** - How information moves through the system
5. **Database Relationships** - Complete schema visualization
6. **Plugin Lifecycle** - Startup and shutdown sequences
7. **Cache Strategy** - Performance optimization approach
8. **Feature Map** - Complete functionality overview

Use these diagrams to:
- Understand how the entire system works together
- Debug issues by following the flow
- Plan new features by seeing integration points
- Onboard new developers quickly
- Document architectural decisions

## Related Documentation

- [Architecture Overview](./architecture.md) - Hexagonal architecture principles
- [Domain Layer](./domain.md) - Core business logic
- [Application Layer](./application.md) - Use cases and actions
- [Infrastructure Layer](./infrastructure.md) - External integrations
- [Interaction Layer](./interaction.md) - User interface
- [Getting Started](./getting-started.md) - Development guide
- [Integration Guide](./integration.md) - Extending the plugin
