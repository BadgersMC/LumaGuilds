package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.infrastructure.i18n.bedrock

internal fun ClaimPermission.bedrockLabel(lang: LangService): String = when (this) {
    ClaimPermission.BUILD -> lang.bedrock("permission.build.name")
    ClaimPermission.HARVEST -> lang.bedrock("permission.harvest.name")
    ClaimPermission.CONTAINER -> lang.bedrock("permission.container.name")
    ClaimPermission.DISPLAY -> lang.bedrock("permission.display.name")
    ClaimPermission.VEHICLE -> lang.bedrock("permission.vehicle.name")
    ClaimPermission.SIGN -> lang.bedrock("permission.sign.name")
    ClaimPermission.REDSTONE -> lang.bedrock("permission.redstone.name")
    ClaimPermission.DOOR -> lang.bedrock("permission.door.name")
    ClaimPermission.TRADE -> lang.bedrock("permission.trade.name")
    ClaimPermission.HUSBANDRY -> lang.bedrock("permission.husbandry.name")
    ClaimPermission.DETONATE -> lang.bedrock("permission.detonate.name")
    ClaimPermission.EVENT -> lang.bedrock("permission.event.name")
    ClaimPermission.SLEEP -> lang.bedrock("permission.sleep.name")
    ClaimPermission.VIEW -> lang.bedrock("permission.view.name")
}
