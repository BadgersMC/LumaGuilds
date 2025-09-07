package net.lumalyte.lg.application.errors

import java.util.UUID

class PlayerNotFoundException(val playerId: UUID) : RuntimeException("Player with UUID $playerId not found.")
