# Trace v2 — TODO

Single source of truth for what gets built, in what order, and by whom. Dev A maintains this file.
Dev B proposes changes to it through the process in `for_devb.md`; Dev A merges them.

- **Deadline:** app complete Aug 21 2026, morning — including rebuild documentation.
- **Hackathon:** Aug 22 2026.
- **Planning started:** Aug 14 2026, 19:00.
- **Specs:** `Trace_PRD_v2.docx` and `Trace — Design Specification.docx`. Both are authoritative.
  Where they disagree with each other or with reality, this file records the resolution and
  `decisions.md` records why.
- **v1 reference:** a local Trace v1 checkout, path differs per developer. Reference and reuse only.
  Read it, understand why it works, rewrite it clean. Never copy a file across unchanged.

## Legend

Ownership:

- `[deva]` — Dev A. Core, data, AI runtime, router, dispatcher, routines, device, background work.
- `[devb]` — Dev B. Design system, screens, navigation, widget, accessibility, UI states.
- `[both]` — needs both developers, or either can take it.

Status: `[ ]` pending · `[~]` in progress · `[x]` done · `[!]` blocked · `[-]` cut.

## Scope reality check

Read this before planning your day.

Seven working days, two developers, an empty repository, and a production-grade target. Design Spec
§41 lists nine P0 items and five P1 items. All fourteen will not reach production quality in seven
days. Pretending otherwise produces fourteen half-features and no demo.

This plan front-loads the demo-critical path: RAG over the user's own notes, a routine firing while
the app is closed, and SOS. Phases 0 through 5 build that path. Phase 6 adds voice and journaling.
Everything after the cut line is explicitly optional.

Cut from the bottom, never from the middle. If a phase slips more than half a day, drop the lowest
remaining item rather than shipping every phase at 70%. A complete core loop demos well. Fourteen
partial features do not demo at all.

## Milestone map

| Phase | Window | Owner | Exit criteria |
|---|---|---|---|
| 0 — Foundation | Aug 14 night → Aug 15 midday | deva solo | Build green, app launches, contracts published, Dev B unblocked |
| 1 — Design system + model runtime | Aug 15 pm → Aug 16 am | both | Gemma loads once and stays resident. Component library exists. Home screen renders |
| 2 — Router + dispatcher + chat | Aug 16 | both | Typed input routes to the right capability. Chat streams and persists |
| 3 — Routines engine + widget | Aug 17 | both | A routine created in natural language fires with the app closed |
| 4 — RAG + Canvas | Aug 18 | both | A question about attached notes returns a grounded, cited answer |
| 5 — Device actions + files + SOS | Aug 19 | both | **Stable baseline.** Full core loop works offline on a real device |
| 6 — Voice + journaling | Aug 20 | both | Voice drives every capability. Journal surfaces a pattern |
| 7 — Hardening, QA, docs | Aug 20 night → Aug 21 am | both | Airplane-mode clean, device QA passed, rebuild docs written, demo rehearsed |
| — cut line — | | | Everything below is post-hackathon |
| 8 — Post-hackathon | after Aug 22 | both | Sherpa voice stack, proactive surfacing, extra triggers |

Dev B has no assigned work in Phase 0. Nothing in an empty repository can be parallelized before the
scaffold, theme tokens, dependency injection, and database schema exist. Dev B's Phase 0 is setup:
get v1 building locally, read both specs, read `for_devb.md`.

## Phase 0 — Foundation

Dev A solo. Ends when Dev B is unblocked. Commit the contracts as soon as they compile, before the
rest of the phase is finished, so Dev B can start.

- [ ] `[deva]` Gradle project. Namespace `com.trace`, minSdk 26, targetSdk 36, Java 17 toolchain,
      Kotlin + Compose. Package layout per Design Spec §36.
- [ ] `[deva]` Version catalog in `gradle/libs.versions.toml`. Pin every version exactly. No
      version ranges, no `latest.release`.
- [ ] `[deva]` Hilt dependency injection and the `Application` class.
- [ ] `[deva]` Theme tokens: rice-paper white surface, sumi near-black text, one muted sumi-olive
      accent. Serif type scale, spacing scale, radius scale. Design Spec §19, §20, §28.
