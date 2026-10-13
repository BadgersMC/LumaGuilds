# TEST economy-provider startup lifecycle

REQ-149 / STARTUP-002: When Vault is loaded before its economy provider, Guild Bank SHALL report that it is waiting rather than claim the bank is broken. When the provider later registers, unregisters or changes, Bank availability SHALL reflect the current ServicesManager registration and SHALL NOT retain a stale provider.

Spec/evidence: Guilds enables in STARTUP and Currency registers in POSTWORLD. Historical TEST logs recorded `No economy provider found! Guild Bank will not function` before Currency enabled. The prior `getEconomy` retained its provider indefinitely once resolved. MockBukkit registration, unregistration and higher-priority replacement regressions cover delayed discovery and stale-cache removal; production financial engine behavior is unchanged. This sanitized PR retains only that lifecycle correction on current reviewed main.

SPEAR: spec -> prove (focused registration tests) -> engine (small infrastructure binding/logging correction) -> arch (no domain/API change) -> refine (full checks and TEST proof). Project-local EARS/state helpers are absent; this file records manual task/evidence. Source PR, clean artifact/source/hash, rollback, download-back verification and startup proof are required. Production and Minecraft player acceptance remain separate.

Local proof: 24 focused cases reproduced one stale-provider failure before the edit. Full check and shadowJar passed with 1752 cases, zero failures/errors, 20 optional skips. TEST activation evidence is recorded in the operational receipt.
