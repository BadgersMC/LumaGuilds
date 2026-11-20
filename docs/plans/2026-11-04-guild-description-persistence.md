# Guild description persistence

## Requirements

- Guild descriptions written by supported creation or editing routes must survive repository and server restart, preserving nullable values and supported formatting.
- Existing guilds and unrelated fields must survive an additive, repeatable SQLite and MariaDB schema upgrade. Older binaries must be able to ignore the nullable column without overwriting it in their existing writes.
- Database write or migration failures must not silently claim a successful description update.

## Evidence and remaining verification

The repository previously cached `Guild.description` but neither persisted nor reloaded it. A repository recreation regression failed against the unmodified source. The fix adds an idempotent nullable column, includes it in all guild insert and update variants, and reloads it at startup. The focused SQLite regression covers creation, edit, clear, repeated migration of an existing guild, and preservation of unrelated fields. Local SQL tests use disposable databases, not a real server restart. MariaDB, hosted checks, old-binary compatibility and in-game acceptance must be verified separately. Descriptions already lost before this fix cannot be recovered from a column that did not exist; investigate other independent sources before promising recovery.

The SQLite suite also closes and reopens the database storage before checking the saved description. This is stronger than reusing the same connection, but it is still not a full server or client restart. A legacy-style SQL update preserves the column; it does not constitute an old-binary compatibility test.

Project-local SPEAR/EARS state tooling was not found in the checkout; this record provides the scoped requirements and evidence instead.
