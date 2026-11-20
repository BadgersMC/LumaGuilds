package net.lumalyte.lg.infrastructure.web.handlers

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.GuildBannerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.infrastructure.web.dto.BannerDto
import net.lumalyte.lg.infrastructure.web.dto.BannerPatternDto
import java.time.Instant
import java.util.UUID

/** REQ-301: the live guild details behind [PrestigeHallFeed]. */
class PrestigeHallGuildsBukkit(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val progression: ProgressionRepository,
    private val banners: GuildBannerService,
) : PrestigeHallFeed.Guilds {
    private val miniMessage = MiniMessage.miniMessage()
    private val plain = PlainTextComponentSerializer.plainText()

    override fun name(guildId: UUID): String? = guildService.getGuild(guildId)?.name

    override fun tag(guildId: UUID): String? = guildService.getGuild(guildId)?.tag?.let { tag ->
        runCatching { plain.serialize(miniMessage.deserialize(tag)) }.getOrDefault(tag)
    }

    override fun level(guildId: UUID): Int = progression.getGuildProgression(guildId)?.currentLevel ?: 1

    override fun members(guildId: UUID): Map<UUID, Instant> =
        memberService.getGuildMembers(guildId).associate { it.playerId to it.joinedAt }

    override fun banner(guildId: UUID): BannerDto? = banners.getGuildBanner(guildId)?.designData?.let { design ->
        BannerDto(
            baseColor = design.baseColor.name,
            baseColorHex = design.baseColor.hexCode,
            patterns = design.patterns.map { BannerPatternDto(it.type, it.color.name, it.color.hexCode) },
        )
    }
}
