# Guild dialog theme integration

Supported Java dialogs use the guild's existing resolved GUI style. Recruitment pages, menu navigation, settings, progression, quest standings, readers and text inputs share the same optional artwork contract. Vanilla stays plain; selecting a theme does not grant a holiday unlock or alter emoji permissions.

The private deployment package adds four glyphs per style:

| Part | ID | Texture size | Ascent | Reserved native lines |
| --- | --- | --- | --- | --- |
| Header | `guild_dialog_<style>_header` | 256 x 18 | 7 | 2 |
| Divider | `guild_dialog_<style>_divider` | 256 x 8 | 7 | 1 |
| Footer | `guild_dialog_<style>_footer` | 256 x 10 | 7 | 2 |
| Button accent | `guild_dialog_<style>_marker` | 8 x 8 | 7 | Native button label |

Styles are `enthusia`, `frostbound`, `verdant`, `voidlight`, `obsidian`, `halloween` and `christmas`. Textures use `lumaguilds:dialogs/<style>/<part>` and the dedicated `lumaguilds:dialogs` font. Nexo assigns characters; plugin code resolves IDs through the public API instead of hardcoding Unicode. Definitions use `is_emoji: false`, no chat placeholders, no tab completion, and the internal `lumaguilds.internal.dialog_art` permission. No new player permission grant is required for server-rendered artwork.

All four style glyphs and the shared transparent `guild_dialog_spacer` must resolve with the expected font and non-emoji metadata, and the player must report a successfully loaded resource pack. Otherwise the complete plain dialog is used. The spacer is a non-whitespace glyph on the reserved second line, so reservation does not depend on clients retaining a trailing empty newline. Its 1 x 1 transparent texture has height 1 and ascent 0. The latest player pack status is not proof of an exact pack hash: a live rollout must verify that the updated Nexo pack was delivered. Artwork is resolved afresh for each dialog rather than cached across Nexo reloads.

Header, separator and footer bands reserve native text lines and do not cover buttons or inputs. Markers are part of the real button labels. Native button surfaces, keyboard focus and hitboxes remain Minecraft-owned; no global vanilla GUI texture override is installed. The previous NONE-after-action transition and owner/revision/lifetime guards remain authoritative.

## Installation and acceptance boundary

Licensed PNGs are excluded from this repository. The private package is rooted at the Nexo plugin directory and contains `glyphs/enthusia/lumaguilds_dialogs.yml` plus 29 namespaced PNGs under `pack/assets/lumaguilds/textures/dialogs`. Preserve existing files, inspect glyph-ID conflicts and back up the pack before an authorized install. Install the artwork before activating a plugin build that uses it, then use Nexo's normal pack rebuild/send flow only when authorized. A plugin build requires its own authorized activation; do not hot-reload the plugin.

Check every supported style and Vanilla, a client without the pack, partial/missing glyph registration, Back/Next/Escape, real guild banners, complete payment quotes, and native text-input focus. Verify desktop GUI scales and long localized labels in Minecraft. The responsive browser preview uses the same private PNGs but does not prove client layout or pack delivery. Production requires merged source and verified release provenance; this document does not authorize installation, pack reload, restart or production changes.
