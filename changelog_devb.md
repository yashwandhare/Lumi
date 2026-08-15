# Trace v2 — Changelog (Dev B)

Major changes made by Dev B on the `devb` branch. Written for a person catching up — not a mirror of
the git log. Dev A folds these entries into `changelog.md` at merge points, prefixes intact.

## How to use this file

- Newest entries at the top, under `## Unreleased`.
- Prefix every entry with `[devb]`.
- Group entries under `Added`, `Changed`, `Fixed`, `Removed`.
- One line per change. Say what changed for the user or for another developer, not which files moved.
- Log a change when it alters behaviour, an interface another developer builds against, or a
  dependency. Do not log refactors nobody outside the file would notice, or work in progress.
- If a change came with a judgement call, put the reasoning in `decisions_devb.md` and keep the
  changelog line short.

---

## Unreleased

### Added

- `[devb]` **Design tokens the whole UI can share.** `TraceMotion` holds every duration and easing in
  one place, including the three mascot timings §6 fixes. `TraceShape` names radii by role — the 28dp
  input, the 24dp tile, the square full-bleed panel — because Material's small/medium/large ramp
  cannot express a role. `TraceSize` holds fixed component dimensions so a spacing change can no
  longer silently resize a control.
- `[devb]` **`LocalMotionEnabled`**, provided at theme level. One flag folding reduced motion, battery
  saver, and lifecycle state, all three observed live. Read it in any animation; when it is false,
  render the resting pose. `LocalReducedMotion` stays for the broader "the user asked for stillness"
  question. See `decisions_devb.md`.
- `[devb]` Mascot colours are named tokens (`MascotCore`, `MascotEdge`, and the three anger values)
  instead of six hex literals inside the composable.

### Changed

- `[devb]` **`DESIGN_LANGUAGE.md` is now the authority for all UI work**, superseding the Design
  Specification's UI sections, which the owner has deprecated. The palette is cyan Slime Blue `#4DB6AC`,
  not the sumi-olive that `todo.md` and `changelog.md` still describe. See `decisions_devb.md`.
- `[devb]` **Phase 1 is being executed end to end by Dev B**, including the items tagged `[deva]`. The
  tags in `todo.md` are left in place as a record of the original split.
- `[devb]` **Spacing tokens now match §5** — md 16, lg 24, xl 32, xxl 48. They previously read 12, 16,
  24, 32 under the same names. `EmptyState` is the only file that used them, so its padding grew.
  `xxxl`, `screen`, and `gutter` are gone; nothing referenced them. See `decisions_devb.md`.
- `[devb]` Light mode's `On Primary` is `#171717` rather than `DESIGN_LANGUAGE.md` §2's `#FAFBF7`. White
  on the cyan accent measures 2.35:1 and fails WCAG AA; the dark value measures 7.35:1. The accent hue
  is unchanged. See `decisions_devb.md`.
- `[devb]` `TraceTheme`'s `darkTheme` parameter now defaults to the system setting instead of always
  light, matching §9's "first launch follows system". `MainActivity` already passed it explicitly, so
  nothing on screen changes — but the default is no longer a trap for the next caller.
- `[devb]` The mascot's typing pose now triggers when the keyboard opens, per §6, rather than waiting
  for the first character to be typed.

### Fixed

- `[devb]` **The mascot now stops.** Its three idle loops — shape shift, blink, glance — and its
  breathing and float animations ran unconditionally, so the mascot kept animating with the
  reduced-motion setting on, in battery saver, and while the app was off screen. All of them are now
  gated, and when motion is off the mascot holds a resting pose rather than freezing mid-squash. The
  wink stays ungated: it is feedback for a tap the user just made, not idle decoration.
- `[devb]` **The mascot's anger shake now actually shakes, and is deterministic.** It read `random()`
  during composition, so it only re-rolled when something unrelated caused a recomposition — the body
  turned red but barely moved. It now steps a fixed pattern from a coroutine, and every random value
  in the mascot comes from one seeded generator, so a run is reproducible. `todo.md` asks for
  deterministic motion explicitly.
- `[devb]` **The mascot was invisible to screen readers** — a clickable with no label. It now has a
  content description and a label for its tap.
- `[devb]` **Light mode `surface` was `#FAFBF7`, the background colour** — so a card, sheet, or input
  field was indistinguishable from the screen behind it. §2 specifies `#E4E7DF`.
- `[devb]` **`outline` was the same colour as the surface it sat on**, measuring 1.20:1 in light and
  1.31:1 in dark. Any stock outlined Material component was drawing a border nobody could see.
- `[devb]` Two Phase 0 items recorded as open are in fact settled, and `todo.md` now says so: Trace is
  **Apache 2.0** licensed (`LICENSE` is in the repository root), and the on-device install is **no
  longer blocked** — a Motorola moto g54 5G on Android 15, `arm64-v8a`, is attached over adb, so the
  LiteRT-LM native runtime has real hardware to run on.

### Removed

- `[devb]` `TraceShapes`, an unused second `Shapes` object that had been dead since the theme moved to
  the 16dp `CohesiveShapes`, and the `SumiGreen`/`ZenIndigo` colours left behind by the olive palette.
