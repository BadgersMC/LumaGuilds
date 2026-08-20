package net.lumalyte.lg.domain.values

/**
 * Represents the expected behaviour of certain events in claims and the hierarchy of one permission to any others.
 */
enum class ClaimPermission(val nameKey: String, val loreKey: String) {
    /**
     * When a block is broken/placed by a player.
     */
    BUILD("permission.build.name", "permission.build.lore"),

    /**
     * When plants are harvested and replanted by a player.
     */
    HARVEST("permission.harvest.name", "permission.harvest.lore"),

    /**
     * When a container is opened by a player.
     */
    CONTAINER("permission.container.name", "permission.container.lore"),

    /**
     * When an item is taken or put in display blocks.
     */
    DISPLAY("permission.display.name", "permission.display.lore"),

    /**
     * When a vehicle is placed or destroyed.
     */
    VEHICLE("permission.vehicle.name", "permission.vehicle.lore"),

    /**
     * When the sign edit menu is opened.
     */
    SIGN("permission.sign.name", "permission.sign.lore"),

    /**
     * When a device used to activate redstone is interacted with by a player.
     */
    REDSTONE("permission.redstone.name", "permission.redstone.lore"),

    /**
     * When a door is opened by a player.
     */
    DOOR("permission.door.name", "permission.door.lore"),

    /**
     * When a villager or travelling merchant is traded with by a player.
     */
    TRADE("permission.trade.name", "permission.trade.lore"),

    /**
     * When a passive mob is interacted with.
     */
    HUSBANDRY("permission.husbandry.name", "permission.husbandry.lore"),

    /**
     * When an explosive is detonated by a player.
     */
    DETONATE("permission.detonate.name", "permission.detonate.lore"),

    /**
     * When an event is triggered by an omen effect.
     */
    EVENT("permission.event.name", "permission.event.lore"),

    /**
     * When a player sleeps in a bed or uses a respawn anchor.
     */
    SLEEP("permission.sleep.name", "permission.sleep.lore"),

    /**
     * When a player views the contents of an interactable block.
     */
    VIEW("permission.view.name", "permission.view.lore")
}
