# Lumi v2 — Changelog

Major changes only. This is a record of what changed in the product, written for a person catching up —
not a mirror of the git log.

## How to use this file

- Newest entries at the top, under `## Unreleased`.
- Prefix every entry with the author: `[deva]` or `[devb]`. Dev B stages entries in `changelog_devb.md`
  on the `devb` branch; Dev A folds them into this file at merge, prefixes intact, and empties the
  staging file. **This file is the complete record** — read it rather than the `_devb` one.
- Group entries under `Added`, `Changed`, `Fixed`, `Removed`.
- One line per change. Say what changed for the user or for another developer, not which files moved.
- Log a change when it alters behaviour, an interface another developer builds against, or a
  dependency. Do not log refactors nobody outside the file would notice, or work in progress.
- If a change came with a judgement call, put the reasoning in `decisions.md` and keep the changelog
  line short.

Good: `[deva] Routines now re-arm after device reboot.`
Bad: `[deva] Updated RoutineWorker.kt and added BootReceiver.kt and modified the manifest.`

---

## Unreleased

> **The UI palette described in older entries below is dead.** `DESIGN_LANGUAGE.md` superseded the
> Design Specification's UI sections on Aug 15. The accent is cyan Slime Blue `#4DB6AC`, not sumi-olive.
> See `decisions.md`.

### Added

- `[deva]` **A Stop control on the composer.** While a reply is decoding the send button becomes a filled
  stop square in the accent; tapping it ends the reply, keeps the text that already arrived, and stores
  it. There was previously no way to interrupt a reply at all — the send button simply went inert, so a
  long answer held the screen until it finished.
- `[deva]` **Every chat turn is audited.** Answered, stopped, and failed turns each write an entry
  recording the capability, the backend it ran on, and the timing. **No prompt text and no reply text
  ever reach the audit log** — the conversation already has one home the user can read and delete, and
  the audit table's job is to show *that* Lumi acted, not to repeat what was said. The backend field is
  the part that matters: it is the evidence the inference was local.
- `[deva]` **All documentation lives in `docs/`.** Nine markdown files and the strategy brief moved out of
  the repository root; only `README.md` stays. Older entries in this file that name `todo.md` mean
  `docs/todo.md`.
- `[deva]` **`docs/COMPETITIVE_LANDSCAPE.md`** — the AI capture wearables Lumi is positioned against
  (Limitless, Plaud, Bee, Omi, Friend, plus the R1, the Humane Ai Pin, and Even Realities G2), the
  weakness they share, and an honest section on where Lumi is worse than them.
- `[deva]` **`tools/seed-model.sh`** — pushes the 2.6GB model to a device from a digest-verified host
  cache in about 75 seconds. `connectedAndroidTest` uninstalls the app, and an uninstall wipes
  app-private storage, so running instrumented tests destroys the model. It has done so four times.
- `[deva]` **Chat works end to end.** Sending a message runs it through the resident model and streams
  the reply into the transcript. Before this the send button cleared the field and called nothing — the
  UI was a shell with no path to the model at all.
- `[deva]` **Replies render markdown.** Numbered and bulleted lists, bold, inline code, and fenced code
  blocks in a monospace face. The model emits markdown whether or not it is asked to, so a plain `Text`
  was showing users the syntax instead of the formatting.
- `[deva]` **Conversations persist, and history works.** Chats and their messages are stored through
  Room, listed newest-first in the history drawer, and can be reopened or deleted. The drawer previously
  hardcoded "No history yet" and the New Chat row was an empty `clickable {}`.
- `[deva]` **A merged settings screen** covering appearance, processor, model parameters, and the system
  prompt. Sliders for temperature, top-P, top-K, and the reply limit, seeded from the defaults v1 shipped
  for this same Gemma build (64 / 0.95 / 1.0 / 4096). The Model Parameters sidebar entry is gone; this
  screen replaces it.
