# Inventory caption and optional advancement startup corrections

## Spec

- GB-001: WHEN themed inventory artwork is drawn over container labels, the
  paired pack SHALL leave the native Inventory caption readable without changing
  slot geometry, callbacks, client translation or font selection.
- GB-002: The Guilds descriptor SHALL NOT require EnthusiaAdvancements to load
  before Guilds. The recorded TEST KOTH ordering already requires Guilds before
  KOTH and KOTH before Advancements.
- GB-003: WHEN the optional projection service appears before or after Guilds,
  Guilds SHALL register its own tree once per selected provider. Removal,
  replacement or companion disable SHALL detach the stale provider. Authoritative
  achievement tracking and rewards SHALL remain independent of projection.

## Prove and engine

Fresh canonical main283d75f1, continued clean owned PR241c7c44556. Last native
capture and startup log reproduce both release blockers. New lifecycle tests
first fail on late service registration and the descriptor ordering edge. The
private pack's caption test first fails because the old background is opaque.
No historical red/green claim is made for unrelated behavior.

The optional reflection adapter observes Bukkit service registration/removal and
plugin disable; repeated registration is idempotent. The descriptor drops only
the optional Advancements ordering edge. The companion ABI remains unchanged.
Private Pack PR9 clears the native caption aperture and removes the opaque plate
rectangle across all 42 theme/row pairs. No licensed artwork is added here.

## Architecture and refine

Lifecycle integration remains in infrastructure; no domain, reward, currency,
persistence, namespace ownership or GUI interaction changes. The test-only ABI
fixture matches the installed provider's reflected constructors and methods and
is excluded from the plugin JAR. Checks cover both startup orders, repeated
service notifications, unregister, replacement and companion disable. Full
configured checks/build preserve the existing database and Market contracts.
No project-local EARS/state helper exists; this is the manual SPEAR record.

Native caption rendering and absence of the cycle on a subsequent TEST startup
remain release gates. TEST/clients were handed to ZoneTitles through SHARED1066;
this source correction does not authorize or perform another live activation.
