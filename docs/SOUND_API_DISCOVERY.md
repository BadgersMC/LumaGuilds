# Hytale Sound API Discovery

## Problem Solved
Successfully discovered the correct API for playing sounds in Hytale server plugins.

## The Solution

### Sound Event ID Resolution
```kotlin
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent

fun getSoundEventIndex(soundName: String): Int? {
    val assetMap = SoundEvent.getAssetMap()
    val index = assetMap.getIndexOrDefault(soundName, -1)
    return if (index == -1) null else index
}
```

### Playing 2D Sounds (UI/Non-positional)
```kotlin
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.universe.PlayerRef

fun play2DSound(playerRef: PlayerRef, soundIndex: Int, volume: Float = 1.0f, pitch: Float = 1.0f) {
    val packet = PlaySoundEvent2D(
        soundIndex,
        SoundCategory.SFX,  // or .UI, .Music, .Ambient
        volume,
        pitch
    )
    playerRef.getPacketHandler().write(packet)
}
```

### Playing 3D Sounds (Positional in World)
```kotlin
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D
import com.hypixel.hytale.protocol.Position

fun play3DSound(playerRef: PlayerRef, soundIndex: Int, x: Double, y: Double, z: Double, volume: Float = 1.0f, pitch: Float = 1.0f) {
    val packet = PlaySoundEvent3D(
        soundIndex,
        SoundCategory.SFX,
        Position(x, y, z),
        volume,
        pitch
    )
    playerRef.getPacketHandler().write(packet)
}
```

## Key API Classes

### Sound Event Registry
- **Class**: `com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent`
- **Method**: `SoundEvent.getAssetMap()`
- **Returns**: `IndexedLookupTableAssetMap<String, SoundEvent>`

### Asset Map Methods
- `getIndex(String soundName)` - Get index, throws if not found
- `getIndexOrDefault(String soundName, int defaultValue)` - Get index or return default
- `getAsset(int index)` - Get SoundEvent by index
- `getNextIndex()` - Get next available index

### Sound Categories
```kotlin
enum class SoundCategory {
    Music,     // Background music
    Ambient,   // Environmental sounds
    SFX,       // Sound effects
    UI         // UI interaction sounds
}
```

### Packet Sending
- **PlayerRef API**: `playerRef.getPacketHandler().write(packet)`
- **NOT**: `sendPacket()` or `send()` - those don't exist
- **Method**: `write(Packet)` or `write(Packet...)`

## Sound Event Names

Sound events are located in `assets.zip` under `Server/Audio/SoundEvents/` as JSON files.

### Naming Convention
The sound name is the path without the extension:
- File: `Server/Audio/SoundEvents/SFX/UI/Discovery/SFX_Discovery_Z1_Medium.json`
- Name: `"SFX/UI/Discovery/SFX_Discovery_Z1_Medium"`

### Common Sound Locations
```
BlockSounds/           - Block interaction sounds (break, place, walk)
SFX/UI/               - User interface sounds
SFX/Magic/            - Magical effect sounds
SFX/Player/           - Player action sounds
SFX/NPC/              - NPC sounds
Music/                - Music tracks
```

## Complete Example

```kotlin
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent2D
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.universe.PlayerRef

object MySounds {
    private var successSoundId: Int? = null

    fun initialize() {
        val assetMap = SoundEvent.getAssetMap()
        successSoundId = assetMap.getIndexOrDefault("SFX/UI/Discovery/SFX_Discovery_Z1_Medium", -1)
            .takeIf { it != -1 }
    }

    fun playSuccess(playerRef: PlayerRef) {
        val soundId = successSoundId ?: return

        val packet = PlaySoundEvent2D(
            soundId,
            SoundCategory.UI,
            0.8f,  // volume
            1.0f   // pitch
        )

        playerRef.getPacketHandler().write(packet)
    }
}

// In plugin startup:
MySounds.initialize()

// In command/listener:
MySounds.playSuccess(playerRef)
```

## Discovery Process

### What We Tried
1. ❌ `CommonAssetRegistry.getByName()` - Returns CommonAsset without ID field
2. ❌ Looking for a global sound registry
3. ✅ Decompiled `SoundEvent` class - Found static `getAssetMap()` method
4. ✅ Checked `IndexedLookupTableAssetMap` API - Found `getIndex()` method
5. ✅ Tested packet structure - Confirmed `PlaySoundEvent2D/3D` with proper fields

### Key Insights
- Sound events use an indexed lookup table (not a simple map)
- The index is assigned when assets are loaded
- Each sound name maps to a unique integer index
- The `SoundEvent` class provides static access to the registry
- Packet sending uses `write()` not `sendPacket()`
- Position requires doubles, not floats
- SoundCategory is an enum (not a string)

## Asset Store Architecture

```
SoundEvent (static class)
    ├─ getAssetStore() → AssetStore
    └─ getAssetMap() → IndexedLookupTableAssetMap
           ├─ getIndex(String) → int
           ├─ getIndexOrDefault(String, int) → int
           ├─ getAsset(int) → SoundEvent
           └─ getNextIndex() → int
```

## Error Handling

### Missing Sound Events
```kotlin
val index = assetMap.getIndexOrDefault(soundName, -1)
if (index == -1) {
    println("Warning: Sound '$soundName' not found")
    return null
}
```

### Graceful Degradation
```kotlin
fun playSound(playerRef: PlayerRef, soundId: Int?) {
    if (soundId == null) return  // Silent failure
    // ... send packet
}
```

### Exception Handling
```kotlin
try {
    val assetMap = SoundEvent.getAssetMap()
    // ... use assetMap
} catch (e: Exception) {
    println("Failed to access sound assets: ${e.message}")
}
```

## Performance Considerations

1. **Cache sound IDs** - Lookup once at startup, not every playback
2. **Null checks** - Early return if sound ID is null
3. **Batch packets** - Use `write(Packet...)` for multiple sounds
4. **Limit instances** - Respect `maxInstance` from sound event config

## Testing

### Verify Sound Assets
```kotlin
val assetMap = SoundEvent.getAssetMap()
val index = assetMap.getIndexOrDefault("SFX/UI/Discovery/SFX_Discovery_Z1_Medium", -1)
println("Sound index: $index")  // Should be >= 0
```

### Debug Sound Playback
```kotlin
println("Playing sound ID $soundId to player ${playerRef.getUsername()}")
playerRef.getPacketHandler().write(packet)
println("Packet sent successfully")
```

### Check Asset Loading
The sound asset map is populated when the server loads assets from `assets.zip`.
If sounds don't play, verify:
1. Assets are loaded before calling `initialize()`
2. Sound names match exactly (case-sensitive)
3. Sound exists in `assets.zip`

## Resources

- Sound events location: `assets.zip/Server/Audio/SoundEvents/`
- Total available sounds: **1,153**
- Sound event format: JSON with layers, volume, pitch settings
- Packet IDs: See `PlaySoundEvent2D.PACKET_ID` and `PlaySoundEvent3D.PACKET_ID`

## Future Exploration

Other asset types likely follow the same pattern:
- `BlockType.getAssetMap()` - Block types
- `ItemType.getAssetMap()` - Item types
- `BlockSoundSet.getAssetMap()` - Block sound sets
- `ItemSoundSet.getAssetMap()` - Item sound sets

This pattern appears to be Hytale's standard approach for accessing indexed asset registries.
