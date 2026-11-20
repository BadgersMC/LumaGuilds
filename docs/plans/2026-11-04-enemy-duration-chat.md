# Enemy duration chat rendering

- **Spec:** When a player views an enemy relationship, started and expiry messages must show the localized duration text rather than an Adventure component's diagnostic representation. The inventory lore and relationship behavior remain unchanged.
- **Evidence:** The reported screenshot shows the component representation in the started message. Both chat paths passed a `Component` directly to `LangService.msg`; GUI placeholders use a separate component-aware adapter.
- **Implementation:** Convert the formatted duration to plain text at the chat boundary for both messages.
- **Verification:** `LangServiceRenderingTest` exercises both real localization templates with a nested duration. In-game acceptance remains separate.
- **SPEAR tooling:** No project-local EARS validator or state helper was found; this record captures the scoped requirement and evidence without claiming those tools passed.
