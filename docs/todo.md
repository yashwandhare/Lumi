# Lumi v2 — TODO

Single source of truth for what gets built, in what order, and by whom. Dev A maintains this file.
Dev B proposes changes to it through the process in `for_devb.md`; Dev A merges them.

- **Deadline:** app complete Aug 21 2026, morning — including rebuild documentation.
- **Hackathon:** Aug 22 2026. **Today: Aug 16.** Five days and a morning remain.
- **Planning started:** Aug 14 2026, 19:00.
- **Specs:** `Lumi_PRD_v2.docx` and `Lumi — Design Specification.docx` are the original product
  documents. **`STRATEGY_BRIEF.pdf` supersedes both at the product level** — it reset how Lumi is
  positioned, and on Aug 16 the owner reset the core feature set on top of it. Where they disagree, this
  file records the resolution and `decisions.md` records why.
- **UI spec:** `DESIGN_LANGUAGE.md` **supersedes the Design Specification's UI sections.** Every UI
  component, optimization, and design decision refers to it. Where an older note here says
  "rice-paper", "sumi", or "one olive accent", read `DESIGN_LANGUAGE.md` §2 instead — the palette is
  cyan Slime Blue `#4DB6AC`.
- **Docs location:** every planning and reference document now lives in `docs/`. Only `README.md` stays
  at the repository root. Older changelog entries that say `todo.md` mean `docs/todo.md`.
- **v1 reference:** a local Trace v1 checkout, path differs per developer. Reference and reuse only.
  Read it, understand why it works, rewrite it clean. Never copy a file across unchanged.

## What Lumi is judged on

This replaces the previous acceptance frame. The owner ruled on Aug 16 that **airplane-mode purity is no
longer an acceptance criterion** — Gmail is an obviously opt-in network feature, and holding the whole
product to "no network code exists" was costing more than it bought. The three criteria that replace it:

1. **Privacy-first.** Every inference is on-device. Nothing leaves the phone unless the user explicitly
   asks for a network feature, and when it does they can see it. This is still the product's spine —
   what changed is how it is proved. **Provable** beats **absent**: the audit log, per-feature opt-in,
   and a visible network indicator are the proof.
2. **Ease of use.** One input, no accounts, no configuration. A judge picks it up and it works.
3. **Voice-first.** Speaking to Lumi is the primary interaction, not a mic button bolted onto a chat box.

Hardware-first competitors and what Lumi does differently are documented in
`COMPETITIVE_LANDSCAPE.md`. Read it before writing demo copy — the pitch is a positioning claim and it
has to survive a judge who already owns a Plaud.

## Core feature set

Fixed by the owner on Aug 16. **These seven are the product.** Everything else is supporting
infrastructure or explicitly optional.

| # | Feature | Phase | Why it is core |
|---|---|---|---|
| 1 | **Voice-first mode** | 2 | The primary interaction |
| 2 | **Widget → reminders, todos, routines** | 3 | Automation reachable without opening the app, executed through device actions |
| 3 | **File fetch** | 4 | "Find my Aadhaar" — the demo no cloud assistant can copy |
| 4 | **Web search via DuckDuckGo, on demand** | 5 | User-initiated only. Never automatic, never background |
| 5 | **Gmail fetch over MCP** | 5 | Read-only, opt-in, one-way. The one deliberate network feature |
| 6 | **Call mode** | 6 | Listens to a meeting and summarises it. Directly replaces a £150 pendant |
| 7 | **Notification reading + proactive suggestions** | 7 | The payoff of living on the phone rather than beside it |

**Cut: SOS, entirely.** It was the most complete v1 subsystem and the largest block of the old Phase 5,
and it is gone — out of scope for what Lumi is now. `MIGRATION_1_2` drops the `emergency_contacts`
table. Do not re-add it without a new decision entry.

**Demoted: the mood journal** is now **Phase 9, below the cut line, optional.** It is not core. Build it
only if Phases 1-8 are solid, and if it ships it ships as pattern surfacing that feeds automation —
never as emotional support.

## Legend

Ownership:

- `[deva]` — Dev A. Core, data, AI runtime, router, dispatcher, routines, device, background work.
- `[devb]` — Dev B. Design system, screens, navigation, widget, accessibility, UI states.
- `[both]` — needs both developers, or either can take it.

Status: `[ ]` pending · `[~]` in progress · `[x]` done · `[!]` blocked · `[-]` cut.

## Scope reality check

Read this before planning your day.

**Seven core features, five days, two developers.** All seven will not reach production quality. That is
the arithmetic, and stating it here is cheaper than discovering it on Aug 20 night.

The order below front-loads the features that carry the most demo weight per hour and that everything
else depends on. Voice and the router come first because five of the seven route through them. Call mode
is late because it needs the ASR stack proven first — not because it matters least. It is the most
legible feature to a judge who has seen a Limitless pendant.

**Cut from the bottom, never from the middle.** If a phase slips more than half a day, drop the lowest
remaining item rather than shipping every phase at 70%. Four complete features demo. Seven partial
features do not demo at all.

