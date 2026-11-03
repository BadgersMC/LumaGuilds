# LumaGuilds Permissions Cheat Sheet

## 🎯 Quick Setup Commands

### For LuckPerms (Most Popular)
```bash
# Allow everyone to create guilds and view info
/lp group default permission set lumaguilds.guild.create true
/lp group default permission set lumaguilds.guild.info true

# Give operators full guild access
/lp group admin permission set lumaguilds.guild.* true
/lp group admin permission set lumaguilds.command.* true
/lp group admin permission set lumaguilds.partychat.* true
/lp group admin permission set lumaguilds.admin true

# Disable claims for performance (optional)
/lp group default permission set lumaguilds.command.* false
```

### For PermissionsEx
```bash
# Allow everyone to create guilds
pex group default add lumaguilds.guild.create
pex group default add lumaguilds.guild.info

# Give moderators guild management
pex group mod add lumaguilds.guild.*
pex group mod add lumaguilds.partychat.*
```

### For GroupManager
```bash
# Add to groups.yml
default:
  permissions:
  - lumaguilds.guild.create
  - lumaguilds.guild.info

admin:
  permissions:
  - lumaguilds.*
```

---

## 📋 Permission Reference

### Essential Permissions (Give to all players)
```
lumaguilds.guild.create  # Create guilds (default: true)
lumaguilds.guild.info    # View guild info (default: true)
lumaguilds.guild.list    # List open/public guilds (default: true)
lumaguilds.guild.lfg     # Browse LFG (Looking For Guild) system (default: true)
```

### Guild Management (Now available to all players!)
```
lumaguilds.guild.*       # All guild commands (default: true)
lumaguilds.partychat.*   # Party chat (default: op)
```

### Claim Management (OP-only recommended)
```
lumaguilds.command.*     # All claim commands (default: op)
```

### Administrative (OP-only)
```
lumaguilds.admin         # Admin commands (default: op)
bellclaims.admin         # Admin override mode (default: op)
lumaguilds.admin.owner.recover # Recover an ownerless guild (default: op)
```

**Admin Override Mode** (`bellclaims.admin`): Allows administrators to toggle a special mode that grants owner-level permissions in all guild claims. When enabled:
- Grants all 13 claim permissions (BUILD, CONTAINER, REDSTONE, etc.)
- Bypasses guild management permission checks; Owner assignment, direct
  demotion, and removal remain protected
- Automatically cleared on logout for security
- Use `/lumaguilds override` or `/bellclaims override` to toggle

**Owner recovery** requires both `lumaguilds.admin` and
`lumaguilds.admin.owner.recover`. The specialist permission is not inherited
from base admin access, `bellclaims.admin`, guild ranks, or an enabled override.
For a non-operator administrator who should recover ownerless guilds:

```text
/lp user <admin> permission set lumaguilds.admin true
/lp user <admin> permission set lumaguilds.admin.owner.recover true
```

`/lumaguilds owner recover <guild-uuid> <member-uuid>` previews the recovery of
an existing member. The returned confirmation command uses a single-use token
that expires after 60 seconds. Recovery refuses to replace an existing Owner.
See the
[recovery guide](wiki/docs/admins/override.md#recovering-an-ownerless-guild).

---

## 🚨 Common Issues & Solutions

### Players Can't Use Guild Commands
**Problem**: "You don't have permission"
**Solution**:
```bash
# Check permissions
/lp user <player> permission info
# Grant basic access
/lp user <player> permission set lumaguilds.guild.* true
```

### Claims Not Working
**Problem**: Claim commands don't work
**Solution**:
```bash
# Enable claims in config first
# Then grant permissions
/lp user <player> permission set lumaguilds.command.* true
```

### Party Chat Not Working
**Problem**: `/pc` commands don't work
**Solution**:
```bash
/lp user <player> permission set lumaguilds.partychat.* true
```

---

## ⚡ Quick Permission Templates

### Minimal Setup (Guilds Only)
```bash
# With new defaults, this is all you need!
# Guild management is now available to all players by default
/lp group admin permission set lumaguilds.admin true
```

### Full Featured Server (Claims + Party Chat)
```bash
# Guild management is already available to all players
/lp group default permission set lumaguilds.command.* true
/lp group default permission set lumaguilds.partychat.* true
/lp group admin permission set lumaguilds.admin true
```

### Claims Disabled Server
```bash
# Guild management and party chat are already available to all players
/lp group admin permission set lumaguilds.admin true
# Don't give lumaguilds.command.* to avoid confusion
```

---

## 🔍 Checking Permissions

### Check Player Permissions
```bash
# LuckPerms
/lp user <player> permission info
/lp user <player> permission check lumaguilds.guild.create

# PermissionsEx
pex user <player> list
pex user <player> check lumaguilds.guild.create
```

### Check Group Permissions
```bash
# LuckPerms
/lp group <group> permission info
/lp group <group> permission check lumaguilds.guild.*

# PermissionsEx
pex group <group> list
pex group <group> check lumaguilds.guild.*
```

---

## 📝 Default Permission Behavior

| Command Type | Default Permission | Reason |
|-------------|-------------------|---------|
| Guild Creation | `lumaguilds.guild.create: true` | Anyone should be able to create guilds |
| Guild Info | `lumaguilds.guild.info: true` | Public information |
| Guild List | `lumaguilds.guild.list: true` | View open guilds |
| Guild LFG | `lumaguilds.guild.lfg: true` | Browse guilds looking for members |
| Invite Blocks | `lumaguilds.guild.block: true` | Anyone may block players from inviting them |
| Guild Management | `lumaguilds.guild.*: true` | Guild members should manage their guilds |
| War Declarations | `lumaguilds.guild.war: true` | Guild members can wage war |
| Claim Management | `lumaguilds.command.*: op` | Resource intensive |
| Party Chat | `lumaguilds.partychat.*: op` | Prevent spam |
| Admin Commands | `lumaguilds.admin: op` | Server management |
| Admin Override | `bellclaims.admin: op` | Powerful debugging tool |
| Owner Recovery | `lumaguilds.admin.owner.recover: op` plus `lumaguilds.admin` | Restore an existing member as Owner only in an ownerless guild |

---

## 🎮 Player Experience Flow

1. **New Player**: Can create guilds, view info, and fully manage their guild
2. **Guild Member**: Complete control over their guild (invite/kick, change settings, wage war, etc.)
3. **Guild Leader**: All guild management permissions including war declarations
4. **Operator**: Everything including claims and admin tools

This setup provides a smooth experience where players can start with basic features and earn more permissions as they prove trustworthy.

### Player-specific guild creation cooldown exception

`lumaguilds.guild.creationcooldown.bypass` defaults to **false**, including for
operators, and is not a declared child of the guild wildcard. Grant it explicitly
to the intended player only. Permission plugins may independently expand wildcard
grants; inspect the player's effective permission before use.

The permission bypasses only creation cooldown admission. It does not erase or
shorten the saved cooldown, remove creation costs, or skip other creation checks.
Revoking it restores ordinary admission against the same saved cooldown.
