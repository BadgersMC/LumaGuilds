package net.alex9849.arm.regions;

import org.bukkit.World;

import java.util.UUID;

/**
 * Compile-time stub for ARM's Region class.
 * This allows compilation without ARM as a dependency.
 * The actual ARM plugin must be present at runtime.
 */
public abstract class Region {

    /**
     * Get the WorldGuard region ID
     */
    public abstract String getId();

    /**
     * Get the world this region is in
     */
    public abstract World getRegionworld();

    /**
     * Get the price per period (rent/purchase price)
     */
    public abstract double getPricePerPeriod();

    /**
     * Get the landlord UUID (who receives shop income)
     */
    public abstract UUID getLandlord();

    /**
     * Set the landlord UUID (who will receive shop income)
     */
    public abstract void setLandlord(UUID landlord);

    /**
     * Check if this region is currently sold/rented
     */
    public abstract boolean isSold();

    /**
     * Get the current owner UUID
     */
    public abstract UUID getOwner();
}