- `[deva]` **A user-selectable backend** — CPU, GPU, or Auto — applied by reloading the engine, with the
  backend actually in use reported back so an Auto fallback is visible rather than silent.
- `[deva]` **Reply timings** under each answer: elapsed time, approximate decode rate, and
  time-to-first-token when it is slow enough to matter. Measured from the first token rather than from
  the send, so prefill does not distort the rate. Switchable off in settings.
- `[deva]` **A status verb while the model works** — one per reply, chosen at random from a dozen, with
  animated dots. Fixed for the duration of the reply on purpose: a label that changed mid-wait read as
  several failed attempts rather than one in progress.
- `[deva]` **The mascot docks beside the menu button** once a conversation starts, keeps reacting, and
  gives the centre of the screen to the transcript. Switchable off with **Live mascot** in settings.
- `[deva]` **A real onboarding screen.** Introduces Lumi and three concrete things it does before
  asking for a 2.6GB download, rather than leading with a size and a button.
- `[deva]` **Download speed** on the setup screen, smoothed and only shown once the sample is long
  enough to be honest. No time estimate — a remaining-time figure over an unknown connection is wrong
  often enough to erode trust in every other number on screen.
- `[devb]` **The LiteRT-LM runtime is wired in.** `LiteRtModelHarness` is the single `@Singleton`
  implementation of `ModelHarness` and the only thing that constructs an `Engine` — serialised through
  the one-thread inference dispatcher *and* a `Mutex`, so neither two native calls nor two logical
  generations can interleave. Loads once, stays resident.
- `[devb]` **`ModelStore` and `ModelDownloader` — the no-redownload guarantee.** The model lives in
  `filesDir`, and is usable only when size **and** a recorded SHA-256 match. v1 checked existence alone,
  which let a truncated file through to the native loader and read as "the app is broken". Downloads
  resume from a `.part` file with a `Range` request and classify failures as retryable or permanent.
- `[devb]` **`ModelSetupScreen` gates the app on first run.** `MainActivity` observes
  `ModelHarness.state` and hands off to `LumiApp` once `Ready`. A returning user whose model is on disk
  passes through in a moment — it loads, not downloads.
- `[devb]` **Manifest: `INTERNET` and `ACCESS_NETWORK_STATE`** — used only by the one-time model
  download — plus four `uses-native-library` entries at `required="false"` that the GPU backend dlopens
  on API 31+.
- `[devb]` **`LoadingState` and `ErrorState`**, completing the state family beside `EmptyState`.
  `LoadingState` takes the operation as a required argument so "Loading…" cannot be written by accident;
  `ErrorState` takes what happened, what to do, and what partly completed as three separate parameters
  so none can be skipped.
- `[devb]` **`LumiGlassPanel`, `LumiIconButton`, and a `hairlineBorder` modifier** — the first of the
  component library, extracted from what was already on screen rather than invented.
- `[devb]` **Shared design tokens.** `LumiMotion` holds every duration and easing; `LumiShape` names
  radii by role because Material's small/medium/large ramp cannot express one; `LumiSize` holds fixed
  component dimensions so a spacing change can no longer silently resize a control.
- `[devb]` **`LocalMotionEnabled`**, one theme-level flag folding reduced motion, battery saver, and
  lifecycle state, all observed live.
