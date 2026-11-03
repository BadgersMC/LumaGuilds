# Nexo guild dialog artwork

Current canonical main 283d75f1 was fetched before continuing recruitment source 6e0b70be in isolated codex/nexo-dialog-theme. Existing public Nexo glyph APIs and the installed TEST Nexo 1.28 texture/config inventory were inspected read-only.

- REC-026: WHEN a supported Java player with a loaded resource pack opens a guild dialog, THE SYSTEM SHALL use the selected resolved guild palette and registered Nexo header, divider, footer and button-marker artwork, preserving readable native controls, complete content and guarded actions.
- REC-027: WHEN the selected style is Vanilla, Nexo is unavailable, or any required theme glyph is missing or invalid, THE SYSTEM SHALL render the complete plain dialog without unresolved artwork. Retired themes SHALL resolve through the existing GuiTheme rules. Nexo SHALL retain responsibility for pack delivery; backend acceptance status SHALL NOT suppress registered artwork after proxy transfers.
- REC-028: WHEN theme assets are prepared, THE SYSTEM SHALL keep licensed texture bytes private, use additive namespaced non-emoji glyph definitions, and preserve existing guild emoji permissions, theme unlocks and inventory providers. Asset preparation SHALL not reload, rebuild or upload the server pack.

Presentation adapters own the glyph lookup; application services, persistence, fees and permissions stay unchanged. The shared menu shell, reader and text input must all receive theme context. Art bands reserve whole native text lines rather than overlaying input hitboxes. Native button surfaces remain client-owned; this implementation does not globally override vanilla buttons or claim an arbitrary full-screen frame.

SPEAR tools/EARS helpers are absent; manual spec/proof/task evidence is used. New theme tests first failed compilation because the adapter did not exist; this is missing-feature proof. Seven theme cases cover selected assets/palette, complete button labels, Vanilla fallback, partial styles, invalid font/emoji metadata, absent Nexo and retired styles, and the shared spacer. An additional presentation case proves selected guild style propagation. The full local check/shadowJar passes 1,867 cases, zero failures/errors, with 16 existing optional skips. Existing localization, architecture, reader/session and emoji behavior checks pass without baseline relaxation. Kotlin formatting and local complexity review pass for the changed presentation helpers. The three static-review findings on the preceding navigation head were addressed as part of this refinement.

Proxy integration refinement: the inspected NexoProxy listener can deny duplicate pack sends, and Velocity documents a synthetic denied response to the backend for denied sends. A backend Player pack-status flag is therefore not an authoritative indication of the client's already-loaded pack. The theme follows existing Nexo inventory behavior: gate on complete registered assets and use Nexo's delivery policy, then require actual client/transfer acceptance during rollout. No proxy configuration or plugin is changed.

The public signatures used by the existing glyph resolver were checked in the local Nexo 1.28 runtime: instance/fontManager, glyphFromName/glyphFromPlaceholder, chars/font/isEmoji/id. The private package has 28 style images plus a transparent shared line spacer; all 29 definitions are namespaced non-emoji glyphs with automatic Nexo character assignment. Dimensions, PNG references, font, permissions and IDs were checked; read-only inspection of all eight installed TEST glyph files found no collisions. No private texture bytes were added to Git. Seven artwork styles plus Vanilla were checked across five preview screens at 320/390/760/1024 pixels without script errors, duplicate frame bands or horizontal overflow.

- [x] Specify the optional artwork contract and fallback boundaries.
- [x] Prove missing-feature and safe fallback behavior; integrate all native presentation paths.
- [x] Validate the private asset package, public runtime API and responsive preview.
- [x] Run full local checks/build and inspect source/style findings.
- [ ] Hosted exact-head checks and maintainer review remain separate delivery gates.
- [ ] Private asset installation, pack generation/delivery and plugin activation are not performed.
- [ ] Real client line spacing, resource-pack acceptance, native focus and models require TEST acceptance after separately authorized installation/activation. Production remains read-only.
