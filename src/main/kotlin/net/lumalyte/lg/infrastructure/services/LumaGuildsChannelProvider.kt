package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.RoseChat
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.hook.channel.ChannelProvider
import dev.rosewood.rosechat.manager.ChannelManager
import dev.rosewood.rosechat.lib.rosegarden.config.CommentedConfigurationSection

/**
 * RoseChat [ChannelProvider] that tells RoseChat's ChannelManager that
 * channels.yml sections with `plugin: LumaGuilds` should be handled by
 * [LumaGuildsChannel].
 *
 * The runtime registers the provider before RoseChat reads the guild channels on a delayed reload.
 */
class LumaGuildsChannelProvider(
    override val channelDependencies: LumaGuildsChannelDependencies,
) : ChannelProvider(), LumaGuildsChannelDependencySource {

    internal fun unregisterFrom(manager: ChannelManager) {
        manager.channelProviders.entries.removeIf { it.value === this }
        manager.channels.entries.removeIf { it.value.provider === this }
        if (manager.defaultChannel?.provider === this) {
            manager.defaultChannel = manager.channels.values.firstOrNull { it.settings.isDefault }
        }
    }

    override fun getSupportedPlugin(): String = "LumaGuilds"

    override fun getChannels(): List<Class<out Channel>> = emptyList()

    override fun getChannelGenerator(): Class<out Channel> =
        LumaGuildsChannel::class.java

    override fun getConfigurationSection(): CommentedConfigurationSection =
        RoseChat.getInstance().getManager(ChannelManager::class.java).channelsConfig
}
