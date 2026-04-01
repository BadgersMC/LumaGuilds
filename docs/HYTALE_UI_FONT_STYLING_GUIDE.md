# Hytale UI Font Styling Guide

## The Font Problem

Hytale's default UI font cannot be changed via plugin APIs. However, we can make it feel better through strategic styling choices.

## Recommended Type Scale

Use this consistent type scale across all UI files for a professional look:

### Display (Main Titles)
```
Style: (FontSize: 24, TextColor: #f8fafc, RenderBold: true, HorizontalAlignment: Center);
```
**Use for**: Main panel titles (e.g., "Guild Control Panel")

### Heading 1 (Page Titles)
```
Style: (FontSize: 18, TextColor: #f1f5f9, RenderBold: true, HorizontalAlignment: Center);
```
**Use for**: Section headers, page titles

### Heading 2 (Subsections)
```
Style: (FontSize: 16, TextColor: #e2e8f0, RenderBold: true);
```
**Use for**: Subsection titles, important labels

### Body Large (Emphasized Text)
```
Style: (FontSize: 15, TextColor: #cbd5e1, RenderBold: false);
```
**Use for**: Player names, guild names, important info

### Body (Default Text)
```
Style: (FontSize: 14, TextColor: #cbd5e1, RenderBold: false);
```
**Use for**: Most UI text, descriptions, labels

### Body Small (Secondary Text)
```
Style: (FontSize: 13, TextColor: #94a3b8, RenderBold: false);
```
**Use for**: Secondary info, metadata (ranks, timestamps)

### Caption (Helper Text)
```
Style: (FontSize: 12, TextColor: #64748b, RenderBold: false);
```
**Use for**: Footer text, hints, help text

### Caption Small (Fine Print)
```
Style: (FontSize: 11, TextColor: #475569, RenderBold: false);
```
**Use for**: Very subtle hints ("Press ESC to close")

## Color System

### Neutral Colors (Text)
- `#f8fafc` - Brightest white (display/main titles only)
- `#f1f5f9` - Very light gray (headings)
- `#e2e8f0` - Light gray (subheadings)
- `#cbd5e1` - Medium-light gray (body text)
- `#94a3b8` - Medium gray (secondary text)
- `#64748b` - Dark gray (captions)
- `#475569` - Very dark gray (fine print)

### Accent Colors
- `#60a5fa` - Blue (links, highlights)
- `#fbbf24` - Gold (special items, guild names)
- `#34d399` - Green (success, positive actions)
- `#f87171` - Red (errors, warnings, destructive actions)
- `#a78bfa` - Purple (special features)

### Background Colors
- `#0f172a` - Very dark blue-gray (main backgrounds)
- `#1e293b` - Dark blue-gray (cards, panels)
- `#334155` - Medium blue-gray (hover states)
- `#475569` - Light blue-gray (separators)

## Font Styling Best Practices

### 1. Avoid Overuse of Bold
Bold text makes the font look "heavier" and more pixelated. Only use `RenderBold: true` for:
- Main titles
- Headings (H1, H2)
- Very important calls-to-action

### 2. Use Softer Colors
Instead of pure white (#ffffff), use off-whites and light grays:
- Better: `#cbd5e1`, `#e2e8f0`, `#f1f5f9`
- Avoid: `#ffffff` (too harsh)

### 3. Size Down
Hytale's font looks better at smaller sizes:
- Prefer 14-16px for body text
- Avoid going above 24px for titles

### 4. Increase Contrast Through Color, Not Size
Instead of making text bigger, use color to create hierarchy:
- Important: `#f1f5f9`
- Normal: `#cbd5e1`
- Secondary: `#94a3b8`

### 5. Consistent Spacing
Match font sizes with appropriate height anchors:
- 24px text → `Anchor: (Height: 32-40)`
- 18px text → `Anchor: (Height: 28-32)`
- 14px text → `Anchor: (Height: 20-24)`
- 12px text → `Anchor: (Height: 16-20)`

## Example: Before & After

### Before (Harsh, Heavy)
```ui
Label {
    Text: "Guild Members";
    Anchor: (Height: 40);
    Style: (FontSize: 28, TextColor: #ffffff, RenderBold: true);
}

Label #MemberName {
    Text: "PlayerName";
    Style: (FontSize: 18, TextColor: #ffffff, RenderBold: true);
}
```

### After (Softer, Professional)
```ui
Label {
    Text: "Guild Members";
    Anchor: (Height: 32);
    Style: (FontSize: 24, TextColor: #f1f5f9, RenderBold: true, HorizontalAlignment: Center);
}

Label #MemberName {
    Text: "PlayerName";
    Style: (FontSize: 15, TextColor: #cbd5e1, RenderBold: false);
}
```

## Implementation

To apply these changes to existing UI files, update the `Style:` properties:

```ui
// Main title
Style: (FontSize: 24, TextColor: #f8fafc, RenderBold: true, HorizontalAlignment: Center);

// Guild name display
Style: (FontSize: 15, TextColor: #fbbf24, HorizontalAlignment: Center);

// Section headers
Style: (FontSize: 16, TextColor: #e2e8f0, RenderBold: true);

// Body text
Style: (FontSize: 14, TextColor: #cbd5e1);

// Secondary info (ranks, timestamps)
Style: (FontSize: 13, TextColor: #94a3b8);

// Helper text
Style: (FontSize: 12, TextColor: #64748b);
```

## Future: When Hytale Adds Custom Fonts

If Hytale adds custom font support in the future, likely paths:
1. Asset pack with fonts folder
2. `FontFamily` property in Style block
3. Font registration via plugin manifest

Until then, this type scale and color system will make your UI feel more polished.