- `[deva]` **Apache 2.0 `LICENSE`.** The open Phase 0 licence question, resolved by the owner.
- `[deva]` **`DESIGN_LANGUAGE.md`**, now the authority for all UI work.
- `[deva]` **Phase 0 foundation.** The app builds, and Dev B is unblocked.
  - Gradle project on AGP 8.13.0 / Kotlin 2.2.0 / Gradle 9.2.1, namespace `com.lumi`, minSdk 31,
    compileSdk and targetSdk 37, Java 17. Every dependency version pinned exactly.
  - Theme tokens with contrast measured, a serif type scale, spacing and radius scales, and
    `LocalReducedMotion` provided at theme level and observed live.
  - Room database with twelve tables, indices, exported schema committed, and no destructive-migration
    fallback. Document ingest and routine save are single transactions.
  - Core contracts: `Capability`, `CapabilityResult`, `StructuredIntent`, `Router`, `RouterOutcome`,
    `Dispatcher`, `ModelHarness`, and `AuditLog`.
  - Hilt modules for the database, DAOs, and coroutine dispatchers, including a dedicated single-thread
    inference dispatcher.
  - `README.md`, `.gitignore`, a unit test covering the embedding blob encoding and enum decoding, and
    instrumented tests covering the schema on real SQLite.
- `[deva]` Planning documents: `todo.md` with the phase plan and Dev A / Dev B split, `decisions.md`,
  `changelog.md`, and `for_devb.md` as Dev B's self-contained brief.

### Changed

- `[deva]` **The home screen mascot's eyes are larger and oval.** Only that one — it owns the middle of an
  otherwise empty screen and is the mascot a user actually looks at, so its eyes carry the expression. The
  docked mascot in the top bar and the sleeping one on the loading screen keep the smaller near-circular
  pair, because the same oval closes to a smudge at 32dp. The blink and squint poses are now fractions of
  the open height rather than fixed dp, so they scale with the eye instead of leaving a large eye barely
  moving when it blinks.

- `[deva]` **The plan is rebuilt around seven core features** fixed by the owner on Aug 16: voice-first
  mode, a widget for reminders/todos/routines, file fetch, on-demand DuckDuckGo search, Gmail fetch over
  MCP, call mode for meetings, and notification reading with proactive suggestions. Phase 1 is now an
  explicit stable-baseline gate that Phase 2 cannot start before. Voice moved from Phase 6 to Phase 2 —
  it is the product's identity, not a late nicety.
- `[deva]` **Airplane mode is no longer an acceptance criterion.** The criteria are privacy-first, ease of
  use, and voice-first. On-device inference is unchanged and non-negotiable; what changed is that it is
  proved by evidence — opt-in, audited network features — rather than by having no network code. This
  promotes the audit log from a transparency surface to the primary proof of the privacy claim.
- `[deva]` **The app is renamed Lumi** — package, identifiers, database, preferences, labels, spec
  documents, and logs. References to Trace v1 and Trace-beta are kept: that is the project this one
  descends from and a real checkout on disk. A new `applicationId` means a new data directory, so the
  model re-downloads once.
- `[deva]` Space required before a download is allowed now accounts for the ~850MB of compilation cache
  the runtime writes on first load. It asked for 2.6GB plus 256MB, which let a download start on a phone
  that then had no room to load it. Settings shows weights plus caches together.
- `[deva]` `ModelStore.clear()` deletes the model directory rather than three named files — it was
  leaving ~600MB of generated caches orphaned with nothing that would ever remove them.
- `[deva]` The voice button in the composer is visibly disabled rather than live-looking and inert.
  Voice is Phase 6; a control that responds to nothing teaches users not to trust the ones that do.

- `[devb]` **The LiteRT-LM runtime is wired in.** `LiteRtModelHarness` is the single `@Singleton`
  implementation of `ModelHarness` and the only thing that constructs an `Engine` — serialised through
  the one-thread inference dispatcher *and* a `Mutex`, so neither two native calls nor two logical
  generations can interleave. Loads once, stays resident. Requests the GPU backend with a 4096-token
  ceiling, a compiled-kernel cache dir, and speculative decoding enabled before `initialize()`.
  Streams via `sendMessageAsync`, detects cumulative-vs-delta streaming by prefix, and closes one-shot
  conversations so background parsing never reaches the user's chat.
