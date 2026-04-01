# Hytale Sound Events Reference

This document contains all available sound events in Hytale that can be used with the sound system.

## Total Sound Events

There are **1,153** sound events available in Hytale's asset system.

## Sound Event Structure

Sound events are defined in JSON format in `assets.zip` under `Server/Audio/SoundEvents/`. Each sound event contains:

- **Layers**: Array of sound layers, each with:
  - `Files`: Array of .ogg file paths to play randomly
  - `Volume`: Volume adjustment in decibels
  - `RandomSettings`: Randomization for pitch/volume
  - `StartDelay`: Delay before playing (ms)
  - `Probability`: Chance this layer plays (0-100)
- **Parent**: Parent sound event to inherit from
- **MaxInstance**: Maximum concurrent instances
- **PreventSoundInterruption**: Whether to prevent interruption

### Example Sound Event

```json
{
  "Layers": [
    {
      "Files": [
        "Sounds/Blocks/Bone/Bone_Break_01.ogg",
        "Sounds/Blocks/Bone/Bone_Break_02.ogg"
      ],
      "RandomSettings": {
        "MinPitch": -2,
        "MaxPitch": 2,
        "MinVolume": -2
      },
      "Volume": -2
    }
  ],
  "PreventSoundInterruption": true,
  "MaxInstance": 5,
  "Parent": "SFX_Attn_Quiet"
}
```

## Sound Event Categories

### Block Sounds
Block interaction sounds (break, build, hit, land, walk)

### Item Sounds
Item interaction sounds (equip, use, etc.)

### Music
Background music and ambient tracks

### NPC Sounds
NPC vocalizations and interactions

### UI Sounds
User interface interaction sounds

## Using Sound Events in Plugins

To play a sound event, you need to:

1. Get the sound event name (path without extension)
2. Look up the sound event ID from the CommonAssetRegistry
3. Create a PlaySoundEvent packet (2D or 3D)
4. Send it to the appropriate players

### Example Code

```kotlin
// Get sound event by name
val soundName = "BlockSounds/Bone/SFX_Bone_Break"
val soundAsset = CommonAssetRegistry.getByName(soundName)

if (soundAsset != null) {
    val soundId = soundAsset.id

    // Play 3D sound at location
    val packet = PlaySoundEvent3D()
    packet.soundEventId = soundId
    packet.position = Vector3f(x, y, z)
    packet.volume = 1.0f
    packet.pitch = 1.0f

    player.connection.sendPacket(packet)
}
```

## Full Sound Event List

See `sound_events_list.txt` for the complete list of all 1,153 sound events.

## Common Sound Events

### Block Interactions
- `BlockSounds/Stone/SFX_Stone_Break`
- `BlockSounds/Wood/SFX_Wood_Break`
- `BlockSounds/Grass/SFX_Grass_Break`
- `BlockSounds/Dirt/SFX_Dirt_Break`

### UI Sounds
- `UI/SFX_UI_Click`
- `UI/SFX_UI_Hover`
- `UI/SFX_UI_Open`
- `UI/SFX_UI_Close`

### Player Actions
- `Player/SFX_Player_Jump`
- `Player/SFX_Player_Land`
- `Player/SFX_Player_Hurt`

## Notes

- Sound event names are case-sensitive
- Paths use forward slashes (/)
- File extension (.json) should NOT be included in the sound name
- Sound IDs are assigned by the server at runtime
- The CommonAssetRegistry must be accessed after assets are loaded
