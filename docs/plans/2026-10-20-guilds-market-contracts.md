# Guilds and current Market companion contracts

## Scope and source

Roundtable **GUILDS-08** adds missing cross-plugin contract coverage to the
reviewed Guilds candidate. The worker is `/root/guilds_market_contract`; the
coordinator owns artifact transfer, native execution, integration and publication.

The worker began from clean Guilds commit
`243370dd815845b06022ed15e12f924fa3cc79ef`, tree
`01a59fe7bfc9ae5b0c70addcee9cd1037c9b1a33`. Only the two contract test classes and
this note change. API semantics, registration code, XP accounting, reward policy
and analysis/build configuration are unchanged.

The coordinator supplied an immutable source extract from the current combined
Market candidate and its owner's artifact manifest:

| Identity | Value |
| --- | --- |
| Market commit | `654db0eec2bcf5afa69e4791a271f4877c9bcac4` |
| Market tree | `537ea445c6dad0c19ff44722e0ecf8fcf26c85a4` |
| Owner-manifest JAR bytes | `4477968` |
| Owner-manifest JAR SHA-256 | `230504aa0243d0ff3145c5fb95723cbbd8db0ff01b60d39de54f57a21257c064` |

The worker read the exact `BukkitGuildShopXpGateway.kt`, `GuildStallReadApi.java`,
`GuildStallSnapshot.java` and `GuildStallMember.java` extracts. The coordinator must
independently verify the copied artifact against the manifest before native tests.
Historical Market 1.0.53 results and the earlier two-case paired run do not execute
the new cases below.

## Contract confirmed in source

The Market gateway resolves Guilds through Bukkit services on every call. It uses
the registered provider's own interface class, requires an enabled plugin and API
version 2, and forwards prepare/complete/abort through JDK-only arguments and
results. Exceptions remain failures; this boundary does not decide whether a
terminal receipt has been acknowledged in Market's replay queue.

The stall API is version 1. Its record constructor accepts four identifying text
fields, two primitive longs, nullable rent/grace timestamps, nullable coordinates
and member records. Member capabilities are strings in a defensively copied set.
That allows a newer companion to return a capability Guilds does not recognize;
Guilds must treat that response as unavailable instead of publishing partial data.

The tests select exact public constructor signatures. Adding another compatible
constructor does not invalidate the contract merely because the record class has
more than one public constructor.

## Coverage

| Test | Cases | What the assertions establish |
| --- | ---: | --- |
| `discoveryAndCleanup` | 1 | Existing Guilds API v2 registration belongs to the plugin and disappears on unregistration. |
| `marketLifecycle` | 1 | Existing actual-gateway prepare/award/abort calls remain intact; a retained award refreshes with a null level, without another level callback. |
| `marketRetainsNonAwardingOutcomes` | 7 | `OWN_GUILD`, `DISABLED`, `COOLDOWN`, `CAPPED`, `STALE_RUN`, `ABORTED` and `CHAPTER_FROZEN` cross prepare/complete/abort unchanged. Sale, guild, buyer and millisecond timestamp reach the repository exactly; no progression publication occurs. |
| `marketFailureRemainsRetryable` | 1 | An exception reaches the caller, publishes no cache update and causes no abort. Retrying the same receipt reaches the repository and can return the committed award. |
| `marketResolvesReplacementAfterUnregistration` | 1 | The same real gateway fails with an absent service, uses a registered provider, fails after its removal and resolves a replacement provider without calling the old repository again. |
| `marketRejectsIncompatibleProvider` | 1 | A version-1 Guilds provider is rejected before its completion method is called. |
| `decodesCompanionRuntime` | 1 | All snapshot fields, deadlines and four current capabilities decode from the selected artifact's real records; guild/viewer identities are forwarded correctly. |
| `discoversCompanionAndRemovesService` | 1 | The production default stall client discovers the separately loaded API through real Bukkit registration, preserves nullable metadata and reports unavailable after removal. |
| `preservesEmptyAndUnavailableResponses` | 1 | A successful empty list remains distinct from an exceptional companion response. |
| `rejectsUnknownCompanionCapabilities` | 1 | An unknown capability in an actual companion record makes the whole response unavailable, even when another row is valid. |

There are **16 expected cases** across the two classes: 12 registration/gateway
cases and 4 stall cases. This retains the previous 3 cases and adds 13. The ordinary
registration case needs no Market artifact. The other 15 are explicitly optional
artifact contracts, so absent or blank opt-ins skip them; a configured missing
file fails rather than silently skipping.

The gateway class must come from the selected runtime JAR's classloader. The stall
API and records load with only the platform classloader as parent, and the tests
assert their defining classloader. This prevents a convenient test-classpath copy
from silently satisfying the selected-artifact contract.

## Execution and acceptance boundary

At this worker handoff the tests are source-reviewed, with `git diff --check`
passing. **The worker ran no build or runtime test.** The coordinator's later
immutable execution receipt must record the actual Guilds head, copied Market
artifact hash, Java version, test counts and skips.

Run both classes under Java 25 with `MARKET_RUNTIME_JAR` and `MARKET_API_JAR`
pointing to the hash-verified candidate that contains their respective classes:

```text
test --tests net.lumalyte.lg.infrastructure.services.GuildShopXpRegistrationTest
     --tests net.lumalyte.lg.infrastructure.services.GuildStallRuntimeContractTest
     --no-build-cache --offline --no-daemon
```

These tests load Market's real gateway and record classes. The Guilds persistence
and progression collaborators in gateway tests are mocked; the stall provider is
a controlled proxy implementing the actual companion interface. They do not run
Market's payment, stock or durable receipt queue, nor do they prove backend
authorization or live GUI rendering.

**MARKET-10 owns the `CHAPTER_FROZEN` receipt acknowledgment/replay correction.**
Forwarding that string correctly in this test does not prove the Market queue
accepts it or progresses to a later receipt. Its independently reviewed source and
native replay contracts remain separate evidence. Unknown or malformed Market
receipt results must retain that consumer's failure behavior; no consumer logic is
changed here.

**GUILDS-05 remains the live acceptance task.** Current artifacts, backend/pack
identity and a bounded acknowledged shared-resource window are required before
transactions or client input. This contribution authorizes no deployment,
canonical merge, live balance/inventory/rank change or compensation.