- `[devb]` **`ModelStore` and `ModelDownloader` — the no-redownload guarantee.** The model lives in
  `filesDir`, and is usable only when size **and** a recorded SHA-256 match. v1 checked existence alone,
  which let a truncated file through to the native loader and read as "the app is broken". Downloads
  resume from a `.part` file with a `Range` request and classify failures as retryable or permanent.
- `[devb]` **First-run download and onboarding screen**, anchored on the mascot rather than a bare
  progress bar. Every number is real — size from the pinned artefact, percentage from bytes on disk —
  and nothing estimates a time, because a download over an unknown connection cannot be estimated
  honestly. Consent-gated: a 1.9GB fetch on a metered plan is never started without asking.
- `[devb]` **`ModelSetupScreen` gates the app on first run.** `MainActivity` observes
  `ModelHarness.state` and hands off to `LumiApp` once `Ready`. A returning user whose model is on disk
  passes through in a moment — it loads, not downloads.
- `[devb]` **`LumiPersona.SYSTEM`**, one short system instruction shared by the chat path and every
  background one, so the persona cannot drift between them. Short on purpose: every token is prefill on
  a 2B model.
- `[devb]` **Manifest: `INTERNET` and `ACCESS_NETWORK_STATE`** — used only by the one-time model
  download — plus four `uses-native-library` entries at `required="false"` that the GPU backend dlopens
  on API 31+. Without them the loader refuses and LiteRT-LM silently falls back to CPU.
- `[devb]` **`LoadingState` and `ErrorState`**, completing the state family beside `EmptyState`.
  `LoadingState` takes the operation as a required argument so "Loading…" cannot be written by accident;
  `ErrorState` takes what happened, what to do, and what partly completed as three separate parameters
  so none can be skipped. Neither renders a red headline.
- `[devb]` **`LumiGlassPanel`, `LumiIconButton`, and a `hairlineBorder` modifier** — the first of the
  component library, extracted from what was already on screen rather than invented. The glass recipe
  was hand-written in eight places.
- `[devb]` **Shared design tokens.** `LumiMotion` holds every duration and easing; `LumiShape` names
  radii by role because Material's small/medium/large ramp cannot express one; `LumiSize` holds fixed
  component dimensions so a spacing change can no longer silently resize a control.
- `[devb]` **`LocalMotionEnabled`**, one theme-level flag folding reduced motion, battery saver, and
  lifecycle state, all observed live. `LocalReducedMotion` stays for the broader question.
- `[deva]` **Apache 2.0 `LICENSE`.** The open Phase 0 licence question, resolved by the owner.
- `[deva]` **`DESIGN_LANGUAGE.md`**, now the authority for all UI work.
- `[deva]` **Phase 0 foundation.** The app builds, and Dev B is unblocked.
  - Gradle project on AGP 8.13.0 / Kotlin 2.2.0 / Gradle 9.2.1, namespace `com.lumi`, minSdk 31,
    compileSdk and targetSdk 37, Java 17. Every dependency version pinned exactly.
  - Theme tokens with contrast measured, a serif type scale, spacing and radius scales, and
    `LocalReducedMotion` provided at theme level and observed live.
  - Room database with twelve tables, indices, exported schema committed, and no destructive-migration
    fallback. Document ingest and routine save are single transactions.
  - Core contracts: `Capability`, `CapabilityResult`, `StructuredIntent`, `Router`, `RouterOutcome`,
    `Dispatcher`, `ModelHarness`, and `AuditLog`.
  - Hilt modules for the database, DAOs, and coroutine dispatchers, including a dedicated single-thread
    inference dispatcher.
  - `README.md`, `.gitignore`, a unit test covering the embedding blob encoding and enum decoding, and
    instrumented tests covering the schema on real SQLite.
- `[deva]` Planning documents: `todo.md` with the phase plan and Dev A / Dev B split, `decisions.md`,
  `changelog.md`, and `for_devb.md` as Dev B's self-contained brief.


