package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.hook.channel.ChannelProvider
import dev.rosewood.rosechat.chat.channel.Channel

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

    override fun getChannels(): List<Class<out Channel>> =
        listOf(LumaGuildsChannel::class.java)

    override fun getChannelGenerator(): Class<out Channel> =
        LumaGuildsChannel::class.java
}