If something has to go, sacrifice in this order: Phase 9 (already below the line) → Phase 7's suggestion
engine (keep notification *reading*, cut the suggestions) → Phase 5's Gmail (keep DuckDuckGo, it is a
tenth of the work) → Phase 6 call mode. **Never cut Phase 8.** Shipping unhardened is how a demo dies on
stage.

## Milestone map

| Phase | Window | Owner | Exit criteria |
|---|---|---|---|
| 0 — Foundation | Aug 14 night → Aug 15 midday | deva | Build green, app launches, contracts published, Dev B unblocked |
| 1 — **Stable baseline** | Aug 15 pm → Aug 16 | both | **Gemma resident, chat solid, zero warnings, tests green, no known crash. Demoable as it stands** |
| 2 — Voice-first + router | Aug 16 pm → Aug 17 midday | both | Hold to talk, get a spoken answer. Typed input routes to the right capability |
| 3 — Actions, reminders, widget | Aug 17 → Aug 18 midday | both | A reminder set from the widget fires with the app closed and runs a device action |
| 4 — File fetch + RAG | Aug 18 → Aug 19 midday | both | "Find my X" returns the file. A question about attached notes returns a cited answer |
| 5 — DuckDuckGo + Gmail MCP | Aug 19 pm | both | Both work on demand, both visibly opt-in, neither runs on its own |
| 6 — Call mode | Aug 20 am | both | A 20-minute meeting is transcribed and summarised on-device |
| 7 — Notifications + proactive | Aug 20 pm | both | A notification produces one useful, dismissible suggestion |
| 8 — Hardening, QA, docs | Aug 20 night → Aug 21 am | both | Device QA passed, rebuild docs written, demo rehearsed twice |
| — cut line — | | | Everything below is optional or post-hackathon |
| 9 — Mood journal | only if 1-8 are solid | both | Journal surfaces a pattern and suggests a routine |
| 10 — Post-hackathon | after Aug 22 | both | Wake word, proactive document surfacing, extra triggers |

## Phase 0 — Foundation

Dev A solo. Complete.

- [x] `[deva]` Gradle project. Namespace `com.lumi`, minSdk 31, compileSdk and targetSdk 37, Java 17,
      Kotlin + Compose. Package layout per Design Spec §36. minSdk and targetSdk raised from the 26/36
      originally planned — see `decisions.md`.
- [x] `[deva]` Version catalog in `gradle/libs.versions.toml`. Every version pinned exactly. No ranges,
      no `latest.release`. Toolchain versions taken from v1's working configuration.
- [x] `[deva]` Hilt dependency injection, the `Application` class, and the coroutine modules —
      including the dedicated single-thread inference dispatcher the native runtime needs.
- [x] `[deva]` Theme tokens. Light and dark, contrast measured, serif type scale, spacing and radius
      scales, reduced-motion flag provided at theme level and observed live. **The palette described
      here is superseded** — see `DESIGN_LANGUAGE.md` §2.
- [x] `[deva]` Room database. Indices, transactional document ingest and routine save, exported schema
      committed, no destructive-migration fallback. No `User` table — see `decisions.md`.
- [x] `[deva]` Navigation skeleton, shallow per §24, with real empty states rather than placeholders.
- [x] `[deva]` Publish core contracts: `Capability`, `CapabilityInput`, `CapabilityResult`,
      `StructuredIntent`, `RouterOutcome`, `Dispatcher`, `ModelHarness`, `AuditLog`.
- [x] `[deva]` `.gitignore` and `README.md`.
- [x] `[deva]` **Licence: Apache 2.0.** Nothing was inherited from the v1 Gallery fork, so this was a
      free choice.
- [x] `[deva]` `assembleDebug`, `testDebugUnitTest`, and `assembleRelease` all green.
- [x] `[deva]` Install on a device and confirm it launches.
- [x] `[devb]` Setup only: v1 builds locally, both spec documents read, `for_devb.md` read.

## Phase 1 — Stable baseline

**This is the gate the owner asked for: Phase 2 does not start until Lumi is stable.** Not
feature-complete — stable. The app must load its model, hold a real conversation, survive a rotation and
a backgrounding, and build with zero warnings. Everything after this adds capability to a foundation
that is already trustworthy.

The design system and model runtime were executed end to end by Dev B on the owner's reassignment,
including the `[deva]` items. Ownership tags are kept as a record of the original split.

### Model runtime

- [x] `[deva]` Add `com.google.ai.edge.litertlm:litertlm-android`, pinned at `0.11.0`.
- [x] `[deva]` Manifest `<uses-native-library>` entries for the GPU backend, `required="false"`. Four
      entries, not two: `libvndksupport.so` plus three `libOpenCL` variants the backend dlopens.
- [x] `[deva]` `Engine` and `EngineConfig` wrapper. Backend from the user's setting, a 4096-token
      ceiling. **Vision and audio backends are null and there is no `cacheDir`** — both were stopping
      the model from loading on GPU at all. See `decisions.md`.
- [x] `[deva]` Call `engine.initialize()` off the main thread, behind a load-progress state with a
      resumable failure path. **Measured on a Samsung SM-M356B (Exynos 1380, Mali): 14-21s on GPU with
      speculative decoding, ~8-12s on CPU.** Nowhere near v1's 90-100s.
