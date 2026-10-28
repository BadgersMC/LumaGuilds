# Guild onboarding prompt follow-up

## Requirement

When a newly joined guild member receives the one-time Getting Started message, the message offers the guide without an extra dismiss action. Existing prompt consumption, the guide action, and explicit dismissal from the guide remain unchanged. Guild Info uses the shared back icon with a vanilla arrow fallback. Recruitment card lines prefer word boundaries and joining fees display enum-style currency names in readable form while preserving custom names.

## Evidence and boundaries

On TEST, the join message displayed an adjacent `Dismiss prompt` action and recruitment listings broke words and displayed `RAW_GOLD`. Guild Info used a barrier fallback unlike other guild menus. The custom `lg_back` icon is still selected when available; its reported incorrect in-game appearance requires a separate resource-pack investigation and native client verification. Focused onboarding and recruitment card tests passed locally, including word-boundary and Unicode cases. This source-only follow-up has not been deployed. Guildless leaderboard access and the member welcome command remain separate outstanding findings.
