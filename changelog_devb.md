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

- `[devb]` **The LiteRT-LM runtime is wired in.** `LiteRtModelHarness` is the single `@Singleton`
  implementation of `ModelHarness` — the only thing that constructs an `Engine`, serialised through a
  one-thread dispatcher and a `Mutex` so two generations can never enter the native runtime at once.
  Loads the model once and keeps it resident for the process. `EngineConfig` requests the GPU backend
  with a 4096-token ceiling and a compiled-kernel cache dir; speculative decoding is enabled before
  `initialize()`. `generate()` streams via `sendMessageAsync`, detects cumulative-vs-delta streaming by
  prefix, and closes one-shot conversations so background parsing never appears in the user's chat.
- `[devb]` **`ModelStore` and `ModelDownloader` — the no-redownload guarantee.** The store lives in
  `filesDir` (not `cacheDir`, which Android deletes first under storage pressure) and marks a model
  usable only when size **and** a recorded SHA-256 match — v1 checked existence alone, which let a
  truncated file through to the native loader and surfaced as "the app is broken". The digest is hashed
  once, streamed in 1MB blocks, the moment the bytes land. The downloader resumes from a partial `.part`
  file with a `Range` request, leaves it on retry, and classifies failures as retryable (no network, a
  dropped connection) or permanent (404, 401/403, not enough room). No permission blitz, no WorkManager.
- `[devb]` **A first-run download and onboarding screen.** Anchored on the mascot rather than a bare
  progress bar, because several minutes of bar with nothing else is the least reassuring first sight of
  the app. Every number is real — the size from the pinned artefact, the percentage from bytes on disk —
  and nothing estimates a time, because a download over an unknown connection cannot be estimated
  honestly. The download is consent-gated: a 1.9GB fetch over a metered plan is never started without
  being asked. Recoverable failures say "nothing was lost, resume"; unrecoverable ones say the model
  cannot run here but the rest of the app still works. Errors are not red, per §3.
- `[devb]` **`ModelSetupScreen` gates the app on first run.** `MainActivity` observes `ModelHarness.state`
  and shows the setup screen until `Ready`, then hands off to `TraceApp` for the rest of the process. A
  returning user whose model is already on disk passes through in a moment — it loads, not downloads —
  so the gate is not a stop sign for them.
- `[devb]` **`TracePersona.SYSTEM`**, one short system instruction shared by the chat path and any
  background one so the persona cannot drift between them. Kept brief on purpose: every token is prefill
  on a 2B model, and the "answer in one or two sentences" instruction is what keeps a reply under a
  couple of seconds instead of thirty.
- `[devb]` **Manifest: `INTERNET` + `ACCESS_NETWORK_STATE`** (only the one-time model download uses
  them; the app is offline by default thereafter) and four `uses-native-library` entries at
  `required="false"` (`libvndksupport`, the three `libOpenCL` variants) that the GPU backend dlopens on
  API 31+.
- `[devb]` **`LoadingState` and `ErrorState`**, the missing two thirds of the state family beside
  `EmptyState`. `LoadingState` takes the operation as a required argument, so "Loading…" cannot be
  written by accident, and drops its indeterminate bar entirely when motion is off rather than
  animating forever. `ErrorState` takes what happened, what to do, and what partly completed as three
  separate parameters, so none of the three can be skipped — and it never renders a red headline,
  because §3 forbids hierarchy by colour.
- `[devb]` **`TraceGlassPanel`, `TraceIconButton`, and a `hairlineBorder` modifier** — the first pieces
  of the §28 component library, extracted from what was already on screen rather than invented. §4's
  glass recipe was hand-written in eight places and the three circular buttons in §7 were three
  near-identical hand-rolled `Box`es.
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
- `[devb]` **Icon buttons are 42dp and attachment tiles 24dp**, the values §4 and §7 specify. They were
  40dp and 20dp. The selected sidebar row now carries the accent on its icon, which §2 lists as one of
  the four places the accent belongs and which was the only one missing.
- `[devb]` **Raw dp literals in `ui/` are down from 126 to 65**, and the eight hand-written copies of
  §4's glass border are down to the one definition. Every remaining literal is either a value §5's
  scale does not define — 10, 11, 12, 13, 14, 18 — or a genuine one-off component dimension. The
  off-scale ones were left alone deliberately: they are Dev A's layout, and rounding them to the
  nearest token would move the UI.
- `[devb]` `material-icons-extended` stays, on the owner's call, superseding the `decisions.md` entry
  that dropped it. It should still become a pinned version-catalogue entry rather than a raw string.
  See `decisions_devb.md`.

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