- [x] `[deva]` Model lifecycle: initialize once, keep the `Engine` resident for the process lifetime.
      Enforced by `@Singleton` on `LiteRtModelHarness` plus a `Mutex`, so neither two native calls nor
      two logical generations can interleave.
- [x] `[deva]` **Verify Gemma 4 E2B multimodality on a real device. PRD §5's top risk — closed.**
      **Both image and audio input work.** Proved by instrumented test on the SM-M356B: a solid blue
      square is correctly described as "Blue". An earlier run reported images rejected; that was a test
      bug — `maxNumTokens = 1024` cannot hold an image. See `decisions.md`. Phase 4 can rely on vision.
- [x] `[deva]` Model acquisition: first-run download, no token, resumable. Pinned by name, size, and
      SHA-256; stored in `filesDir`; verified by digest rather than existence; resumes from a `.part`
      file via `Range`. Consent-gated on a metered connection.
- [x] `[deva]` `ConversationConfig`: `systemInstruction` for the persona and `SamplerConfig` from
      `Sampling`, both user-editable in settings.
- [x] `[deva]` Stream with `sendMessageAsync(contents): Flow<Message>`. Also detects
      cumulative-vs-delta streaming by prefix, which the plan did not anticipate.
- [x] `[deva]` `ExperimentalFlags.enableSpeculativeDecoding`, gated on a `Capabilities` probe rather
      than set blind. Dropped before the backend is if a load fails.
- [x] `[deva]` Bundle the shared embedder. **EmbeddingGemma 300m via MediaPipe `tasks-text`**, from
      Google's ungated CDN, fetched on first use rather than bundled — 184MB in the APK would have been
      worse than a 20-second download. Unblocks router tier 2 and all of RAG.
- [x] `[deva]` System prompt and persona module. `LumiPersona.SYSTEM`, shared by the chat path and every
      background one so the persona cannot drift between them.
- [x] `[deva]` Chat capability: streaming generation, per-chat context memory, history in Room. One
      session id per conversation holds the KV cache; new-chat resets it. Conversations persist and are
      reopenable. **Not yet behind the `Capability` interface** — moves behind the dispatcher in Phase 2.
- [x] `[deva]` `MIGRATION_1_2` dropping `emergency_contacts`, with a migration test that builds a real
      v1 database and proves `chats` and `journal_entries` survive it.
- [x] `[deva]` `tools/seed-model.sh`. `connectedAndroidTest` uninstalls the app and an uninstall wipes
      the 2.6GB model; this reseeds it from a digest-verified host cache in ~75s instead of a download.

### Design system

- [x] `[devb]` Component library. `LumiGlassPanel`, `LumiIconButton`, `LumiInput`, `LumiBlob`,
      `MarkdownText`, the three state components, and now cards (`LumiCard`, `LumiCardRow`),
      dialogs (`LumiDialog`), chips (`LumiChip`), and styled sliders (`LumiSlider`, used by the
      settings screen) all exist with shared tokens.
- [x] `[devb]` Mascot composable. The fantasy slime per `DESIGN_LANGUAGE.md` §6, with a gradient body,
      eyes, and a glow aura — a deliberate departure from "simple circular blob".
- [x] `[devb]` Mascot breathing animation, gated on `LocalMotionEnabled` so it respects reduced motion,
      battery saver, and lifecycle state, and holds a resting pose rather than freezing mid-frame.
- [x] `[devb]` Sleeping mascot easter egg on the model loading screen — monochrome, relaxed, sans-serif
      Z's. Double-tap the home mascot to make it bounce.
- [x] `[devb]` Home screen. Mascot anchor, one natural-language input, attach and audio affordances,
      time-of-day greeting. Sparse.
- [x] `[devb]` Chat screen: message list, streaming markdown render, follow-scroll that yields to touch,
      status verb while generating, reply timings in a fainter tone.
- [x] `[devb]` Shared state components: `LoadingState` takes the operation as a required argument so
      "Loading…" cannot be written by accident; `ErrorState` takes what happened, what to do, and what
      partly completed as three separate parameters so none can be skipped.
- [x] `[devb]` Settings: one merged menu for model parameters, system prompt, backend choice, and
      motion. Backend defaults to CPU with GPU available as an explicit choice.

### Stabilisation — the actual gate

Nothing here is a feature. All of it is the difference between a demo and a crash on stage.

- [x] `[deva]` Zero compiler warnings. Currently zero — keep it there. A warning that survives a day
      becomes a warning nobody reads.
- [x] `[deva]` Disk-space accounting that matches reality: the runtime writes ~850MB of compilation
      cache beyond the 2.6GB model, so the preflight check reserves 1.1GB of headroom.
- [x] `[deva]` `ModelStore.clear()` deletes the whole model directory. It previously removed three named
      files and orphaned ~600MB of cache.
- [x] `[deva]` **Wire `AuditLog` into the chat path.** Every turn writes an entry — success, stopped, and
      failed alike — carrying the capability, the backend, and the timing, and **never the prompt or the
      reply text.** The audit log is how privacy-first is *proved* rather than claimed, so it could not
      start life incomplete. Four instrumented tests cover the three outcomes and the no-content rule.
      Every capability from Phase 2 on follows this pattern; chat is the reference.
