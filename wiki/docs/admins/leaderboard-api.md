---
title: Web leaderboard API
audience: admin
topic: leaderboard-api
summary: Public HTTP API exposing guild leaderboard data for external dashboards.
keywords: [leaderboard, api, web, http, json]
related: [installation]
updated: 2026-05-14
---

# Web leaderboard API

Expose guild leaderboard data (rankings by level, balance, activity, member count, and age) via a read-only HTTP API. Designed for website backends to fetch and cache; do NOT expose directly to the internet.

## How it works

LumaGuilds embeds a lightweight HTTP server (Java's built-in `com.sun.net.httpserver.HttpServer`) that listens on a configurable host and port. By default it runs on `localhost:8123`, disabled unless you explicitly enable it in `config.yml`. The server runs in a fixed thread pool (2 threads) isolated from the main Minecraft server tick.

The API serves one public endpoint: `/api/leaderboards/guilds`. It accepts query parameters to control the leaderboard type (level, balance, activity, members, age), period (weekly, daily, monthly, all-time), and limit (1–50). All responses are JSON. The server also exposes a simple health check at `/api/health`.

## Endpoints

### GET /api/leaderboards/guilds

Returns the top guilds ranked by the specified category.

**Query parameters:**

| Parameter | Type | Default | Range | Notes |
|-----------|------|---------|-------|-------|
| `type` | string | `level` | `level`, `balance`, `activity`, `members`, `age` | Leaderboard category |
| `period` | string | `ALL_TIME` | `WEEKLY`, `DAILY`, `MONTHLY`, `ALL_TIME` | Activity period (ignored for level/balance/members/age, which are point-in-time snapshots) |
| `limit` | integer | 10 | 1–50 | Number of entries to return |

**Request example:**
```
GET /api/leaderboards/guilds?type=level&limit=5&period=WEEKLY
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "type": "level",
  "period": "ALL_TIME",
  "generatedAt": "2026-05-14T10:30:45.123Z",
  "count": 5,
  "entries": [
    {
      "rank": 1,
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Dragon Slayers",
      "tag": "<red>🐉",
      "tagPlain": "🐉",
      "level": 42,
      "totalExperience": 85000,
      "experienceThisLevel": 3500,
      "experienceForNextLevel": 12000,
      "memberCount": 28,
      "bankBalance": 50000,
      "activityScore": 1200,
      "topMemberUuids": [
        "uuid-1",
        "uuid-2",
        "uuid-3",
        "uuid-4",
        "uuid-5"
      ],
      "banner": {
        "baseColor": "RED",
        "baseColorHex": "#FF0000",
        "patterns": [
          {
            "type": "SKULL",
            "color": "YELLOW",
            "colorHex": "#FFFF00"
          }
        ]
      },
      "createdAt": "2025-10-14T08:00:00Z",
      "score": 42000000.0
    }
  ]
}
```

**Response (401 Unauthorized):** If `bearer_token` is set in config and the request doesn't include a valid token.
```json
{
  "error": "unauthorized"
}
```

**Response (405 Method Not Allowed):** If you POST or PUT instead of GET.
```json
{
  "error": "method_not_allowed"
}
```

**Response (500 Internal Server Error):** If the database query fails.
```json
{
  "error": "internal_error"
}
```

### GET /api/health

Simple health check. Always returns 200 OK if the server is running.

**Response:**
```json
{
  "status": "ok"
}
```

## Setup

Enable and configure the web API in `plugins/LumaGuilds/config.yml`:

```yaml
web_api:
  # Enable the web API server (default: false)
  enabled: true
  
  # Host to bind to. Use 127.0.0.1 (localhost) for security; only expose via reverse proxy
  host: "127.0.0.1"
  
  # Port to listen on (default: 8123)
  port: 8123
  
  # Optional bearer token for authentication. If blank, no auth required
  # Only safe if the server is behind a firewall or reverse proxy
  bearer_token: ""
  
  # Default number of leaderboard entries to return if ?limit is not specified
  leaderboard_limit_default: 10
  
  # Maximum number of leaderboard entries a single request can ask for
  leaderboard_limit_max: 50
  
  # Number of member UUIDs to include in each leaderboard entry
  # Used by the website to render player heads or avatars
  top_members_per_guild: 5
```

**What each setting does:**

- `enabled`: Set to `true` to start the HTTP server on plugin load.
- `host`: Bind address. Always use `127.0.0.1` for security; your website backend connects to it via localhost or a reverse proxy.
- `port`: Listen port. Change if it conflicts with another service on the same machine.
- `bearer_token`: If set (non-empty), all requests must include `Authorization: Bearer <token>` header. Leave empty if you trust your firewall.
- `leaderboard_limit_default`: Default `limit` if the request omits `?limit`.
- `leaderboard_limit_max`: Enforce an upper bound on `?limit` to prevent DB abuse.
- `top_members_per_guild`: How many player UUIDs to return in `topMemberUuids` per guild. Use 0 to omit the field entirely.

**Restart required:** Yes. The server binds to the port at startup; changing the config without restarting will have no effect.

## Reverse-proxy example

Expose the API via nginx on a website domain. This example proxies `https://wiki.example.com/api/` to the internal HTTP server:

```nginx
server {
    listen 443 ssl http2;
    server_name wiki.example.com;
    ssl_certificate /etc/ssl/certs/wiki.crt;
    ssl_certificate_key /etc/ssl/private/wiki.key;

    location /api/ {
        # Forward requests to the internal LumaGuilds API
        proxy_pass http://127.0.0.1:8123;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        
        # Optional: Strip /api prefix before forwarding
        # rewrite ^/api/(.*)$ /$1 break;
    }

    # HTTPS redirect for HTTP
    location / {
        return 200 "OK";
    }
}

# HTTP redirect
server {
    listen 80;
    server_name wiki.example.com;
    return 301 https://$server_name$request_uri;
}
```

With this config, your website backend can fetch the leaderboard at `https://wiki.example.com/api/leaderboards/guilds?type=level&limit=10`.

## Authentication

If `bearer_token` is set in `config.yml`, all requests must include the header:
```
Authorization: Bearer <your-token-here>
```

The token is compared using constant-time comparison to prevent timing attacks. If the header is missing or the token is wrong, the server responds with `401 Unauthorized`.

If `bearer_token` is blank, no authentication is enforced. **This is only safe if the API is behind a firewall or reverse proxy that restricts access.**

## Caching and rate limits

The API does not cache responses; every request hits the database. The handler is executed off the main server thread, so it won't lag your server.

**Recommendation:** Your website backend should cache the leaderboard response for at least 1 minute to avoid hammering the database. Example:

```javascript
// Fetch leaderboard once, cache for 1 minute
const cacheTime = 60_000; // ms
let cached = null;
let cacheExpiry = 0;

async function getLeaderboard() {
  const now = Date.now();
  if (cached && now < cacheExpiry) {
    return cached;
  }
  
  const res = await fetch('https://wiki.example.com/api/leaderboards/guilds?type=level&limit=10');
  cached = await res.json();
  cacheExpiry = now + cacheTime;
  return cached;
}
```

If you poll more frequently than once per minute, consider increasing the cache time or implementing request debouncing.

## Gotchas

- **Public data only**: The API exposes no private information — only the guild name, tag, level, public member count, and a list of top member UUIDs. It does not expose guild chat, alliance status, vault contents, or player balance.
- **No rate limiting**: The API has no built-in rate limiting. If your website is compromised and makes thousands of requests per second, your database will suffer. Always proxy through a firewall or reverse proxy that enforces rate limits.
- **Port collision**: If port 8123 is already in use by another service, change `web_api.port` in `config.yml` and restart the server.
- **Restart to apply changes**: The server binds at startup. Reloading the config or using PlugMan will NOT pick up new settings; you must fully restart.
- **Bearer token is plaintext**: The token is stored in `config.yml` as plaintext. Do not use a password you care about; treat it like an API key. Secure the file with appropriate file permissions.
- **Activity leaderboard period is weekly**: Today, the underlying tracking is bucketed by week. The `period` parameter accepts other values (DAILY, MONTHLY, ALL_TIME) but they all map to the current week. Future updates may support other periods.

## Related

- [Installation & config.yml](installation.md) — web_api section details
