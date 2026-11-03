package net.lumalyte.lg.infrastructure.services

import java.time.Duration

/** Reading a menu does not expire a purchase quote: business deadlines stay in their services. */
internal val GUILD_DIALOG_LIFETIME: Duration = Duration.ofHours(12)
