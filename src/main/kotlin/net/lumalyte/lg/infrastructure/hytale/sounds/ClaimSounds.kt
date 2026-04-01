package net.lumalyte.lg.infrastructure.hytale.sounds

import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.protocol.Position
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent

/**
 * Sound effects for the claim system.
 *
 * Sound Selection Rationale:
 * - CLAIM_CREATE: Discovery sound for new territory claimed
 * - CLAIM_DELETE: Avatar disable sound for protection removal
 * - CLAIM_EXPAND: Crafting complete for successful expansion
 * - CLAIM_VIOLATION: Processing failed sound for denied action
 * - CLAIM_INFO: Chest open sound for viewing information
 * - CLAIM_SUCCESS: Workbench craft for successful operations
 * - CLAIM_BORDER: Subtle inventory drag for border visualization
 */
object ClaimSounds {

    // Sound event names (from assets.zip)
    private const val SOUND_CLAIM_CREATE = "SFX/UI/Discovery/SFX_Discovery_Z1_Medium"
    private const val SOUND_CLAIM_DELETE = "SFX/Magic/Avatar/SFX_Avatar_Powers_Disable"
    private const val SOUND_CLAIM_EXPAND = "SFX/Crafting/SFX_Workbench_Upgrade_Complete_Default"
    private const val SOUND_CLAIM_VIOLATION = "SFX/UI/Interactions/Benches/SFX_Furnace_Bench_Processing_Failed"
    private const val SOUND_CLAIM_INFO = "SFX/UI/Interactions/Chests/SFX_Chest_Wooden_Open"
    private const val SOUND_CLAIM_SUCCESS = "SFX/UI/Interactions/Benches/SFX_Workbench_Craft"
    private const val SOUND_CLAIM_BORDER = "SFX/UI/Inventory/SFX_Drag_Items_Gems"
    private const val SOUND_CLAIM_ENABLE = "SFX/Magic/Avatar/SFX_Avatar_Powers_Enable"

    // Cached sound IDs (loaded at runtime)
    private var claimCreateId: Int? = null
    private var claimDeleteId: Int? = null
    private var claimExpandId: Int? = null
    private var claimViolationId: Int? = null
    private var claimInfoId: Int? = null
    private var claimSuccessId: Int? = null
    private var claimBorderId: Int? = null
    private var claimEnableId: Int? = null

    /**
     * Initialize sound IDs from the asset registry.
     * Call this after assets are loaded.
     */
    fun initialize() {
        claimCreateId = getSoundId(SOUND_CLAIM_CREATE)
        claimDeleteId = getSoundId(SOUND_CLAIM_DELETE)
        claimExpandId = getSoundId(SOUND_CLAIM_EXPAND)
        claimViolationId = getSoundId(SOUND_CLAIM_VIOLATION)
        claimInfoId = getSoundId(SOUND_CLAIM_INFO)
        claimSuccessId = getSoundId(SOUND_CLAIM_SUCCESS)
        claimBorderId = getSoundId(SOUND_CLAIM_BORDER)
        claimEnableId = getSoundId(SOUND_CLAIM_ENABLE)
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
     * Play claim creation sound (2D - UI feedback)
     */
    fun playClaimCreate(playerRef: PlayerRef) {
        play2D(playerRef, claimCreateId, volume = 0.8f, pitch = 1.0f)
    }

    /**
     * Play claim deletion sound (2D - UI feedback)
     */
    fun playClaimDelete(playerRef: PlayerRef) {
        play2D(playerRef, claimDeleteId, volume = 0.7f, pitch = 0.9f)
    }

    /**
     * Play claim expansion sound (2D - UI feedback)
     */
    fun playClaimExpand(playerRef: PlayerRef) {
        play2D(playerRef, claimExpandId, volume = 0.75f, pitch = 1.0f)
    }

    /**
     * Play claim violation sound (3D - positional warning)
     */
    fun playClaimViolation(playerRef: PlayerRef, x: Float, y: Float, z: Float) {
        play3D(playerRef, claimViolationId, x, y, z, volume = 0.6f, pitch = 0.8f)
    }

    /**
     * Play claim violation sound (2D - UI warning)
     */
    fun playClaimViolationUI(playerRef: PlayerRef) {
        play2D(playerRef, claimViolationId, volume = 0.6f, pitch = 0.8f)
    }

    /**
     * Play claim info sound (2D - UI feedback)
     */
    fun playClaimInfo(playerRef: PlayerRef) {
        play2D(playerRef, claimInfoId, volume = 0.5f, pitch = 1.0f)
    }

    /**
     * Play general success sound (2D - UI feedback)
     */
    fun playSuccess(playerRef: PlayerRef) {
        play2D(playerRef, claimSuccessId, volume = 0.6f, pitch = 1.1f)
    }

    /**
     * Play claim border sound (3D - subtle positional feedback)
     */
    fun playClaimBorder(playerRef: PlayerRef, x: Float, y: Float, z: Float) {
        play3D(playerRef, claimBorderId, x, y, z, volume = 0.4f, pitch = 1.2f)
    }

    /**
     * Play claim enable/activation sound (2D - UI feedback)
     */
    fun playClaimEnable(playerRef: PlayerRef) {
        play2D(playerRef, claimEnableId, volume = 0.7f, pitch = 1.0f)
    }

    /**
     * Play a 2D sound effect (UI sound, not positional)
     */
    private fun play2D(playerRef: PlayerRef, soundId: Int?, volume: Float = 1.0f, pitch: Float = 1.0f) {
        if (soundId == null) return

        val packet = PlaySoundEvent2D(
            soundId,
            SoundCategory.SFX,
            volume,
            pitch
        )

        playerRef.getPacketHandler().write(packet)
    }

    /**
     * Play a 3D sound effect (positional sound in the world)
     */
    private fun play3D(playerRef: PlayerRef, soundId: Int?, x: Float, y: Float, z: Float, volume: Float = 1.0f, pitch: Float = 1.0f) {
        if (soundId == null) return

        val packet = PlaySoundEvent3D(
            soundId,
            SoundCategory.SFX,
            Position(x.toDouble(), y.toDouble(), z.toDouble()),
            volume,
            pitch
        )

        playerRef.getPacketHandler().write(packet)
    }
}