- [x] `[deva]` **A Stop control.** Found by the exit run below: the composer had no way to interrupt a
      reply. The send button becomes a filled stop square in the accent while a reply is in flight, the
      partial text is kept and persisted, and the turn is audited as `PARTIAL`. A model that cannot be
      interrupted is a model that owns the screen for as long as it wants — on a 2B decoding at ~12 tok/s
      a long answer is thirty seconds of nothing the user can do.
- [x] `[deva]` Process-death and rotation pass on the chat screen. A streaming reply interrupted by a
      configuration change must not lose the turn or leak the generation coroutine.
- [x] `[deva]` One low-memory pass with the model resident. The Engine holds ~2.6GB of mapped weights;
      confirm what happens when Android reclaims the process mid-conversation, and that the reload path
      is the same one first-run uses.
- [x] `[both]` **Baseline exit run on the physical device**, fresh launch, no debugger. All five
      scenarios pass. **The gate is closed.** Dev A verified scenarios 1-2 on a Samsung SM-M356B; Dev B
      verified the remainder on a moto g54 5G (Dimensity 7020), Aug 17:
      1. **[x]** Cold start → model loads on CPU. **3.0s** with warm compilation caches, 8-12s without.
          **GPU re-verified on the moto g54: it loads, but 48.5s** — unusably slow on the Mali-G57. CPU
          confirmed as the correct default on mid-range hardware; GPU stays optional.
      2. **[x]** Conversation renders markdown, a numbered list, and a fenced code block correctly, and
          holds context across turns ("which was the second one?" resolves against the previous answer).
          Warm reply 4.3s against 18.6s cold. Context confirmed across turns on the g54 too.
      3. **[x]** New chat, reopen an old chat from the drawer, both keep their own context.
      4. **[x]** Background the app mid-reply, return, the reply is intact.
      5. **[x]** Kill and relaunch. History is there. No crash, no ANR, no orphaned notification.

**Phase 2 does not start until every box in Stabilisation is ticked.** All boxes are now ticked — the
gate closed on Aug 17. One residual note: a reply force-killed mid-generation (task swiped or `am kill`
during decode) leaves an honest "could not finish that reply" state and restarts generation; it does not
crash. Also found on the g54: Google's EmbeddingGemma `.task` artefact on its CDN carries two stray bytes
before the ZIP magic — the digest is self-consistent but MediaPipe rejects it, so the router's similarity
tier degrades to lexical scoring. Chat still works; the artefact itself needs attention (see
`decisions_devb.md`).

## Phase 2 — Voice-first mode and the router

Two things at once because they are one critical path: five of the seven core features arrive through
voice and dispatch through the router.

**Voice is the product's identity now, not a Phase 6 nicety.** That is the biggest structural change the
strategy brief forced, and it means the ASR question gets settled on day one of this phase.

### ASR and TTS

- [x] `[deva]` **Prove Sherpa-ONNX streaming ASR on the device before anything else in this phase.**
      Highest-risk item in the plan. v1 built an offline stack, hit unacceptable latency and glitching on
      the target hardware, and reverted it. **Timeboxed to two hours:** a streaming model on the
      SM-M356B, partial results while speaking, latency measured and written down. **If it fails, fall
      back to `SpeechRecognizer` immediately** and record the decision — do not spend a day rescuing it.
      *(Superseded by the owner's 2026-08-16 ASR reversal in `decisions_devb.md`: sherpa-ONNX is the
      Phase 2 stack, proven on the connected g54; the SpeechRecognizer fallback is not wired.)*
- [x] `[deva]` Streaming partial results, so text appears as the user speaks. v1's batch model only
      produced text after recording stopped, which read as seconds of dead latency and is the single
      reason its voice stack felt broken.
- [x] `[deva]` **Bug 1 from v1 — cancel on send.** If the mic is active and the user types and hits
      send, the recording loop must be cancelled cleanly. In v1 the mic stayed live in the background.
- [x] `[deva]` **Bug 2 from v1 — TTS queue.** Append streamed chunks to the TTS queue. Do **not** cancel
      the speak job on each new chunk — v1 did, so the engine interrupted its own sentence, skipped
      words, and restarted mid-phrase.
- [x] `[deva]` `InteractionOrigin`, so TTS speaks only turns that came from voice.
- [x] `[deva]` Push-to-talk only. No always-on wake word — battery drain, false triggers, and
      background-service reliability make it a live-demo risk. Describe it as next; do not claim it works.
- [ ] `[deva]` Voice session invocation from the widget and a home-screen hold, sharing the resident
      model session rather than a separate activity holding its own Engine.
- [x] `[deva]` Offline-capability check with an honest failure. If neither Sherpa nor an installed
      offline language pack is available, say so plainly and fall back to typed input rather than
      appearing to hang.
- [x] `[devb]` Voice session UI: distinct listening, thinking, and speaking states, mascot-anchored.
      This is the screen a judge will remember — it carries the voice-first claim on its own.
- [x] `[devb]` Live transcript surface, so the user sees the words being recognised and can correct
      course before Lumi acts.

