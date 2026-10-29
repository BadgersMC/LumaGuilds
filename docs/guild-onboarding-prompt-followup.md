# Guild onboarding prompt follow-up

## Requirement

When a newly joined guild member receives the one-time Getting Started message, the message offers the guide without an extra dismiss action. The join confirmation directs ordinary members to the accessible `/guild` overview, rather than the restricted management command. Existing prompt consumption, the guide action, and explicit dismissal from the guide remain unchanged. Guild Info uses the shared back icon with a vanilla arrow fallback. Recruitment card lines prefer word boundaries and joining fees display enum-style currency names in readable form while preserving custom names.

## Evidence and boundaries

On TEST, the join message displayed an adjacent `Dismiss prompt` action, directed an ordinary member to a management-only menu, and recruitment listings broke words and displayed `RAW_GOLD`. Guild Info used a barrier fallback unlike other guild menus. The custom `lg_back` icon is still selected when available; TEST showed the same incorrect framed-square appearance in Guild Info and its member list, and its cause requires further investigation. Focused onboarding, root routing and recruitment card tests passed locally. This source-only follow-up has not been deployed or client-verified. Guildless leaderboard access remains a separate outstanding finding.
