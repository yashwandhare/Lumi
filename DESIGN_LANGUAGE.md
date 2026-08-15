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
| Background | `#151515` | Root screens, nav drawers |
| Surface | `#20201F` | Cards, sheets, input fields |
| Surface Variant | `#2C2C2B` | Secondary containers, drawer rows |
| On Background / On Surface | `#F0EFEC` | All body text |
| Primary (accent) | `#4DB6AC` | Slime Blue - Send button, selected state, mascot |
| Secondary (accent light) | `#80CBC4` | Lighter accent moments |
| On Primary | `#151515` | Text/icon on primary buttons |
| Error | `#CF6679` | Errors only |

### Light Mode (default when system is light)

| Token | Value | Usage |
|---|---|---|
| Background | `#FAFBF7` | Root screens, nav drawers |
| Surface / Surface Variant | `#E4E7DF` | Cards, sheets, secondary containers |
| On Background / On Surface | `#171717` | All body text |
| Primary (accent) | `#4DB6AC` | **Same Slime Blue as dark mode** |
| On Primary | `#FAFBF7` | Text/icon on primary buttons |
| Error | `#5C2A2A` | Errors only |

### Accent Colour Rule
The Slime Blue (`#4DB6AC`) is the **only** accent colour. It is used sparingly:
- The mascot's body
- The Send button fill
- Active/selected drawer row indicator
- The app launcher icon

The app is otherwise **monochrome** in both modes. No secondary accent colours.

---

## 3. Typography

**90% Serif, 10% Sans-serif.** The split is intentional and strictly enforced.

| Role | Family | Usage |
|---|---|---|
| Display / Headline / Title / Body | Serif | All content the user reads |
| Label | Sans-serif | Buttons, chips, timestamps, metadata |

Hierarchy comes from **size and weight only**. Never use colour to establish hierarchy.

---

## 4. Shape & Glassmorphism

All corners use a single cohesive radius, except for full-bleed panels:

| Context | Radius |
|---|---|
| Default (all Material shape tokens) | 16dp |
| Input field | 28dp (pill-ish, primary interaction surface) |
| Attachment tiles | 24dp |
| Icon buttons | CircleShape (42dp diameter) |
| Sidebars & Modals | **RectangleShape (0dp)** - edge-to-edge layout |

**Glassmorphism**: Sidebars, web search fields, and attachment panels use frosted glass aesthetics. This means using `surfaceVariant` with partial alpha transparency and a subtle 0.5dp border, blurring the underlying content softly.

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

### Anatomy (The Fantasy Slime)
- Form: A dynamic, bouncing dome-like slime. It uses animated `RoundedCornerShape` percentages to maintain a perfectly round top and a flatter, squishier base.
- Glossy Volume: It has a **reversed radial gradient**. The core is a deep dark teal (`#00838F`), smoothly fading outwards into the primary cyan (`#4DB6AC`), ending in a glowing light mint edge (`#E0F7FA`) to simulate a 3D gelatinous/water-bubble look.
- Two small white circular eyes.
- Soft glow aura (20% opacity radial gradient behind the body).

### Always-Running Animations
| Animation | Description |
|---|---|
| Breathing | Scales 0.98x to 1.05x on a 2s sine loop |
| Float | Oscillates ±5dp vertically on a 2.5s sine loop |
| Shape Shifting | Randomly morphs between a tall blob and flat puddle every 5-12s |

### Context & Interaction States
| State | Trigger | Visual |
|---|---|---|
| Typing | Keyboard opens | Glides to the bottom above the input field, scales down |
| Wink | Tapping UI buttons (e.g., Send, Attach) | Eyes squint for 300ms instantly |
| Angry | 5+ taps within 2 seconds | Body turns deep red, shakes |

---

## 7. Input Field (TraceInput)

Always at the bottom of the screen. Has imePadding() so it rises above the keyboard. Uses `animateContentSize()` to smoothly expand vertically.

### Structure
```
[Attach +]          [Audio wave]  [Send arrow]
       [Text field — 4 lines max]
```

### Buttons
- Attach: 42dp circle, onSurface 10% background
- Audio: 42dp circle, onSurface solid fill
- Send: 42dp circle, primary fill — only tappable when text is present
- Gap between Audio and Send: 12dp
- The device keyboard **Enter** button adds a new line (does not send the message).

---

## 8. Navigation

### Left Sidebar
Uses full-height glassmorphic panels (RectangleShape) and padding for text to avoid bezels.

**Destinations in order:**
1. Profile row (avatar + "User")
2. Divider
3. Home
4. Memory
5. Notes
...
14. Settings

### Right Sidebar (History)
Opens from the right on history icon tap. Visually identical and cohesive with the left sidebar. Includes a "New Chat" button at the bottom.

---

## 9. Theme Persistence

- First launch: follows system dark/light setting (including the splash screen).
- User override: Dark Mode toggle in Settings (scaled down slightly for sleekness).
- Override persisted in SharedPreferences.
