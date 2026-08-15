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

### [devb] 2026-08-15 — Nine sidebar destinations is the intended set, not a shortfall

`DESIGN_LANGUAGE.md` §8 numbers its destination list to 14 and elides items 6 through 13 as `...`. The
code has nine. **The owner confirmed the nine are correct and sufficient for the baseline scaffold**,
so §8's numbering is aspirational rather than a specification, and the gap is not a defect to close.

**Why record it:** a later reader comparing §8 to `TraceDestination` will count nine against fourteen
and assume five screens were dropped. They were never named. If destinations are added, §8 should be
updated to name them at the same time.

### [devb] 2026-08-15 — `material-icons-extended` stays, superseding the entry that dropped it

`decisions.md` records `material-icons-extended` being dropped in Phase 0 because it put 40MB of
generated classes into the debug APK — 63MB total, 42MB in one dex file — against `material-icons-core`
at 30.5MB. It has since been re-added, and **the owner has ruled that it stays.**

**Why:** the icons are needed. `TraceApp` and `HomeScreen` between them use `NoteAlt`, `Event`, `Book`,
`Tune`, `FindInPage`, `History`, `GraphicEq`, `CameraAlt`, `PhotoLibrary`, `UploadFile`, and `Public`,
none of which are in `material-icons-core`. The owner's position: final APK size is irrelevant for a
hackathon, runtime optimization is what matters, and icon coverage was a real gap before.

**What this does not excuse.** Two things still need attention and neither is about size:

1. The dependency is declared as a raw string, `implementation("androidx.compose.material:material-icons-extended")`,
   with no version and no version-catalogue entry. Every other dependency in this project is pinned
   exactly — `decisions.md` is explicit that nothing uses `latest.release`. It resolves through the
   Compose BOM today, which is why it works, but it should be a catalogue entry like its siblings.
2. R8 strips unused icons from the release build, so the cost is a debug-build and build-time cost,
   not a shipped one. Worth knowing before anyone re-opens this on size grounds.

`decisions.md`'s Phase 0 entry is superseded on the decision, not on its measurements — those numbers
were real.

### [devb] 2026-08-15 — Glass is alpha and a hairline; there is no blur

`DESIGN_LANGUAGE.md` §4 describes glassmorphism as `surfaceVariant` at partial alpha with a 0.5dp
border, "blurring the underlying content softly". The first half is implemented. **The blur is not, and
was not before this branch.**

**Why:** Compose has no backdrop blur. `Modifier.blur` blurs a composable's own content, not what is
behind it. Blurring the content *behind* a drawer or a sheet needs either a platform window-blur API,
which does not apply to a Compose surface inside one window, or capturing the background to a layer and
blurring that — expensive per frame and fragile across densities.

**What is shipped instead:** §4's own recipe, alpha plus the hairline, which reads as frosted against
Trace's surfaces because the palette is low-contrast to begin with. Revisit if a backdrop API lands.
Do not fake it by screenshotting the background.

**Left deliberately alone:** both sidebars use opaque `surface` rather than a translucent
`surfaceVariant`, and the attachment sheet keeps its 24dp top corners rather than §4's square
"Sidebars & Modals" rule. Both are Dev A's established look, both would be visible changes, and §4's
full-bleed-panel reasoning does not obviously extend to a bottom sheet. They need an owner call, not an
agent's.

### [devb] 2026-08-15 — The GPU build of the model, not the generic one

The model artefact is `gemma-4-E2B-it-gpu.litertlm` (2,008,432,640 bytes) rather than the generic
`gemma-4-E2B-it.litertlm` (2,588,147,712 bytes). The GPU build is ~580MB smaller and matches the
`Backend.GPU()` the harness requests.

**Why record it:** the two files live in the same HF repo and look interchangeable, and choosing the
generic one would add 580MB to every download for no gain. The GPU build is also what the LiteRT-LM
samples pair with the GPU backend. Vendor-specific builds exist too; they are not used because the test
device is a MediaTek mt6855, and there is no mt6855-specific build to prefer.

**The build is pinned by name, size, and SHA-256 in `GemmaModel`**, so a silent upstream change (a
re-push, a tag move) fails `ModelStore.isReady` loudly rather than loading a different file. `maxNumImages`
and the vision/audio backends are set on the engine config but whether this build actually accepts image
and audio input is a device test, not an assumption.

### [devb] 2026-08-15 — The model lives in `filesDir`, verified by size and digest, not existence

Three connected choices, recorded together because they are the no-redownload guarantee the owner asked
for and each is only meaningful with the other two.

1. **`filesDir`, not `cacheDir`, not external storage.** `cacheDir` is what Android deletes first under
   storage pressure — using it would silently trigger a 1.9GB re-download. External storage is
   world-readable and invites the user to delete the weights from a file manager. `filesDir` also
   survives `installDebug` over the same signing key, so a dev rebuild never costs another download —
   which is why the dev loop must never `adb uninstall` (it would wipe app-private files and re-trigger
   the fetch). The signing keys were reconciled once so this holds going forward.
2. **`isReady` is size **plus** a recorded SHA-256, not `exists()`.** v1 checked existence only — its
   biggest correctness hole. A truncated file that survived a crash mid-rename passes `exists()` and then
   fails inside the native loader, which reads to the user as "the app is broken" rather than "the
   download needs retrying". The digest is verified exactly once, by `verifyAndCommit`, the moment the
   bytes land; the receipt file records it so `isReady` never re-hashes 1.9GB on a cold start.
3. **The partial download is left on disk on retry.** v1 wiped resume state on every retry, so a flaky
   connection meant starting 1.9GB again each time. The v2 downloader resumes from the `.part` file with
   a `Range` request; if the server ignores `Range` and replies 200, it deletes the partial and restarts
   rather than concatenating.

**Reverse only together and only for cause.** Dropping the digest check alone reintroduces v1's hole.
Moving to `cacheDir` alone reintroduces silent re-downloads.

### [devb] 2026-08-15 — The first model download is consent-gated; a present model is not

`ModelSetupViewModel` shows a consent step and waits for `start()` when the model is absent, but calls
`start()` immediately when the model is already on disk.

**Why:** 1.9GB is minutes of waiting and real money on a metered plan — a fetch that large is never
started without being asked. But a model already on disk needs permission for nothing; prompting a user
to "download" something they already have would be the worse bug, and a returning user should see a load
bar, not a download prompt.

**The gate lives in `MainActivity`, above `TraceApp` and its NavHost.** Putting the setup screen in the
nav graph would let the drawer and routes appear before the model exists, and would make "did the model
load" a navigation question rather than a lifecycle one. Observing `ModelHarness.state` at the activity
means the whole app appears at once, only when ready.

**`Unavailable` is split into recoverable and not.** A dropped connection (recoverable) leaves the
partial file and offers "resume"; a device that cannot run the model (not) is told so honestly, with the
rest of the app still usable. Retryable vs permanent is decided in the downloader from the HTTP status
and the preconditions (network present, enough room), not guessed in the UI.