- `[devb]` **`DESIGN_LANGUAGE.md` is the authority for all UI work**, superseding the Design
  Specification's UI sections, which the owner deprecated. The palette is cyan Slime Blue `#4DB6AC`.
- `[devb]` **Phase 1 was executed end to end by Dev B**, including the items tagged `[deva]`. The tags
  in `todo.md` remain as a record of the original split.
- `[devb]` **Spacing token values now match `DESIGN_LANGUAGE.md` §5** — md 16, lg 24, xl 32, xxl 48,
  previously 12/16/24/32 under the same names. `EmptyState`'s padding grew as a result. `xxxl`, `screen`,
  and `gutter` are gone; nothing referenced them.
- `[devb]` Light mode's `onPrimary` is `#171717`, not §2's `#FAFBF7` — white on the cyan accent measures
  2.35:1 and fails AA; the dark value measures 7.35:1. The accent hue is unchanged.
- `[devb]` `LumiTheme`'s `darkTheme` now defaults to the system setting rather than always light,
  matching §9. Nothing on screen changes, but the default is no longer a trap for the next caller.
- `[devb]` The mascot's typing pose triggers when the keyboard opens, not on the first character typed.
- `[devb]` Icon buttons are 42dp and attachment tiles 24dp, per §4 and §7. The selected sidebar row now
  carries the accent on its icon — one of the four places §2 says the accent belongs, and the only one
  that was missing.
- `[devb]` **Raw dp literals in `ui/` are down from 126 to 65**, and eight copies of the glass border
  are down to one definition. The remaining literals are values §5's scale does not define, left alone
  deliberately because rounding them would move Dev A's layout.
- `[deva]` **`material-icons-extended` stays**, on the owner's call, superseding the Phase 0 entry that
  dropped it — Lumi's UI needs eleven glyphs `material-icons-core` does not carry, and R8 strips the
  rest from release, so the cost is debug-only. **Now a pinned version-catalogue entry** rather than the
  bare unversioned string it was.
- `[deva]` minSdk is 31 and targetSdk 37, raised from the 26 and 36 originally planned. A device that
  can run Gemma 4 E2B locally is Android 12+ in practice.
- `[deva]` No model may require an access token, a gate, or an account to download. Gemma 4 E2B IT is
  ungated and fetched directly. FunctionGemma and EmbeddingGemma are both gated and therefore ruled out.
- `[deva]` Router is three tiers: regex rules, then embedding similarity against labelled example
  phrases, then Gemma 4 only for what the first two cannot settle.
- `[deva]` The small router model is a bundled APK asset shared with RAG retrieval — one embedder, two
  jobs, no download.
- `[deva]` Voice targets native Android `SpeechRecognizer` and `TextToSpeech` for the Aug 22 build.
  Sherpa-ONNX moves to Phase 8; v1 built the offline stack and reverted it for latency and glitching.
- `[deva]` All persistence is Room. v1's proto DataStore stores are not carried forward.
- `[deva]` Package namespace is `com.lumi`, replacing v1's `com.lumi.app`.
- `[deva]` Branch workflow: Dev A works on `deva`, Dev B works on `devb`, and neither agent pushes to
  `main`. `main` advances only when the owner says so.
- `[deva]` Lumi v2 builds against the published LiteRT-LM Kotlin API and derives no code from Google
  AI Edge Gallery, so no attribution is inherited from that fork.

### Fixed

- `[deva]` **Image and audio input both work.** An earlier finding said this Gemma build rejected images,
  which was wrong — the *test* set a 1024-token context, and an image does not fit in one. At 4096 tokens
  a solid blue square is described correctly on a Samsung SM-M356B. PRD §5's top risk is closed and Phase
  4 can rely on vision.
- `[deva]` **The model loads on the GPU.** Requesting a GPU *audio* backend fails engine creation
  outright, and passing a compiled-kernel `cacheDir` for an app-internal model path fails it too — v1's
  working config does neither. Both are gone, and the model now loads on GPU with speculative decoding
  in 14-21s. Verified on a Samsung SM-M356B, Exynos 1380 / Mali.