- [ ] `[deva]` Room database. Entities per Design Spec §17: `Chat`, `ChatMessage`, `Document`,
      `DocumentChunk`, `Memory`, `Routine`, `RoutineTrigger`, `RoutineAction`, `JournalEntry`,
      `AuditEvent`, `EmergencyContact`. DAOs, indices, and a migration policy from day one.
- [ ] `[deva]` Navigation skeleton: Home, Data, Time, Safety, Settings. Shallow, per §24.
- [ ] `[deva]` Publish core contracts: `Capability`, `CapabilityInput`, `CapabilityResult`,
      `RouterResult`, `StructuredIntent`, and the repository interfaces Dev B binds to.
- [ ] `[deva]` `.gitignore`, `README.md` stub, and the licence/attribution decision. v1 forked
      Google AI Edge Gallery under Apache 2.0 and carries `LICENSE` + `NOTICE`. Any v2 code derived
      from that fork inherits the attribution obligation. Resolve this in `decisions.md` before
      reusing runtime code.
- [ ] `[deva]` `./gradlew assembleDebug` green. App installs and opens to an empty home screen.
- [ ] `[devb]` Setup only: get v1 building locally, read both spec documents, read `for_devb.md`,
      confirm Android Studio and a physical test device work.

## Phase 1 — Design system and model runtime

- [ ] `[deva]` LiteRT-LM dependency and Gemma 4 E2B model load. Verify Android setup and multimodal
      support early — PRD §5 flags this as the top risk. Document the fallback path if audio or image
      input blocks during setup.
- [ ] `[deva]` Model lifecycle: load once, stay resident for the process lifetime. Never reload per
      request. Cold load measured ~90-100s on v1's target hardware, so a per-request reload makes the
      app unusable. This is a hard rule carried from v1.
- [ ] `[deva]` Model acquisition strategy: bundled asset or first-run download, with progress state
      and a resumable failure path.
- [ ] `[deva]` Inference config: max tokens 512-768, temperature 0.4-0.5, topP 0.9, topK 32-40, GPU
      backend, thinking mode off for latency. Tune against real device numbers, not v1's notes.
- [ ] `[deva]` System prompt and persona module. Concise by default, 1-2 sentences unless the user
      asks for more — a UX choice and a decode-speed constraint.
- [ ] `[deva]` Audit log repository and write path. Build this first so every later capability can
      log from its first commit instead of being retrofitted.
- [ ] `[devb]` Component library: buttons, text fields, cards, dialogs, bottom sheets, chips,
      toggles, sliders, navigation elements. One cohesive system, shared tokens. Design Spec §28.
- [ ] `[devb]` Mascot composable. Simple circular blob with eyes, reused from v1's identity.
- [ ] `[devb]` Mascot breathing animation: slow, low amplitude, deterministic. Must respect
      reduced-motion settings, lifecycle state, and battery saver. Static when reduced motion is on.
- [ ] `[devb]` Home screen. Mascot as visual anchor, one natural-language input, Type and Speak
      affordances. Deliberately sparse — no feature grid, no dashboard cards, no conversation-first
      layout. Design Spec §23.
- [ ] `[devb]` Shared state components: loading, empty, error. Loading text names the real operation
      ("Retrieving your notes…", not "Loading…"). Design Spec §31, §32, §33.

## Phase 2 — Router, dispatcher, chat

The router is the architectural centre of the product. Everything else dispatches through it.

- [ ] `[deva]` Input normalization layer. Typed text, voice transcript, widget input, and power-button
      invocation all converge on one internal representation. Design Spec §4.2.
- [ ] `[deva]` Rule-based router. Returns intent, confidence, and a structured payload. Start with
      regex and keyword matching — v1 chose this deliberately to keep latency at zero and avoid
      loading a second model, and PRD §4.2 accepts it for v2.
- [ ] `[deva]` Router ordering rules. SOS is classified first, before every other action. Reminder
      and attach verbs overlap ("add"), so fix the precedence order and test the collisions.
- [ ] `[deva]` Dispatcher and capability registry. Each capability independently testable behind the
      `Capability` interface. Design Spec §5.
- [ ] `[deva]` Router uncertainty path: ask for clarification rather than execute an ambiguous
      action. Design Spec §38.
