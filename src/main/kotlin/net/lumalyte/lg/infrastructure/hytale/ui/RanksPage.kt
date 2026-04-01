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
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.RankPermission
import org.koin.java.KoinJavaComponent.get
import java.util.UUID
import javax.annotation.Nonnull

/**
 * Ranks & Permissions Page - View and manage guild ranks
 *
 * Features:
 * - View all guild ranks with priorities
 * - See permission counts for each rank
 * - Edit permissions (placeholder - would need complex UI)
 * - Rename ranks
 * - Delete ranks
 * - Add new ranks
 */
class RanksPage(
    @Nonnull playerRef: PlayerRef,
    private val guildId: UUID
) : InteractiveCustomUIPage<RanksPage.RankEventData>(
    playerRef,
    CustomPageLifetime.CanDismiss,
    RankEventData.CODEC
) {

    companion object {
        const val LAYOUT = "LumaGuilds/RanksPage.ui"
        const val RANK_ITEM_TEMPLATE = "LumaGuilds/RankItem.ui"
    }

    private val playerRef: PlayerRef = playerRef
    private val rankService: RankService = get(RankService::class.java)

    override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    ) {
        // Load the main layout
        cmd.append(LAYOUT)

        // Check if player has permission to manage ranks
        val hasPermission = rankService.hasPermission(playerRef.uuid, guildId, RankPermission.MANAGE_RANKS)

        // Get all ranks for the guild, sorted by priority
        val ranks = rankService.listRanks(guildId).sortedBy { it.priority }

        if (ranks.isEmpty()) {
            // Show "no ranks" message
            cmd.appendInline("#RankList", """
                Group {
                    FlexWeight: 1;
                    LayoutMode: Center;

                    Label {
                        Text: "No ranks found";
                        Style: (FontSize: 16, TextColor: #96a9be, HorizontalAlignment: Center);
                    }
                }
            """.trimIndent())
        } else {
            // Add each rank to the list
            ranks.forEachIndexed { index, rank ->
                // Add spacing between ranks (except first one)
                if (index > 0) {
                    cmd.appendInline("#RankList", """
                        Group { Anchor: (Height: 8); }
                    """.trimIndent())
                }

                // Append the rank item template
                cmd.append("#RankList", RANK_ITEM_TEMPLATE)

                // Calculate the actual index (accounting for spacers)
                val itemIndex = if (index == 0) 0 else (index * 2)
                val selector = "#RankList[$itemIndex]"

                // Set rank info
                cmd.set("$selector #RankName.Text", rank.name)
                cmd.set("$selector #RankPriority.Text", "Priority: ${rank.priority}")
                cmd.set("$selector #PermissionsSummary.Text", "Permissions: ${rank.permissions.size}")

                // Bind edit button (for now, just shows message - full UI would be complex)
                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "$selector #EditBtn",
                    EventData()
                        .append("Action", "edit")
                        .append("RankId", rank.id.toString()),
                    false
                )

                // Bind rename button
                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "$selector #RenameBtn",
                    EventData()
                        .append("Action", "rename")
                        .append("RankId", rank.id.toString()),
                    false
                )

                // Bind delete button
                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "$selector #DeleteBtn",
                    EventData()
                        .append("Action", "delete")
                        .append("RankId", rank.id.toString()),
                    false
                )
            }
        }

        // Bind add rank button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#AddRankBtn",
            EventData().append("Action", "add"),
            false
        )

        // Bind back button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BackBtn",
            EventData().append("Action", "back"),
            false
        )

        playerRef.sendMessage(
            Message.raw("Ranks & Permissions page opened!").color("green")
        )
    }

    override fun handleDataEvent(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull data: RankEventData
    ) {
        val actorId = playerRef.uuid

        // Check if player has permission
        val hasPermission = rankService.hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)
        if (!hasPermission && data.action != "back") {
            playerRef.sendMessage(
                Message.raw("You don't have permission to manage ranks!").color("red")
            )
            return
        }

        when (data.action) {
            "back" -> {
                // Close this page
                this.close()
            }
            "edit" -> {
                // For now, just show a message
                // Full implementation would need a complex permission editor UI
                playerRef.sendMessage(
                    Message.raw("Permission editing UI coming soon! Use /guild rank commands for now.").color("yellow")
                )
            }
            "rename" -> {
                // For now, just show a message
                // Full implementation would need text input UI
                playerRef.sendMessage(
                    Message.raw("Rank renaming UI coming soon! Use /guild rank rename <rank> <newname> for now.").color("yellow")
                )
            }
            "delete" -> {
                val rankId = UUID.fromString(data.rankId)
                if (rankService.deleteRank(rankId, actorId)) {
                    playerRef.sendMessage(
                        Message.raw("Rank deleted successfully!").color("green")
                    )
                    // Refresh the page
                    rebuild()
                } else {
                    playerRef.sendMessage(
                        Message.raw("Failed to delete rank. It may be the owner or default rank, or you may not have permission.").color("red")
                    )
                }
            }
            "add" -> {
                // For now, just show a message
                // Full implementation would need text input UI
                playerRef.sendMessage(
                    Message.raw("Add rank UI coming soon! Use /guild rank create <name> for now.").color("yellow")
                )
            }
        }
    }

    // Event data class with codec
    class RankEventData {
        companion object {
            val CODEC: BuilderCodec<RankEventData> = BuilderCodec.builder(
                RankEventData::class.java
            ) { RankEventData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { e, v -> e.action = v },
                    { e -> e.action }
                )
                .add()
                .append(
                    KeyedCodec("RankId", Codec.STRING),
                    { e, v -> e.rankId = v },
                    { e -> e.rankId }
                )
                .add()
                .build()
        }

        var action: String? = null
        var rankId: String? = null
    }
}
