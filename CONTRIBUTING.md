# Contributing to LumaGuilds

Thank you for your interest in contributing to LumaGuilds! We welcome contributions from developers, designers, testers, translators, and anyone passionate about making great Minecraft server tools.

## Communication

### Getting in Touch

- **GitHub Issues**: For bug reports, feature requests, and discussions
- **Discord**: [@mizarc](https://discord.com/users/97295777734332416)

### Community Resources

- [PaperMC Forums](https://forums.papermc.io/) - For Bukkit/Paper development help
- [Kotlin Slack](https://kotlinlang.org/community/) - For Kotlin language questions

---

## How to Contribute

### Bug Reports

Found a bug? Please open an issue on the [GitHub Issues](https://github.com/BadgersMC/LumaGuilds/issues) page.

**Include the following:**
1. Server version (`/version`)
2. LumaGuilds version
3. Steps to reproduce the issue
4. Expected vs actual behavior
5. Error logs (if applicable)

### Feature Requests

Have an idea? We'd love to hear it!

**When requesting features, please:**
- Check existing issues to avoid duplicates
- Describe the use case clearly
- Explain why it would benefit users
- Keep scope reasonable for implementation

### Code Contributions

Ready to write some code? Here's the workflow:

1. **Fork** the repository
2. **Clone** your fork locally
3. **Create** a feature branch (`feature/my-feature` or `fix/bug-description`)
4. **Make** your changes
5. **Test** your changes
6. **Submit** a pull request

#### Branch Naming

| Prefix | Purpose |
|--------|---------|
| `feature/` | New features or improvements |
| `fix/` | Bug fixes |
| `hotfix/` | Urgent bug fixes for production |
| `tweak/` | Small adjustments to existing functionality |
| `docs/` | Documentation changes |

---

## Development Setup

### Prerequisites

- **JDK 21** or higher
- **Git**
- **IDE**: IntelliJ IDEA recommended (has excellent Kotlin support)

### Building

```bash
git clone https://github.com/BadgersMC/LumaGuilds.git
cd LumaGuilds
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Generating the Plugin JAR

```bash
./gradlew shadowJar
# Output: build/libs/LumaGuilds-0.5.0.jar
```

---

## Project Architecture

LumaGuilds follows **hexagonal (clean) architecture** with clear separation of concerns.

### Layer Structure

```
net.lumalyte.lg
├── domain/          # Core business logic (no external dependencies)
│   ├── entities/    # Claim, Guild, Member, War, etc.
│   ├── values/      # Position3D, Area, etc.
│   └── exceptions/  # Domain-specific exceptions
│
├── application/     # Use cases and ports
│   ├── actions/     # Business operations (CreateClaim, GrantPermission, etc.)
│   ├── results/     # Sealed result types for each action
│   └── persistence/ # Repository interfaces
│
├── infrastructure/  # External concerns
│   ├── persistence/ # SQLite repository implementations
│   ├── adapters/    # Bukkit API adapters
│   ├── listeners/   # Protection event handlers
│   └── services/    # Technical services (caching, scheduling)
│
└── interaction/     # Player-facing layer
    ├── commands/    # ACF command handlers
    ├── menus/       # GUI menus (Java & Bedrock)
    └── listeners/   # User interaction listeners
```

### Key Principles

1. **Domain is pure** - No Bukkit or framework dependencies
2. **Actions return Results** - Sealed classes for exhaustive error handling
3. **Repositories are interfaces** - Defined in `application`, implemented in `infrastructure`
4. **Dependency injection** - Koin wires everything together

For detailed documentation, see the [`/docs`](docs/) folder.

---

## Style Guidelines

### Git Commit Messages

- Use **imperative, present tense** (e.g., "Fix bug" not "Fixed bug")
- **Subject line**: Max 50 characters
- **Body**: Explain *why* the changes were made, wrap at 72 characters
- Leave a blank line between subject and body

**Example:**
```
Add guild bank transaction logging

Track all deposits and withdrawals for audit purposes.
This enables guild leaders to review financial activity
and helps identify potential abuse.

Closes #123
```

### Kotlin Code Style

Follow the [Official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

**Project-specific guidelines:**
- **Line limit**: 120 characters max
- **Imports**: No wildcards, organize alphabetically
- **Naming**: `camelCase` for functions/variables, `PascalCase` for classes
- **Nullability**: Prefer non-null types, use `?` only when necessary

### Code Quality

- Write **unit tests** for new actions
- Use **sealed classes** for results with multiple outcomes
- **Document** public APIs with KDoc comments
- Avoid `!!` (non-null assertion) - handle nulls properly

---

## Documentation

### Updating Docs

Documentation lives in the [`/docs`](docs/) folder. When making significant changes:

1. Update relevant documentation files
2. Keep Mermaid diagrams in sync with code changes
3. Update the API reference if adding new actions

### Mermaid Diagrams

We use [Mermaid](https://mermaid.js.org/) for diagrams. Test your diagrams at [mermaid.live](https://mermaid.live/).

---

## Pull Request Process

1. **Ensure tests pass**: `./gradlew test`
2. **Update documentation** if needed
3. **Write a clear PR description** explaining:
   - What changes were made
   - Why they were needed
   - How to test them
4. **Link related issues** (e.g., "Closes #123")
5. **Request review** from maintainers

### PR Checklist

- [ ] Code compiles without warnings
- [ ] Tests pass
- [ ] Documentation updated (if applicable)
- [ ] Follows code style guidelines
- [ ] Commit messages are clear

---

## Keeping `/g help` and the wiki in sync

LumaGuilds enforces parity between the in-game help system and the player wiki. **Any PR that adds, removes, or renames a player-facing command must touch both `HelpTopics.kt` and the matching wiki page in the same PR.** CI rejects PRs that break this rule.

### What CI checks

1. **Front-matter lint** (`tools/wiki/lint_frontmatter.py`): every `wiki/docs/**/*.md` must have a valid front-matter block with `title`, `audience`, `topic`, `summary`, `keywords`, `related`, `updated`.
2. **Topic parity** (`tools/wiki/check_topic_parity.py`): every `slug = "..."` in [`HelpTopics.kt`](src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopics.kt) must have a matching `wiki/docs/players/<slug>.md` and vice versa.
3. **Strict MkDocs build** (`mkdocs build --strict`): no broken internal links, missing nav entries, or malformed markdown.

### Adding a new player-facing command

1. Decide which existing topic in `HelpTopics.kt` the command belongs to. If none fits, you're adding a new topic (rare — see below).
2. Add a `HelpCommandEntry(syntax, blurb, prefill)` to the topic's `commands` list.
3. Update the matching `wiki/docs/players/<slug>.md`: add a row to the Quick Reference table, add or expand a "Common task" H2 if needed.
4. Run the checks locally before opening the PR:

```bash
python tools/wiki/lint_frontmatter.py
python tools/wiki/check_topic_parity.py
mkdocs build --strict
./gradlew test
```

### Adding a new topic

1. Add a new `HelpTopic(...)` block to `HelpTopics.all` in `HelpTopics.kt`. Pick a stable lowercase-hyphenated slug — it becomes a URL forever.
2. Create `wiki/docs/players/<slug>.md` using the wiki page template (see other pages in that folder for the canonical shape).
3. Add the topic to `wiki/docs/players/.pages` so it appears in the nav.
4. Update `wiki/docs/players/how-do-i.md` with at least one entry deep-linking into the new page.
5. Run the checks above.

### Renaming or removing a topic

- **Removing:** delete the `HelpTopic` entry, delete the wiki page, and add a `mkdocs-redirects` entry to `mkdocs.yml` mapping the old URL to wherever the content moved (or to the topic index).
- **Renaming the slug:** treat as remove + add. The old slug is permanently retired (Hermes Agent and any cached links may still reference it via the redirect).

Slugs are forever. The display name, summary, and command list inside a topic are not — those evolve freely.

---

## Questions?

Don't hesitate to ask! Open a discussion on GitHub or reach out on Discord. We're happy to help newcomers get started.

Thank you for contributing to LumaGuilds!