### Router and dispatcher

- [x] `[deva]` Input normalization layer. Typed text, voice transcript, and widget input all converge on
      one internal representation. Design Spec §4.2.
- [x] `[deva]` Router tier 1 — rules. Regex and keyword matching for exact device commands. Zero
      latency, deterministic, no model. Always runs first. **SOS phrases are gone from this tier.**
- [x] `[deva]` Router tier 2 — embedding similarity over EmbeddingGemma. Classify reminder, todo,
      routine, RAG query, file request, web search, mail fetch, and plain chat by cosine similarity
      against labelled example phrases per intent. Keep the phrase lists in one editable place: when the
      router is wrong, the fix should be adding a phrase, not changing code.
- [ ] `[deva]` Router tier 3 — Gemma 4. Only for reasoning, generation, and ambiguity the first two
      tiers cannot settle.
- [x] `[deva]` Every tier returns the same shape — intent, confidence, structured payload — so the
      dispatcher does not care which tier produced it.
- [x] `[deva]` Confidence thresholds per tier and the tier-2 to tier-3 escalation rule. Write the
      numbers down. They will need tuning against real phrasing. *(Tier 3 is deferred this phase; the
      numbers live in `RouterContracts.kt` and escalate to chat fallback.)*
- [x] `[deva]` Router ordering rules. Reminder, todo, and attach verbs all overlap on "add" — fix the
      precedence order and test the collisions.
- [x] `[deva]` **Network-intent gate.** Web search and mail fetch are the only intents that may leave
      the device. They route through one chokepoint that checks the per-feature opt-in and writes an
      audit event *before* the request. One gate, not two code paths.
- [x] `[deva]` Dispatcher and capability registry. Each capability independently testable behind the
      `Capability` interface. Design Spec §5.
- [ ] `[deva]` Router uncertainty path: ask for clarification rather than execute an ambiguous action.
      Design Spec §38.
- [ ] `[deva]` Move the chat capability behind the `Capability` interface and dispatch it like
      everything else. One dispatch path, not two. *(Dev B lands this as the final single revertible
      commit of Phase 2.)*
- [ ] `[deva]` Persistent memory: explicit "remember this" writes, retrievable across chats.
- [x] `[deva]` Router unit tests: reminders, todos, routines, RAG requests, file requests, device
      commands, web search, mail fetch, and ambiguous input. Design Spec §40.
- [ ] `[deva]` Settle LiteRT-LM's built-in tool calling against Gemma 4 **before** the router commits to
      a tool path. If it is unreliable, tier 2 plus structured prompting is the fallback.
- [ ] `[devb]` Interpreted-intent surface. Where an action has consequence, show what Lumi understood
      before it acts. This is the ease-of-use criterion made visible.

## Phase 3 — Actions, reminders, and the widget

Core item 2: **a widget that sets reminders and todos and builds routines, executed through device
actions.** PRD §3.3's example is still the acceptance test: "When I get to college, put phone on silent
and turn on wifi."

- [ ] `[deva]` Reminder and todo data model. Simple and first-class, separate from the routine graph —
      "remind me at 6" should not have to become a trigger/action graph to work.
- [ ] `[deva]` Routine data model: trigger plus action graph, persisted in structured form.
- [ ] `[deva]` Natural language parsed to structure **at creation time only**. A firing routine reads its
      persisted structure and never re-interprets the original sentence. Design Spec §4.3.
- [ ] `[deva]` Validation and confirmation before a routine is saved. Design Spec §8.1.
- [ ] `[deva]` Trigger: time of day. **Ship this one first** — it is the only trigger a demo can
      reliably show in a room with no wifi to join and no campus to arrive at.
- [ ] `[deva]` Trigger: battery level.
- [ ] `[deva]` Trigger: wifi network name, on join and on leave.
- [ ] `[deva]` Trigger: location geofence.
- [ ] `[deva]` Trigger: calendar event.
- [ ] `[deva]` Device actions: wifi, bluetooth, flashlight, do-not-disturb, application launch. Prefer
      Intent APIs. Accessibility Service only where no Intent path exists.
- [ ] `[deva]` **Never use Accessibility Service for screen reading.** `takeScreenshot()` and related
      APIs are the same mechanism stalkerware and banking trojans use for silent capture. Using
      Accessibility for device *control* is fine; using it to read the screen is not. If screen capture is
      ever needed, use MediaProjection — its visible system notification is a feature for this product,
      not a limitation. Hard rule from v1.
- [ ] `[deva]` App-launch fuzzy matching. Strip spaces and non-alphanumerics from both the query and the
      package label before matching, otherwise multi-word app names fail. Cache the installed-app list;
      v1 fixed a real latency bug here.
- [ ] `[deva]` WorkManager execution. Must fire with the app closed. Design Spec §37.
- [ ] `[deva]` Idempotency guard. A retried worker must not run its actions twice.
- [ ] `[deva]` Boot re-registration. Re-arm every routine and reminder on `BOOT_COMPLETED`.
- [ ] `[deva]` Doze and App Standby handling. v1 used `setExactAndAllowWhileIdle` with graceful
      degradation to `setAndAllowWhileIdle` when exact alarms are not permitted — reminders still fire,
      less precisely, and the UI can prompt for the grant.
