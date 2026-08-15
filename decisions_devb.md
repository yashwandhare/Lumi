# Trace v2 — Decisions (Dev B)

Running record of *why* Dev B made presentation-layer choices, so they are not re-litigated later
without cause. Dev A folds these entries into `decisions.md` at merge points, prefixes intact.

## How to use this file

- Append new entries at the bottom. Never edit an old entry to change its meaning.
- If a past decision is reversed, do not delete it. Add a new entry that references it and explains
  what changed.
- Prefix every entry with `[devb]` and date it. A decision without a date cannot be ordered against
  the one that supersedes it.
- Log a decision when someone could reasonably reverse the choice later and would need the reasoning
  to decide whether to. Do not log routine implementation detail.
- The rules already binding this branch live in `decisions.md`. Do not restate them here; only record
  new choices, and reference the existing entry when a choice follows from one.

---

### [devb] 2026-08-15 — `DESIGN_LANGUAGE.md` supersedes the Design Specification's UI sections

The owner ruled that `Trace — Design Specification.docx` is now **mostly deprecated for UI**, because
`DESIGN_LANGUAGE.md` has drifted from it deliberately. Every UI component, every component
optimization, and every design decision on the presentation layer resolves against
`DESIGN_LANGUAGE.md`. The docx remains authoritative for non-UI matters — architecture, data model,
capability contracts, testing.

**Why this needs an entry:** `todo.md` and `decisions.md` both describe the palette as "rice-paper
white, sumi near-black, one muted sumi-olive accent". That palette no longer exists. The accent is cyan
Slime Blue `#4DB6AC` and the surfaces are `#151515`/`#20201F` dark and `#FAFBF7`/`#E4E7DF` light. Anyone
reading the older files without this entry would build to a dead spec.

**Consequence:** section numbers cited in `todo.md` Phase 1-7 `[devb]` items (§23, §28, §31-35) point at
the docx. Read them as intent — "a component library exists", "loading states name the real
operation" — and take concrete values from `DESIGN_LANGUAGE.md`.

### [devb] 2026-08-15 — Light-mode `onPrimary` is `#171717`, not `#FAFBF7`

`DESIGN_LANGUAGE.md` §2's light table specifies `On Primary` = `#FAFBF7`. That single cell is
overridden. Everything else in §2 stands, including `#4DB6AC` as the only accent in both modes.

**Why:** measured against the accent, `#FAFBF7` on `#4DB6AC` is **2.35:1**. WCAG AA needs 4.5:1 for
text and 3:1 for a graphical object. A white arrow on the cyan Send button is therefore unreadable to
low-vision users, and `for_devb.md` states contrast compliance as a hard rule. `#171717` on `#4DB6AC`
measures **7.35:1**.

**Why this and nothing more:** the accent hue itself is untouched, so §2's "the Slime Blue is the only
accent colour" and the mascot's gradient are unaffected. The accent fill against the light background
is also 2.35:1, but the Send button is identified by its arrow glyph rather than by its fill alone, and
the glyph now passes at 7.35:1 — so the component is identifiable without changing the palette. This is
the smallest possible deviation that clears the rule.

**Reverse it only by** changing the accent hue for light mode, which is a larger spec change and needs
an owner decision.

### [devb] 2026-08-15 — One motion gate, not three checks

`todo.md` requires the mascot's animation to respect reduced-motion, lifecycle state, and battery
saver. Those three signals collapse into a single `LocalMotionEnabled` provided at theme level, beside
the existing `LocalReducedMotion`.

**Why:** a caller required to check three conditions will eventually check two. Reduced motion is a
stated accessibility rule; battery saver is listed alongside it in the spec and an infinite animation is
exactly the work a user in power-save mode is trying to avoid; below `Lifecycle.State.STARTED` the app
is off screen and an infinite loop is burning frames nobody sees. All three mean the same thing to a
composable: do not animate.

**Both flags are kept.** `LocalReducedMotion` answers "the user asked for stillness" — it also governs
non-animation choices. `LocalMotionEnabled` answers "run this loop right now".

**When it is false, show the resting state, never a frozen mid-frame.** A mascot stopped mid-squash
looks broken; a mascot at rest looks deliberate.