- [ ] `[deva]` Chat capability: streaming generation, per-chat context memory, history in Room.
- [ ] `[deva]` Persistent memory: explicit "remember this" writes, retrievable across chats.
- [ ] `[deva]` Tool architecture through the same dispatcher: calculator, calendar, reminders,
      device actions. One dispatch path, not two. Design Spec §9.
- [ ] `[deva]` Router unit tests: routine commands, RAG requests, file requests, device commands,
      journal input, SOS phrases, and ambiguous input. Design Spec §40.
- [ ] `[devb]` Chat screen: message list, streaming render, attachment chips, scroll behaviour.
- [ ] `[devb]` Interpreted-intent surface. Where an action has consequence, show what Trace
      understood before it acts.
- [ ] `[devb]` Navigation wiring for the Data, Time, and Safety sections.

## Phase 3 — Routine engine and widget

The flagship feature. PRD §3.3's example is the acceptance test: "When I get to college, put phone on
silent and turn on wifi."

- [ ] `[deva]` Routine data model: trigger plus action graph, persisted in structured form.
- [ ] `[deva]` Natural language parsed to structure **at creation time only**. A firing routine reads
      its persisted structure and never re-interprets the original sentence. Design Spec §4.3.
- [ ] `[deva]` Validation and confirmation step before a routine is saved. Design Spec §8.1.
- [ ] `[deva]` Trigger: time of day.
- [ ] `[deva]` Trigger: battery level.
- [ ] `[deva]` Trigger: wifi network name, on join and on leave.
- [ ] `[deva]` Trigger: location geofence.
- [ ] `[deva]` Trigger: calendar event.
- [ ] `[deva]` WorkManager execution. Must fire with the app closed. Design Spec §37.
- [ ] `[deva]` Idempotency guard. A retried worker must not run its actions twice.
- [ ] `[deva]` Boot re-registration. Re-arm every routine on `BOOT_COMPLETED`.
- [ ] `[deva]` Doze and App Standby handling. v1 used `setExactAndAllowWhileIdle` with graceful
      degradation to `setAndAllowWhileIdle` when exact alarms are not permitted — reminders still
      fire, less precisely, and the UI can prompt for the grant.
- [ ] `[deva]` Audit event written on every routine fire, including failures.
- [ ] `[deva]` Routine tests: trigger detection, worker execution, duplicate prevention, app-closed
      execution, permission failure, retry behaviour. Design Spec §40.
- [ ] `[devb]` Routine creation UI. Starts with a natural-language sentence. Then shows the
      interpreted WHEN/DO structure for verification and editing — the structure exists for
      transparency, not as the primary input method. Design Spec §25.
- [ ] `[devb]` Routine list and detail screens.
- [ ] `[devb]` Glance home-screen widget. Quick invoke, command entry, routine creation. Must match
      the app exactly: rice-paper, sumi type, the one olive accent, the mascot, the same spacing.
      Sparse. It should read as a piece of Trace, not a second product. Design Spec §27.

## Phase 4 — RAG and Canvas

- [ ] `[deva]` Ingestion: explicit user attachment or explicit folder selection only. Never a
      background scan of device storage. This is a hard rule from v1 — it also sidesteps Android 13+
      scoped-storage limits entirely.
- [ ] `[deva]` Text extraction: plain text, PDF, docx, and images via OCR.
- [ ] `[deva]` Chunker with overlap. Tune chunk size against retrieval quality, not by guess.
- [ ] `[deva]` On-device embedding model as a bundled asset. v1 shipped a ~5.9MB Universal Sentence
      Encoder via MediaPipe `TextEmbedder`. Note the distinction: MediaPipe's **LLM Inference API** is
      maintenance-only, but `tasks-text` `TextEmbedder` is a separate, still-current component.
- [ ] `[deva]` Vector store: brute-force cosine over Room-backed chunks. At personal-notes scale
      (tens to low hundreds of chunks) a linear scan is sub-frame and a real index is unwarranted
      complexity. v1 evaluated Qdrant Edge with a Rust JNI bridge and rejected it — do not revisit
      without a new decision entry.
- [ ] `[deva]` Retrieval and grounded generation.
- [ ] `[deva]` Citations built deterministically from the actual retrieved chunks — source, snippet,
      score. Never parsed out of model output, so they cannot be hallucinated.
