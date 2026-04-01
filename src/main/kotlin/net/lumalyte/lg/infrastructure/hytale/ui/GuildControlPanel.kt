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
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import javax.annotation.Nonnull

/**
 * Guild Control Panel - Main UI for guild management
 *
 * Built using Hytale's native UI system with proper document paths.
 * Based on French documentation from hytale-docs.com
 *
 * UI File: src/main/resources/Common/UI/Custom/LumaGuilds/GuildControlPanel.ui
 */
class GuildControlPanel(@Nonnull playerRef: PlayerRef) :
    InteractiveCustomUIPage<GuildControlPanel.GuildEventData>(
        playerRef,
        CustomPageLifetime.CanDismiss,
        GuildEventData.CODEC
    ) {

    companion object {
        const val LAYOUT = "LumaGuilds/GuildControlPanel.ui"
        const val FEATURE_BUTTON_TEMPLATE = "LumaGuilds/FeatureButton.ui"
    }

    private val playerRef: PlayerRef = playerRef

    // Feature list for Phase 6, 7, 8, 9 & 10
    private val features = listOf(
        "Members Management" to "members",
        "Invitations System" to "invites",
        "Ranks & Permissions" to "ranks",
        "Guild Bank" to "bank",
        "Announcements" to "announcements",
        "Alliances" to "alliances",
        "Wars" to "wars",
        "Statistics" to "stats",
        "Guild Settings" to "settings",
        "Transfer Leadership" to "transfer",
        "Disband Guild" to "disband",
        "Leave Guild" to "leave",
        "Guild Chat" to "chat",
        "Territory/Claims" to "claims",
        "Guild Info" to "info"
    )

    override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    ) {
        // Load the main layout
        cmd.append(LAYOUT)

        // Get and set the guild name
        val guildService: GuildService = org.koin.java.KoinJavaComponent.get(GuildService::class.java)
        val guilds = guildService.getPlayerGuilds(playerRef.uuid)

        if (guilds.isNotEmpty()) {
            val guildName = guilds.first().name
            cmd.set("#GuildName.Text", guildName)
        } else {
            cmd.set("#GuildName.Text", "No Guild")
        }

        // Add feature buttons dynamically using template file (similar to MembersPage pattern)
        features.forEachIndexed { index, (featureName, action) ->
            // Append the feature button template
            cmd.append("#ContentArea", FEATURE_BUTTON_TEMPLATE)

            // Calculate the index for this button
            // Each button template creates a Group with button + spacer, so index matches directly
            val selector = "#ContentArea[$index]"

            // Set button text
            cmd.set("$selector #FeatureBtn.Text", featureName)

            // Bind click event to the button
            evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$selector #FeatureBtn",
                EventData().append("Action", action),
                false
            )
        }

        // Bind close button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            EventData().append("Action", "close"),
            false
        )

        playerRef.sendMessage(
            Message.raw("Guild Control Panel opened!").color("green")
        )
    }

    override fun handleDataEvent(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull data: GuildEventData
    ) {
        when (data.action) {
            "close" -> {
                this.close()
            }
            "members" -> {
                // Open the Members Management page
                // First, we need to get the player's guild
                val guildService: GuildService = org.koin.java.KoinJavaComponent.get(GuildService::class.java)
                val guilds = guildService.getPlayerGuilds(playerRef.uuid)

                if (guilds.isEmpty()) {
                    playerRef.sendMessage(
                        Message.raw("You are not in a guild!").color("red")
                    )
                    return
                }

                val guildId = guilds.first().id
                val membersPage = MembersPage(playerRef, guildId)

                // Close this page and open the members page
                this.close()

                // We need to open the members page on the correct thread
                // We'll need access to Player component to get PageManager
                val player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                if (player != null) {
                    player.pageManager.openCustomPage(ref, store, membersPage)
                }
            }
            "invites" -> {
                // Open the Invitations page
                val invitationsPage = InvitationsPage(playerRef)

                // Close this page and open the invitations page
                this.close()

                // Open the invitations page
                val player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                if (player != null) {
                    player.pageManager.openCustomPage(ref, store, invitationsPage)
                }
            }
            "leave" -> {
                // Get player's guild
                val guildService: GuildService = org.koin.java.KoinJavaComponent.get(GuildService::class.java)
                val guilds = guildService.getPlayerGuilds(playerRef.uuid)

                if (guilds.isEmpty()) {
                    playerRef.sendMessage(
                        Message.raw("You are not in a guild!").color("red")
                    )
                    return
                }

                val guildId = guilds.first().id
                val memberService: MemberService = org.koin.java.KoinJavaComponent.get(MemberService::class.java)

                // Try to remove the player from the guild
                // The service will check if the player is the owner and prevent removal
                if (memberService.removeMember(playerRef.uuid, guildId, playerRef.uuid)) {
                    playerRef.sendMessage(
                        Message.raw("You have left the guild.").color("green")
                    )
                    // Close the panel
                    this.close()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to leave the guild. You may be the guild owner - transfer leadership first.").color("red")
                    )
                }
            }
            "ranks" -> {
                // Open the Ranks & Permissions page
                val guildService: GuildService = org.koin.java.KoinJavaComponent.get(GuildService::class.java)
                val guilds = guildService.getPlayerGuilds(playerRef.uuid)

                if (guilds.isEmpty()) {
                    playerRef.sendMessage(
                        Message.raw("You are not in a guild!").color("red")
                    )
                    return
                }

                val guildId = guilds.first().id
                val ranksPage = RanksPage(playerRef, guildId)

                // Close this page and open the ranks page
                this.close()

                val player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                if (player != null) {
                    player.pageManager.openCustomPage(ref, store, ranksPage)
                }
            }
            "bank" -> {
                playerRef.sendMessage(
                    Message.raw("Guild Bank - Use /guild bank commands for now!").color("yellow")
                )
            }
            "announcements" -> {
                playerRef.sendMessage(
                    Message.raw("Guild Announcements - Use /guild announce commands for now!").color("yellow")
                )
            }
            "alliances" -> {
                playerRef.sendMessage(
                    Message.raw("Guild Alliances - Use /guild ally commands for now!").color("yellow")
                )
            }
            "wars" -> {
                playerRef.sendMessage(
                    Message.raw("Guild Wars - Use /guild war commands for now!").color("yellow")
                )
            }
            "stats" -> {
                playerRef.sendMessage(
                    Message.raw("Guild Statistics - Coming soon!").color("yellow")
                )
            }
            "settings" -> {
                playerRef.sendMessage(
                    Message.raw("Guild Settings UI - Use /guild commands for configuration!").color("yellow")
                )
            }
            "transfer" -> {
                playerRef.sendMessage(
                    Message.raw("Transfer Leadership - Use /guild transfer <player> command!").color("yellow")
                )
            }
            "disband" -> {
                // Get player's guild
                val guildService: GuildService = org.koin.java.KoinJavaComponent.get(GuildService::class.java)
                val guilds = guildService.getPlayerGuilds(playerRef.uuid)

                if (guilds.isEmpty()) {
                    playerRef.sendMessage(
                        Message.raw("You are not in a guild!").color("red")
                    )
                    return
                }

                val guildId = guilds.first().id
                val memberService: MemberService = org.koin.java.KoinJavaComponent.get(MemberService::class.java)

                // Check if player is the owner
                val highestRank = memberService.getPlayerRankId(playerRef.uuid, guildId)
                val ownerRank = org.koin.java.KoinJavaComponent.get<net.lumalyte.lg.application.persistence.RankRepository>(
                    net.lumalyte.lg.application.persistence.RankRepository::class.java
                ).getHighestRank(guildId)

                if (highestRank != ownerRank?.id) {
                    playerRef.sendMessage(
                        Message.raw("Only the guild owner can disband the guild!").color("red")
                    )
                    return
                }

                playerRef.sendMessage(
                    Message.raw("WARNING: Disbanding the guild is permanent!").color("red")
                )
                playerRef.sendMessage(
                    Message.raw("Use /guild disband to permanently delete your guild.").color("yellow")
                )
            }
            "chat" -> {
                playerRef.sendMessage(
                    Message.raw("Use /guild chat commands for guild chat!").color("green")
                )
            }
            "claims" -> {
                playerRef.sendMessage(
                    Message.raw("Use /claim commands to manage territory!").color("green")
                )
            }
            "info" -> {
                playerRef.sendMessage(
                    Message.raw("Use /guild info to view guild information!").color("green")
                )
            }
        }
    }

    // Event data class with codec
    class GuildEventData {
        companion object {
            val CODEC: BuilderCodec<GuildEventData> = BuilderCodec.builder(
                GuildEventData::class.java
            ) { GuildEventData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .build()
        }

        var action: String? = null
    }
}

/*
 * FEATURE ROADMAP:
 *
 * Phase 6 - Core Social (HIGH PRIORITY - TO IMPLEMENT):
 * - Members Management (view, kick, promote)
 * - Invitations System (send, accept, decline)
 * - Leave Guild
 * - Guild Chat Settings
 *
 * Phase 7 - Management & Permissions:
 * - Ranks & Permissions
 * - Guild Settings
 * - Transfer Leadership
 * - Disband Guild
 *
 * Already Implemented:
 * - Territory/Claims System (Phase 5 Complete)
 */
