# Shared dialog alignment correction

User client screenshots show per-line background drift in every themed dialog,
an overflowing recruitment method button, and duplicate status heading text.
The deployed 8535c515 candidate remains untouched while this local fix is tested.
Authoritative main fetched and inspected: 283d75f1; no new upstream commits.

## Requirements and proof

- DA-001: Every framed cap and content row advances exactly 257 logical pixels,
  independent of text length. Wrap text within the frame before native wrapping.
- DA-002: Preserve component color, bold, interaction and Unicode codepoints.
  Unknown custom font/non-text content uses readable unframed fallback.
- DA-003: Keep action labels within native button width; full text remains in
  the tooltip. Recruitment method shows its value once in the status card.
- DA-004: Retain existing admission, reward, permission, navigation and CLOSE
  semantics. No production or TEST deployment/restart is authorized by this fix.

Spec: screenshots are direct client failure evidence. Prove: the old renderer
fails GuildDialogAlignmentTest because row advance varies with text width;
alignment-red.log records that failing run. Engine: fixed advance padding,
numeric default-font metrics and pre-wrapping. Architecture: infrastructure-only
rendering helper and optional private pack spacing font; no new domain mechanics.
Refine: validate actual cached vanilla glyph formulas, focused/full checks,
private pack lint, and an interactive native-layout simulation. EARS/state
helpers are absent; this record and build evidence provide the scoped SPEAR log.

Native client centering was checked against cached client 26.3 bytecode. Default
font numeric metrics come from its bitmap providers and the cached unifont;
no copyrighted texture bytes are included in the plugin repository. Custom client
fonts, forced Unicode, GUI scale and actual Minecraft rendering require client
acceptance; a browser CSS mockup does not prove Minecraft glyph alignment.

The matching private pack adds dialog_spacing_v2.json and a layout version
marker; legacy spacing remains unchanged. Old pack definitions fail closed to
unframed content instead of using unsupported padding glyphs. Both source and
pack remain local/unmerged until separately authorized delivery.

- [x] Reproduce native line-advance regression
- [x] Pixel wrapping, fallback and label regression checks
- [x] Complete build and private pack lint
- [x] Accurate interactive preview with mobile link
- [x] Local commits, artifact hashes and package
- [ ] In-game acceptance after separate TEST activation

Verification: complete check/shadowJar passed with 1942 tests, zero failures/errors, 16 existing skips. Private pack lint: 202 glyphs/549 items, zero errors and pre-existing unrelated warnings. Native geometry comparison and all eleven reference screens checked at 320/390/760/1024px, with no browser errors or overflow. Source/pack/artifact hashes and delivery limits are recorded beside this checkout. No server operation was performed.