- [ ] `[deva]` Knowledge scope toggle: "my notes only" strict grounding, or "notes plus model
      knowledge" where the model may blend but never contradict the notes. A prompt mode, not a data
      source change. Both fully offline.
- [ ] `[deva]` MCQ and study-card generation from indexed notes.
- [ ] `[deva]` RAG invocable by a routine, not only by an explicit chat query — a daily revision
      routine pulls from the index on its own. PRD §3.2.
- [ ] `[deva]` RAG tests: ingestion, chunking, retrieval precision, local storage, grounded output.
- [ ] `[devb]` Notes and documents list screens.
- [ ] `[devb]` Canvas. A lightweight document workspace for retrieved notes, generated study
      material, and MCQ cards. Editable, with visible source references. It must feel like a small
      document editor, not a second chatbot. Design Spec §7.3, §26.
- [ ] `[devb]` Audit log screen. What happened, when, which capability, which object, what result.
      This is the primary transparency surface and the privacy pitch's proof. Design Spec §16, §34.

## Phase 5 — Device actions, file fetch, SOS

- [ ] `[deva]` Device actions: wifi, bluetooth, flashlight, application launch. Prefer Intent APIs.
      Accessibility Service only where no Intent path exists.
- [ ] `[deva]` **Never use Accessibility Service for screen reading.** `takeScreenshot()` and related
      APIs are the same mechanism stalkerware and banking trojans use for silent capture. Using
      Accessibility for device *control* is fine; using it to read the screen is not. If screen
      capture is ever needed, use MediaProjection — its visible system notification is a feature for
      this product, not a limitation. Hard rule from v1.
- [ ] `[deva]` App-launch fuzzy matching. Strip spaces and non-alphanumerics from both the query and
      the package label before matching, otherwise multi-word app names fail. Cache the installed-app
      list; v1 fixed a real latency bug here.
- [ ] `[deva]` File resolver: recent files, Downloads, and user-selected folders. Metadata search
      first. Scope configurable in settings.
- [ ] `[deva]` Attach-file-by-name. Resolve the file, extract its text, inject it into the chat draft
      as a visible chip. The chip is the confirmation surface — do not auto-send.
- [ ] `[deva]` SOS as a prebuilt routine on the shared trigger/action engine, not a separate
      subsystem. Design Spec §13.
- [ ] `[deva]` SOS detector with tiers. Bare keyword fires instantly with no confirmation and no
      model call. Keyword embedded in longer text gets a cancellable countdown. A described crisis
      with no keyword goes to the model, which answers with calm guidance and then invokes the SOS
      tool. Word-boundary matching so "society" and "so sorry" can never match.
- [ ] `[deva]` Flashlight Morse signal. Reuse one shared camera-id lookup with the flashlight device
      action rather than duplicating it.
- [ ] `[deva]` Siren on the alarm stream at raised volume, restored on stop.
- [ ] `[deva]` Location fix: GPS and network, fresh fix with a last-known fallback, no Play Services
      dependency.
- [ ] `[deva]` SMS to configured emergency contacts with a map link, multipart-safe, per-contact
      result reported to the UI.
- [ ] `[deva]` SOS foreground service and wake lock so signals survive screen-off and backgrounding.
- [ ] `[deva]` Multiple stop paths: full-screen control, notification action, and spoken cancel.
- [ ] `[deva]` Permissions requested during SOS setup, never as a blocker at trigger time. Whatever
      is not granted degrades gracefully — flash and siren still run, SMS is skipped with a visible
      and spoken reason.
- [ ] `[deva]` Airplane-mode behaviour: signals run, SMS fails **loudly** on screen and by voice.
      Never silently.
- [ ] `[deva]` SOS tests: tier detection, near-miss words, transcription variants, Morse timing,
      SMS body builder, countdown cancellation.
- [ ] `[devb]` SOS screen: full-screen, unmissable stop control, live status per signal, shown over
      the lock screen.
- [ ] `[devb]` SOS setup screen: emergency contacts CRUD, user name for the message, flash and siren
      toggles, and a safe test button that previews the flash pattern without sending SMS.
