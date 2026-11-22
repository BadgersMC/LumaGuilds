package net.lumalyte.lg.acceptance;

import net.lumalyte.lg.LumaGuilds;
import net.lumalyte.lg.application.persistence.QuestRepository;
import net.lumalyte.lg.application.services.GuildService;
import net.lumalyte.lg.application.services.QuestService;
import org.bukkit.plugin.Plugin;

/** Typed services from the Guilds instance pinned by the current probe operation. */
public final class ProbeServices {
    private final LumaGuilds provider;

    private ProbeServices(LumaGuilds provider) {
        this.provider = provider;
    }

    public static ProbeServices bind(Plugin plugin) {
        if (!(plugin instanceof LumaGuilds guilds) ||
                plugin.getClass().getClassLoader() != LumaGuilds.class.getClassLoader()) {
            throw new IllegalStateException("Probe services must come from the pinned Guilds plugin");
        }
        if (!guilds.isEnabled()) {
            throw new IllegalStateException("Guilds is disabled");
        }
        return new ProbeServices(guilds);
    }

    public GuildService getGuilds() {
        return provider.getGuildService();
    }

    public QuestRepository getRepository() {
        return provider.getQuestRepository();
    }

    public QuestService getQuests() {
        return provider.getQuestService();
    }
}
