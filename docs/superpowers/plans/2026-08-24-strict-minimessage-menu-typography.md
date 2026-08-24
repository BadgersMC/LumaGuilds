# Strict MiniMessage and Menu Typography Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate legacy-formatted localization output, add opaque black shadows wherever Adventure Components preserve them, and render translated GUI copy in small caps without changing dynamic proper names.

**Architecture:** `LangService.msg()` remains the normal chat Component source. A GUI-specific renderer transforms only literal text in the locale MiniMessage template before resolving placeholders, so player and domain names retain their supplied spelling. Component-aware ItemStack and menu APIs preserve shadow styling; string-only Bedrock consumers receive plain-text serialization.

**Tech Stack:** Kotlin, Paper 1.21, Adventure 4.26.1, MiniMessage, JUnit 5, Nexus i18n.

**Spec:** `docs/requirements.md` REQ-016

## Global Constraints

- Locale resources contain no section-sign or ampersand legacy formatting codes and parse with standard MiniMessage.
- Opaque black is `#000000FF` and is applied only where the destination accepts Adventure Components.
- GUI literal copy is small caps; digits, punctuation, glyphs, and placeholder-provided proper names are unchanged.
- Chat retains normal casing.
- Floodgate and other String-only APIs receive plain text without a shadow guarantee.
- Existing unrelated worktree changes remain untouched.

---

### Task 1: Strict locale and source contracts

**Files:**
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LocaleContractTest.kt`
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/LocaleSourceScanner.kt`

**Interfaces:**
- Consumes: flattened `lang/en_US.yml` values and Kotlin production sources.
- Produces: regression gates requiring zero legacy locale codes and zero `lang.legacy` production calls.

- [ ] Write tests that report the exact offending locale keys and call sites.
- [ ] Run the focused locale contract and capture the expected red inventory.
- [ ] Extend lexical scanning only as needed to avoid comments and test fixtures.
- [ ] Re-run the focused contract and keep it red until Tasks 3-5 remove every violation.

### Task 2: GUI typography renderer

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/i18n/GuiTextRenderer.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/i18n/GuiTextRendererTest.kt`

**Interfaces:**
- Consumes: locale key plus named placeholder values.
- Produces: `fun msg(key: String, vararg placeholders: Pair<String, Any?>): Component` and a template transformer used before placeholder resolution.

- [ ] Test A-Z/a-z small-cap mapping, digit/punctuation/glyph preservation, MiniMessage tag preservation, placeholder-name preservation, dynamic proper-name preservation, and opaque black shadow.
- [ ] Run the test and verify it fails because `GuiTextRenderer` does not exist.
- [ ] Implement a deterministic Unicode small-cap table with ordinary-letter fallback and a MiniMessage-aware scanner that never mutates tag bodies.
- [ ] Resolve placeholders as Components, disable default italics for item text, and apply `ShadowColor.shadowColor(0xFF000000)` at the root.
- [ ] Run the renderer tests and verify they pass.

### Task 3: Component-native item and inventory APIs

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/utils/ItemStackExtensions.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/utils/ItemStackExtensionsTest.kt`

**Interfaces:**
- Consumes: Adventure `Component` names and lore from `GuiTextRenderer`.
- Produces: `ItemStack.name(Component)`, `ItemStack.lore(Component)`, and list/vararg Component overloads; legacy String overloads are removed after callers migrate.

- [ ] Write metadata tests proving color, black shadow, small caps, and non-italic styling survive in item names/lore.
- [ ] Verify red against the current String/legacy serializer implementation.
- [ ] Add Component overloads and remove `String.c()`/`LegacyComponentSerializer` from the extension file.
- [ ] Migrate Java inventory title construction to Component-capable Paper/InventoryFramework APIs.
- [ ] Re-run focused item and menu tests.

### Task 4: Java GUI call-site migration

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/**/*.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/utils/{MenuItemBuilder,FlagUtils,ClaimPermissionUtils}.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/interaction/menus/MenuLocalizationTest.kt`

**Interfaces:**
- Consumes: `GuiTextRenderer.msg(...)` and Component ItemStack extensions.
- Produces: all translated menu titles, names, and lore as shadowed small-cap Components.

- [ ] Add source contracts rejecting `lang.legacy` and raw localized Strings in Java GUI item/title sinks.
- [ ] Verify red and record the call-site inventory.
- [ ] Replace each menu localization sink while retaining placeholder values as untransformed Components.
- [ ] Preserve Nexo glyph/title cursor components and append the translated Component after the rewind.
- [ ] Re-run menu localization and title-builder tests after each menu family.

### Task 5: Chat and other Component-capable surfaces

**Files:**
- Modify: command, listener, notification, action-bar, and hologram adapters under `src/main/kotlin/net/lumalyte/lg`
- Test: corresponding localization tests under `src/test/kotlin/net/lumalyte/lg`

**Interfaces:**
- Consumes: `LangService.msg(...)` Components with normal-case locale copy.
- Produces: Component-native output with opaque black shadow and no legacy serialization.

- [ ] Add focused contracts for each Component-capable output family.
- [ ] Verify failures at current `lang.legacy`/serializer boundaries.
- [ ] Replace legacy calls with Components and apply black shadow centrally without changing letter case.
- [ ] Retain legacy serializers only for genuinely external legacy protocol compatibility, never for localized Paper output.
- [ ] Run command, listener, service, and i18n test groups.

### Task 6: String-only adapters

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/**/*.kt`
- Modify: other verified String-only adapters
- Test: Bedrock localization tests

**Interfaces:**
- Consumes: normal or GUI-styled Components.
- Produces: `PlainTextComponentSerializer` output with no formatting control codes.

- [ ] Test that Bedrock forms contain readable plain text and no MiniMessage/legacy tokens.
- [ ] Verify red at representative form titles, labels, content, and buttons.
- [ ] Centralize plain-text conversion in the Bedrock base adapter and remove direct `lang.legacy` calls.
- [ ] Apply small caps to Bedrock menu literal copy while preserving placeholder values; document that shadows are unsupported.
- [ ] Run all Bedrock tests.

### Task 7: Locale cleanup and complete verification

**Files:**
- Modify: `src/main/resources/lang/en_US.yml`
- Modify: `docs/tasks.md`

**Interfaces:**
- Consumes: strict contracts from Task 1.
- Produces: zero legacy locale codes, zero production `lang.legacy` calls, and completed LG-702/LG-703 evidence.

- [ ] Replace every legacy locale formatting token with the equivalent MiniMessage syntax and require standard MiniMessage parsing.
- [ ] Run `./gradlew clean test --tests 'net.lumalyte.lg.infrastructure.i18n.*'` and verify all i18n contracts pass.
- [ ] Run `./gradlew clean test shadowJar` and verify the complete suite and shaded artifact pass.
- [ ] Update LG-702/LG-703 evidence with exact test counts and commands.
- [ ] Review the diff for accidental changes to dynamic names, chat casing, Nexo glyphs, and unrelated dirty files, then commit only migration files.
