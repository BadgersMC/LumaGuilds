package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.ClaimService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import org.bukkit.Bukkit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.CompletableFuture

/**
 * Provides tab completion suggestions for Brigadier commands.
 * Comprehensive suggestion system for all command arguments.
 */
object SuggestionProviders : KoinComponent {

    private val playerService: PlayerService by inject()
    private val claimService: ClaimService by inject()
    private val guildService: GuildService by inject()
    private val fileExportManager: FileExportManager by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    /**
     * Suggests online player names.
     */
    fun players(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.name.lowercase().startsWith(input)) {
                builder.suggest(player.name)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests players that can be trusted.
     */
    fun trustablePlayers(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.name.lowercase().startsWith(input)) {
                builder.suggest(player.name)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests guild member names for the current player's guild.
     */
    fun guildMembers(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        val sender = ctx.source.sender as? org.bukkit.entity.Player ?: return@SuggestionProvider builder.buildFuture()
        
        try {
            val guilds = guildService.getPlayerGuilds(sender.uniqueId)
            if (guilds.isNotEmpty()) {
                val guild = guilds.first()
                val members = memberService.getGuildMembers(guild.id)
                members.forEach { member ->
                    val playerName = Bukkit.getOfflinePlayer(member.playerId).name
                    if (playerName != null && playerName.lowercase().startsWith(input)) {
                        builder.suggest(playerName)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to online players if guild service fails
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.name.lowercase().startsWith(input)) {
                    builder.suggest(player.name)
                }
            }
        }
        
        builder.buildFuture()
    }

    /**
     * Suggests guild home names for the current player's guild.
     */
    fun guildHomes(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        val sender = ctx.source.sender as? org.bukkit.entity.Player ?: return@SuggestionProvider builder.buildFuture()
        
        try {
            val guilds = guildService.getPlayerGuilds(sender.uniqueId)
            if (guilds.isNotEmpty()) {
                val guild = guilds.first()
                val homes = guildService.getHomes(guild.id)
                homes.homeNames.forEach { homeName ->
                    if (homeName.lowercase().startsWith(input)) {
                        builder.suggest(homeName)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to common home names
            listOf("main", "spawn", "base", "home").forEach { homeName ->
                if (homeName.lowercase().startsWith(input)) {
                    builder.suggest(homeName)
                }
            }
        }
        
        builder.buildFuture()
    }

    /**
     * Suggests guild mode values.
     */
    fun guildModes(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        listOf("peaceful", "hostile", "private").forEach { mode ->
            if (mode.lowercase().startsWith(input)) {
                builder.suggest(mode)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests available claim flag names.
     */
    fun flags(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        listOf("pvp", "explosion", "fire", "mob-spawning", "monster-spawning", "animal-spawning", "mob-damage", "leaf-decay", "grass-growth", "mycelium-spread", "vine-growth").forEach { flag ->
            if (flag.lowercase().startsWith(input)) {
                builder.suggest(flag)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests claim names.
     */
    fun playerClaims(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        
        // Suggest generic claim names (player-specific claims not implemented yet)
        listOf("home", "base", "farm", "mine", "shop", "storage").forEach { claimName ->
            if (claimName.lowercase().startsWith(input)) {
                builder.suggest(claimName)
            }
        }
        
        builder.buildFuture()
    }

    /**
     * Suggests guild names.
     */
    fun guilds(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        
        try {
            val guilds = guildService.getAllGuilds()
            guilds.forEach { guild ->
                if (guild.name.lowercase().startsWith(input)) {
                    builder.suggest(guild.name)
                }
            }
        } catch (e: Exception) {
            // Fallback to common guild names
            listOf("TestGuild", "ExampleGuild", "MyGuild").forEach { guildName ->
                if (guildName.lowercase().startsWith(input)) {
                    builder.suggest(guildName)
                }
            }
        }
        
        builder.buildFuture()
    }

    /**
     * Suggests world names.
     */
    fun worlds(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        Bukkit.getWorlds().forEach { world ->
            if (world.name.lowercase().startsWith(input)) {
                builder.suggest(world.name)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests boolean values.
     */
    fun booleans(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        listOf("true", "false").forEach { bool ->
            if (bool.lowercase().startsWith(input)) {
                builder.suggest(bool)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests partition names.
     */
    fun partitions(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        listOf("default", "admin", "member", "guest").forEach { partition ->
            if (partition.lowercase().startsWith(input)) {
                builder.suggest(partition)
            }
        }
        builder.buildFuture()
    }

    /**
     * Suggests export file names for the current player.
     */
    fun exportFiles(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        val sender = ctx.source.sender as? org.bukkit.entity.Player ?: return@SuggestionProvider builder.buildFuture()
        
        try {
            val activeExports = fileExportManager.getActiveExports(sender.uniqueId)
            activeExports.forEach { fileName ->
                if (fileName.lowercase().startsWith(input)) {
                    builder.suggest(fileName)
                }
            }
        } catch (e: Exception) {
            // Fallback to common export file names
            listOf("guilds.csv", "members.csv", "claims.csv").forEach { fileName ->
                if (fileName.lowercase().startsWith(input)) {
                    builder.suggest(fileName)
                }
            }
        }
        
        builder.buildFuture()
    }

    /**
     * Suggests rank names for the current player's guild.
     */
    fun guildRanks(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        val sender = ctx.source.sender as? org.bukkit.entity.Player ?: return@SuggestionProvider builder.buildFuture()
        
        try {
            val guilds = guildService.getPlayerGuilds(sender.uniqueId)
            if (guilds.isNotEmpty()) {
                val guild = guilds.first()
                val ranks = rankService.getGuildRanks(guild.id)
                ranks.forEach { rank ->
                    if (rank.name.lowercase().startsWith(input)) {
                        builder.suggest(rank.name)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to common rank names
            listOf("Owner", "Admin", "Moderator", "Member", "Guest").forEach { rankName ->
                if (rankName.lowercase().startsWith(input)) {
                    builder.suggest(rankName)
                }
            }
        }
        
        builder.buildFuture()
    }

    /**
     * Suggests emoji names for guild customization.
     */
    fun guildEmojis(): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val input = builder.remainingLowerCase
        listOf("⚔", "🛡", "👑", "⭐", "🔥", "💎", "🏰", "⚡", "🌟", "🎯").forEach { emoji ->
            if (emoji.startsWith(input)) {
                builder.suggest(emoji)
            }
        }
        builder.buildFuture()
    }
}