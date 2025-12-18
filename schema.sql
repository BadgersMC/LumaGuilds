-- =====================================
-- LumaGuilds MariaDB Schema
-- Complete database schema for production use
-- Version: 0.5.3 (Schema v15)
-- =====================================

-- Create schema version table
CREATE TABLE IF NOT EXISTS schema_version (
    version INT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Set schema version to 15
DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (15);

-- =====================================
-- Core Guild Tables
-- =====================================

-- Guilds table (main guild data)
CREATE TABLE IF NOT EXISTS guilds (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    banner TEXT,
    emoji VARCHAR(100),
    tag VARCHAR(50),
    home_world VARCHAR(255),
    home_x INT,
    home_y INT,
    home_z INT,
    level INT NOT NULL DEFAULT 1,
    bank_balance INT NOT NULL DEFAULT 0,
    mode VARCHAR(20) NOT NULL DEFAULT 'Hostile',
    mode_changed_at DATETIME,
    created_at DATETIME NOT NULL,
    -- Vault columns (v13)
    vault_status VARCHAR(20) DEFAULT 'NEVER_PLACED',
    vault_chest_world VARCHAR(255),
    vault_chest_x INT,
    vault_chest_y INT,
    vault_chest_z INT,
    -- LFG columns (v14)
    is_open TINYINT(1) DEFAULT 0,
    join_fee_enabled TINYINT(1) DEFAULT 0,
    join_fee_amount INT DEFAULT 0,
    INDEX idx_guilds_name (name),
    INDEX idx_guilds_mode (mode),
    INDEX idx_guilds_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ranks table (guild ranks/roles)
CREATE TABLE IF NOT EXISTS ranks (
    id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    permissions TEXT,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_ranks_guild_id (guild_id),
    INDEX idx_ranks_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Members table (guild membership)
CREATE TABLE IF NOT EXISTS members (
    player_id VARCHAR(36) NOT NULL,
    guild_id VARCHAR(36) NOT NULL,
    rank_id VARCHAR(36) NOT NULL,
    joined_at DATETIME NOT NULL,
    PRIMARY KEY (player_id, guild_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE,
    INDEX idx_members_guild_id (guild_id),
    INDEX idx_members_player_id (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Relations table (alliances, wars, truces)
CREATE TABLE IF NOT EXISTS relations (
    id VARCHAR(36) PRIMARY KEY,
    guild_a VARCHAR(36) NOT NULL,
    guild_b VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('Ally', 'Enemy', 'Truce', 'Neutral')),
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE,
    UNIQUE KEY unique_relation (guild_a, guild_b),
    INDEX idx_relations_guild_a (guild_a),
    INDEX idx_relations_guild_b (guild_b),
    INDEX idx_relations_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Guild invitations table (v11)
CREATE TABLE IF NOT EXISTS guild_invitations (
    guild_id VARCHAR(36) NOT NULL,
    guild_name VARCHAR(255) NOT NULL,
    invited_player_id VARCHAR(36) NOT NULL,
    inviter_player_id VARCHAR(36) NOT NULL,
    inviter_name VARCHAR(255) NOT NULL,
    timestamp DATETIME NOT NULL,
    PRIMARY KEY (invited_player_id, guild_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_guild_invitations_guild_id (guild_id),
    INDEX idx_guild_invitations_invited_player_id (invited_player_id),
    INDEX idx_guild_invitations_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Guild banners table
CREATE TABLE IF NOT EXISTS guild_banners (
    id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    name VARCHAR(255),
    base_color VARCHAR(50) NOT NULL,
    patterns TEXT NOT NULL,
    submitted_by VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_guild_banners_guild_id (guild_id),
    INDEX idx_guild_banners_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Party System Tables
-- =====================================

-- Parties table
CREATE TABLE IF NOT EXISTS parties (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255),
    guild_ids TEXT NOT NULL,
    leader_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME,
    -- Moderation columns (v15)
    muted_players TEXT DEFAULT '{}',
    banned_players TEXT DEFAULT '[]',
    INDEX idx_parties_leader_id (leader_id),
    INDEX idx_parties_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Party requests table
CREATE TABLE IF NOT EXISTS party_requests (
    id VARCHAR(36) PRIMARY KEY,
    from_guild_id VARCHAR(36) NOT NULL,
    to_guild_id VARCHAR(36) NOT NULL,
    requester_id VARCHAR(36) NOT NULL,
    message TEXT,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_party_requests_from_guild (from_guild_id),
    INDEX idx_party_requests_to_guild (to_guild_id),
    INDEX idx_party_requests_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Player party preferences table (v8)
CREATE TABLE IF NOT EXISTS player_party_preferences (
    player_id VARCHAR(36) PRIMARY KEY,
    party_id VARCHAR(36) NOT NULL,
    set_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Banking & Economy Tables
-- =====================================

-- Bank transactions table
CREATE TABLE IF NOT EXISTS bank_tx (
    id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('Deposit', 'Withdraw')),
    amount INT NOT NULL,
    fee INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_bank_tx_guild_id (guild_id),
    INDEX idx_bank_tx_actor_id (actor_id),
    INDEX idx_bank_tx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Vault System Tables (v12)
-- =====================================

-- Vault slots table (stores items)
CREATE TABLE IF NOT EXISTS vault_slots (
    guild_id VARCHAR(36) NOT NULL,
    slot INT NOT NULL,
    item_data TEXT,
    last_modified BIGINT NOT NULL,
    PRIMARY KEY (guild_id, slot),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_vault_slots_guild_id (guild_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vault gold balance table
CREATE TABLE IF NOT EXISTS vault_gold (
    guild_id VARCHAR(36) PRIMARY KEY,
    balance INT NOT NULL DEFAULT 0,
    last_modified BIGINT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Vault transaction log table
CREATE TABLE IF NOT EXISTS vault_transaction_log (
    id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount INT,
    item_data TEXT,
    slot INT,
    timestamp BIGINT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_vault_transaction_log_guild_id (guild_id),
    INDEX idx_vault_transaction_log_timestamp (timestamp),
    INDEX idx_vault_transaction_log_player_id (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Combat & War System Tables
-- =====================================

-- Kills table (v10 - updated schema)
CREATE TABLE IF NOT EXISTS kills (
    id VARCHAR(36) PRIMARY KEY,
    killer_id VARCHAR(36) NOT NULL,
    victim_id VARCHAR(36) NOT NULL,
    killer_guild_id VARCHAR(36),
    victim_guild_id VARCHAR(36),
    timestamp DATETIME NOT NULL,
    weapon VARCHAR(255),
    location_world VARCHAR(255),
    location_x DOUBLE,
    location_y DOUBLE,
    location_z DOUBLE,
    FOREIGN KEY (killer_guild_id) REFERENCES guilds(id) ON DELETE SET NULL,
    FOREIGN KEY (victim_guild_id) REFERENCES guilds(id) ON DELETE SET NULL,
    INDEX idx_kills_timestamp (timestamp),
    INDEX idx_kills_killer_guild (killer_guild_id),
    INDEX idx_kills_victim_guild (victim_guild_id),
    INDEX idx_kills_killer_id (killer_id),
    INDEX idx_kills_victim_id (victim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Guild kill stats table
CREATE TABLE IF NOT EXISTS guild_kill_stats (
    guild_id VARCHAR(36) PRIMARY KEY,
    total_kills INT NOT NULL DEFAULT 0,
    total_deaths INT NOT NULL DEFAULT 0,
    net_kills INT NOT NULL DEFAULT 0,
    kill_death_ratio DOUBLE NOT NULL DEFAULT 0.0,
    last_updated DATETIME NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Player kill stats table
CREATE TABLE IF NOT EXISTS player_kill_stats (
    player_id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36),
    total_kills INT NOT NULL DEFAULT 0,
    total_deaths INT NOT NULL DEFAULT 0,
    streak INT NOT NULL DEFAULT 0,
    best_streak INT NOT NULL DEFAULT 0,
    last_kill_time BIGINT,
    last_death_time BIGINT,
    INDEX idx_player_kill_stats_guild_id (guild_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Anti-farm data table
CREATE TABLE IF NOT EXISTS anti_farm_data (
    player_id VARCHAR(36) PRIMARY KEY,
    recent_kills TEXT NOT NULL,
    farm_score DOUBLE NOT NULL DEFAULT 0.0,
    last_farm_check DATETIME NOT NULL,
    is_currently_farming TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Wars table
CREATE TABLE IF NOT EXISTS wars (
    id VARCHAR(36) PRIMARY KEY,
    guild_a VARCHAR(36) NOT NULL,
    guild_b VARCHAR(36) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('Declared', 'Accepted', 'Active', 'Resolved')),
    started_at DATETIME,
    ended_at DATETIME,
    result VARCHAR(20) CHECK (result IN ('Victory_A', 'Victory_B', 'Draw', 'Cancelled')),
    stats TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_wars_guild_a (guild_a),
    INDEX idx_wars_guild_b (guild_b),
    INDEX idx_wars_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Progression System Tables (v9)
-- =====================================

-- Guild progression table
CREATE TABLE IF NOT EXISTS guild_progression (
    guild_id VARCHAR(36) PRIMARY KEY,
    total_experience INT NOT NULL DEFAULT 0,
    current_level INT NOT NULL DEFAULT 1,
    experience_this_level INT NOT NULL DEFAULT 0,
    experience_for_next_level INT NOT NULL DEFAULT 1000,
    last_level_up BIGINT,
    total_level_ups INT NOT NULL DEFAULT 0,
    unlocked_perks TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    last_updated BIGINT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_guild_progression_level (current_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Experience transactions table
CREATE TABLE IF NOT EXISTS experience_transactions (
    id VARCHAR(36) PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    amount INT NOT NULL,
    source VARCHAR(100) NOT NULL,
    description TEXT,
    actor_id VARCHAR(36),
    timestamp BIGINT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_experience_transactions_guild_id (guild_id),
    INDEX idx_experience_transactions_timestamp (timestamp),
    INDEX idx_experience_transactions_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Guild activity metrics table
CREATE TABLE IF NOT EXISTS guild_activity_metrics (
    guild_id VARCHAR(36) PRIMARY KEY,
    member_count INT NOT NULL DEFAULT 0,
    active_members INT NOT NULL DEFAULT 0,
    claims_owned INT NOT NULL DEFAULT 0,
    claims_created_this_week INT NOT NULL DEFAULT 0,
    kills_this_week INT NOT NULL DEFAULT 0,
    deaths_this_week INT NOT NULL DEFAULT 0,
    bank_deposits_this_week INT NOT NULL DEFAULT 0,
    relations_formed INT NOT NULL DEFAULT 0,
    wars_participated INT NOT NULL DEFAULT 0,
    last_updated DATETIME NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    INDEX idx_guild_activity_metrics_member_count (member_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Leaderboards & Auditing Tables
-- =====================================

-- Leaderboards table
CREATE TABLE IF NOT EXISTS leaderboards (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    period_start DATETIME NOT NULL,
    period_end DATETIME NOT NULL,
    data TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_leaderboards_type (type),
    INDEX idx_leaderboards_period_start (period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audits table
CREATE TABLE IF NOT EXISTS audits (
    id VARCHAR(36) PRIMARY KEY,
    time DATETIME NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    guild_id VARCHAR(36),
    action VARCHAR(100) NOT NULL,
    details TEXT,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE SET NULL,
    INDEX idx_audits_guild_id (guild_id),
    INDEX idx_audits_time (time),
    INDEX idx_audits_actor_id (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Chat System Tables
-- =====================================

-- Chat visibility settings table
CREATE TABLE IF NOT EXISTS chat_visibility_settings (
    player_id VARCHAR(36) PRIMARY KEY,
    guild_chat_visible TINYINT(1) NOT NULL DEFAULT 1,
    ally_chat_visible TINYINT(1) NOT NULL DEFAULT 1,
    party_chat_visible TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chat rate limits table
CREATE TABLE IF NOT EXISTS chat_rate_limits (
    player_id VARCHAR(36) PRIMARY KEY,
    last_announce_time BIGINT NOT NULL DEFAULT 0,
    last_ping_time BIGINT NOT NULL DEFAULT 0,
    announce_count INT NOT NULL DEFAULT 0,
    ping_count INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Claims System Tables (Optional)
-- Only created if claims_enabled = true
-- =====================================

-- Claims table
CREATE TABLE IF NOT EXISTS claims (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    world VARCHAR(255) NOT NULL,
    min_x INT NOT NULL,
    min_z INT NOT NULL,
    max_x INT NOT NULL,
    max_z INT NOT NULL,
    is_admin_claim TINYINT(1) NOT NULL DEFAULT 0,
    claim_metadata TEXT,
    team_id VARCHAR(36),
    INDEX idx_claims_owner_id (owner_id),
    INDEX idx_claims_world (world),
    INDEX idx_claims_team_id (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Claim partitions table
CREATE TABLE IF NOT EXISTS claim_partitions (
    id VARCHAR(36) PRIMARY KEY,
    claim_id VARCHAR(36) NOT NULL,
    name VARCHAR(255),
    min_x INT NOT NULL,
    min_z INT NOT NULL,
    max_x INT NOT NULL,
    max_z INT NOT NULL,
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
    INDEX idx_claim_partitions_claim_id (claim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Claim default permissions table
CREATE TABLE IF NOT EXISTS claim_default_permissions (
    claim_id VARCHAR(36) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    value TINYINT(1) NOT NULL,
    PRIMARY KEY (claim_id, permission),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Claim flags table
CREATE TABLE IF NOT EXISTS claim_flags (
    claim_id VARCHAR(36) NOT NULL,
    flag VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    PRIMARY KEY (claim_id, flag),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Claim player permissions table
CREATE TABLE IF NOT EXISTS claim_player_permissions (
    claim_id VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    value TINYINT(1) NOT NULL,
    PRIMARY KEY (claim_id, player_id, permission),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
    INDEX idx_claim_player_permissions_player_id (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================
-- Schema complete
-- =====================================