- [ ] `[devb]` File candidate list UI for ambiguous matches.
- [ ] `[devb]` Settings screens: file scope, knowledge scope, routine permissions, SOS.
- [ ] `[devb]` Accessibility pass 1: system text scaling, contrast, touch targets, content
      descriptions, focus states. No information conveyed by colour alone. Design Spec §29.

### Stable baseline — exit criteria, end of Aug 19

Verified on a physical device in airplane mode, app freshly launched:

1. Attach a set of notes. Ask a question. Get a grounded answer with citations.
2. Say or type a routine in natural language. Confirm the interpreted structure. Save it.
3. Close the app. Trigger the condition. The routine fires and the actions run.
4. Fire SOS. Flashlight signals, siren sounds, stop works from the notification.
5. Open the audit log. Every one of the above appears with a timestamp and a result.

Do not start Phase 6 until all five pass. If they do not pass by Aug 19 night, cut Phase 6 and spend
Aug 20 making these five solid.

## Phase 6 — Voice and journaling

Voice ships on the native Android stack for the hackathon. Sherpa-ONNX moves to Phase 8. See
`decisions.md` for the full reasoning — the short version is that v1 built the offline stack, hit
unacceptable latency and glitching on the target hardware, and reverted it. Repeating that inside a
seven-day window is the single highest-risk thing this plan could do.

The two v1 bugs below are the reason the native stack felt broken in v1. Both are simple. Fix them in
the first commit of this phase rather than rediscovering them.

- [ ] `[deva]` `SpeechRecognizer` for ASR, using streaming partial results so text appears as the
      user speaks. v1's offline batch model only produced text after recording stopped, which read as
      seconds of dead latency.
- [ ] `[deva]` **Bug 1 — cancel on send.** If the mic is active and the user types and hits send, the
      recording loop must be cancelled cleanly. In v1 the mic stayed live in the background.
- [ ] `[deva]` **Bug 2 — TTS queue.** Append streamed chunks to the TTS queue. Do **not** cancel the
      speak job on each new chunk — v1 did, so the engine interrupted its own sentence, skipped
      words, and restarted mid-phrase.
- [ ] `[deva]` Verify offline ASR on the target device with the network disabled. Native recognition
      needs an installed offline language pack. If it is unavailable, the app must say so plainly and
      fall back to typed input rather than appearing to hang. This is the one place the native choice
      is weaker than sherpa — test it on day one of this phase, not at the end.
- [ ] `[deva]` `InteractionOrigin`, so TTS speaks only turns that came from voice.
- [ ] `[deva]` Voice session invocation: power button or widget, as a dialog sharing the resident
      model session rather than a separate activity holding its own.
- [ ] `[deva]` Voice drives every capability: chat, routines, file retrieval, device actions, SOS.
- [ ] `[deva]` Push-to-talk only. No always-on wake word — battery drain, false triggers, and
      background-service reliability make it a live-demo risk. Describe it as the next step; do not
      claim it works.
- [ ] `[deva]` Journal entries in Room. Private, local, never uploaded.
- [ ] `[deva]` Pattern surfacing over logged entries, producing an optional routine suggestion:
      "You've logged poor sleep four times this week. Set a wind-down routine?"
- [ ] `[deva]` **Journaling scope guard.** Trace must never present itself as a therapist, a mental
      health provider, an emotional companion, or a crisis detection system. This is data pattern
      surfacing that feeds automation. Do not build proactive emotional support or crisis detection.
      PRD §3.9 is explicit and it is a liability boundary, not a style note.
- [ ] `[devb]` Voice session UI: distinct listening, thinking, and speaking states. Mascot-anchored.
- [ ] `[devb]` Journal screens: entry, history, surfaced patterns.
- [ ] `[devb]` Accessibility pass 2 and a reduced-motion audit across every animation.

## Phase 7 — Hardening, QA, documentation

Aug 20 night through Aug 21 morning. No new features. None.

- [ ] `[both]` Full airplane-mode run across every feature. A single accidental network call breaks
      the product's entire premise if a judge finds it. No feature is complete until it has been
      tested with the network off.
