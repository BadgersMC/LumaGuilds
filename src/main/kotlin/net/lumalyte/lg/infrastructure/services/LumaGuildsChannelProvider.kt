package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.RoseChat
import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.hook.channel.ChannelProvider
import dev.rosewood.rosechat.manager.ChannelManager
import dev.rosewood.rosegarden.config.CommentedConfigurationSection

/**
 * RoseChat [ChannelProvider] that tells RoseChat's ChannelManager that
 * channels.yml sections with `plugin: LumaGuilds` should be handled by
 * [LumaGuildsChannel].
 *
 * Registered in [LumaGuilds.onEnable] after Koin is initialised so that
 * guild/ally/modchat channels resolve from `channels.yml` on a delayed reload.
 */
class LumaGuildsChannelProvider : ChannelProvider {

    override fun getSupportedPlugin(): String = "LumaGuilds"

    override fun getChannels(): List<Class<out Channel>> = emptyList()

    override fun getChannelGenerator(): Class<out Channel> =
        LumaGuildsChannel::class.java

    override fun getConfigurationSection(): CommentedConfigurationSection =
        RoseChat.getInstance().getManager(ChannelManager::class.java).channelsConfig
}
