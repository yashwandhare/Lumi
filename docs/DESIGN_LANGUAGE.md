# Lumi — Design Language Baseline
> Phase 0 UI Foundation. All future screens, components, and flows must be cohesive with this document.

---

## 1. Philosophy

Lumi reads as **editorial and calm**. Every decision comes from restraint:
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

Always use LumiSpacing tokens. Never raw dp literals in layout.

| Token | Value |
|---|---|
| xs | 4dp |
| sm | 8dp |
| md | 16dp |
| lg | 24dp |
| xl | 32dp |
| xxl | 48dp |

---

## 6. The Mascot (LumiBlob)

The mascot is Lumi's personality. Lives at the centre of the home screen.

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

### Docked Mascot
Once a conversation starts, the mascot leaves the centre of the home screen and docks immediately right
of the hamburger menu, at avatar size. The transcript is the content of the screen at that point, and two
mascots — one in the bar, one in the middle — would be one too many.

It keeps its breathing and its reactions while docked. Users who find a moving companion beside text they
are reading distracting can turn it off with **Live mascot** in Settings.

### Context & Interaction States
| State | Trigger | Visual |
|---|---|---|
| Typing | Keyboard opens | Glides to the bottom above the input field, scales down |
| Wink | Tapping UI buttons (e.g., Send, Attach) | Eyes squint for 300ms instantly |
| Angry | 5+ taps within 2 seconds | Body turns deep red, shakes gently — ±1dp tapering to rest, not a hard jitter |

---

## 6b. Chat Transcript

The user's turn sits in a `surfaceVariant` bubble, capped at 300dp so it never spans the screen. The
model's reply is plain text on the background, unboxed — the reply *is* the content, and boxing it makes a
two-paragraph answer read as a quoted aside. This also keeps the accent out of the transcript entirely,
which §2 reserves for actions.

Both are `bodyMedium` (15sp). At `bodyLarge` a phone-width column fit so few words per line that replies
broke into tall stacks of fragments.

**Markdown is rendered, not shown.** Lists, bold, inline code, and fenced code blocks all format. Code is
the single exception to the serif rule in §3 — it is set in a monospace face, because alignment and
telling `l` from `1` is the entire point. Nothing else departs from the type scale.

**While the model works**, the reply slot shows one status verb — *pondering*, *drafting*, *cooking*,
*resonating* and others, picked at random — with animated dots. The verb is fixed for the whole reply:
one that changed mid-wait read as several failed attempts rather than one in progress. The motion lives in
the dots, which are ignorable in a way a changing word is not.

**Under a finished reply**, at 55% `onSurfaceVariant` in `labelSmall`: elapsed time, approximate decode
rate, and time-to-first-token when it exceeds 1.5s. A footnote about the answer, never competing with it.

**The view follows a streaming reply** and stops the instant the user drags, resuming when they let go at
the bottom.

## 7. Input Field (LumiInput)

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

## 10. Settings

One screen, four sections: Appearance, Processor, Model parameters, System prompt. Model parameters are
**not** a separate destination — they were a sidebar entry once, and splitting them from Settings only made
users hunt for which of two screens held the control they wanted.

Sliders carry both a label and a live value, plus one line saying what the parameter does in plain words.
A setting that cannot take effect immediately says so rather than pretending: sampling applies to the next
conversation, and changing the processor reloads the model and clears what it remembers.

## 11. Onboarding

The first screen introduces Lumi before asking for anything. Name, one line on what it is, three concrete
capabilities, the privacy promise, then the download button with the size on it.

Concrete over adjectives: "answer questions about your own notes" is something a person can picture,
"powerful AI assistant" is not. One screen, not a carousel — an onboarding flow standing between the user
and a 2.6GB download is a worse first impression than one that gets out of the way.

**Every number is real.** Size comes from the pinned artefact, percentage from bytes on disk, speed from a
smoothed sample once it is long enough to be honest. Nothing estimates remaining time: over an unknown
connection that figure is wrong often enough to discredit the numbers beside it. All sizes are decimal GB,
matching how connections are sold — mixing in binary GiB once made a download count past its own total.
