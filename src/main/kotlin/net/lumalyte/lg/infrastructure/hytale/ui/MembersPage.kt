package net.lumalyte.lg.infrastructure.hytale.ui

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Member
import org.koin.java.KoinJavaComponent.get
import java.util.UUID
import javax.annotation.Nonnull

/**
 * Members Management Page - View and manage guild members
 *
 * Features:
 * - View all guild members with their ranks
 * - Promote members
 * - Demote members
 * - Kick members
 */
class MembersPage(
    @Nonnull playerRef: PlayerRef,
    private val guildId: UUID
) : InteractiveCustomUIPage<MembersPage.MemberEventData>(
    playerRef,
    CustomPageLifetime.CanDismiss,
    MemberEventData.CODEC
) {

    companion object {
        const val LAYOUT = "LumaGuilds/MembersPage.ui"
        const val MEMBER_ITEM_TEMPLATE = "LumaGuilds/MemberItem.ui"
    }

    private val playerRef: PlayerRef = playerRef
    private val memberService: MemberService = get(MemberService::class.java)
    private val playerService: PlayerService = get(PlayerService::class.java)
    private val guildService: GuildService = get(GuildService::class.java)

    override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    ) {
        // Load the main layout
        cmd.append(LAYOUT)

        // Get all guild members
        val members = memberService.getGuildMembers(guildId).sortedByDescending { member ->
            val rank = memberService.getPlayerRankId(member.playerId, guildId)
            // Sort by rank priority (you'd need to fetch rank details here)
            0 // Placeholder for rank priority
        }

        // Add each member to the list
        members.forEachIndexed { index, member ->
            // Add spacing between members (except first one)
            if (index > 0) {
                cmd.appendInline("#MemberList", """
                    Group { Anchor: (Height: 8); }
                """.trimIndent())
            }

            // Append the member item template
            cmd.append("#MemberList", MEMBER_ITEM_TEMPLATE)

            // Calculate the actual index (accounting for spacers)
            val itemIndex = if (index == 0) 0 else (index * 2)
            val selector = "#MemberList[$itemIndex]"

            // Get player name
            val playerName = playerService.getPlayerName(member.playerId) ?: "Unknown"

            // Get rank name
            val rankId = member.rankId
            val rankName = "Member" // TODO: Get actual rank name from rank repository

            // Set member info
            cmd.set("$selector #MemberName.Text", playerName)
            cmd.set("$selector #MemberRank.Text", rankName)

            // Bind promote button
            evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$selector #PromoteBtn",
                EventData()
                    .append("Action", "promote")
                    .append("PlayerId", member.playerId.toString()),
                false
            )

            // Bind demote button
            evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$selector #DemoteBtn",
                EventData()
                    .append("Action", "demote")
                    .append("PlayerId", member.playerId.toString()),
                false
            )

            // Bind kick button
            evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$selector #KickBtn",
                EventData()
                    .append("Action", "kick")
                    .append("PlayerId", member.playerId.toString()),
                false
            )
        }

        // Bind back button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BackBtn",
            EventData().append("Action", "back"),
            false
        )

        playerRef.sendMessage(
            Message.raw("Members Management opened!").color("green")
        )
    }

    override fun handleDataEvent(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull data: MemberEventData
    ) {
        val actorId = playerRef.uuid

        when (data.action) {
            "back" -> {
                // Close this page and return to main guild menu
                this.close()
            }
            "promote" -> {
                val targetPlayerId = UUID.fromString(data.playerId)
                if (memberService.promoteMember(targetPlayerId, guildId, actorId)) {
                    playerRef.sendMessage(
                        Message.raw("Member promoted successfully!").color("green")
                    )
                    // Refresh the page
                    rebuild()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to promote member. Check permissions.").color("red")
                    )
                }
            }
            "demote" -> {
                val targetPlayerId = UUID.fromString(data.playerId)
                if (memberService.demoteMember(targetPlayerId, guildId, actorId)) {
                    playerRef.sendMessage(
                        Message.raw("Member demoted successfully!").color("green")
                    )
                    // Refresh the page
                    rebuild()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to demote member. Check permissions.").color("red")
                    )
                }
            }
            "kick" -> {
                val targetPlayerId = UUID.fromString(data.playerId)
                val targetPlayerName = playerService.getPlayerName(targetPlayerId) ?: "Unknown"

                if (memberService.removeMember(targetPlayerId, guildId, actorId)) {
                    playerRef.sendMessage(
                        Message.raw("$targetPlayerName has been kicked from the guild!").color("green")
                    )
                    // Refresh the page
                    rebuild()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to kick member. Check permissions.").color("red")
                    )
                }
            }
        }
    }

    // Event data class with codec
    class MemberEventData {
        companion object {
            val CODEC: BuilderCodec<MemberEventData> = BuilderCodec.builder(
                MemberEventData::class.java
            ) { MemberEventData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .append(
                    KeyedCodec("PlayerId", Codec.STRING),
                    { e, v -> e.playerId = v },
                    { e -> e.playerId }
                )
                .add()
                .build()
        }

        var action: String? = null
        var playerId: String? = null
    }
}
