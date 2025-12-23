package net.alex9849.arm.adapters;

import net.alex9849.arm.regions.Region;
import org.bukkit.Location;

/**
 * Compile-time stub for ARM's ARMVersionAdapter class.
 * This allows compilation without ARM as a dependency.
 * The actual ARM plugin must be present at runtime.
 */
public abstract class ARMVersionAdapter {

    /**
     * Get the region at a specific location
     * @param location The location to check
     * @return The ARM region at this location, or null if none exists
     */
    public abstract Region getRegion(Location location);

    /**
     * Get a region by ID and world name
     * @param regionId The WorldGuard region ID
     * @param worldName The world name
     * @return The ARM region, or null if not found
     */
    public abstract Region getRegion(String regionId, String worldName);
}
