# Bedrock Resource Pack — LumaGuilds UI Icons & Controller Glyphs

## Overview

Build a Bedrock `.mcpack` resource pack containing:

1. **65 menu icons** adapted from the existing Java Nexo pack icons
2. **Controller button glyph atlas** for cross-platform button hints in form labels
3. **Texture registration** via `textures_list.json`

Reference material: `/opt/data/nexo-config/luma-guilds-nexo-pack-v2.zip`

---

## Part 1 — Controller Glyph Atlas

### Font Registration

Create `font/glyph_E0.png` — a 256×16 glyph sheet (16 cells × 16px each).

The font config `font/default.json`:

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "font/glyph_E0.png",
      "ascent": 8,
      "height": 10,
      "chars": [
        "\uE001\uE002\uE003\uE004\uE005\uE006\uE007\uE008\uE009\uE00A\uE00B\uE00C\uE00D\uE00E\uE00F\uE010"
      ]
    }
  ]
}
```

### Glyph Mapping

| Unicode | Purpose    | Icon |
|---------|-----------|------|
| `\uE001` | Confirm / Accept | A button (○ on PS) |
| `\uE002` | Cancel / Close | B button (✕ on PS) |
| `\uE003` | View / Info | X button (□ on PS) |
| `\uE004` | Edit / Settings | Y button (△ on PS) |
| `\uE005` | Previous / Scroll Up | LB / L1 |
| `\uE006` | Next / Scroll Down | RB / R1 |
| `\uE007` | Quick Action / Use | LT / L2 |
| `\uE008` | Alternate Action | RT / R2 |
| `\uE009` | Navigate Up | D-Pad Up |
| `\uE00A` | Navigate Down | D-Pad Down |
| `\uE00B` | Teleport / Warp | Left Stick |
| `\uE00C` | Special | Right Stick |
| `\uE00D` | Menu / Options | Start / Menu |
| `\uE00E` | Social / Players | Select / View |
| `\uE00F` | Back / Return | Back / Share |
| `\uE010` | Delete / Remove | (trash icon) |

**Each glyph cell is 16×16 pixels** — white vector icons on transparent background. The Bedrock client will tint them based on text colour, so keep them white.

### Kana vs Standard Font

Bedrock uses different font files depending on locale settings. The glyph atlas should be referenced in **both** `font/default.json` and `font/legacy_unicode.json` to cover all players.

---

## Part 2 — Menu Icon Textures

### Path Convention

All icons go under `textures/ui/` with descriptive names matching the `BedrockConfig` paths.

### Full Icon List

| Path | Size | Source from Nexo pack | Purpose |
|------|------|---------------------|---------|
| `textures/ui/icon.png` | 64×64 | (default fallback) | Generic LumaGuilds icon |
| `textures/ui/members.png` | 64×64 | `lg/nav_members.png` | Guild members |
| `textures/ui/settings.png` | 64×64 | `lg/nav_settings.png` | Guild settings |
| `textures/ui/bank.png` | 64×64 | `lg/bank.png` | Guild bank |
| `textures/ui/wars.png` | 64×64 | `lg/nav_warfare.png` | War & party |
| `textures/ui/home.png` | 64×64 | `lg/home.png` | Guild home |
| `textures/ui/tag.png` | 64×64 | `lg/tag.png` | Guild tag/emoji |
| `textures/ui/info.png` | 64×64 | `lg/nav_info.png` | Guild information |
| `textures/ui/ranks.png` | 64×64 | `lg/nav_ranks.png` | Rank management |
| `textures/ui/economy.png` | 64×64 | `lg/nav_economy.png` | Economy |
| `textures/ui/progression.png` | 64×64 | `lg/nav_progression.png` | Progression |
| `textures/ui/diplomacy.png` | 64×64 | `lg/nav_diplomacy.png` | Diplomacy |
| `textures/ui/confirm.png` | 64×64 | `lg/confirm.png` | Confirm action button |
| `textures/ui/cancel.png` | 64×64 | `lg/cancel.png` | Cancel action button |
| `textures/ui/back.png` | 64×64 | `lg/back.png` | Back navigation |
| `textures/ui/close.png` | 64×64 | `lg/close.png` | Close form |
| `textures/ui/edit.png` | 64×64 | `lg/description.png` or remake | Edit / rename |
| `textures/ui/delete.png` | 64×64 | `lg/disband.png` | Delete / disband |
| `textures/ui/invite.png` | 64×64 | `lg/invite.png` | Invite player |
| `textures/ui/kick.png` | 64×64 | `lg/kick.png` | Kick player |
| `textures/ui/promote.png` | 64×64 | `lg/promote.png` | Promote |
| `textures/ui/demote.png` | 64×64 | `lg/demote.png` | Demote |
| `textures/ui/rank_create.png` | 64×64 | `lg/rank_create.png` | Create rank |
| `textures/ui/rank_edit.png` | 64×64 | `lg/rank_edit.png` | Edit rank |
| `textures/ui/rank_delete.png` | 64×64 | `lg/rank_delete.png` | Delete rank |
| `textures/ui/reset.png` | 64×64 | `lg/reset.png` | Reset to defaults |
| `textures/ui/alliance.png` | 64×64 | `lg/alliance.png` | Alliance |
| `textures/ui/enemy.png` | 64×64 | `lg/enemy.png` | Enemy list |
| `textures/ui/truce.png` | 64×64 | `lg/truce.png` | Truce / peace |
| `textures/ui/peace.png` | 64×64 | `lg/peace.png` | Peace agreement |
| `textures/ui/deposit.png` | 64×64 | `lg/deposit.png` | Bank deposit |
| `textures/ui/withdraw.png` | 64×64 | `lg/withdraw.png` | Bank withdraw |
| `textures/ui/budget.png` | 64×64 | `lg/budget.png` | Bank budget |
| `textures/ui/security.png` | 64×64 | `lg/security.png` | Bank security |
| `textures/ui/automation.png` | 64×64 | `lg/automation.png` | Bank automation |
| `textures/ui/history.png` | 64×64 | `lg/history.png` | Transaction history |
| `textures/ui/vault.png` | 64×64 | `lg/vault.png` | Guild vault |
| `textures/ui/gold.png` | 64×64 | `lg/gold.png` | Gold / economy |
| `textures/ui/level.png` | 64×64 | `lg/level.png` | Guild level |
| `textures/ui/xp.png` | 64×64 | `lg/xp.png` | Experience |
| `textures/ui/reward.png` | 64×64 | `lg/reward.png` | Rewards |
| `textures/ui/prestige.png` | 64×64 | `lg/prestige.png` | Prestige system |
| `textures/ui/emoji.png` | 64×64 | `lg/emoji.png` | Guild emoji |
| `textures/ui/banner.png` | 64×64 | `lg/banner.png` | Guild banner |
| `textures/ui/mode.png` | 64×64 | `lg/mode.png` | Guild mode (peaceful/hostile) |
| `textures/ui/online.png` | 64×64 | `lg/online.png` | Online indicator |
| `textures/ui/offline.png` | 64×64 | `lg/offline.png` | Offline indicator |
| `textures/ui/active.png` | 64×64 | `lg/active.png` | Active status |
| `textures/ui/inactive.png` | 64×64 | `lg/inactive.png` | Inactive status |
| `textures/ui/locked.png` | 64×64 | `lg/locked.png` | Locked / restricted |
| `textures/ui/star.png` | 64×64 | `lg/star.png` | Featured / top |
| `textures/ui/leave.png` | 64×64 | `lg/leave.png` | Leave guild |
| `textures/ui/crown.png` | 64×64 | `lg/crown.png` | Owner / leader |
| `textures/ui/filler.png` | 64×64 | `lg/filler.png` | Empty slot background |
| `textures/ui/declare_war.png` | 64×64 | `lg/declare_war.png` | Declare war |
| `textures/ui/war_stats.png` | 64×64 | `lg/war_stats.png` | War statistics |
| `textures/ui/join_req.png` | 64×64 | `lg/join_req.png` | Join requests |
| `textures/ui/party_create.png` | 64×64 | `lg/party_create.png` | Create party |
| `textures/ui/party_list.png` | 64×64 | `lg/party_list.png` | Party list |
| `textures/ui/relations_history.png` | 64×64 | `lg/relations_history.png` | Relations history |
| `textures/ui/page_next.png` | 64×64 | `lg/page_next.png` | Next page |
| `textures/ui/page_prev.png` | 64×64 | `lg/page_prev.png` | Previous page |
| `textures/ui/disband.png` | 64×64 | `lg/disband.png` | Disband guild |
| `textures/ui/description.png` | 64×64 | `lg/description.png` | Description editor |

---

## Part 3 — Resource Pack Structure

### File layout

```text
LumaGuilds-Bedrock-UI.mcpack/
├── manifest.json
├── pack_icon.png
├── textures/
│   ├── ui/
│   │   ├── icon.png
│   │   ├── members.png
│   │   ├── settings.png
│   │   ├── bank.png
│   │   ├── wars.png
│   │   ├── home.png
│   │   ├── tag.png
│   │   ├── info.png
│   │   ├── ranks.png
│   │   ├── economy.png
│   │   ├── progression.png
│   │   ├── diplomacy.png
│   │   ├── confirm.png
│   │   ├── cancel.png
│   │   ├── back.png
│   │   ├── close.png
│   │   ├── edit.png
│   │   ├── delete.png
│   │   ├── invite.png
│   │   ├── kick.png
│   │   ├── promote.png
│   │   ├── demote.png
│   │   ├── rank_create.png
│   │   ├── rank_edit.png
│   │   ├── rank_delete.png
│   │   ├── reset.png
│   │   ├── alliance.png
│   │   ├── enemy.png
│   │   ├── truce.png
│   │   ├── peace.png
│   │   ├── deposit.png
│   │   ├── withdraw.png
│   │   ├── budget.png
│   │   ├── security.png
│   │   ├── automation.png
│   │   ├── history.png
│   │   ├── vault.png
│   │   ├── gold.png
│   │   ├── level.png
│   │   ├── xp.png
│   │   ├── reward.png
│   │   ├── prestige.png
│   │   ├── emoji.png
│   │   ├── banner.png
│   │   ├── mode.png
│   │   ├── online.png
│   │   ├── offline.png
│   │   ├── active.png
│   │   ├── inactive.png
│   │   ├── locked.png
│   │   ├── star.png
│   │   ├── leave.png
│   │   ├── crown.png
│   │   ├── filler.png
│   │   ├── declare_war.png
│   │   ├── war_stats.png
│   │   ├── join_req.png
│   │   ├── party_create.png
│   │   ├── party_list.png
│   │   ├── relations_history.png
│   │   ├── page_next.png
│   │   ├── page_prev.png
│   │   ├── disband.png
│   │   └── description.png
│   ├── font/
│   │   └── glyph_E0.png
│   └── textures_list.json
└── font/
    ├── default.json
    └── legacy_unicode.json
