package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import java.util.UUID

interface WarNotificationService {
    fun declarationCreated(declaration: WarDeclaration)
    fun warAccepted(war: War)
    fun warEnded(war: War)
    fun deliverUnread(playerId: UUID)
}
