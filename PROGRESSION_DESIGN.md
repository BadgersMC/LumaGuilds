# Progression System Configuration Design

## Overview
This document outlines the design for a fully configurable guild progression system using `progression.yml`.

## File Structure: `progression.yml`

```yaml
# =====================================
# LumaGuilds Progression Configuration
# Fully configurable guild leveling system
# =====================================

# =====================================
# Global Settings
# =====================================
progression:
  # Enable/disable the entire progression system
  enabled: true

  # Use claims-based perks (if false, uses alternative progression path)
  claims_based: true

  # Maximum guild level
  max_level: 30

  # XP Leveling Formula Configuration
  # Formula: base_xp * (level - 1)^level_exponent + (level * linear_bonus)
  leveling:
    base_xp: 800.0
    level_exponent: 1.3
    linear_bonus: 200

  # Rate limiting to prevent XP farming
  rate_limiting:
    xp_cooldown_ms: 5000
    max_xp_per_batch: 50

# =====================================
# Experience Sources
# Configure how guilds gain XP
# =====================================
experience_sources:
  # Guild-wide activities
  guild_activities:
    bank_deposit:
      enabled: true
      xp_per_100_coins: 1

    member_joined:
      enabled: true
      xp_amount: 50

    war_won:
      enabled: true
      xp_amount: 500

    war_lost:
      enabled: true
      xp_amount: 100

    claim_created:
      enabled: true  # Auto-disabled if claims_based: false
      xp_amount: 100

    alliance_formed:
      enabled: true
      xp_amount: 150

    party_created:
      enabled: true
      xp_amount: 25

  # Player activities (accumulated for guild)
  player_activities:
    player_kill:
      enabled: true
      xp_amount: 25

    mob_kill:
      enabled: true
      xp_amount: 2

    crop_harvest:
      enabled: true
      xp_amount: 1

    block_break:
      enabled: true
      xp_amount: 1

    block_place:
      enabled: true
      xp_amount: 1

    crafting:
      enabled: true
      xp_amount: 2

    smelting:
      enabled: true
      xp_amount: 2

    brewing:
      enabled: true
      xp_amount: 3

    fishing:
      enabled: true
      xp_amount: 3

    enchanting:
      enabled: true
      xp_amount: 10

# =====================================
# Level Rewards (Claims-Based)
# Rewards when claims system is enabled
# =====================================
level_rewards_claims:
  # Level 1 (Starting perks - always unlocked)
  1:
    perks:
      - CUSTOM_BANNER_COLORS
      - ANIMATED_EMOJIS
    claim_blocks: 0
    claim_count: 0
    bank_balance_limit: 50000
    bank_interest_rate: 0.0
    home_limit: 1
    member_limit: 10

  # Level 2
  2:
    perks:
      - INCREASED_CLAIM_BLOCKS
    claim_blocks: 100
    claim_count: 0
    bank_balance_limit: 75000
    bank_interest_rate: 0.0
    home_limit: 1
    member_limit: 10

  # Level 3
  3:
    perks:
      - FASTER_CLAIM_REGEN
    claim_blocks: 200
    claim_count: 0
    bank_balance_limit: 100000
    bank_interest_rate: 0.0
    home_limit: 1
    member_limit: 15

  # Level 5
  5:
    perks:
      - BANK_INTEREST
      - ANNOUNCEMENT_SOUND_EFFECTS
    claim_blocks: 500
    claim_count: 0
    bank_balance_limit: 150000
    bank_interest_rate: 0.01
    home_limit: 1
    member_limit: 20

  # Level 7
  7:
    perks:
      - REDUCED_WITHDRAWAL_FEES
    claim_blocks: 1000
    claim_count: 1
    bank_balance_limit: 200000
    bank_interest_rate: 0.01
    home_limit: 1
    member_limit: 25
    withdrawal_fee_multiplier: 0.5  # 50% of base fee

  # Level 10
  10:
    perks:
      - SPECIAL_PARTICLES
      - HOME_TELEPORT_SOUND_EFFECTS
    claim_blocks: 2000
    claim_count: 2
    bank_balance_limit: 300000
    bank_interest_rate: 0.015
    home_limit: 2
    member_limit: 30

  # Level 15
  15:
    perks:
      - ADDITIONAL_HOMES
    claim_blocks: 5000
    claim_count: 3
    bank_balance_limit: 500000
    bank_interest_rate: 0.015
    home_limit: 3
    member_limit: 40

  # Level 20
  20:
    perks:
      - TELEPORT_COOLDOWN_REDUCTION
      - WAR_DECLARATION_SOUND_EFFECTS
    claim_blocks: 10000
    claim_count: 5
    bank_balance_limit: 750000
    bank_interest_rate: 0.02
    home_limit: 4
    member_limit: 50
    home_cooldown_multiplier: 0.6  # 60% of base cooldown

  # Level 25
  25:
    perks:
      - HIGHER_BANK_BALANCE
    claim_blocks: 20000
    claim_count: 8
    bank_balance_limit: 1000000
    bank_interest_rate: 0.02
    home_limit: 5
    member_limit: 60

  # Level 30
  30:
    perks:
      - INCREASED_BANK_LIMIT
    claim_blocks: 35000
    claim_count: 12
    bank_balance_limit: 1500000
    bank_interest_rate: 0.025
    home_limit: 8
    member_limit: 75

# =====================================
# Level Rewards (Non-Claims Based)
# Alternative rewards when claims are disabled
# =====================================
level_rewards_no_claims:
  1:
    perks:
      - CUSTOM_BANNER_COLORS
      - ANIMATED_EMOJIS
    bank_balance_limit: 50000
    bank_interest_rate: 0.0
    home_limit: 1
    member_limit: 10
    war_slots: 3

  2:
    perks:
      - HIGHER_BANK_BALANCE
    bank_balance_limit: 100000
    bank_interest_rate: 0.005
    home_limit: 1
    member_limit: 15
    war_slots: 3

  3:
    perks:
      - BANK_INTEREST
    bank_balance_limit: 150000
    bank_interest_rate: 0.01
    home_limit: 1
    member_limit: 15
    war_slots: 3

  5:
    perks:
      - ANNOUNCEMENT_SOUND_EFFECTS
      - SPECIAL_PARTICLES
    bank_balance_limit: 250000
    bank_interest_rate: 0.015
    home_limit: 2
    member_limit: 20
    war_slots: 4

  7:
    perks:
      - REDUCED_WITHDRAWAL_FEES
      - ADDITIONAL_HOMES
    bank_balance_limit: 400000
    bank_interest_rate: 0.015
    home_limit: 3
    member_limit: 25
    war_slots: 4
    withdrawal_fee_multiplier: 0.5

  10:
    perks:
      - HOME_TELEPORT_SOUND_EFFECTS
      - TELEPORT_COOLDOWN_REDUCTION
    bank_balance_limit: 600000
    bank_interest_rate: 0.02
    home_limit: 4
    member_limit: 30
    war_slots: 5
    home_cooldown_multiplier: 0.6

  15:
    perks:
      - INCREASED_BANK_LIMIT
    bank_balance_limit: 1000000
    bank_interest_rate: 0.02
    home_limit: 6
    member_limit: 40
    war_slots: 6

  20:
    perks:
      - WAR_DECLARATION_SOUND_EFFECTS
    bank_balance_limit: 1500000
    bank_interest_rate: 0.025
    home_limit: 8
    member_limit: 50
    war_slots: 6
    war_cost_multiplier: 0.75  # 75% of base cost

  25:
    perks:
      - HIGHER_BANK_BALANCE
    bank_balance_limit: 2500000
    bank_interest_rate: 0.03
    home_limit: 10
    member_limit: 60
    war_slots: 8

  30:
    perks:
      - INCREASED_BANK_LIMIT
    bank_balance_limit: 5000000
    bank_interest_rate: 0.035
    home_limit: 12
    member_limit: 100
    war_slots: 10
    war_cost_multiplier: 0.5

# =====================================
# Perk Definitions
# Define what each perk does
# =====================================
perk_definitions:
  CUSTOM_BANNER_COLORS:
    name: "Custom Banner Colors"
    description: "Unlock custom guild banner color schemes"
    type: COSMETIC

  ANIMATED_EMOJIS:
    name: "Animated Emojis"
    description: "Use animated emojis in guild chat"
    type: COSMETIC

  INCREASED_CLAIM_BLOCKS:
    name: "Increased Claim Blocks"
    description: "Gain additional claim blocks for territory expansion"
    type: CLAIM

  FASTER_CLAIM_REGEN:
    name: "Faster Claim Regeneration"
    description: "Claims regenerate faster after being damaged"
    type: CLAIM

  BANK_INTEREST:
    name: "Bank Interest"
    description: "Earn passive interest on guild bank balance"
    type: ECONOMIC

  HIGHER_BANK_BALANCE:
    name: "Higher Bank Balance"
    description: "Increased maximum bank balance limit"
    type: ECONOMIC

  INCREASED_BANK_LIMIT:
    name: "Increased Bank Limit"
    description: "Further increased bank balance limit"
    type: ECONOMIC

  REDUCED_WITHDRAWAL_FEES:
    name: "Reduced Withdrawal Fees"
    description: "Lower fees when withdrawing from guild bank"
    type: ECONOMIC

  ADDITIONAL_HOMES:
    name: "Additional Homes"
    description: "Set more guild home locations"
    type: UTILITY

  TELEPORT_COOLDOWN_REDUCTION:
    name: "Teleport Cooldown Reduction"
    description: "Reduced cooldown between home teleports"
    type: UTILITY

  HOME_TELEPORT_SOUND_EFFECTS:
    name: "Home Teleport Sound Effects"
    description: "Custom sound effects when teleporting home"
    type: COSMETIC

  SPECIAL_PARTICLES:
    name: "Special Particles"
    description: "Unique particle effects for guild members"
    type: COSMETIC

  ANNOUNCEMENT_SOUND_EFFECTS:
    name: "Announcement Sound Effects"
    description: "Custom sounds for guild announcements"
    type: COSMETIC

  WAR_DECLARATION_SOUND_EFFECTS:
    name: "War Declaration Sound Effects"
    description: "Epic sound effects when declaring war"
    type: COSMETIC

# =====================================
# Multiplier Effects
# Additional bonuses that scale with level
# =====================================
multipliers:
  # Combat bonuses
  combat:
    player_kill_xp_bonus:
      10: 1.1   # Level 10: +10% kill XP
      20: 1.25  # Level 20: +25% kill XP
      30: 1.5   # Level 30: +50% kill XP

    war_victory_gold_bonus:
      15: 0.05  # Level 15: +5% of enemy bank on victory
      25: 0.10  # Level 25: +10% of enemy bank on victory

  # Economic bonuses
  economy:
    deposit_fee_reduction:
      7: 0.5    # Level 7: 50% deposit fees
      15: 0.25  # Level 15: 25% deposit fees
      25: 0.0   # Level 25: No deposit fees

  # Social bonuses
  social:
    member_join_xp_bonus:
      5: 1.5    # Level 5: +50% XP from new members
      10: 2.0   # Level 10: +100% XP from new members

# =====================================
# Activity Tracking
# Configure activity score calculations
# =====================================
activity_tracking:
  enabled: true

  # Weekly activity reset
  weekly_reset: true
  reset_day: MONDAY  # Day of week to reset activity

  # Activity score weights
  weights:
    member_count: 10
    active_members: 50
    claims_owned: 20
    kills_this_week: 25
    bank_deposits_this_week: 5
    relations_formed: 75
    wars_participated: 200

# =====================================
# Leaderboard Configuration
# =====================================
leaderboards:
  enabled: true

  # Leaderboard types
  types:
    - TOTAL_LEVEL
    - TOTAL_XP
    - WEEKLY_ACTIVITY
    - MONTHLY_ACTIVITY
    - BANK_BALANCE
    - MEMBER_COUNT
    - WAR_VICTORIES

  # Update frequency
  update_interval_minutes: 5

  # Display size
  top_guilds_display: 10

# =====================================
# Level-Up Rewards
# Additional rewards given on level up
# =====================================
level_up_rewards:
  # Every 5 levels
  milestone_5:
    broadcast: true
    broadcast_message: "&6&l{guild} has reached level {level}!"
    rewards:
      - type: COINS
        amount: 1000

  # Every 10 levels
  milestone_10:
    broadcast: true
    broadcast_message: "&e&l✨ {guild} has achieved level {level}! ✨"
    rewards:
      - type: COINS
        amount: 5000
      - type: ITEMS
        items:
          - material: DIAMOND
            amount: 10

  # Max level
  milestone_max:
    broadcast: true
    broadcast_message: "&4&l⚡ {guild} has reached MAX LEVEL! ⚡"
    rewards:
      - type: COINS
        amount: 25000
      - type: TITLE
        value: "Legendary Guild"
      - type: ITEMS
        items:
          - material: NETHER_STAR
            amount: 1
            name: "&6&lLegendary Guild Star"
            lore:
              - "&7Proof of reaching max level"
              - "&7{guild} - {date}"
```

## Implementation Notes

### Data Classes Needed

1. **ProgressionConfig.kt**
   - Main config container
   - Global settings
   - Experience source configs
   - Level reward maps

2. **LevelRewardConfig.kt**
   - Per-level reward configuration
   - Perk unlocks
   - Stat bonuses
   - Multipliers

3. **ExperienceSourceConfig.kt**
   - Enable/disable sources
   - XP amounts
   - Cooldowns

4. **PerkDefinitionConfig.kt**
   - Perk metadata
   - Type categorization
   - Descriptions

5. **ActivityTrackingConfig.kt**
   - Activity weights
   - Reset schedules
   - Leaderboard configs

### Loading Strategy

1. Load `progression.yml` on plugin enable
2. Parse into data classes
3. Cache in memory for fast access
4. Validate all configs on load
5. Provide `/guild progression reload` command

### Backward Compatibility

- Keep existing `progression` section in `config.yml` as fallback
- Migrate to `progression.yml` on first load
- Support both files during transition period

### Extensibility

- Easy to add new perk types
- Simple to adjust XP formulas
- Configurable multipliers per level
- Custom rewards per milestone