```

### manifest.json

```json
{
  "format_version": 2,
  "header": {
    "name": "LumaGuilds Bedrock UI Icons",
    "description": "Icons and controller glyphs for LumaGuilds Bedrock forms",
    "uuid": "<generate a fresh UUID>",
    "version": [1, 0, 0],
    "min_engine_version": [1, 20, 0]
  },
  "modules": [
    {
      "type": "resources",
      "uuid": "<generate a fresh UUID>",
      "version": [1, 0, 0]
    }
  ]
}
```

### textures_list.json

```json
{
  "resource_pack_name": "LumaGuilds Bedrock UI",
  "texture_name": "atlas.items",
  "texture_data": {
    "lg_icon": { "textures": "textures/ui/icon" },
    "lg_members": { "textures": "textures/ui/members" },
    "lg_settings": { "textures": "textures/ui/settings" },
    "lg_bank": { "textures": "textures/ui/bank" },
    "lg_wars": { "textures": "textures/ui/wars" },
    "lg_home": { "textures": "textures/ui/home" },
    "lg_tag": { "textures": "textures/ui/tag" },
    "lg_info": { "textures": "textures/ui/info" },
    "lg_ranks": { "textures": "textures/ui/ranks" },
    "lg_economy": { "textures": "textures/ui/economy" },
    "lg_progression": { "textures": "textures/ui/progression" },
    "lg_diplomacy": { "textures": "textures/ui/diplomacy" },
    "lg_confirm": { "textures": "textures/ui/confirm" },
    "lg_cancel": { "textures": "textures/ui/cancel" },
    "lg_back": { "textures": "textures/ui/back" },
    "lg_close": { "textures": "textures/ui/close" },
    "lg_edit": { "textures": "textures/ui/edit" },
    "lg_delete": { "textures": "textures/ui/delete" },
    "lg_invite": { "textures": "textures/ui/invite" },
    "lg_kick": { "textures": "textures/ui/kick" },
    "lg_promote": { "textures": "textures/ui/promote" },
    "lg_demote": { "textures": "textures/ui/demote" },
    "lg_rank_create": { "textures": "textures/ui/rank_create" },
    "lg_rank_edit": { "textures": "textures/ui/rank_edit" },
    "lg_rank_delete": { "textures": "textures/ui/rank_delete" },
    "lg_reset": { "textures": "textures/ui/reset" },
    "lg_alliance": { "textures": "textures/ui/alliance" },
    "lg_enemy": { "textures": "textures/ui/enemy" },
    "lg_truce": { "textures": "textures/ui/truce" },
    "lg_peace": { "textures": "textures/ui/peace" },
    "lg_deposit": { "textures": "textures/ui/deposit" },
    "lg_withdraw": { "textures": "textures/ui/withdraw" },
    "lg_budget": { "textures": "textures/ui/budget" },
    "lg_security": { "textures": "textures/ui/security" },
    "lg_automation": { "textures": "textures/ui/automation" },
    "lg_history": { "textures": "textures/ui/history" },
    "lg_vault": { "textures": "textures/ui/vault" },
    "lg_gold": { "textures": "textures/ui/gold" },
    "lg_level": { "textures": "textures/ui/level" },
    "lg_xp": { "textures": "textures/ui/xp" },
    "lg_reward": { "textures": "textures/ui/reward" },
    "lg_prestige": { "textures": "textures/ui/prestige" },
    "lg_emoji": { "textures": "textures/ui/emoji" },
    "lg_banner": { "textures": "textures/ui/banner" },
    "lg_mode": { "textures": "textures/ui/mode" },
    "lg_online": { "textures": "textures/ui/online" },
    "lg_offline": { "textures": "textures/ui/offline" },
    "lg_active": { "textures": "textures/ui/active" },
    "lg_inactive": { "textures": "textures/ui/inactive" },
    "lg_locked": { "textures": "textures/ui/locked" },
    "lg_star": { "textures": "textures/ui/star" },
    "lg_leave": { "textures": "textures/ui/leave" },
    "lg_crown": { "textures": "textures/ui/crown" },
    "lg_filler": { "textures": "textures/ui/filler" },
    "lg_declare_war": { "textures": "textures/ui/declare_war" },
    "lg_war_stats": { "textures": "textures/ui/war_stats" },
    "lg_join_req": { "textures": "textures/ui/join_req" },
    "lg_party_create": { "textures": "textures/ui/party_create" },
    "lg_party_list": { "textures": "textures/ui/party_list" },
    "lg_relations_history": { "textures": "textures/ui/relations_history" },
    "lg_page_next": { "textures": "textures/ui/page_next" },
    "lg_page_prev": { "textures": "textures/ui/page_prev" },
    "lg_disband": { "textures": "textures/ui/disband" },
    "lg_description": { "textures": "textures/ui/description" }
  }
}
```

---

## Part 4 — Design Guidelines for Codex

### Icon Style

- **Flat/vector style** — clean silhouettes, minimal shading
- **White/light fill with transparency** — Bedrock forms work best with clear silhouettes; the game engine handles colour/tint
- **64×64 pixels** — Bedrock standard for form button icons
- **PNG with alpha transparency** — no JPG, no hard backgrounds
- Avoid fine detail below 4px — form icons are displayed small on mobile screens

### How to Use the Reference Pack

The Nexo pack at `/opt/data/nexo-config/luma-guilds-nexo-pack-v2.zip` contains 61 Java-side icons. Adapt them to Bedrock:

- Java pack uses `custom_model_data` on Paper items; Bedrock uses direct `textures/ui/*.png` paths
- Keep the same visual identity but simplify for Bedrock's rendering
- The Java pack has 5×3 and 8×6 grid backgrounds for GUI — those are NOT needed in Bedrock (forms have their own background)

### Glyph Atlas Details

- `glyph_E0.png`: 256×16px total = 16 cells × 16px wide each
- Each cell is a **white vector icon** on full transparency
- Row 1 (glyph_E0 covers \uE001-\uE010)
- Use the standard Minecraft controller glyph style (Xbox Official Glyphs are the de-facto standard; Bedrock auto-converts to PlayStation/Switch)
- Keep lines 2px thick minimum for visibility on mobile

### Installation on Server

The .mcpack goes into `Geyser/packs/` directory on the server. Geyser automatically serves it to all Bedrock clients on join. No client-side install needed.

### Verification Checklist

- [ ] All 65 icons present in `textures/ui/`
- [ ] Glyph atlas `font/glyph_E0.png` with 16 controller buttons
- [ ] `font/default.json` and `font/legacy_unicode.json` both reference the glyph atlas
- [ ] `textures_list.json` registers every icon
- [ ] `manifest.json` has proper UUIDs and versioning
- [ ] Pack loads without errors in-game (`/geyser dump`)
- [ ] Form buttons display icons correctly
- [ ] Button glyphs render in form button text
