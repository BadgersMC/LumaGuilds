# Font Customization in Hytale - Complete Guide

## TL;DR - The Unfortunate Truth

**You cannot change the font family in Hytale CustomUI.** The font is baked into the Hytale client and plugin APIs don't support custom fonts (yet).

**However**, you CAN make it look significantly better through strategic styling.

---

## What You CANNOT Do

- ❌ Load custom font files (TTF, OTF, WOFF)
- ❌ Use `FontFamily` property (doesn't exist)
- ❌ Change font weight beyond bold/normal
- ❌ Use italic, underline, or other styles
- ❌ Override Hytale's default UI font

## What You CAN Do

- ✅ Change font size (`FontSize: 14`)
- ✅ Change text color (`TextColor: #cbd5e1`)
- ✅ Toggle bold rendering (`RenderBold: true/false`)
- ✅ Control text alignment
- ✅ **Make the default font look way better through smart styling**

---

## The Solution: Professional Type Styling

I've created a complete type scale and styling guide that will make your UI look significantly more polished.

### Key Improvements:

1. **Smaller font sizes** - The font looks better at 14-18px instead of 24-28px
2. **Softer colors** - Use `#cbd5e1` instead of harsh `#ffffff`
3. **Less bold text** - Only bold for titles, not body text
4. **Better color hierarchy** - Use shades of gray to create visual hierarchy

### Files Created:

1. **`docs/HYTALE_UI_FONT_STYLING_GUIDE.md`** - Complete styling guide with:
   - Professional type scale (Display → Caption)
   - Color system (neutrals + accents)
   - Best practices
   - Before/after examples

2. **`GuildControlPanel_Refined.ui`** - Example refined UI with better styling

3. **`FeatureButton_Refined.ui`** - Example refined button template

---

## Quick Win: Update Your Current UI

Here's what to change in your existing files to make them feel less "heavy":

### GuildControlPanel.ui

**Before:**
```ui
Label {
    Text: "Guild Control Panel";
    Anchor: (Height: 40);
    Style: (FontSize: 28, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center);
}

Label #GuildName {
    Text: "Loading...";
    Anchor: (Height: 30);
    Style: (FontSize: 16, TextColor: #ffd700, HorizontalAlignment: Center);
}
```

**After:**
```ui
Label {
    Text: "Guild Control Panel";
    Anchor: (Height: 36);
    Style: (FontSize: 24, TextColor: #f1f5f9, RenderBold: true, HorizontalAlignment: Center);
}

Label #GuildName {
    Text: "Loading...";
    Anchor: (Height: 26);
    Style: (FontSize: 15, TextColor: #fbbf24, HorizontalAlignment: Center);
}
```

**Changes:**
- Reduced title from 28px → 24px
- Changed harsh white (#ffffff) → softer white (#f1f5f9)
- Reduced guild name from 16px → 15px
- Changed gold (#ffd700) → warmer gold (#fbbf24)
- Adjusted heights to match smaller text

### FeatureButton.ui

**Current (using Common.ui button):**
```ui
$C.@TextButton #FeatureBtn {
    @Text = "Feature";
    Anchor: (Height: 44);
}
```

The issue: `$C.@TextButton` likely uses Hytale's default button styling with bold text and large font.

**Options:**

#### Option A: Keep button, accept the font
If `Common.ui` buttons have fixed styling, you may be stuck with them for interactive buttons. This is a Hytale limitation.

#### Option B: Create custom clickable groups
If you can bind click events to regular Groups (test this!), create custom button styling:

```ui
Group #FeatureBtn {
    Anchor: (Height: 42);
    Background: #1e293b;
    LayoutMode: Center;
    Padding: (Horizontal: 16, Vertical: 10);

    Label #FeatureBtnText {
        Text: "Feature";
        Style: (FontSize: 14, TextColor: #cbd5e1, HorizontalAlignment: Center);
    }
}
```

Then in Kotlin:
```kotlin
evt.addEventBinding(
    CustomUIEventBindingType.Activating,
    "$selector #FeatureBtn",  // Bind to the Group, not a button
    EventData().append("Action", action),
    false
)
```

---

## Recommended Type Scale (Copy-Paste Ready)

Use these exact styles for consistency:

```ui
// DISPLAY - Main titles
Style: (FontSize: 24, TextColor: #f1f5f9, RenderBold: true, HorizontalAlignment: Center);

// HEADING 1 - Page titles
Style: (FontSize: 18, TextColor: #e2e8f0, RenderBold: true, HorizontalAlignment: Center);

// HEADING 2 - Section headers
Style: (FontSize: 16, TextColor: #e2e8f0, RenderBold: true);

// BODY LARGE - Player names, important text
Style: (FontSize: 15, TextColor: #cbd5e1);

// BODY - Default text
Style: (FontSize: 14, TextColor: #cbd5e1);

// BODY SMALL - Secondary info
Style: (FontSize: 13, TextColor: #94a3b8);

// CAPTION - Helper text
Style: (FontSize: 12, TextColor: #64748b);

// CAPTION SMALL - Fine print
Style: (FontSize: 11, TextColor: #475569);
```

---

## Color Palette (Copy-Paste Ready)

### Text Colors
```
#f8fafc  - Brightest (display only)
#f1f5f9  - Very light (H1)
#e2e8f0  - Light (H2)
#cbd5e1  - Body text
#94a3b8  - Secondary
#64748b  - Captions
#475569  - Fine print
```

### Accent Colors
```
#fbbf24  - Gold (guild names, highlights)
#60a5fa  - Blue (links, info)
#34d399  - Green (success)
#f87171  - Red (errors, warnings)
#a78bfa  - Purple (special)
```

### Backgrounds
```
#0f172a  - Main dark background
#1e293b  - Cards/panels
#334155  - Hover states
#475569  - Separators
```

---

## Implementation Steps

1. **Read the style guide**: `docs/HYTALE_UI_FONT_STYLING_GUIDE.md`

2. **Choose an approach**:
   - **Quick**: Just update font sizes and colors in existing files
   - **Full**: Switch to refined UI templates and implement the type scale

3. **Test one file first**: Update `GuildControlPanel.ui` and see how it feels

4. **Roll out gradually**: Update other UI files once you're happy with the style

---

## When Will Hytale Support Custom Fonts?

Unknown. Possible indicators:
- Hytale is still in beta - features are being added
- Minecraft uses resource packs for custom fonts
- Hytale's asset pack system (`"IncludesAssetPack": true`) might support it later

**What to watch for:**
- Hytale API updates mentioning fonts
- Community requests on Hytale forums
- New properties in `Style:` blocks

---

## Telling Your Team

"We can't change the actual font (Hytale limitation), but I've implemented professional typography styling that makes the UI look way more polished. Smaller sizes, softer colors, better hierarchy."

Then show them the before/after. It should feel noticeably better.

---

## Final Thoughts

Yes, it sucks that you can't use custom fonts. But good typography is 80% about spacing, sizing, and color - which you CAN control.

The refined styling I've provided will make your UI feel significantly more professional, even with Hytale's default font.

---

## Quick Reference

**Files to read:**
- `docs/HYTALE_UI_FONT_STYLING_GUIDE.md` - Complete guide
- `GuildControlPanel_Refined.ui` - Example implementation
- `FeatureButton_Refined.ui` - Example button

**Files to update:**
- All `.ui` files in `src/main/resources/Common/UI/Custom/LumaGuilds/`

**Key changes:**
- FontSize: 28 → 24 (titles)
- FontSize: 16 → 14 (body)
- TextColor: #ffffff → #cbd5e1 (body)
- TextColor: #ffd700 → #fbbf24 (gold)
- RenderBold: true → false (body text)