- [ ] `[deva]` Audit event on every fire, including failures.
- [ ] `[deva]` Tests: trigger detection, worker execution, duplicate prevention, app-closed execution,
      permission failure, retry behaviour. Design Spec §40.
- [ ] `[devb]` **Glance widget — the phase's headline.** Quick voice invoke, one-line command entry,
      reminder and todo capture, routine creation. Must match the app exactly: the same surfaces, the one
      Slime Blue accent, the mascot, the same spacing and interaction states per `DESIGN_LANGUAGE.md`.
      Sparse. It should read as a piece of Lumi, not a second product.
- [ ] `[devb]` Reminder and todo list surfaces, with completion and dismissal.
- [ ] `[devb]` Routine creation UI. Starts with a natural-language sentence, then shows the interpreted
      WHEN/DO structure for verification and editing — the structure exists for transparency, not as the
      primary input method. Design Spec §25.
- [ ] `[devb]` Routine list and detail screens.

## Phase 4 — File fetch and RAG

Core item 3. **File fetch is the more important half** — it is the demo no cloud assistant can copy,
because the file never leaves the phone.

- [ ] `[deva]` File resolver: recent files, Downloads, and user-selected folders. Metadata search first.
      Scope configurable in settings.
- [ ] `[deva]` Attach-file-by-name. Resolve the file, extract its text, inject it into the chat draft as
      a visible chip. The chip is the confirmation surface — do not auto-send.
- [ ] `[deva]` Semantic fallback for file search, but only deliberately. v1 classified images one by one
      through the vision model at ~0.5s each and it was the wrong default. Metadata first, semantic
      second, and only when the user asks for it.
- [ ] `[deva]` Ingestion: explicit user attachment or explicit folder selection only. Never a background
      scan of device storage. Hard rule from v1 — it also sidesteps Android 13+ scoped-storage limits
      entirely.
- [ ] `[deva]` Text extraction: plain text, PDF, docx, and images. **Vision is confirmed working**, so
      image understanding goes through Gemma rather than a separate OCR dependency — with a real context
      budget, because an image plus a prompt does not fit in 1024 tokens.
- [ ] `[deva]` Chunker with overlap. Tune chunk size against retrieval quality, not by guess.
- [ ] `[deva]` Reuse the EmbeddingGemma embedder from Phase 1 — the same model the router's similarity
      tier uses. No second embedding model, no token.
- [ ] `[deva]` Vector store: brute-force cosine over Room-backed chunks. At personal-notes scale (tens to
      low hundreds of chunks) a linear scan is sub-frame and a real index is unwarranted complexity. v1
      evaluated Qdrant Edge with a Rust JNI bridge and rejected it — do not revisit without a new
      decision entry.
- [ ] `[deva]` Retrieval and grounded generation.
- [ ] `[deva]` Citations built deterministically from the actual retrieved chunks — source, snippet,
      score. Never parsed out of model output, so they cannot be hallucinated.
- [ ] `[deva]` Knowledge scope toggle: "my notes only" strict grounding, or "notes plus model knowledge"
      where the model may blend but never contradict the notes. A prompt mode, not a data source change.
- [ ] `[deva]` RAG invocable by a routine, not only by an explicit chat query — a daily revision routine
      pulls from the index on its own. PRD §3.2.
- [ ] `[deva]` MCQ and study-card generation from indexed notes.
- [ ] `[deva]` Tests: ingestion, chunking, retrieval precision, local storage, grounded output.
- [ ] `[devb]` File candidate list UI for ambiguous matches.
- [ ] `[devb]` Notes and documents list screens.
- [ ] `[devb]` **Audit log screen.** What happened, when, which capability, which object, what result.
      With airplane-mode purity gone, this is *the* proof of the privacy claim — no longer a transparency
      nicety, it is the evidence. Design Spec §16, §34.
- [ ] `[devb]` Canvas. A lightweight document workspace for retrieved notes, generated study material,
      and MCQ cards. Editable, with visible source references. It must feel like a small document
      editor, not a second chatbot. Design Spec §7.3, §26. **First candidate to cut in this phase** if
      time runs short — file fetch and citations carry the demo, Canvas decorates it.

## Phase 5 — DuckDuckGo search and Gmail over MCP

Core items 4 and 5. **The only two features that touch the network.** Both opt-in, both user-initiated,
both through the Phase 2 network gate.

- [ ] `[deva]` DuckDuckGo search, **on user demand only.** Never automatic, never speculative, never in
      the background. The user asks for a search or Lumi does not search.
- [ ] `[deva]` Search result summarisation through Gemma, with the source links preserved and shown.
      Summarise what was fetched; never let the model answer from memory and pass it off as a result.
- [ ] `[deva]` Visible network indicator whenever a request is in flight, and an audit event for every
      one. A judge should be able to watch the network being used and watch it stop.
- [ ] `[deva]` Gmail over MCP: **read-only, one-way, fetch only.** No send, no draft, no delete, no
      label changes. Request the narrowest scope that works.
