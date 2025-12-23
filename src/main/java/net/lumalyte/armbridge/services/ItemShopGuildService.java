package net.lumalyte.armbridge.services;

import org.bukkit.Location;
import java.util.UUID;

/**
 * Compile-time stub for ItemShopGuildService.
 */
public interface ItemShopGuildService {
    boolean registerGuildItemShop(Location shopLocation, UUID guildId, UUID playerUuid);
    UUID getGuildForItemShop(Location shopLocation);
}
