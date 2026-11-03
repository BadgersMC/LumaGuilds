# Guild Prestige System

The former level-200 and unbounded-prestige proposal in this location is superseded.

The approved Chapter 2 contract is maintained in:

- `docs/superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md`
- REQ-049, REQ-050, REQ-051, REQ-054, REQ-056, REQ-092, and REQ-093

The replacement design uses a level-100 current-run reset, a lifetime maximum of six prestiges, raw-gold costs of 10,000/20,000/30,000/30,000/30,000/30,000, one permanent perk plus one permanent home per prestige, strict war/capacity eligibility, and no Elo reset. It ships enabled for the production Chapter 2 cutover and remains explicitly operator-disableable.

## Owner correction — 2026-11-03

Earned Chapter 2 guild-home capacity is exactly **one base slot + nine purchasable permanent Guild Outposts + one permanent slot per completed prestige (up to six)**, with an absolute maximum of **16**. Historical Chapter 1 saved-home counts do **not** grant matching free permanent capacity. This owner correction supersedes the contrary migration-capacity language in the dated 2026-08-30 design snapshot.

Migration and prestige must never delete or truncate a saved guild-home location or its paid activation. Previously migrated account fields such as `guild_reward_accounts.initial_home_capacity` retain their recorded home count as historical audit evidence; the entitlement resolver excludes that count. A guild with 15 saved homes, nine Outpost purchases, and one prestige therefore has **15 existing locations but only 11 earned slots**, and may edit/use those existing locations subject to normal permissions and paid activation, but cannot create another until earned capacity exceeds the saved count. Java and Bedrock creation paths preflight capacity before attempting guild-gold payment; the persistence service rechecks before writing. Historical prestige receipt snapshots remain immutable and may reflect the former capacity calculation; live UI must use current reward entitlements, not past receipt capacity.