- `[deva]` **Speculative decoding is only attempted when the model file reports supporting it**, using
  v1's `Capabilities` probe instead of setting the flag blind.
- `[deva]` **A GPU that will not load falls back to the CPU** rather than reporting "cannot run on this
  device" on hardware that runs it fine. The manifest declares OpenCL `required="false"`; this is where
  that promise is kept. Every load failure is logged with its cause — the exception used to be swallowed,
  which made this the one failure in the app nobody could diagnose.
- `[deva]` **The transcript stays pinned to the end of a streaming reply**, and lets go the moment the
  user drags. Two faults: `scrollToItem(index)` pins an item's *top*, so a reply taller than the screen
  kept yanking the reader back to the start of the message and fought every attempt to reach the end;
  and follow state was driven by `isScrollInProgress`, which is also true during the transcript's own
  programmatic scrolls, so it read its own scrolling as user input.
- `[deva]` **The stated download size matches the file.** 2,588,147,712 bytes is 2.59 GB decimal but
  2.41 GiB, and the label used the binary figure while the progress readout formatted decimal — so the
  download counted up past its own stated total and looked broken at the finish line.
- `[deva]` Chat text is `bodyMedium` rather than `bodyLarge`. At 17sp a phone-width transcript fit so
  few words per line that a reply broke into a tall column of fragments.
- `[deva]` Reply timings are dimmed to 55% — a footnote about the answer, not part of it.
- `[deva]` The mascot's anger shake is gentler: ±1dp tapering to rest at 70ms per step, from ±2dp at
  50ms, which read as violent rather than annoyed.
- `[devb]` **The mascot now stops.** Its shape-shift, blink, and glance loops plus breathing and float
  ran unconditionally — animating with reduced motion on, in battery saver, and while the app was off
  screen. All are gated now, and when motion is off it holds a resting pose rather than freezing
  mid-squash. The wink stays ungated: it is feedback for a tap, not idle decoration.
- `[devb]` **The mascot's anger shake actually shakes, and is deterministic.** It read `random()` during
  composition, so it only re-rolled on an unrelated recomposition — the body turned red but barely moved.
- `[devb]` **The mascot was invisible to screen readers** — a clickable with no label.
- `[devb]` **Light mode `surface` was the background colour**, so a card, sheet, or input field was
  indistinguishable from the screen behind it.
- `[devb]` **`outline` was the same colour as the surface it sat on**, measuring 1.20:1 light and 1.31:1
  dark — any stock outlined Material component drew a border nobody could see.
- `[deva]` Nav tab switches no longer cross-fade. The default animated alpha across the whole container,
  so two destinations were partly transparent at once and their centred headlines visibly overlapped.
- `[deva]` Secondary text and outlines failed WCAG AA in both themes — 3.3:1 light and 4.1:1 dark. They
  looked correctly restrained and were quietly unreadable, which is the worst combination.

### Removed

- `[deva]` **SOS, entirely.** The largest planned subsystem and the most complete one in v1 — tiered
  detection, Morse flashlight, siren, location, multipart SMS, foreground service. Out of scope for what
  Lumi is now. `MIGRATION_1_2` drops the `emergency_contacts` table, tested against a real v1 database.
- `[deva]` **The mood journal is demoted**, not removed — it moves below the cut line as an optional
  Phase 9. The rule that Lumi is never a therapist, companion, or crisis detector still binds if it ships.
- `[devb]` `LumiShapes`, an unused second `Shapes` object dead since the theme moved to the 16dp
  `CohesiveShapes`, and the `SumiGreen`/`ZenIndigo` colours left behind by the olive palette.
- `[deva]` The five-item bottom navigation bar, replaced by nine sidebar destinations with left and
  right drawers per `DESIGN_LANGUAGE.md` §8.
