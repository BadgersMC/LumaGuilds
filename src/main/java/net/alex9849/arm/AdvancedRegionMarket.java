package net.alex9849.arm;

import net.alex9849.arm.adapters.ARMVersionAdapter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Compile-time stub for ARM's main plugin class.
 * This allows compilation without ARM as a dependency.
 * The actual ARM plugin must be present at runtime.
 */
public class AdvancedRegionMarket extends JavaPlugin {

    private static AdvancedRegionMarket instance;

    /**
     * Get the plugin instance
     * @return ARM plugin instance
     */
    public static AdvancedRegionMarket getInstance() {
        return instance;
    }

    /**
     * Get the version adapter for region lookups
     * @return The ARMVersionAdapter instance
     */
    public ARMVersionAdapter getAdapterHandler() {
        throw new UnsupportedOperationException("Stub method - ARM plugin required at runtime");
    }
}
