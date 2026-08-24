# Complete i18n Migration Design

## Objective

Complete `LG-701` and satisfy `REQ-016` by moving every player-visible
LumaGuilds string into Nexus i18n. This includes Java Edition and Bedrock
commands, menus, forms, listeners, services, broadcasts, action bars, item
metadata, and validation feedback.

The migration follows the current BadgersMC conventions established by
LumaTrivia, EnthusiaVotes, and EnthusiaMarket.

## Source of Truth

`src/main/resources/lang/en_US.yml` is the only bundled English locale.
LumaGuilds uses:

```kotlin
@LangFile(resourcePrefix = "lang", defaultLocale = "en_US")
class LumaGuildsLang
```

One `LangService` is constructed during dependency injection and shared by
all consumers. The removed properties-based provider, localization constants,
defaults directory, and override directory do not return.

Additional locales may later be added as `lang/<locale>.yml` without changing
call sites.

## Locale Structure and Naming

Keys describe the product surface and meaning rather than the implementation
class. The principal namespaces are:

- `prefix`: reusable LumaGuilds message prefix.
- `common`: shared player-facing words and generic feedback.
- `command`: command responses and usage text.
- `menu`: Java inventory titles, item names, and lore.
- `bedrock`: Cumulus form titles, content, buttons, and Bedrock-only feedback.
- `guild`, `claim`, `bank`, `war`, `party`, `rank`, and `progression`: shared
  feature messages used by more than one delivery surface.
- `error`: shared failures that are not owned by one feature.

Keys use lowercase `snake_case` path segments. Equivalent Java and Bedrock
copy shares a key; copy that is intentionally different remains under
`menu` or `bedrock`.

All values are MiniMessage. YAML owns colors, gradients, shadows, click and
hover events, line breaks, and the `<prefix>` tag. Kotlin must not reconstruct
localized sentences from colored fragments unless it is joining separately
meaningful localized rows.

## Placeholder Contract

All placeholders are named after their meaning:

```yaml
command:
  guild:
    invite:
      success: "<prefix><green>Invitation sent to <player>.</green>"
```

```kotlin
sender.sendMessage(lang.msg("command.guild.invite.success", "player" to target.name))
```

Positional placeholders such as `<0>` and `<1>` are prohibited. Call sites
must pass the exact names declared by their locale value. Values may be
strings, numbers, or Adventure components as supported by Nexus i18n.

## Rendering Boundaries

The three Nexus APIs have distinct uses:

- `lang.msg(key, ...)` returns an Adventure `Component`. Use it for chat,
  action bars, broadcasts, component-aware item APIs, and component-aware
  menu APIs.
- `lang.legacy(key, ...)` returns a legacy-formatted string. Use it only when
  a Bukkit, InventoryFramework, Cumulus, or compatibility API requires a
  `String`.
- `lang.raw(key)` returns undecorated source text. Use it only where markup
  must not be parsed, such as a plain Bedrock form title or a value consumed
  by another formatter.

Domain entities and application results must not import `LangService` or
Nexus. They expose typed state. Translation happens at interaction or
infrastructure adapters. Existing domain enums may carry stable key strings
when the key identifies display metadata, but they must not render messages.

## Migration Scope

The migration covers every string shown to a player, including:

1. All player and administrator command responses, help, usage, and errors.
2. Java inventory titles, item names, lore, confirmations, pagination, and
   empty states.
3. Bedrock form titles, content, buttons, validation, and failure feedback.
4. Listener-driven chat, action bars, announcements, moderation, protection,
   teleportation, vault, party, war, and progression messages.
5. Service-produced player notifications and item display metadata.
6. Player-visible text built dynamically from lists or result objects.

The following are deliberately not locale entries:

- Logger output and exception diagnostics intended for operators.
- Database identifiers, migration SQL, permission nodes, command aliases,
  configuration keys, enum persistence values, and protocol constants.
- Nexo glyph identifiers, menu positioning markup, placeholder identifiers,
  and resource-pack metadata.
- Pure formatting utilities whose return values are not human-language copy.
- Test fixtures that intentionally exercise legacy color conversion.

## Migration Batches

Work proceeds in independently verifiable batches:

1. Normalize the recovered locale, replace numeric placeholders, and install
   validation gates.
2. Migrate all commands, including monolithic guild and admin commands.
3. Migrate Java inventory menus by feature: core guild, members/ranks,
   bank/progression, relations/war, then claims.
4. Migrate every Bedrock form and its shared form utilities.
5. Migrate listeners, services, broadcasts, action bars, and item factories.
6. Remove dead keys and obsolete localization plumbing.
7. Run repository-wide validation and produce the shaded plugin JAR.

Each batch starts with a failing contract or behavior test and ends with its
focused tests passing. A clean full build is required after the final batch.

## Validation Gates

Automated tests must enforce the finished contract:

1. `en_US.yml` loads through Bukkit/Nexus-compatible YAML without duplicate
   mappings or boolean-coerced keys/values.
2. Every statically referenced `msg`, `legacy`, and `raw` key exists.
3. Every YAML key is referenced, including keys reached through declared
   enum or dynamic key families. Dynamic families are enumerated explicitly
   in test data rather than hidden behind a broad ignore list.
4. No locale value contains numeric placeholders matching `<\d+>`.
5. Placeholder names used by a call site match the corresponding YAML value.
6. No production source contains a player-visible hardcoded section-sign
   string. Narrow exclusions must identify technical formatting code by exact
   file and purpose.
7. No old `LocalizationProvider`, `LocalizationKeys`, or properties locale
   resource remains referenced.

Behavior tests cover representative command, Java menu, Bedrock form, and
listener paths so the validation does not merely inspect source text.

## Compatibility and Error Handling

English wording and behavior remain unchanged unless the existing text is
malformed, duplicated, or cannot be represented safely in MiniMessage.
Legacy colors are translated to equivalent MiniMessage formatting.

Missing keys are build failures, not runtime fallbacks. Runtime delivery
failures retain existing fail-open behavior where appropriate and log a
technical diagnostic without exposing stack traces to players.

Bedrock forms continue to use legacy or raw strings where Cumulus requires
them. Java and Bedrock clients receive semantically equivalent feedback.

## Completion Criteria

The migration is complete when:

- Every in-scope player-visible string renders through `LangService`.
- No positional placeholders remain.
- No missing or unreferenced locale keys remain.
- The old localization implementation is absent.
- Focused localization tests and the entire repository test suite pass.
- `clean test shadowJar` succeeds and produces the deployable plugin JAR.
- `LG-701` is marked complete with test and build evidence in `docs/tasks.md`.
