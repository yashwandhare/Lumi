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

### [devb] 2026-08-15 — Dev A's baseline UI is amended in place, never re-implemented

`TraceBlob`, `TraceInput`, `HomeScreen`, and the two sidebars in `TraceApp` are Dev A's established
baseline. Dev B changes them by the smallest diff that satisfies `DESIGN_LANGUAGE.md` and `todo.md`,
and new components adopt their style rather than introducing a second one.

**Why:** the owner's instruction, and a good one. A rewrite of a working component is unreviewable —
Dev A returning cannot tell a bug fix from a preference, so every line becomes a negotiation. A small
diff against a file they wrote is legible in a minute.

**Recorded because it cost work.** A full `TraceBlob` rewrite (+332/-226) was written and then
reverted under this rule. It compiled and it fixed four real defects, but it replaced the whole file,
so it was the wrong shape of change. Those defects are now being re-approached as a targeted diff:

1. Neither motion flag has any consumer, and three `while (true)` loops run regardless.
2. The shake reads `random()` during composition, so it is non-deterministic and mostly still —
   `todo.md` asks for deterministic motion explicitly.
3. No `contentDescription`, so a screen reader announces an unlabelled clickable.
4. `.size(48.dp)` is chained after the caller's modifier, so `HomeScreen`'s 72dp request is silently
   capped and the mascot draws smaller than its slot.

**Deliberately not carried across from the reverted version**, because each is a change of appearance
rather than a fix, and appearance is Dev A's call: geometry derived from the mascot's size instead of
fixed dp; a gradient radius computed from density instead of a fixed `120f`; and one shared body
composable behind both `TraceBlob` and `TraceLogoIcon`. The density point is a real portability bug —
the mascot's gloss lands differently on every screen density — and it should be reopened once the
targeted diff has landed.

### [devb] 2026-08-15 — Spacing token *values* changed, which moves existing layout

`DESIGN_LANGUAGE.md` §5's table is md 16 / lg 24 / xl 32 / xxl 48. The shipped `TraceSpacing` was
md 12 / lg 16 / xl 24 / xxl 32 with extra `xxxl`, `screen`, `gutter`, and `hairline` steps. The names
matched and the values did not, so the doc's "always use TraceSpacing tokens, never raw dp" could not
be obeyed and be correct at the same time. The tokens now match §5.

**Consequence, stated plainly:** `EmptyState.kt` is the only file that read these tokens, and its
padding therefore changed — `spacing.xxl` went from 32dp to 48dp and `spacing.md` from 12dp to 16dp.
That is a visible change to Dev A's baseline, made because §5 is authoritative, not because the old
spacing looked wrong.

`xxxl`, `screen`, and `gutter` were removed rather than kept: nothing referenced them, and §5 does not
define them. `hairline` moved to `TraceSize` at its real used value of 0.5dp rather than the declared
1dp — every call site in the app already passed 0.5dp by hand.


