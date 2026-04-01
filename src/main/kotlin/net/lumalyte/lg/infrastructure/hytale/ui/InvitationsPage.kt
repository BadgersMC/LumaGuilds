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
import net.lumalyte.lg.application.services.InvitationService
import org.koin.java.KoinJavaComponent.get
import java.util.UUID
import javax.annotation.Nonnull

/**
 * Invitations Page - View and manage guild invitations
 *
 * Features:
 * - View all pending invitations
 * - Accept invitations
 * - Decline invitations
 */
class InvitationsPage(@Nonnull playerRef: PlayerRef) :
    InteractiveCustomUIPage<InvitationsPage.InvitationEventData>(
        playerRef,
        CustomPageLifetime.CanDismiss,
        InvitationEventData.CODEC
    ) {

    companion object {
        const val LAYOUT = "LumaGuilds/InvitationsPage.ui"
        const val INVITATION_ITEM_TEMPLATE = "LumaGuilds/InvitationItem.ui"
    }

    private val playerRef: PlayerRef = playerRef
    private val invitationService: InvitationService = get(InvitationService::class.java)

    override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    ) {
        // Load the main layout
        cmd.append(LAYOUT)

        // Get all invitations for the player
        val invitations = invitationService.getPlayerInvitations(playerRef.uuid)

        if (invitations.isEmpty()) {
            // Show "no invitations" message
            cmd.appendInline("#InvitationList", """
                Group {
                    FlexWeight: 1;
                    LayoutMode: Center;

                    Label {
                        Text: "No pending invitations";
                        Style: (FontSize: 16, TextColor: #96a9be, HorizontalAlignment: Center);
                    }
                }
            """.trimIndent())
        } else {
            // Add each invitation to the list
            invitations.forEachIndexed { index, invitation ->
                // Add spacing between invitations (except first one)
                if (index > 0) {
                    cmd.appendInline("#InvitationList", """
                        Group { Anchor: (Height: 8); }
                    """.trimIndent())
                }

                // Append the invitation item template
                cmd.append("#InvitationList", INVITATION_ITEM_TEMPLATE)

                // Calculate the actual index (accounting for spacers)
                val itemIndex = if (index == 0) 0 else (index * 2)
                val selector = "#InvitationList[$itemIndex]"

                // Set invitation info
                cmd.set("$selector #GuildName.Text", invitation.guildName)
                cmd.set("$selector #InviterName.Text", invitation.inviterName)

                // Bind accept button
                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "$selector #AcceptBtn",
                    EventData()
                        .append("Action", "accept")
                        .append("GuildId", invitation.guildId.toString()),
                    false
                )

                // Bind decline button
                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "$selector #DeclineBtn",
                    EventData()
                        .append("Action", "decline")
                        .append("GuildId", invitation.guildId.toString()),
                    false
                )
            }
        }

        // Bind back button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BackBtn",
            EventData().append("Action", "back"),
            false
        )

        playerRef.sendMessage(
            Message.raw("Invitations page opened!").color("green")
        )
    }

    override fun handleDataEvent(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull data: InvitationEventData
    ) {
        val playerId = playerRef.uuid

        when (data.action) {
            "back" -> {
                // Close this page
                this.close()
            }
            "accept" -> {
                val guildId = UUID.fromString(data.guildId)
                if (invitationService.acceptInvitation(playerId, guildId)) {
                    playerRef.sendMessage(
                        Message.raw("You have joined the guild!").color("green")
                    )
                    // Refresh the page to show updated list
                    rebuild()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to accept invitation. You may already be in a guild.").color("red")
                    )
                }
            }
            "decline" -> {
                val guildId = UUID.fromString(data.guildId)
                if (invitationService.declineInvitation(playerId, guildId)) {
                    playerRef.sendMessage(
                        Message.raw("Invitation declined.").color("yellow")
                    )
                    // Refresh the page to show updated list
                    rebuild()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to decline invitation.").color("red")
                    )
                }
            }
        }
    }

    // Event data class with codec
    class InvitationEventData {
        companion object {
            val CODEC: BuilderCodec<InvitationEventData> = BuilderCodec.builder(
                InvitationEventData::class.java
            ) { InvitationEventData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .append(
                    KeyedCodec("GuildId", Codec.STRING),
                    { e, v -> e.guildId = v },
                    { e -> e.guildId }
                )
                .add()
                .build()
        }

        var action: String? = null
        var guildId: String? = null
    }
}
