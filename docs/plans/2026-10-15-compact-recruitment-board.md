# Compact recruitment board

Local follow-up to clickable cards, starting from 0e49935. Authoritative main
was fetched and remains 283d75f1. No source reset, public art, push or server work.

- CRB-001: WHEN browsing guilds, THE SYSTEM SHALL show four concise clickable
  banner cards per page and retain the full route/fee details in their tooltips.
- CRB-002: THE SYSTEM SHALL move search/activity inputs, sorting and reset off
  the main board into the filter dialog, reachable through one compact body link.
- CRB-003: THE SYSTEM SHALL show My Applications and, for guild members, Manage
  Recruitment as a two-column footer, plus necessary pagination and Back.
- CRB-004: THE SYSTEM SHALL preserve existing permissions, filtering, fallback
  Open actions and other screens. Online counts exclude players the viewer cannot
  see. No real payment or recruitment policy changes.

The reference's burgundy button backgrounds cannot be promised by the native
dialog API. Existing Nexo card art and labels are reused; native buttons remain
native. Browser layout is a responsive simulation, not client acceptance.
Project EARS/state tooling is absent; this small spec and adjacent build evidence
record SPEAR proof, implementation and verification. In-game GUI scales, click
areas, close/parent transitions and long labels remain client tests.

Proof: the two new compact-layout regressions failed against the preceding
implementation (search still on board; filters had no search). After revision,
the full Java 25/Paper 26.2 check and shadowJar passed, including online visibility
and unavailable-fee coverage. Source diff whitespace and targeted formatting
checks passed. Browser simulation passed eight styles at widths 320, 390, 760
and 1024, four cards/two footer actions, separate search, retained query/profile
return and guest footer restrictions. Exact counts/hash/commit are recorded next
to the local candidate. No in-game test, hosted CI, push or server operation.
