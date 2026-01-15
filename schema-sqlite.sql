-- =====================================
-- LumaGuilds SQLite Schema
-- Complete database schema for SQLite
-- Version: 2.1.0 (Schema v15)
-- =====================================

-- Create schema version table
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY
);

-- Set schema version to 15
DELETE FROM schema_version;
INSERT INTO schema_version (version) VALUES (15);

-- =====================================
-- Core Guild Tables
-- =====================================

-- Guilds table (main guild data)
CREATE TABLE IF NOT EXISTS guilds (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    banner TEXT,
    emoji TEXT,
    tag TEXT,
    description TEXT,
    home_world TEXT,
    home_x INTEGER,
    home_y INTEGER,
    home_z INTEGER,
    level INTEGER NOT NULL DEFAULT 1,
    bank_balance INTEGER NOT NULL DEFAULT 0,
    mode TEXT NOT NULL DEFAULT 'HOSTILE' CHECK (mode IN ('PEACEFUL', 'HOSTILE')),
    mode_changed_at TEXT,
    created_at TEXT NOT NULL,
    vault_status TEXT DEFAULT 'NEVER_PLACED' CHECK (vault_status IN ('NEVER_PLACED', 'PLACED', 'ACTIVE', 'INACTIVE')),
    vault_chest_world TEXT,
    vault_chest_x INTEGER,
    vault_chest_y INTEGER,
    vault_chest_z INTEGER,
    vault_locked INTEGER DEFAULT 0,
    is_open INTEGER DEFAULT 0,
    join_fee_enabled INTEGER DEFAULT 0,
    join_fee_amount INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_guilds_name ON guilds(name);
CREATE INDEX IF NOT EXISTS idx_guilds_mode ON guilds(mode);
CREATE INDEX IF NOT EXISTS idx_guilds_level ON guilds(level);

-- Ranks table (guild ranks/roles)
CREATE TABLE IF NOT EXISTS ranks (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    name TEXT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    permissions TEXT,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ranks_guild_id ON ranks(guild_id);
CREATE INDEX IF NOT EXISTS idx_ranks_priority ON ranks(priority);

-- Members table (guild membership)
CREATE TABLE IF NOT EXISTS members (
    player_id TEXT NOT NULL,
    guild_id TEXT NOT NULL,
    rank_id TEXT NOT NULL,
    joined_at TEXT NOT NULL,
    PRIMARY KEY (player_id, guild_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_members_guild_id ON members(guild_id);
CREATE INDEX IF NOT EXISTS idx_members_player_id ON members(player_id);

-- Relations table (alliances, wars, truces)
CREATE TABLE IF NOT EXISTS relations (
    id TEXT PRIMARY KEY,
    guild_a TEXT NOT NULL,
    guild_b TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('ALLY', 'ENEMY', 'TRUCE', 'NEUTRAL')),
    status TEXT NOT NULL,
    expires_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE,
    UNIQUE (guild_a, guild_b)
);

CREATE INDEX IF NOT EXISTS idx_relations_guild_a ON relations(guild_a);
CREATE INDEX IF NOT EXISTS idx_relations_guild_b ON relations(guild_b);
CREATE INDEX IF NOT EXISTS idx_relations_type ON relations(type);

-- Guild invitations table
CREATE TABLE IF NOT EXISTS guild_invitations (
    guild_id TEXT NOT NULL,
    guild_name TEXT NOT NULL,
    invited_player_id TEXT NOT NULL,
    inviter_player_id TEXT NOT NULL,
    inviter_name TEXT NOT NULL,
    invited_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, invited_player_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_guild_invitations_player ON guild_invitations(invited_player_id);
CREATE INDEX IF NOT EXISTS idx_guild_invitations_guild ON guild_invitations(guild_id);

-- =====================================
-- Party System Tables
-- =====================================

-- Parties table
CREATE TABLE IF NOT EXISTS parties (
    id TEXT PRIMARY KEY,
    guild_a TEXT NOT NULL,
    guild_b TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE,
    UNIQUE (guild_a, guild_b)
);

CREATE INDEX IF NOT EXISTS idx_parties_guild_a ON parties(guild_a);
CREATE INDEX IF NOT EXISTS idx_parties_guild_b ON parties(guild_b);

-- Party requests table
CREATE TABLE IF NOT EXISTS party_requests (
    requesting_guild_id TEXT NOT NULL,
    requesting_guild_name TEXT NOT NULL,
    requested_guild_id TEXT NOT NULL,
    requested_guild_name TEXT NOT NULL,
    requester_player_id TEXT NOT NULL,
    requester_name TEXT NOT NULL,
    requested_at TEXT NOT NULL,
    PRIMARY KEY (requesting_guild_id, requested_guild_id),
    FOREIGN KEY (requesting_guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (requested_guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_party_requests_requesting ON party_requests(requesting_guild_id);
CREATE INDEX IF NOT EXISTS idx_party_requests_requested ON party_requests(requested_guild_id);

-- Player party preferences table
CREATE TABLE IF NOT EXISTS player_party_preferences (
    player_id TEXT PRIMARY KEY,
    auto_accept_party INTEGER DEFAULT 0
);

-- =====================================
-- Chat System Tables
-- =====================================

-- Chat settings table
CREATE TABLE IF NOT EXISTS chat_settings (
    player_id TEXT PRIMARY KEY,
    guild_chat_visible INTEGER DEFAULT 1,
    ally_chat_visible INTEGER DEFAULT 1,
    party_chat_visible INTEGER DEFAULT 1
);

-- =====================================
-- Economy Tables
-- =====================================

-- Bank transactions table (audit log)
CREATE TABLE IF NOT EXISTS bank_transactions (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'FEE', 'TRANSFER', 'ADMIN')),
    amount INTEGER NOT NULL,
    balance_before INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bank_transactions_guild ON bank_transactions(guild_id);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_player ON bank_transactions(player_id);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_type ON bank_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_created ON bank_transactions(created_at);

-- =====================================
-- Progression Tables
-- =====================================

-- Progression table (guild XP and progression)
CREATE TABLE IF NOT EXISTS progression (
    guild_id TEXT PRIMARY KEY,
    xp INTEGER NOT NULL DEFAULT 0,
    total_xp_earned INTEGER NOT NULL DEFAULT 0,
    last_xp_gain TEXT,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

-- Kills table (PvP kill tracking)
CREATE TABLE IF NOT EXISTS kills (
    id TEXT PRIMARY KEY,
    killer_id TEXT NOT NULL,
    victim_id TEXT NOT NULL,
    killer_guild_id TEXT,
    victim_guild_id TEXT,
    xp_gained INTEGER NOT NULL DEFAULT 0,
    killed_at TEXT NOT NULL,
    FOREIGN KEY (killer_guild_id) REFERENCES guilds(id) ON DELETE SET NULL,
    FOREIGN KEY (victim_guild_id) REFERENCES guilds(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_kills_killer ON kills(killer_id);
CREATE INDEX IF NOT EXISTS idx_kills_victim ON kills(victim_id);
CREATE INDEX IF NOT EXISTS idx_kills_killer_guild ON kills(killer_guild_id);
CREATE INDEX IF NOT EXISTS idx_kills_victim_guild ON kills(victim_guild_id);
CREATE INDEX IF NOT EXISTS idx_kills_time ON kills(killed_at);

-- =====================================
-- Claims System Tables
-- =====================================

-- Claims table
CREATE TABLE IF NOT EXISTS claims (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    world TEXT NOT NULL,
    x_min INTEGER NOT NULL,
    z_min INTEGER NOT NULL,
    x_max INTEGER NOT NULL,
    z_max INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claims_guild ON claims(guild_id);
CREATE INDEX IF NOT EXISTS idx_claims_world ON claims(world);
CREATE INDEX IF NOT EXISTS idx_claims_coords ON claims(world, x_min, z_min, x_max, z_max);

-- Claim flags table
CREATE TABLE IF NOT EXISTS claim_flags (
    claim_id TEXT NOT NULL,
    flag_name TEXT NOT NULL,
    flag_value TEXT NOT NULL,
    PRIMARY KEY (claim_id, flag_name),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_flags_claim ON claim_flags(claim_id);

-- Claim permissions table
CREATE TABLE IF NOT EXISTS claim_permissions (
    claim_id TEXT NOT NULL,
    rank_id TEXT NOT NULL,
    permission_type TEXT NOT NULL CHECK (permission_type IN ('BUILD', 'INTERACT', 'ACCESS')),
    allowed INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (claim_id, rank_id, permission_type),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
    FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_permissions_claim ON claim_permissions(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_permissions_rank ON claim_permissions(rank_id);

-- Player access table (per-player claim permissions)
CREATE TABLE IF NOT EXISTS player_access (
    claim_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    access_level TEXT NOT NULL CHECK (access_level IN ('OWNER', 'TRUSTED', 'GUEST', 'DENIED')),
    granted_at TEXT NOT NULL,
    PRIMARY KEY (claim_id, player_id),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_player_access_claim ON player_access(claim_id);
CREATE INDEX IF NOT EXISTS idx_player_access_player ON player_access(player_id);

-- Partitions table (claim subdivisions)
CREATE TABLE IF NOT EXISTS partitions (
    id TEXT PRIMARY KEY,
    claim_id TEXT NOT NULL,
    name TEXT NOT NULL,
    x_min INTEGER NOT NULL,
    z_min INTEGER NOT NULL,
    x_max INTEGER NOT NULL,
    z_max INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_partitions_claim ON partitions(claim_id);
CREATE INDEX IF NOT EXISTS idx_partitions_coords ON partitions(claim_id, x_min, z_min, x_max, z_max);

-- =====================================
-- Audit Tables
-- =====================================

-- Audit log table (comprehensive action logging)
CREATE TABLE IF NOT EXISTS audit_log (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    action_type TEXT NOT NULL,
    details TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_audit_log_guild ON audit_log(guild_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_player ON audit_log(player_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at);

-- Guild banners table (stored banner designs)
CREATE TABLE IF NOT EXISTS guild_banners (
    guild_id TEXT PRIMARY KEY,
    banner_data TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
