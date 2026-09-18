# LG-1213 atomic reward purchases — SPEAR

Scope: Chapter 2 progression only. References: REQ-050/092/093 and the approved
LG-1202 catalog. MariaDB ownership validation is now available through the same
contract suite as SQLite; the next dependency is payment plus ownership atomicity.

One immutable purchase request contains transaction ID, guild, actor, reward ID,
quoted price and expected ownership version. The application entry point belongs
to GuildGoldService and defaults to unavailable until an adapter is configured.
An injected authorization guard defaults to deny. No menu or command is enabled.

The SQL transaction locks the guild reward account, resolves any existing receipt,
then revalidates the current catalog price, persisted run level, ownership version,
authorization, frozen/pending gold state and offer eligibility. It calls the existing
canonical gold mutation implementation on the same JDBC connection as the ownership
write and immutable receipt. Any exception rolls all three back. No extra balance
authority, asynchronous grant, compensation credit or debit-then-save is permitted.

Matching retries return the durable receipt without charging or regranting, even
after ownership or policy changes. Reusing a transaction ID for a different request
is rejected. A rejected request is terminal; a changed quote or new attempt uses a
new ID. Concurrent requests serialize at the database account lock; an ownership
version conflict never charges. Receipt replay must agree with the canonical gold
journal. Failure/unknown commit status reports retry with the same ID, never refund.

Test first: success and permanent home purchase, restart replay, request fingerprint
mismatch, duplicate/stale/locked/dominated offers, permission/freeze/pending guards,
insufficient funds, price mismatch, missing progression/ownership, concurrent buys,
and failure injection after debit and before receipt. Run identical SQL contracts
on SQLite and disposable MariaDB, then the full test/shadowJar and layer gates.

Live configuration/read-model migration, purchase menus, home activation and actual
prestige remain LG-1214 and the existing Chapter 2 tasks.
