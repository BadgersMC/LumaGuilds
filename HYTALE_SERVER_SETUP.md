# Hytale Server Setup Guide for LumaGuilds

**Based on:** Official Hytale Server Manual
**Date:** 2026-01-11
**For:** Server administrators hosting LumaGuilds on Hytale

---

## 🚨 Critical Information for LumaGuilds

### Server Requirements

**Minimum:**
- Java 25 (Adoptium recommended)
- 4GB RAM minimum
- x64 or arm64 architecture
- UDP port 5520 open (QUIC protocol, NOT TCP!)

**Recommended for LumaGuilds:**
- 8GB+ RAM (guild data, claims, large player counts)
- View distance limited to 12 chunks (384 blocks) - balances performance/gameplay
- Monitor RAM/CPU based on player behavior

### Resource Drivers

| Resource | What Drives Usage | LumaGuilds Impact |
|----------|-------------------|-------------------|
| **CPU** | High player/entity counts | Medium (guilds, claims don't spawn entities) |
| **RAM** | Large loaded world area | HIGH (claims keep chunks loaded, players exploring) |

**⚠️ LumaGuilds Warning:** Claims system can keep large areas loaded. Recommend:
- Limit max claims per guild
- Implement claim unload timeout when no members online
- Monitor chunk loading in claim-heavy areas

---

## 🔧 Server Split Setup (Your Hosting)

Assuming you already have:
- ✅ Java 25 installed
- ✅ Server split created
- ✅ SSH/FTP access

### Step 1: Download Hytale Server Files

**Option A: Hytale Downloader CLI (Recommended for Production)**

```bash
# Download the downloader
wget https://hytale.com/downloads/hytale-downloader.zip
unzip hytale-downloader.zip

# Download latest server files
./hytale-downloader

# Extract to your server directory
unzip game.zip -d /path/to/your/server/
```

**Option B: Manual Copy from Local Launcher**

If you have the Hytale launcher installed locally:

**Windows:**
```powershell
# Find files at:
%appdata%\Hytale\install\release\package\game\latest

# Copy Server folder and Assets.zip to your hosting via FTP/SCP
```

**Linux/Mac:**
```bash
# Find files at:
$XDG_DATA_HOME/Hytale/install/release/package/game/latest  # Linux
~/Application Support/Hytale/install/release/package/game/latest  # Mac

# Copy to server
scp -r Server Assets.zip user@yourhost:/path/to/server/
```

### Step 2: Server Directory Structure

Your server should look like this:

```
/path/to/your/hytale/server/
├── Server/
│   ├── HytaleServer.jar
│   └── HytaleServer.aot        # AOT cache for faster startup
├── Assets.zip                  # Game assets
├── mods/                       # Put LumaGuilds here!
├── universe/                   # Auto-generated: world saves
├── logs/                       # Auto-generated: server logs
├── .cache/                     # Auto-generated: optimized files
├── config.json                 # Auto-generated: server config
├── permissions.json            # Auto-generated: permissions
├── whitelist.json              # Optional: whitelist
└── bans.json                   # Auto-generated: bans
```

### Step 3: Configure Firewall (CRITICAL!)

**Hytale uses QUIC over UDP, NOT TCP!**

**Your Hosting Panel:**
1. Open UDP port 5520 (or custom port)
2. DO NOT open TCP port 5520 (not needed)

**If you have shell access:**

```bash
# Linux (ufw)
sudo ufw allow 5520/udp

# Linux (iptables)
sudo iptables -A INPUT -p udp --dport 5520 -j ACCEPT
sudo iptables-save > /etc/iptables/rules.v4

# Verify
sudo ufw status  # or
sudo iptables -L
```

**Verify Port is Open:**
```bash
# From another machine
nc -u -v -z your-server-ip 5520
```

### Step 4: Create Startup Script

**`start.sh`:**
```bash
#!/bin/bash

# Configuration
JAVA_HOME=/path/to/java25
SERVER_JAR="Server/HytaleServer.jar"
ASSETS_PATH="Assets.zip"
PORT=5520
MIN_RAM=4G
MAX_RAM=8G

# Java arguments for production
JAVA_ARGS="-Xms${MIN_RAM} -Xmx${MAX_RAM}"
JAVA_ARGS="$JAVA_ARGS -XX:+UseG1GC"                    # G1 garbage collector
JAVA_ARGS="$JAVA_ARGS -XX:+ParallelRefProcEnabled"     # Parallel reference processing
JAVA_ARGS="$JAVA_ARGS -XX:MaxGCPauseMillis=200"        # GC pause target
JAVA_ARGS="$JAVA_ARGS -XX:+UnlockExperimentalVMOptions"
JAVA_ARGS="$JAVA_ARGS -XX:+DisableExplicitGC"          # Prevent System.gc() calls
JAVA_ARGS="$JAVA_ARGS -XX:+AlwaysPreTouch"             # Pre-touch memory for better performance
JAVA_ARGS="$JAVA_ARGS -XX:G1NewSizePercent=30"         # G1 tuning
JAVA_ARGS="$JAVA_ARGS -XX:G1MaxNewSizePercent=40"
JAVA_ARGS="$JAVA_ARGS -XX:G1HeapRegionSize=8M"
JAVA_ARGS="$JAVA_ARGS -XX:G1ReservePercent=20"
JAVA_ARGS="$JAVA_ARGS -XX:G1HeapWastePercent=5"
JAVA_ARGS="$JAVA_ARGS -XX:G1MixedGCCountTarget=4"
JAVA_ARGS="$JAVA_ARGS -XX:InitiatingHeapOccupancyPercent=15"
JAVA_ARGS="$JAVA_ARGS -XX:G1MixedGCLiveThresholdPercent=90"
JAVA_ARGS="$JAVA_ARGS -XX:G1RSetUpdatingPauseTimePercent=5"
JAVA_ARGS="$JAVA_ARGS -XX:SurvivorRatio=32"
JAVA_ARGS="$JAVA_ARGS -XX:+PerfDisableSharedMem"       # Better performance monitoring
JAVA_ARGS="$JAVA_ARGS -XX:MaxTenuringThreshold=1"
JAVA_ARGS="$JAVA_ARGS -Dusing.aikars.flags=https://mcflags.emc.gs"  # Credit to Aikar
JAVA_ARGS="$JAVA_ARGS -Daikars.new.flags=true"

# AOT cache for faster startup (recommended by Hypixel)
JAVA_ARGS="$JAVA_ARGS -XX:AOTCache=Server/HytaleServer.aot"

# Server arguments
SERVER_ARGS="--assets $ASSETS_PATH"
SERVER_ARGS="$SERVER_ARGS --bind 0.0.0.0:$PORT"
SERVER_ARGS="$SERVER_ARGS --disable-sentry"              # Disable crash reporting (optional)
SERVER_ARGS="$SERVER_ARGS --accept-early-plugins"        # Allow mods during early access

# Start server
echo "Starting Hytale server..."
echo "Java: $JAVA_HOME/bin/java"
echo "RAM: $MIN_RAM - $MAX_RAM"
echo "Port: $PORT"
echo ""

$JAVA_HOME/bin/java $JAVA_ARGS -jar $SERVER_JAR $SERVER_ARGS

# If server crashes, wait before restart
echo "Server stopped. Restarting in 10 seconds..."
sleep 10
exec $0  # Restart script
```

**Make executable:**
```bash
chmod +x start.sh
```

### Step 5: First Launch & Authentication

**Start the server:**
```bash
./start.sh
```

**Authenticate (REQUIRED):**

After first launch, you'll see:
```
> /auth login device
===================================================================
DEVICE AUTHORIZATION
===================================================================
Visit: https://accounts.hytale.com/device
Enter code: ABCD-1234
Or visit: https://accounts.hytale.com/device?user_code=ABCD-1234
===================================================================
Waiting for authorization (expires in 900 seconds)...
```

**Steps:**
1. Visit `https://accounts.hytale.com/device` in browser
2. Enter the code shown (e.g., `ABCD-1234`)
3. Log in with your Hytale account
4. Authorize the server

**Result:**
```
> Authentication successful! Mode: OAUTH_DEVICE
```

**⚠️ Server Limit:** Maximum 100 servers per Hytale license. If you need more, purchase additional licenses or apply for Server Provider account.

### Step 6: Install LumaGuilds

Once we port LumaGuilds to Hytale:

```bash
# Navigate to server directory
cd /path/to/your/hytale/server/

# Create mods folder if it doesn't exist
mkdir -p mods

# Download LumaGuilds (when available)
cd mods
wget https://github.com/yourrepo/lumaguilds/releases/download/v2.0.0/LumaGuilds-Hytale-2.0.0.jar

# Restart server
cd ..
./start.sh
```

### Step 7: Configure LumaGuilds

**Default config location:** `mods/LumaGuilds/config.json`

**Example config.json:**
```json
{
  "database": {
    "type": "SQLITE",
    "path": "mods/LumaGuilds/data.db"
  },
  "guilds": {
    "maxMembers": 50,
    "maxClaims": 100,
    "claimChunkRadius": 12,
    "unloadInactiveClaimMinutes": 30
  },
  "performance": {
    "maxClaimsLoaded": 500,
    "unloadChunksWhenNoMembersOnline": true
  }
}
```

**⚠️ Performance Recommendation:**
- Enable `unloadChunksWhenNoMembersOnline`
- Set reasonable `maxClaimsLoaded` limit
- Monitor RAM usage with `/lumaguilds stats memory`

---

## 📊 Performance Tuning for LumaGuilds

### RAM Allocation

**Formula:**
```
Base Server: 2-4GB
+ (Players × 50-100MB)
+ (Loaded Claims × 10-20MB)
+ (Active Worlds × 500MB)
= Total RAM
```

**Example:**
- 50 players
- 200 loaded claims
- 2 active worlds
= 2GB + (50 × 100MB) + (200 × 15MB) + (2 × 500MB)
= 2GB + 5GB + 3GB + 1GB
= **11GB total**

**Recommendation:** Allocate 12-16GB for this scenario

### View Distance Optimization

**Default Hytale:** 12 chunks (384 blocks)
**Recommended for LumaGuilds:** 8-10 chunks (256-320 blocks)

**Why?**
- Claims keep chunks loaded regardless of view distance
- Lower view distance = less RAM for players exploring
- More headroom for claim chunk loading

**Configure in `universe/worlds/[world]/config.json`:**
```json
{
  "ChunkConfig": {
    "MaxViewDistance": 10
  }
}
```

### Recommended Plugins

**For Production Hosting:**

| Plugin | Purpose | Why for LumaGuilds |
|--------|---------|-------------------|
| **Nitrado:PerformanceSaver** | Dynamic view distance based on RAM | Auto-reduce view distance when claims load heavily |
| **Nitrado:Query** | HTTP API for server status | Expose guild stats via web |
| **ApexHosting:PrometheusExporter** | Metrics export | Monitor claim chunk loading, database queries |

**Install:**
```bash
cd mods
wget https://plugins.hytale.com/nitrado/PerformanceSaver.jar
wget https://plugins.hytale.com/nitrado/Query.jar
wget https://plugins.hytale.com/apexhosting/PrometheusExporter.jar
```

---

## 🌐 Multi-Server Setup (Proxy/Network)

### Native Hytale Multi-Server (No BungeeCord!)

Hytale has **built-in player referral** - no need for BungeeCord/Velocity!

**Use Cases for LumaGuilds:**
- **Hub → Game Servers:** Lobby server refers to guild survival servers
- **Cross-Server Guilds:** Guild members on different game servers
- **Load Balancing:** Distribute players across multiple servers

### Player Referral API

**Transfer player to another server:**

```kotlin
// In your Hytale plugin code
fun transferPlayerToServer(player: PlayerRef, targetHost: String, targetPort: Int) {
    // Optional: Include guild data in payload
    val payload = serializeGuildData(player)

    player.referToServer(targetHost, targetPort, payload)
}

// Example: Send player to guild server
fun onJoinGuild(player: PlayerRef, guild: Guild) {
    val guildServer = getServerForGuild(guild.id)
    transferPlayerToServer(player, guildServer.host, guildServer.port)
}
```

**⚠️ Security Warning:**
- Payload is sent through client (can be tampered)
- **Sign payloads with HMAC!**

**Secure Payload Example:**
```kotlin
fun createSecurePayload(guildId: UUID): ByteArray {
    val data = JsonObject()
    data.addProperty("guildId", guildId.toString())
    data.addProperty("timestamp", System.currentTimeMillis())

    val json = gson.toJson(data)
    val signature = hmacSHA256(json, sharedSecret)

    val payload = JsonObject()
    payload.addProperty("data", json)
    payload.addProperty("signature", signature)

    return gson.toJson(payload).toByteArray()
}

fun verifyPayload(payload: ByteArray): JsonObject? {
    val wrapper = gson.fromJson(String(payload), JsonObject::class.java)
    val data = wrapper.get("data").asString
    val signature = wrapper.get("signature").asString

    val expectedSignature = hmacSHA256(data, sharedSecret)
    if (signature != expectedSignature) {
        logger.warn("Invalid payload signature!")
        return null
    }

    return gson.fromJson(data, JsonObject::class.java)
}
```

### Connection Redirect (Load Balancing)

**Redirect during handshake:**

```kotlin
// Redirect new connections to least-loaded server
fun onPlayerConnect(event: PlayerSetupConnectEvent) {
    val targetServer = getLeastLoadedServer()

    if (targetServer != thisServer) {
        event.referToServer(targetServer.host, targetServer.port, null)
    }
}
```

### Disconnect Fallback (Coming Soon)

**Return to lobby on crash:**

```kotlin
// When implemented:
fun configureFallback(player: PlayerRef) {
    player.setFallbackServer("lobby.yourdomain.com", 5520)
}
```

**Use Case:** Guild server crashes → player returns to lobby instead of disconnect

---

## 🔐 Security & Best Practices

### 1. Authentication Limits

**Problem:** 100 servers per license
**Solution for Networks:**
- Apply for Server Provider account (coming soon)
- Use multiple licenses for large networks
- Share authentication across server group

### 2. Sentry Crash Reporting

**During Development:**
```bash
java -jar HytaleServer.jar --assets Assets.zip --disable-sentry
```

**In Production:**
- Enable Sentry (default)
- Hypixel uses this to fix bugs
- Your crashes help improve Hytale!

### 3. Config File Handling

**⚠️ Warning:** Config files are read on startup, written during runtime.

**Problem:** Manual edits while server running = overwritten
**Solution:**
- Stop server before editing configs
- Use in-game commands for runtime changes
- Use config reload commands (if available)

### 4. Backups

**Enable automatic backups:**
```bash
java -jar HytaleServer.jar \
  --assets Assets.zip \
  --backup \
  --backup-dir /path/to/backups \
  --backup-frequency 30  # minutes
```

**Manual Backup:**
```bash
# Stop server
./stop.sh

# Backup universe folder (world saves, player data)
tar -czf backup-$(date +%Y%m%d-%H%M%S).tar.gz universe/

# Backup configs
tar -czf config-backup-$(date +%Y%m%d-%H%M%S).tar.gz config.json permissions.json whitelist.json bans.json

# Restart server
./start.sh
```

---

## 🚀 Advanced: Building a Custom Proxy

**Why?** Custom authentication, anti-DDoS, traffic inspection

**Hytale uses Netty QUIC:**

```kotlin
// Packet definitions in HytaleServer.jar:
// com.hypixel.hytale.protocol.packets

// Example: Decode login packet
fun decodeLoginPacket(buffer: ByteBuf): LoginPacket {
    // Use Hytale's packet classes
    val packet = LoginPacket()
    packet.decode(buffer)
    return packet
}

// Custom proxy server
class HytaleProxy {
    fun start() {
        val bootstrap = Bootstrap()
        bootstrap.group(NioEventLoopGroup())
        bootstrap.channel(NioDatagramChannel::class.java)
        bootstrap.handler(QuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(30, TimeUnit.SECONDS)
            .handler(ProxyChannelHandler())
            .build())

        bootstrap.bind(5520).sync()
    }
}
```

**Use Cases:**
- Custom authentication gateway
- DDoS protection layer
- Traffic analytics
- Multi-region routing

---

## 📅 Coming Soon Features

### 1. Server Discovery

**What:** In-game server browser
**When:** Post-launch
**Impact on LumaGuilds:**
- List your guild server in official catalogue
- Players discover servers by features
- Must self-rate content (PG, Teen, Mature)
- Player counts verified by telemetry (no spoofing)

**Preparation:**
- Ensure server follows operator guidelines
- Prepare accurate content rating
- Build compelling server description

### 2. Party System

**What:** Built-in party/group system
**When:** Post-launch
**Impact on LumaGuilds:**
- Integrate with guild system
- Party members can join guild server together
- Cross-server party support

**Integration Ideas:**
- Auto-create party when guild members group
- Guild-wide events with party support
- Party-based guild challenges

### 3. Integrated Payments

**What:** Built-in payment gateway
**When:** Post-launch
**Impact on LumaGuilds:**
- Sell guild perks (extra claims, members, etc.)
- Secure transactions without external sites
- Traceable payment history

**Monetization Ideas:**
- Guild tier upgrades
- Premium claim features
- Cosmetic guild banners/flags

### 4. First-Party API Endpoints

**What:** Official REST API for server operations
**When:** Post-launch
**Endpoints:**
- UUID ↔ Name lookup
- Player profile data
- Server telemetry
- Payment processing
- ToS violation reporting

**LumaGuilds Use Cases:**
- Resolve player UUIDs for guild invites
- Fetch player cosmetics for guild profiles
- Report griefers to platform
- Process guild upgrade purchases

---

## 🐛 Troubleshooting

### Players Can't Connect

**Check:**
1. ✅ UDP port 5520 open (NOT TCP!)
2. ✅ Firewall allows QUIC traffic
3. ✅ Server authenticated
4. ✅ Client and server protocol versions match

**Test Connectivity:**
```bash
# From client machine
nc -u -v -z your-server-ip 5520
```

### High RAM Usage

**Symptoms:**
- Increased CPU (garbage collection)
- Server lag spikes
- Out of memory errors

**Solutions:**
1. Lower view distance (12 → 8 chunks)
2. Limit loaded claims
3. Increase `-Xmx` allocation
4. Install `Nitrado:PerformanceSaver` plugin
5. Monitor with `/lumaguilds stats memory`

### Server Crashes on Startup

**Common Causes:**
1. Wrong Java version (need Java 25)
2. Missing Assets.zip
3. Corrupted world data
4. Incompatible plugins

**Debug:**
```bash
# Check Java version
java --version

# Run with debug logging
java -jar HytaleServer.jar --assets Assets.zip --debug
```

### Protocol Version Mismatch

**Error:** "Protocol hash mismatch"

**Cause:** Client and server versions don't match exactly

**Solution:**
- Update server: `./hytale-downloader`
- Wait for protocol tolerance (coming soon: ±2 version window)

---

## 📚 Quick Reference

### Essential Commands

```bash
# Start server
./start.sh

# Authenticate
/auth login device

# Check server status
/status

# List players
/list

# View performance
/tps
/gc

# Save world
/save-all

# Stop server
/stop
```

### File Locations

| File | Path | Purpose |
|------|------|---------|
| Server JAR | `Server/HytaleServer.jar` | Main server |
| Assets | `Assets.zip` | Game assets |
| Configs | `config.json` | Server config |
| Worlds | `universe/worlds/` | World saves |
| Mods | `mods/` | LumaGuilds here |
| Logs | `logs/` | Server logs |

### Port Reference

| Port | Protocol | Purpose |
|------|----------|---------|
| 5520 | **UDP** | Hytale QUIC (default) |
| 25565 | UDP | Custom port (optional) |

**Remember:** QUIC uses UDP, not TCP!

---

## ✅ Production Checklist

Before going live with LumaGuilds:

- [ ] Java 25 installed (Adoptium)
- [ ] UDP port 5520 open in firewall
- [ ] Server authenticated with Hytale account
- [ ] Automatic backups enabled
- [ ] View distance tuned (8-12 chunks)
- [ ] RAM allocated (8GB+ recommended)
- [ ] LumaGuilds installed and configured
- [ ] Performance plugins installed (PerformanceSaver, Query)
- [ ] Monitoring set up (logs, metrics)
- [ ] Tested player connections
- [ ] Guild creation tested
- [ ] Claims system tested
- [ ] Database backups scheduled

---

**Last Updated:** 2026-01-11
**Hytale Server Version:** Early Access
**LumaGuilds Version:** 2.0.0 (Hytale - Coming Soon)