- [ ] `[both]` Device QA on the actual demo hardware. Not an emulator.
- [ ] `[both]` Crash pass, ANR pass, memory pass. Watch for OOM on image-heavy paths.
- [ ] `[both]` Cold start and model load timing measured and written down.
- [ ] `[both]` Permission-denied path for every permission the app requests.
- [ ] `[deva]` **Rebuild documentation.** Required deliverable. How to rebuild Trace v2 from empty:
      environment, model acquisition, phase order, and the non-obvious decisions with their reasons.
- [ ] `[deva]` Architecture document reflecting what was actually built, not what was planned.
- [ ] `[deva]` Fold Dev B's decision and changelog entries into the main files.
- [ ] `[devb]` Visual consistency audit. No placeholder screens, no unfinished interactions, no debug
      UI, no inconsistent component styling, no abrupt layout shifts. Design Spec §35.
- [ ] `[devb]` Every loading, empty, and error state reviewed for real copy. No "Loading…", no raw
      exception text surfaced to the user.
- [ ] `[both]` Demo script written and rehearsed end to end at least twice.

## Cut line

Nothing below this line ships before Aug 22. If Phases 0-7 finish early, harden them further rather
than starting Phase 8.

## Phase 8 — Post-hackathon

- [ ] `[both]` Sherpa-ONNX voice stack, rebuilt cleanly. Both ASR and TTS on sherpa, no Whisper.
      The three things that must be different from v1's attempt:
      1. Streaming ASR with partial results, not batch transcription after recording stops.
      2. TTS queue that appends chunks instead of cancelling the current utterance on each arrival.
      3. Model size and thread count tuned to the target device before integration, not after.
      Ship it behind a settings toggle so the native stack stays the fallback rather than a revert.
- [ ] `[both]` Proactive document surfacing from calendar context — surface an ID before a bank
      appointment. PRD §3.4, §6.
- [ ] `[both]` Additional routine triggers, for example app-open events.
- [ ] `[both]` Multilingual and multi-voice TTS.
- [ ] `[both]` Whole-library RAG indexing, if the product ever needs scope beyond explicit
      attachment. This requires a new decision entry — the explicit-attachment rule exists for real
      reasons.
- [ ] `[both]` Always-on wake word, with an honest battery and reliability assessment.

## Reuse inventory

v1 is reference material. For each item: read it, understand why it works, then write the v2 version
clean. Do not copy files across. Carry the lesson, not the code.

| v1 area | v2 target | What to fix while rewriting |
|---|---|---|
| Chat, attachments | `ui/chat`, chat capability | Untangle from the model-catalogue UI it inherited from the Gallery fork |
| Rule-based router | `router/` | Was scattered across two classes and the chat view model. Make it one testable unit with no Android dependencies |
| Device actions | `device/` | Keep the fuzzy app matching and the installed-app cache. Extract the camera-id lookup once, shared with SOS |
| File fetch | `files/` | v1's semantic fallback classified images one by one through the vision model at ~0.5s each. Metadata search first; only fall back deliberately |
| Memory | `memory/` | Move off proto DataStore onto Room |
| Schedules, notifications | `routines/` | Good reliability work — exact-alarm degradation, boot re-arm. Generalize from reminders to the full trigger set |
| RAG | `rag/` | Sound approach. Move chunk storage to Room. Keep citations built from retrieved chunks |
| SOS | `sos/` | Most complete v1 subsystem. Re-express as a routine on the shared engine instead of a parallel path |
| Voice | `voice/` | Carry the two bug fixes in Phase 6. Do not carry the abandoned sherpa integration |
| Theme, mascot | `ui/theme`, `ui/components` | Keep the identity. Rebuild as real design-system tokens |
| Gallery fork remnants | — | Drop entirely: model catalogue, model picker, benchmark UI, example task modules, Firebase |

## Definition of done

A task is done when the code compiles, the behaviour was verified on a device or by a test, the change
is committed with a one-line conventional message, and — if it changed something a person would care
about — `changelog.md` has an entry. If it involved a judgement call someone could reasonably reverse
later, `decisions.md` has an entry explaining why.

A phase is done when its exit criteria in the milestone map pass, not when its checkboxes are ticked.

## Maintaining this file

Tick boxes as work lands. Do not delete cut items — mark them `[-]` so the reason stays visible.
When a task turns out to be wrong, strike it and add a `decisions.md` entry rather than quietly
editing it away. Dev A owns merges; Dev B proposes through the process in `for_devb.md`.