- [ ] `[deva]` OAuth flow, with tokens in `EncryptedSharedPreferences` or the Keystore — never in plain
      preferences and never in the database. Opt-in, revocable from settings, and revocation actually
      deletes the token.
- [ ] `[deva]` Fetched mail is summarised on-device by Gemma. **The mail body is never sent anywhere
      else.** That is the whole pitch: the network fetches, it never infers.
- [ ] `[deva]` Graceful offline behaviour for both. No network is a normal state, not an error state —
      say what is unavailable and carry on.
- [ ] `[deva]` Tests: the gate blocks when opted out, tokens are not readable from a plain preferences
      dump, and both capabilities fail closed rather than open.
- [ ] `[devb]` Search and mail result surfaces, with the source visible on every item.
- [ ] `[devb]` One settings panel for network features: what is enabled, what it can reach, and a single
      control that turns all of it off.

## Phase 6 — Call mode

Core item 6, and the feature with the clearest hardware counterpart — this is what a Limitless Pendant
or a Plaud NotePin sells for £100-200 plus a subscription. Read `COMPETITIVE_LANDSCAPE.md` first.

- [ ] `[deva]` Long-form capture as a foreground service with a visible, non-dismissible notification.
      Recording must be obvious. An assistant that can listen invisibly is the exact thing this product
      is positioned against.
- [ ] `[deva]` Streaming transcription over the Phase 2 ASR stack, chunked so a 45-minute meeting never
      holds the whole audio buffer in memory.
- [ ] `[deva]` **Gemma's audio input does not do this job.** It accepts audio — measured — but with a
      ~30-second clip cap, which is unusable for a meeting. Sherpa keeps 100% of call-mode ASR. Do not
      revisit without a new decision entry.
- [ ] `[deva]` Rolling summarisation. Summarise each chunk as it completes, then summarise the
      summaries, so the final output never needs a 45-minute transcript in one context window.
- [ ] `[deva]` Action items and decisions extracted into reminders and todos through the Phase 3 model,
      each one confirmable rather than auto-created.
- [ ] `[deva]` Transcript and summary in Room, local only, deletable per session.
- [ ] `[deva]` **Consent and legality copy.** One-party and two-party consent differ by jurisdiction. The
      UI states plainly that the user is responsible for consent, before the first recording, not buried
      in settings.
- [ ] `[deva]` Battery and thermal check on a real 20-minute run. Continuous ASR on a mid-range Exynos is
      the most demanding thing Lumi does; measure it and write the number down.
- [ ] `[deva]` Tests: chunk boundaries do not lose words, a rotation mid-recording does not stop the
      service, and a killed process leaves a recoverable partial transcript.
- [ ] `[devb]` Call mode UI: recording state, elapsed time, live partial transcript, and a stop control
      that is impossible to miss.
- [ ] `[devb]` Summary screen: the summary, the action items, and access to the full transcript.

## Phase 7 — Notification reading and proactive suggestions

Core item 7. The payoff of living on the phone instead of beside it — and the only phase where Lumi acts
without being asked, so the rules here are tighter than anywhere else.

- [ ] `[deva]` `NotificationListenerService`, **opt-in, with a per-app allowlist that starts empty.** The
      user chooses which apps Lumi may read. Never all-apps-by-default.
- [ ] `[deva]` Notification content stays on-device and is never persisted beyond what a suggestion
      needs. Read, act, discard.
- [ ] `[deva]` Suggestion engine: a notification plus context produces at most one suggestion. Never a
      queue, never a badge count, never a nag.
- [ ] `[deva]` **Suggestions are suggestions.** Lumi proposes; the user accepts. No autonomous action
      from a notification, ever — this is the no-autonomous-action rule in `decisions.md` at its
      sharpest, because the trigger came from outside the user's intent.
- [ ] `[deva]` Rate limiting and dismissal memory. A suggestion dismissed twice is not offered a third
      time.
- [ ] `[deva]` Audit event for every notification read and every suggestion made. If Lumi read
      something, the user can see that it did.
- [ ] `[deva]` Tests: the allowlist is honoured, a dismissed suggestion stays dismissed, and no
      suggestion path can reach a capability without user confirmation.
- [ ] `[devb]` Suggestion surface: quiet, dismissible, mascot-anchored, never modal.
- [ ] `[devb]` Notification access settings: which apps, what Lumi saw, and one switch to stop it.

## Phase 8 — Hardening, QA, documentation

Aug 20 night through Aug 21 morning. **No new features. None.** This phase is never cut.

- [ ] `[both]` **Privacy audit** — the replacement for the old airplane-mode run. Enumerate every network
      call the app can make, prove each one sits behind an opt-in and an audit event, and confirm no
      other code path can reach the network. Then run the whole app with the network off and confirm
      every non-network feature is unaffected.
- [ ] `[both]` Device QA on the actual demo hardware. Not an emulator.
- [ ] `[both]` Crash pass, ANR pass, memory pass. Watch for OOM on image-heavy paths and during call
      mode, which holds the model resident and an audio pipeline at once.
- [ ] `[both]` Cold start, model load, and call-mode battery drain measured and written down.
- [ ] `[both]` Permission-denied path for every permission the app requests. Every one degrades
      gracefully and says what it lost.
