# Guild bank entry and chat cooldown review

## Scope

This record documents the sanitized rebuild of the original #210 bank/cooldown repair on current post-#223 `main`. Only the four direct #210 feature/test commits are replayed. Inherited #208/#209 history, private resource-pack files, and the old stack-reconciliation merge are intentionally excluded.

## Requirements

- REQ-122: `/guild bank` and `/g bank` open the existing platform-aware guild bank for a current member with the normal `lumaguilds.guild.menu` command permission. Bank operations keep their existing rank/transaction authorization.
- REQ-123: announcement and ping time gates use `chat.announce_cooldown_minutes` and `chat.ping_cooldown_minutes`; non-positive values disable only the time gate, while hourly caps remain active.

## Architecture review

- `lumaguilds.guild.menu` is `default: true` in `plugin.yml`; direct bank entry therefore does not require operator status or management-rank permissions.
- Java/Bedrock selection remains centralized in `MenuFactory.createGuildBankMenu`; the command does not instantiate a platform-specific menu directly.
- The shortcut checks current guild membership before opening a menu. Deposit, withdrawal, automation, budget, security and other bank mutations remain authorized inside the existing bank services/menus.
- `ChatServiceBukkit` reads current configuration during each rate-limit check, so a configuration reload changes cooldown behavior without recreating the service. Existing hourly announcement/ping counts are unchanged.
- No persistence schema, economy policy, guild-rank policy or private resource-pack content is changed by this repair.

## Validation boundary

The focused bank/cooldown regressions and clean full suite are merge gates for the sanitized replacement. Hosted Gradle/Codacy review and real Java/Bedrock command walkthrough remain separate acceptance evidence. This document does not claim production deployment or live-client validation.

## Remaining unrelated gaps

Home interaction reports, shop-XP integration, menu-asset reconciliation, progression policy and other cross-plugin work remain outside this PR and should be handled by their owning tasks rather than folded into this repair.
