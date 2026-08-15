# Trace — Design Language Baseline
> Phase 0 UI Foundation. All future screens, components, and flows must be cohesive with this document.

---

## 1. Philosophy

Trace reads as **editorial and calm**. Every decision comes from restraint:
- Fewer elements, better placed.
- Hierarchy from size, weight, and spacing — never from colour.
- The mascot is the only thing that is ever playful. Everything else is composed.

**Rules that are never broken:**
- No emoji anywhere in the UI.
- No coloured border accents or glowing outlines.
- No dashboard patterns for things that are not dashboards.
- No purple on dark backgrounds.

---

## 2. Colour

### Dark Mode (default when system is dark)

| Token | Value | Usage |
|---|---|---|
| Background | `#121212` | Root screens, nav drawers |
| Surface | `#1F1F1F` | Cards, sheets, input fields |
| Surface Variant | `#2A2A2A` | Secondary containers, drawer rows |
| On Background / On Surface | `#D5D5D5` | All body text |
| Primary (accent) | `#D99B58` | Send button, selected state, mascot |
| Secondary (accent light) | `#E8C37C` | Lighter accent moments |
| On Primary | `#121212` | Text/icon on primary buttons |
| Error | `#CF6679` | Errors only |

### Light Mode (default when system is light)

| Token | Value | Usage |
|---|---|---|
| Background | `#FAFBF7` | Root screens, nav drawers |
| Surface / Surface Variant | `#E4E7DF` | Cards, sheets, secondary containers |
| On Background / On Surface | `#171717` | All body text |
| Primary (accent) | `#D99B58` | **Same yellow-orange as dark mode** |
| On Primary | `#FAFBF7` | Text/icon on primary buttons |
| Error | `#5C2A2A` | Errors only |

### Accent Colour Rule
The soft yellow-orange (`#D99B58`) is the **only** accent colour. It is used sparingly:
- The mascot's body gradient
- The Send button fill
- Active/selected drawer row indicator
- The app launcher icon

The app is otherwise **monochrome** in both modes. No secondary accent colours.

The second accent (`#C1410C`) is reserved for the mascot's **anger state** only.

---

## 3. Typography

**90% Serif, 10% Sans-serif.** The split is intentional and strictly enforced.

| Role | Family | Usage |
|---|---|---|
| Display / Headline / Title / Body | Serif | All content the user reads |
| Label | Sans-serif | Buttons, chips, timestamps, metadata |

### Scale

| Style | Size | Weight | Notes |
|---|---|---|---|
| displayLarge | 44sp | Normal | Ceremonial only |
| headlineLarge | 26sp | Medium | Screen titles |
| headlineMedium | 22sp | Medium | Home greeting |
| titleLarge | 20sp | Medium | Section headers, drawer title |
| titleMedium | 17sp | Medium | Input placeholder, list items |
| bodyLarge | 17sp | Normal | Chat, notes |
| bodyMedium | 15sp | Normal | Supporting copy |
| labelMedium | 13sp | Medium, Sans | Attachment labels, section headers |

Hierarchy comes from **size and weight only**. Never use colour to establish hierarchy.

---

## 4. Shape

All corners use a single cohesive radius:

| Context | Radius |
|---|---|
| Default (all Material shape tokens) | 16dp |
| Input field | 28dp (pill-ish, primary interaction surface) |
| Attachment tiles | 24dp |
| Icon buttons | CircleShape (42dp diameter) |
| Drawer rows | 16dp |

---

## 5. Spacing Tokens

Always use TraceSpacing tokens. Never raw dp literals in layout.

| Token | Value |
|---|---|
| xs | 4dp |
| sm | 8dp |
| md | 16dp |
| lg | 24dp |
| xl | 32dp |
| xxl | 48dp |

---

## 6. The Mascot (TraceBlob)

The mascot is Trace's personality. Lives at the centre of the home screen.

### Anatomy
- Radial gradient blob in accent yellow-orange.
- Two small white circular eyes.
- Soft glow aura (20% opacity radial gradient behind the body).

### Always-Running Animations
| Animation | Description |
|---|---|
| Breathing | Scales 0.98x to 1.05x on a 2s sine loop |
| Float | Oscillates ±5dp vertically on a 2.5s sine loop |
| Blink | Random blink every 1-5 seconds (eyes squint for 150ms) |
| Look around | Eyes shift randomly (±4dp X, ±2dp Y) every 2-6 seconds |

### Interaction States
| State | Trigger | Visual |
|---|---|---|
| Wink | Single tap | Eyes squint for 300ms |
| Angry | 5+ taps within 2 seconds | Body turns Sumi Red (#5C2A2A), shakes |
| Reset | 2 seconds after last tap | Returns to idle |

### Sizing
- Home screen: 72dp container
- App launcher icon: 48dp container

---

## 7. Input Field (TraceInput)

Always at the bottom of the screen. Has imePadding() so it rises above the keyboard.

### Structure
```
[Attach +]          [Audio wave]  [Send arrow]
       [Text field — 4 lines max]
```

### Buttons
- Attach: 42dp circle, onSurface 10% background
- Audio: 42dp circle, onSurface solid fill
- Send: 42dp circle, primary (yellow-orange) fill — only tappable when text is present
- Gap between Audio and Send: 12dp
- All buttons use Box + .clickable (not IconButton) to allow sub-48dp sizing.

---

## 8. Navigation

### Left Sidebar
**Destinations in order:**
1. Profile row (avatar + "User")
2. Divider
3. Home
4. Memory
5. Notes
6. Routine
7. Journal
8. Lists
9. [Developer] section label
10. Model Parameters
11. Search Scope
12. (spacer)
13. Divider
14. Settings

Drawer background: app background colour (not surface variant).
Selected row: secondaryContainer fill, 16dp radius.

### Right Sidebar (History)
Opens from the right on history icon tap. Visually identical to left sidebar:
- Same background colour.
- Header: History icon circle + "History" bold title + divider.

### Top App Bar
No title. Hamburger left, History icon right. Invisible (matches background).

---

## 9. Theme Persistence

- First launch: follows system dark/light setting.
- User override: Dark Mode toggle in Settings.
- Override persisted in SharedPreferences (key: theme_mode, 1=dark, 0=light, -1=follow system).

---

## 10. Phase 0 Checklist

| Item | Status |
|---|---|
| Dark mode colour scheme | Done |
| Light mode colour scheme (monochrome + shared accent) | Done |
| Theme persistence + system fallback | Done |
| Serif typography system | Done |
| Cohesive 16dp shape system | Done |
| Mascot (breathing, floating, blinking, looking, anger) | Done |
| App launcher icon (vector, mascot-style) | Done |
| Input field (IME-aware, 28dp radius, 42dp buttons, 12dp gap) | Done |
| Left sidebar (all destinations + profile) | Done |
| Right history sidebar (cohesive with left) | Done |
| Attachment bottom sheet (Camera, Photos, Files, Audio, Web toggle) | Done |
| Home greeting (time-aware, no emoji) | Done |
| APK baseline saved to device Downloads | Done |
