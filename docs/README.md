# LumaGuilds Documentation

Welcome to the comprehensive documentation for **LumaGuilds** - a Minecraft plugin for land claiming and guild management built with hexagonal architecture.

## 📚 Documentation Index

### Getting Started

- **[Architecture Overview](./architecture.md)** - Understanding the hexagonal architecture and layer structure
- **[Getting Started Guide](./getting-started.md)** - How to develop and extend the plugin
- **[Master System Diagram](./master-diagram.md)** - Complete visual map of all components

### Layer Documentation

- **[Domain Layer](./domain.md)** - Core business logic, entities, and value objects
- **[Application Layer](./application.md)** - Use cases, actions, and results
- **[Infrastructure Layer](./infrastructure.md)** - Database, adapters, and external integrations
- **[Interaction Layer](./interaction.md)** - Commands, menus, and player interactions

### Advanced Topics

- **[Integration Guide](./integration.md)** - Integrating with external plugins and extending functionality

## 🎯 Quick Navigation

### I want to...

**Understand the codebase:**
1. Start with [Architecture Overview](./architecture.md) for the big picture
2. Read [Domain Layer](./domain.md) to understand core concepts
3. Check [Master Diagram](./master-diagram.md) for complete system visualization

**Add a new feature:**
1. Read [Getting Started Guide](./getting-started.md)
2. Follow the step-by-step example
3. Reference [Application Layer](./application.md) for action patterns

**Integrate with another plugin:**
1. Read [Integration Guide](./integration.md)
2. Use the Public API examples
3. Listen to LumaGuilds events

**Fix a bug:**
1. Check [Master Diagram](./master-diagram.md) to understand the flow
2. Reference the specific layer documentation
3. Follow the debugging tips in [Getting Started](./getting-started.md)

## 🏗️ Architecture Overview

LumaGuilds uses **Hexagonal Architecture** (Clean Architecture) with four distinct layers:

```
┌─────────────────────────────────────┐
│   Interaction Layer (Outer)        │
│   Commands, Menus, Listeners        │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│   Application Layer (Use Cases)    │
│   Actions, Results, Interfaces      │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│   Domain Layer (Core)               │
│   Entities, Value Objects, Events   │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│   Infrastructure Layer (Outer)      │
│   Repositories, Adapters, Database  │
└─────────────────────────────────────┘
```

**Key Principles:**
- Dependencies point inward
- Domain has zero external dependencies
- Application defines interfaces, infrastructure implements them
- Dependency injection via Koin

## 📖 Documentation Features

All documentation includes:

✅ **Extensive code examples** - Real, working code snippets
✅ **Mermaid diagrams** - Visual representations of flows and architectures
✅ **Best practices** - Dos and don'ts for each layer
✅ **Testing examples** - How to write unit tests
✅ **Integration patterns** - Working with external systems

## 🔍 Context7 Integration

This documentation is optimized for **Context7 MCP server** to enable AI assistants to understand and work with the codebase effectively.

**Configuration:** See [context7.json](../context7.json) at repository root

**Key Rules:**
- Use hexagonal architecture with distinct layers
- Domain entities have no external dependencies
- All database operations go through repository interfaces
- Use Result sealed classes for action outcomes
- Claims are anchored by Bell blocks
- Dependency injection with Koin

## 🚀 Quick Start Example

Here's how to add a simple feature (from [Getting Started](./getting-started.md)):

```kotlin
// 1. Create Action
class UpdateClaimIcon(private val claimRepository: ClaimRepository) {
    fun execute(claimId: UUID, newIcon: String): UpdateClaimIconResult {
        val claim = claimRepository.getById(claimId)
            ?: return UpdateClaimIconResult.ClaimNotFound

        claimRepository.update(claim.copy(icon = newIcon))
        return UpdateClaimIconResult.Success(claim)
    }
}

// 2. Define Result
sealed class UpdateClaimIconResult {
    data class Success(val claim: Claim) : UpdateClaimIconResult()
    object ClaimNotFound : UpdateClaimIconResult()
}

// 3. Create Command
@Subcommand("seticon")
fun onSetIcon(player: Player) {
    when (val result = updateClaimIcon.execute(claimId, itemType)) {
        is UpdateClaimIconResult.Success ->
            player.sendMessage("§aIcon updated!")
        UpdateClaimIconResult.ClaimNotFound ->
            player.sendMessage("§cClaim not found!")
    }
}
```

See [Getting Started Guide](./getting-started.md) for complete walkthrough.

## 📊 Documentation Structure

```
docs/
├── README.md                  # This file
├── architecture.md            # Hexagonal architecture overview
├── domain.md                  # Domain layer (entities, value objects)
├── application.md             # Application layer (actions, results)
├── infrastructure.md          # Infrastructure layer (repos, adapters)
├── interaction.md             # Interaction layer (commands, menus)
├── getting-started.md         # Development guide
├── integration.md             # External plugin integration
└── master-diagram.md          # Complete system visualization
```

## 🛠️ Technology Stack

- **Language:** Kotlin
- **Minecraft:** Paper/Spigot 1.20+
- **Database:** SQLite (via IDB)
- **DI:** Koin
- **Commands:** ACF (Annotation Command Framework)
- **Build:** Gradle 8.x with Kotlin DSL

## 📝 Contributing to Documentation

When adding documentation:

1. Include code examples for all concepts
2. Add Mermaid diagrams for flows and architectures
3. Follow the established structure
4. Update this README with new sections
5. Ensure Context7 compatibility (markdown with code blocks)

## 🔗 External Resources

- **Main README:** [../README.md](../README.md)
- **Contributing Guide:** [../CONTRIBUTING.md](../CONTRIBUTING.md)
- **Changelog:** [../CHANGELOG.md](../CHANGELOG.md)
- **Commands Reference:** [../COMMANDS.md](../COMMANDS.md)
- **Permissions Reference:** [../PERMISSIONS.md](../PERMISSIONS.md)

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/your-org/bell-claims/issues)
- **Discussions:** [GitHub Discussions](https://github.com/your-org/bell-claims/discussions)

## 📄 License

This project is licensed under the MIT License - see [LICENSE](../LICENSE) for details.

---

**Happy Coding! 🎉**

*Documentation last updated: 2025*
