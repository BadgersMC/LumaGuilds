package net.lumalyte.lg.domain.values

/**
 * Represents the expected behaviour of certain events in claims that do not pertain to players.
 */
enum class Flag(val nameKey: String, val loreKey: String) {
    /**
     * When fire can spread from one block to another in the claim.
     */
    FIRE("flag.fire.name",
        "flag.fire.lore"),

    /**
     * When a mob destroys or otherwise changes blocks in the claim.
     */
    MOB("flag.mob.name",
        "flag.mob.lore"),

    /**
     * When TNT or other entities explode blocks in the claim.
     */
    EXPLOSION("flag.explosion.name",
        "flag.explosion.lore"),

    /**
     * When a piston placed outside of the claim can move blocks in the claim.
     */
    PISTON("flag.piston.name",
        "flag.piston.lore"),

    /**
     * When fluids can flow into the claim.
     */
    FLUID("flag.fluid.name",
        "flag.fluid.lore"),

    /**
     * When trees planted outside a claim grows into the claim.
     */
    TREE("flag.tree.name",
        "flag.tree.lore"),

    /**
     * When sculk placed outside a claim spreads into the claim.
     */
    SCULK("flag.sculk.name",
        "flag.sculk.lore"),

    /**
     * When dispensers dispense into the claim.
     */
    DISPENSER("flag.dispenser.name",
        "flag.dispenser.lore"),

    /**
     * When sponge placed outside the claim can drain water in the claim.
     */
    SPONGE("flag.sponge.name",
        "flag.sponge.lore"),

    /**
     * When lighting can cause damage to a claim.
     */
    LIGHTNING("flag.lightning.name",
        "flag.lightning.lore"),

    /**
     * When falling blocks can materialise when landing in a claim.
     */
    FALLING_BLOCK("flag.falling_block.name",
        "flag.falling_block.lore"),

    /**
     * When passive entities can be placed in vehicles in a claim.
     */
    PASSIVE_ENTITY_VEHICLE("flag.passive_entity_vehicle.name",
        "flag.passive_entity_vehicle.lore")
}