- [ ] `[deva]` **Rebuild documentation.** Required deliverable, due with the app. How to rebuild Lumi v2
      from empty: environment, model acquisition, phase order, and the non-obvious decisions with their
      reasons.
- [ ] `[deva]` Architecture document reflecting what was actually built, not what was planned.
- [ ] `[deva]` Fold Dev B's decision and changelog entries into the main files.
- [ ] `[devb]` Visual consistency audit. No placeholder screens, no unfinished interactions, no debug UI,
      no inconsistent component styling, no abrupt layout shifts. Design Spec §35.
- [ ] `[devb]` Every loading, empty, and error state reviewed for real copy. No "Loading…", no raw
      exception text surfaced to the user.
- [ ] `[devb]` Accessibility pass: system text scaling, contrast, touch targets, content descriptions,
      focus states, and a reduced-motion audit across every animation. No information conveyed by colour
      alone. Design Spec §29.
- [ ] `[both]` Demo script written and rehearsed end to end at least twice, on the demo device, with the
      model cold.

## Cut line

Nothing below this line ships before Aug 22 unless Phases 1-8 are genuinely finished. If they finish
early, harden them further rather than starting Phase 9.

## Phase 9 — Mood journal (optional)

Demoted from core by the owner on Aug 16. Build it only if everything above is solid.

- [ ] `[deva]` Journal entries in Room. Private, local, never uploaded.
- [ ] `[deva]` Pattern surfacing over logged entries, producing an optional routine suggestion: "You've
      logged poor sleep four times this week. Set a wind-down routine?"
- [ ] `[deva]` **Journaling scope guard.** Lumi must never present itself as a therapist, a mental health
      provider, an emotional companion, or a crisis detection system. This is data pattern surfacing that
      feeds automation. Do not build proactive emotional support or crisis detection. PRD §3.9 is
      explicit and it is a liability boundary, not a style note. **This guard binds even though the
      feature is optional** — a half-built journal is exactly where the line gets crossed by accident.
- [ ] `[devb]` Journal screens: entry, history, surfaced patterns.

## Phase 10 — Post-hackathon

- [ ] `[both]` Always-on wake word, with an honest battery and reliability assessment.
- [ ] `[both]` Proactive document surfacing from calendar context — surface an ID before a bank
      appointment. PRD §3.4, §6.
- [ ] `[both]` Additional routine triggers, for example app-open events.
- [ ] `[both]` Multilingual and multi-voice TTS.
- [ ] `[both]` Whole-library RAG indexing, if the product ever needs scope beyond explicit attachment.
      Requires a new decision entry — the explicit-attachment rule exists for real reasons.
- [ ] `[both]` Additional MCP sources beyond Gmail, if the one-way read-only pattern proves itself.

## Reuse inventory

v1 is reference material. For each item: read it, understand why it works, then write the v2 version
clean. Do not copy files across. Carry the lesson, not the code.

| v1 area | v2 target | What to fix while rewriting |
|---|---|---|
| Chat, attachments | `ui/chat`, chat capability | Untangle from the model-catalogue UI it inherited from the Gallery fork |
| Rule-based router | `router/` | Was scattered across two classes and the chat view model. Make it one testable unit with no Android dependencies |
| Device actions | `device/` | Keep the fuzzy app matching and the installed-app cache. Extract the camera-id lookup once |
| File fetch | `files/` | v1's semantic fallback classified images one by one through the vision model at ~0.5s each. Metadata search first; fall back only on request |
| Memory | `memory/` | Move off proto DataStore onto Room |
| Schedules, notifications | `routines/` | Good reliability work — exact-alarm degradation, boot re-arm. Generalize from reminders to the full trigger set |
| RAG | `rag/` | Sound approach. Move chunk storage to Room. Keep citations built from retrieved chunks |
| Vision | Phase 4 extraction | v1's working vision proves the artefact supports it. Carry the 4096-token context budget, not the accelerator comment — that refers to Gemma 3n |
| Voice | Phase 2 | Carry the two bug fixes. **Do** carry the sherpa lesson: streaming, not batch. Voice is core now, so a timeboxed spike replaces v1's open-ended attempt |
| SOS | — | **Cut.** Do not port it. `MIGRATION_1_2` drops its table |
| Theme, mascot | `ui/theme`, `ui/components` | Keep the identity. Rebuild as real design-system tokens |
| Gallery fork remnants | — | Drop entirely: model catalogue, model picker, benchmark UI, example task modules, Firebase |

## Definition of done

A task is done when the code compiles, the behaviour was verified on a device or by a test, the change is
committed with a one-line conventional message, and — if it changed something a person would care about —
`changelog.md` has an entry. If it involved a judgement call someone could reasonably reverse later,
`decisions.md` has an entry explaining why.

A phase is done when its exit criteria in the milestone map pass, not when its checkboxes are ticked.

## Maintaining this file

Tick boxes as work lands. Do not delete cut items — mark them `[-]` so the reason stays visible. When a
task turns out to be wrong, strike it and add a `decisions.md` entry rather than quietly editing it away.
Dev A owns merges; Dev B proposes through the process in `for_devb.md`.
