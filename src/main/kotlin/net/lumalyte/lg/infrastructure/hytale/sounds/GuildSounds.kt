package net.lumalyte.lg.infrastructure.hytale.sounds

import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent

/**
 * Sound effects for the guild system.
 *
 * Sound Selection Rationale:
 * - INVITATION_SENT: Discovery sound for sending an invitation
 * - INVITATION_RECEIVED: Chime sound for receiving an invitation
 * - INVITATION_ACCEPTED: Success sound for joining a guild
 * - INVITATION_DECLINED: Soft decline sound
 * - GUILD_CREATED: Epic discovery sound for creating a guild
 */
object GuildSounds {

    // Sound event names (from assets.zip)
    private const val SOUND_INVITATION_SENT = "SFX/UI/Discovery/SFX_Discovery_Z1_Medium"
    private const val SOUND_INVITATION_RECEIVED = "SFX/UI/Interactions/Benches/SFX_Workbench_Craft"
    private const val SOUND_INVITATION_ACCEPTED = "SFX/Crafting/SFX_Workbench_Upgrade_Complete_Default"
    private const val SOUND_INVITATION_DECLINED = "SFX/UI/Interactions/Benches/SFX_Furnace_Bench_Processing_Failed"
    private const val SOUND_GUILD_CREATED = "SFX/UI/Discovery/SFX_Discovery_Z1_Medium"

    // Cached sound IDs (loaded at runtime)
    private var invitationSentId: Int? = null
    private var invitationReceivedId: Int? = null
    private var invitationAcceptedId: Int? = null
    private var invitationDeclinedId: Int? = null
    private var guildCreatedId: Int? = null

    /**
     * Initialize sound IDs from the asset registry.
     * Call this after assets are loaded.
     */
    fun initialize() {
        invitationSentId = getSoundId(SOUND_INVITATION_SENT)
        invitationReceivedId = getSoundId(SOUND_INVITATION_RECEIVED)
        invitationAcceptedId = getSoundId(SOUND_INVITATION_ACCEPTED)
        invitationDeclinedId = getSoundId(SOUND_INVITATION_DECLINED)
        guildCreatedId = getSoundId(SOUND_GUILD_CREATED)
    }

    private fun getSoundId(soundName: String): Int? {
        return try {
            val assetMap = SoundEvent.getAssetMap()
            val index = assetMap.getIndexOrDefault(soundName, -1)
            if (index == -1) {
                println("Warning: Sound event not found in asset map: $soundName")
                null
            } else {
                index
            }
        } catch (e: Exception) {
            println("Error looking up sound event '$soundName': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Play invitation sent sound (2D - UI feedback)
     */
    fun playInvitationSent(playerRef: PlayerRef) {
        play2D(playerRef, invitationSentId, volume = 0.7f, pitch = 1.0f)
    }

    /**
     * Play invitation received sound (2D - UI notification)
     */
    fun playInvitationReceived(playerRef: PlayerRef) {
        play2D(playerRef, invitationReceivedId, volume = 0.8f, pitch = 1.2f)
    }

    /**
     * Play invitation accepted sound (2D - UI success)
     */
    fun playInvitationAccepted(playerRef: PlayerRef) {
        play2D(playerRef, invitationAcceptedId, volume = 0.8f, pitch = 1.0f)
    }

    /**
     * Play invitation declined sound (2D - UI feedback)
     */
    fun playInvitationDeclined(playerRef: PlayerRef) {
        play2D(playerRef, invitationDeclinedId, volume = 0.5f, pitch = 0.9f)
    }

    /**
     * Play guild created sound (2D - UI celebration)
     */
    fun playGuildCreated(playerRef: PlayerRef) {
        play2D(playerRef, guildCreatedId, volume = 0.9f, pitch = 1.0f)
    }

    /**
     * Play a 2D sound effect (UI sound, not positional)
     */
    private fun play2D(playerRef: PlayerRef, soundId: Int?, volume: Float = 1.0f, pitch: Float = 1.0f) {
        if (soundId == null) return

        val packet = PlaySoundEvent2D(
            soundId,
            SoundCategory.UI,
            volume,
            pitch
        )

        playerRef.getPacketHandler().write(packet)
    }
}
