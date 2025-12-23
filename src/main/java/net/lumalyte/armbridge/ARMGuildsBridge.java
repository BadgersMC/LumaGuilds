package net.lumalyte.armbridge;

/**
 * Compile-time stub for ARM-Guilds-Bridge main class.
 * Allows compilation without ARM-Guilds-Bridge as a dependency.
 * The actual ARM-Guilds-Bridge plugin must be present at runtime.
 */
public class ARMGuildsBridge extends org.bukkit.plugin.java.JavaPlugin {

    public net.lumalyte.armbridge.services.GuildShopService getGuildShopService() {
        throw new UnsupportedOperationException("Stub method - ARM-Guilds-Bridge required at runtime");
    }

    public net.lumalyte.armbridge.services.ItemShopGuildService getItemShopGuildService() {
        throw new UnsupportedOperationException("Stub method - ARM-Guilds-Bridge required at runtime");
    }
}
