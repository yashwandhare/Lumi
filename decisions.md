# Lumi v2 — Decisions

Running record of *why* choices were made, so they are not re-litigated later without cause.

## How to use this file

- Append new entries at the bottom. Never edit an old entry to change its meaning.
- If a past decision is reversed, do not delete it. Add a new entry that references it and explains
  what changed.
- Prefix every entry with the author: `[deva]` or `[devb]`. Dev B keeps entries in
  `decisions_devb.md` on the `devb` branch; Dev A folds them into this file at merge, prefixes intact.
- Date every entry. A decision without a date cannot be ordered against the one that supersedes it.
- Log a decision when someone could reasonably reverse the choice later and would need the reasoning
  to decide whether to. Do not log routine implementation detail.

---

# Part 1 — Carried forward from v1

These were decided during v1, still bind v2, and should not be reopened without a new entry below.
They are restated rather than referenced because Dev B does not have the v1 decision log.

### [deva] Carried — Never use Accessibility Service to read the screen

`takeScreenshot()` and the related Accessibility APIs are the same mechanism stalkerware and banking
trojans use for silent, invisible screen capture: no notification, no visible indicator.

**Why:** the product's entire positioning is privacy. Shipping the same API surface as malware would
undermine it on inspection. If screen capture is ever needed, use MediaProjection — its visible system
notification is a feature here, not a limitation, because it is proof of consent.

**Scope note:** using Accessibility Service for device *control* is fine and v2 does it. The rule is
specifically about reading screen content.

### [deva] Carried — The model loads once and stays resident

Never write code that loads Gemma per request.

**Why:** cold load measured roughly 90-100 seconds on v1's target hardware. A per-request reload makes
the app unusable and the demo dead on arrival.

### [deva] Carried — Every feature must work with the network disabled

No feature is complete until it has been tested in airplane mode.

**Why:** a single accidental network call anywhere breaks the entire premise of the product if it is
discovered during a live demo.

**One deliberate exception:** opt-in web search. It is off by default, session-only, deliberately not
persisted to disk so it resets on every launch, never auto-enabled, and never triggered by a background
flow. No core feature may depend on it being on. It is a convenience utility, never part of the
privacy-first pitch.

### [deva] Carried — RAG ingests only what the user explicitly attaches

Ingestion happens when the user attaches a file in chat or selects one directly. Lumi never performs a
background scan of device storage.

**Why:** it is the honest behaviour for a privacy product, and it sidesteps Android 13+ scoped-storage
restrictions entirely rather than fighting them.

### [deva] Carried — Pure-Kotlin vector search, no Rust or JNI

Brute-force cosine similarity over the user's chunks. v1 evaluated Qdrant Edge, which ships only as a
Rust crate or Python bindings, and rejected the JNI bridge.

**Why:** cross-compiling a Rust crate and debugging a JNI boundary is high-risk, high-cost work with no
product-visible payoff at this data scale. At tens to low hundreds of chunks a linear scan is
sub-frame. Do not reintroduce Rust without a new entry here.

### [deva] Carried — Push-to-talk only, no always-on wake word

**Why:** battery drain, false triggers, and Android's background-service reliability constraints make
always-on listening a genuine live-demo risk. It is real future scope. The pitch may describe it as the
next step but must not claim it already works.

### [deva] Carried — No autonomous action on personal data

Anything with real-world consequence if wrong — financial data, identity data, submitted forms,
outbound messages — shows the user what it is about to do and requires explicit confirmation. No
"smart enough to skip it" carve-out.

**This rule now has no exceptions.** v1 carved one out for SOS's bare-keyword tier, on the grounds that
typing "sos" in an emergency *is* the confirmation and a dialog costs seconds that matter. SOS is cut from
v2 (brief §7), so the exception applies to nothing and is removed rather than left as a precedent someone
could reach for later. The strategy brief independently reinforces the universal form of the rule in §4.5
and §4.6: propose an action, always confirm before anything is written or sent.

### [deva] Carried — Journaling is pattern surfacing, not companionship

Lumi logs entries locally and may surface patterns that suggest a routine. It must never present
itself as a therapist, a mental health provider, an emotional companion, or a crisis detection system.

**Why:** a 2B on-device model cannot reliably recognize a real mental health crisis, and the
companion framing invites exactly the scrutiny a judge or investor applies hardest. It is a liability
boundary, not a positioning preference.

### [deva] Carried — Phone only, Kotlin only

No external hardware, no companion devices, no multi-device or swarm architecture. Single phone, single
user, single model instance. Kotlin is the app language.

# Part 2 — v2 decisions

### [deva] 2026-08-14 — Voice: native Android for the hackathon, sherpa-ONNX in Phase 8

v2 ships `SpeechRecognizer` and `TextToSpeech` for the Aug 22 build. The full offline sherpa-ONNX
stack — sherpa ASR and sherpa TTS, no Whisper — moves to Phase 8, behind a settings toggle so the
native path stays a supported fallback rather than something to revert to.

**Why:** PRD §3.6 and §4.2 specify sherpa-ONNX. But v1 already built that stack — Whisper C++ for STT,
Piper-VITS for TTS — and reverted it. v1's own debug report records the reason: too heavy for the target
hardware, seconds of transcription latency because the offline model transcribed in one batch after
recording stopped, and TTS that skipped words and restarted mid-sentence. Rebuilding it inside a
seven-day window, on the critical path, is the highest-risk move available. Native gets a working voice
loop into the baseline; sherpa becomes an upgrade with room to fail safely.

**Owner's position, recorded verbatim in intent:** rebuild the sherpa stack cleanly and fix what caused
the latency, no Whisper this time — but do native first and add sherpa in a further phase. This entry
implements the second half for the hackathon and commits the first half to Phase 8.

**What must be different when sherpa is built in Phase 8:**

1. Streaming ASR with partial results, not batch transcription after the recording ends.
2. A TTS queue that appends incoming chunks. v1 cancelled the speak job on every new chunk, which is
   what produced the word-skipping and the restarts.
3. Model size and thread count tuned against the target device before integration, not after.

**Known weakness of the native choice:** offline recognition depends on an installed language pack. If
it is missing, the app must say so plainly and fall back to typed input rather than appearing to hang.
This must be tested on the first day of Phase 6, not at the end.

### [deva] 2026-08-14 — Room for all persistence, dropping proto DataStore

Every entity in Design Spec §17 lives in Room. v1's proto DataStore stores for memory, schedules, and
the notes index are not carried forward.

**Why:** the §17 model has twelve entities with real query needs — filtering the audit log, finding
routines by trigger type, retrieving chunks by document. DataStore cannot query; it deserializes whole
messages. Two persistence systems in one app is worse than rewriting the persistence layer of the three
components being reused, especially since those components are being rewritten anyway.

**Cost accepted:** the reused memory, schedule, and notes code loses its persistence layer and gets a
new one. The `.proto` files are dropped.

### [deva] 2026-08-14 — Work split by layer, after a solo Phase 0

Dev A builds the scaffold, theme tokens, dependency injection, Room schema, and the core interfaces
alone. After those are committed, Dev A owns core, data, AI runtime, router, dispatcher, routines,
device actions, and background execution. Dev B owns the design system, screens, navigation, the
Glance widget, accessibility, and UI states.

**Why:** nothing in an empty repository can be parallelized before the scaffold and the contracts
exist — a pillar split would have both developers editing the same foundation files on day one. A layer
split also produces the fewest merge conflicts across two agent-driven branches, which matters more
than usual here because both developers are working through AI agents that touch many files per commit.

**Consequence to manage:** Dev B is blocked during Phase 0. Dev A commits the contracts as soon as they
compile, before finishing the rest of the phase, to shorten that window.

### [deva] 2026-08-14 — v1 is reference and reuse only; no file is copied across

Read v1, understand why a thing works, then write the v2 version clean with the cleanups and
optimizations it needs. No direct copy-paste.

**Why:** v1 is a fork of Google AI Edge Gallery with a chat-app information architecture. v2 is a
router-first automation platform. Dragging files across carries the old structure into a codebase whose
whole point is a different shape, and v1's own reconstruction notes describe several deliberate,
time-boxed hacks that should not survive into a production build.

**Note:** v1's reconstruction guide actively encouraged copying. That guidance is superseded for v2 by
the owner's instruction.

### [deva] 2026-08-14 — Package namespace is `com.lumi`, not v1's `com.lumi.app`

**Why:** Design Spec §36 specifies the flat layout. v2 is a fresh project, so there is no migration
cost, and keeping the `.app` segment would only exist to match files that are not being copied anyway.

### [deva] 2026-08-14 — Each developer keeps their own changelog and decisions file

Dev A maintains `changelog.md` and `decisions.md` on `main`. Dev B maintains `changelog_devb.md` and
`decisions_devb.md` on the `devb` branch. Both prefix every entry with `[deva]` or `[devb]`. Dev A folds
Dev B's entries into the main files at merge points, prefixes intact.

**Why:** two append-only logs written by two AI agents on two branches would conflict on nearly every
merge, and the conflicts would all be at the bottom of the same file. Separate files make merges
trivial and the prefixes preserve provenance once folded together.

**Cost accepted:** `changelog.md` on `main` is incomplete between merges. Acceptable, because it is a
record, not a coordination mechanism.

### [deva] 2026-08-14 — Dev B never pushes to `main`

Dev B commits to `devb` and pushes only `devb`. Merges into `main` happen when the owner says so.

**Why:** one person owns the state of `main` so nobody has to reason about what is in it. This is a
process rule, not a judgement about code quality.

### [deva] 2026-08-14 — Scope policy: cut from the bottom, never from the middle

If a phase slips more than half a day, drop the lowest remaining item in `todo.md`. Do not spread the
slip across every phase.

**Why:** seven days, two developers, and fourteen P0 and P1 items in Design Spec §41 do not fit. The
failure mode to avoid is fourteen features at 70%, none of which demo. A complete core loop — notes to
grounded answer, natural language to a routine that fires with the app closed, SOS — demos. Design Spec
§41 states the same rule; this entry commits to enforcing it against a real deadline.

### [deva] 2026-08-14 — Open question: attribution obligation from the Gallery fork

**Not yet decided.** v1 is a fork of Google AI Edge Gallery, Apache 2.0, and carries `LICENSE` and
`NOTICE` files. v2 is written fresh, but if any v2 code is derived closely enough from that fork —
most plausibly the LiteRT-LM model loading and lifecycle handling, which v1's own notes call "the
hardest-solved part" it deliberately preserved — the attribution obligation carries over.

**Resolve before reusing runtime code, in Phase 0.** The likely answer is to include `LICENSE` and
`NOTICE` regardless, since the cost is two files and the downside of getting it wrong in a judged,
publicly-shown project is real. Needs an owner decision, then a replacement entry here.

### [deva] 2026-08-14 — Branch workflow: `deva` and `devb`, neither pushes to `main`

Supersedes the "Dev B never pushes to `main`" entry above, which was correct but incomplete.

`main` is the integration branch. Dev A works on `deva` and pushes `deva`. Dev B works on `devb` and
pushes `devb`. **Neither agent pushes to `main`.** Dev A merges both branches into `main` when the owner
says so. The exception, for either developer, is an explicit instruction to push to `main` in that
specific instance — it does not carry forward.

**Why:** the earlier rule constrained only Dev B, which left Dev A's agent free to push to `main`
unprompted. Symmetry is the point: `main` advances only on a human decision, so its state is always
something a person chose.

### [deva] 2026-08-14 — Resolved: build against the LiteRT-LM Kotlin API, drop the Gallery fork entirely

Supersedes the open attribution question above. **No v2 code derives from Google AI Edge Gallery.**
Lumi v2 depends on the published LiteRT-LM Android artifact and nothing else from Google's samples.

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:<pinned version>")
```

**Why this closes the question:** LiteRT-LM ships a first-party, documented Kotlin API. The whole
surface v1's fork existed to wrap — model load, backend selection, conversation sessions, streaming,
system instructions, sampler config — is public, documented API:

```kotlin
val engine = Engine(EngineConfig(modelPath, backend = Backend.GPU(), cacheDir = ...))
engine.initialize()                                   // off the main thread
engine.createConversation(ConversationConfig(systemInstruction, samplerConfig)).use { conv ->
    conv.sendMessageAsync(contents).collect { … }      // Flow, preferred for coroutines
}
```

Writing our own thin wrapper over that is ordinary API use, not derivation from a sample app. There is
therefore no Apache 2.0 source-attribution obligation inherited from the Gallery, and no `NOTICE` to
propagate from it. Lumi picks its own licence.

**The distinction that resolves it:** depending on Google's Maven artifacts creates no
source-attribution obligation on our code — every Android app depends on dozens of Apache 2.0
libraries. Copying or adapting the Gallery's *source* is what would have created one. We are doing the
first and not the second.

**Separate obligation that does still apply:** the Gemma model itself is distributed under the Gemma
Terms of Use and its Prohibited Use Policy, not Apache 2.0. That applies regardless of how the app is
built, and it is a model-distribution question, not a code question. Read the terms for whichever
`.litertlm` build ships.

**Pin the version.** Google's docs use `latest.release`. Do not. Resolve it once and pin the exact
version in the version catalogue.

### [deva] 2026-08-14 — No model may require an access token to download

Every model Lumi ships or fetches must be reachable without a Hugging Face token, without accepting a
gate, and without an account.

**Why:** a token is a credential. Shipping one in an APK leaks it; asking a user for one makes the app
unusable for its actual target — rural users, field workers, elderly and low-tech-literacy users who
will not manage accounts or keys. It also breaks the demo the moment the token expires or rate-limits.

**What this rules in and out:**

| Model | Status | Consequence |
|---|---|---|
| Gemma 4 E2B IT | Ungated, Apache 2.0 | Primary model. Fetch the `.litertlm` build directly, no token |
| FunctionGemma | **Gated** | Ruled out. Cannot be the tool-calling model |
| EmbeddingGemma 300m | **Gated** on both `google/` and `litert-community/` | Ruled out as the embedder |

**Consequence for tool calling:** LiteRT-LM's native tool calling is model-dependent and Google's docs
cite FunctionGemma as the example. Since FunctionGemma is out, native tool calling must be tested
against Gemma 4 itself — which does have native tool-calling per its own release notes. If it does not
work through LiteRT-LM, fall back to a hand-rolled registry with JSON extraction, as v1 did.

### [deva] 2026-08-14 — The small router model is a bundled embedder, shared with RAG

Modes, routines, and every other task that does not need Gemma 4 are handled by a small on-device
embedding model **bundled in the APK as an asset**, not downloaded. The same model serves RAG
retrieval. One model, two jobs.

Routing runs in three tiers, cheapest first:

1. **Rules.** SOS phrases and exact device commands. Regex, zero latency, deterministic, no model.
2. **Embedding similarity.** Classify the fuzzy cases — routine, RAG query, journal entry, file
   request, plain chat — by cosine similarity against labelled example phrases per intent.
3. **Gemma 4.** Only for actual reasoning, generation, and ambiguity the first two tiers cannot settle.

**Why bundled rather than downloaded:** bundling removes the token question entirely — there is no
fetch, so there is nothing to gate. It also removes a first-run failure mode on the exact bad
connectivity the product is built for.

**Why one model for both jobs:** RAG needs an embedder regardless. Reusing it for routing costs no
extra APK weight, no extra memory, and no second inference stack. PRD §4.2 asks explicitly for this —
"reuse the main runtime rather than adding a second inference stack under time pressure" — and accepts
embedding similarity as a valid router.

**Candidate embedders, all ungated and tokenless:** the Universal Sentence Encoder `.tflite` via
MediaPipe `TextEmbedder`, which v1 shipped at ~5.9MB and is known to work; or a
sentence-transformers model such as `all-MiniLM-L6-v2` (Apache 2.0, ungated) through ONNX Runtime if
MediaPipe's `tasks-text` turns out to be deprecated alongside its LLM Inference API. Verify which in
Phase 1, then pin it.

**Why not a fine-tuned classifier:** it would need training data we do not have and a training pass we
have no time for. Embedding similarity against example phrases is editable by adding a phrase to a
list, which matters when the router is wrong at 2am on Aug 20.

### [deva] 2026-08-14 — Phase 0 foundation: toolchain and project shape

Versions are taken from v1's working configuration rather than chosen fresh, because v1 demonstrably
builds in this environment: AGP 8.13.0, Kotlin 2.2.0, Gradle 9.2.1, Compose BOM 2026.02.00, Hilt 2.58,
KSP 2.3.6. Room 2.8.4 is new — v1 had no Room. Every version is pinned exactly; nothing uses
`latest.release`.

- **minSdk 31, compileSdk and targetSdk 37, Java 17.** The plan originally said minSdk 26 and
  targetSdk 36. Raised to match v1, which shipped on 31. A device that can run Gemma 4 E2B locally is
  an Android 12+ device in practice, so supporting older releases would add compatibility surface for
  users who cannot run the product's core feature anyway. Java 17 is an upgrade from v1's Java 11.
- **KSP for both Room and Hilt, no kapt.** v1 used kapt for Hilt. KSP is faster, and build time is a
  real constraint on a seven-day schedule.
- **One `:app` module.** Multi-module would buy cleaner boundaries and slower iteration. Package
  boundaries per Design Spec §36 are enough discipline for two developers over one week.
- **`material-icons-extended` dropped.** Adding it for five navigation glyphs put 40MB of generated
  classes into the debug APK: 63MB total, 42MB in `classes.dex` alone. Switching to
  `material-icons-core` brought the debug APK to 30.5MB. The design system needs a purpose-drawn icon
  set anyway, so the extended pack was never going to survive.

### [deva] 2026-08-14 — Database: no destructive migrations, and no `User` table

**Migrations are mandatory.** `exportSchema` is on, `app/schemas/` is committed, and
`fallbackToDestructiveMigration` is deliberately absent. This database holds notes, journal entries,
and emergency contacts. A destructive migration deletes all of it and the user finds out by opening an
empty app. During development, uninstall rather than loosening the rule.

**No `User` table**, despite Design Spec §17 listing one. Lumi is single-user with no accounts and no
sign-in, so the table would hold exactly one row and add a join to every query that touched it.

**Enums are stored as names, not ordinals.** Reordering an enum would silently reinterpret every
existing row. Unknown names decode to a safe default rather than throwing, so a row written by a newer
build cannot crash an older one mid-session.

**Chunk embeddings live in their own table.** Retrieval scans every vector but needs no chunk text to
do it. Keeping the vector separate means a search reads ids and blobs only, instead of pulling the
whole corpus's text through memory on every query; text for the top matches is fetched afterwards in
one query. Both the document ingest and the routine save are single transactions — a half-indexed
document would silently return partial context, and a routine with triggers but no actions would arm
itself and then do nothing.

### [deva] 2026-08-14 — Model harness seam: one owner, serialised, session-optional

The `ModelHarness` interface is defined in Phase 0 even though Phase 1 implements it, so the router,
the capabilities, and the load UI can all be written against it in parallel.

Three properties are load-bearing:

1. **One owner.** Nothing else in the app touches the inference runtime. A second owner would
   eventually reload the model, and cold load costs real seconds.
2. **Serialised through a dedicated single thread.** The native runtime holds one loaded model and one
   conversation; two coroutines entering it concurrently is a native crash, not a catchable exception.
   A single-thread dispatcher is what prevents that, and it is an `Executors` one rather than
   `limitedParallelism`, which is still opt-in experimental API.
3. **Sessions are optional.** A null session means a one-shot inference with no history and no side
   effects. Background work — parsing a routine, extracting a reminder — must not appear in the user's
   chat or disturb its context. v1 needed exactly this and called it a "single silent inference".

`prepare()` never throws; failures land in observable state so every caller sees the same truth and can
degrade to a non-model path.

### [deva] 2026-08-15 — Resolved: Lumi is Apache 2.0

The open licence question from Phase 0 is settled by the owner: **Apache 2.0**, `LICENSE` in the
repository root. Nothing was inherited from the v1 Gallery fork, so this was a free choice rather than
a constrained one. The Gemma weights remain under their own terms regardless — that is a
model-distribution obligation, not a code one.

---

# Part 3 — Dev B, folded in at merge

Written by Dev B on the `devb` branch and folded into this file when `devb` merged into `main` on
2026-08-15, prefixes intact. Dev B's staging file starts empty again from that point.

### [devb] 2026-08-15 — `DESIGN_LANGUAGE.md` supersedes the Design Specification's UI sections

The owner ruled that `Lumi — Design Specification.docx` is **mostly deprecated for UI**, because
`DESIGN_LANGUAGE.md` has drifted from it deliberately. Every UI component, optimization, and
presentation-layer decision resolves against `DESIGN_LANGUAGE.md`. The docx stays authoritative for
non-UI matters: architecture, data model, capability contracts, testing.

**Why this needs an entry:** the entries above describe the palette as "rice-paper white, sumi
near-black, one muted sumi-olive accent". That palette no longer exists. The accent is cyan Slime Blue
`#4DB6AC`; surfaces are `#151515`/`#20201F` dark and `#FAFBF7`/`#E4E7DF` light. Anyone reading the older
entries without this one would build to a dead spec.

**Consequence:** the docx section numbers cited throughout `todo.md` still point at the docx. Read them
as intent — "a component library exists", "loading states name the real operation" — and take concrete
values from `DESIGN_LANGUAGE.md`.

### [devb] 2026-08-15 — Light-mode `onPrimary` is `#171717`, not `#FAFBF7`

`DESIGN_LANGUAGE.md` §2's light table specifies `On Primary` = `#FAFBF7`. That single cell is
overridden; everything else in §2 stands, including `#4DB6AC` as the only accent in both modes.

**Why:** `#FAFBF7` on `#4DB6AC` measures **2.35:1**. WCAG AA needs 4.5:1 for text and 3:1 for a
graphical object, and contrast compliance is a hard rule. `#171717` on `#4DB6AC` measures **7.35:1**.
The accent hue is untouched, so the mascot gradient and the one-accent rule are unaffected. This is the
smallest deviation that clears the rule.

**Reverse it only by** changing the accent hue for light mode, which is a larger spec change needing an
owner decision.

### [devb] 2026-08-15 — One motion gate, not three checks

Reduced motion, battery saver, and lifecycle state collapse into a single `LocalMotionEnabled` provided
at theme level, beside the existing `LocalReducedMotion`.

**Why:** a caller required to check three conditions will eventually check two. All three mean the same
thing to a composable — do not animate. Below `Lifecycle.State.STARTED` an infinite loop burns frames
nobody sees; in battery saver an infinite animation is exactly the work the user is trying to avoid.

**Both flags are kept.** `LocalReducedMotion` answers "the user asked for stillness" and also governs
non-animation choices. `LocalMotionEnabled` answers "run this loop right now". When it is false, render
the resting pose — a mascot stopped mid-squash looks broken; one at rest looks deliberate.

### [devb] 2026-08-15 — Dev A's baseline UI is amended in place, never re-implemented

`LumiBlob`, `LumiInput`, `HomeScreen`, and the sidebars are changed by the smallest diff that
satisfies `DESIGN_LANGUAGE.md`, and new components adopt their style rather than introducing a second.

**Why:** a rewrite of a working component is unreviewable — the original author cannot tell a bug fix
from a preference, so every line becomes a negotiation. A small diff against a file they wrote is
legible in a minute.

**Recorded because it cost work.** A full `LumiBlob` rewrite (+332/-226) was written and reverted under
this rule. It compiled and fixed four real defects, but it replaced the whole file. Those defects were
then re-approached as a targeted diff. One item was deliberately **not** carried across and is still
open: the gradient radius is a fixed `120f` rather than computed from density, so the mascot's gloss
lands differently on every screen density. That is a real portability bug and should be reopened.

### [devb] 2026-08-15 — Spacing token *values* changed, which moved existing layout

`DESIGN_LANGUAGE.md` §5 is md 16 / lg 24 / xl 32 / xxl 48. The shipped tokens were md 12 / lg 16 /
xl 24 / xxl 32 under the same names, so "always use tokens, never raw dp" could not be obeyed and be
correct at once. The tokens now match §5.

**Consequence, stated plainly:** `EmptyState.kt` was the only reader, so its padding changed —
visibly. Made because §5 is authoritative, not because the old spacing looked wrong. `xxxl`, `screen`,
and `gutter` were removed as unreferenced and undefined by §5; `hairline` moved to `LumiSize` at its
real used value of 0.5dp rather than the declared 1dp.

### [devb] 2026-08-15 — Nine sidebar destinations is the intended set, not a shortfall

§8 numbers its list to 14 and elides items 6 through 13. The code has nine, and the owner confirmed
nine are correct for the baseline scaffold — so §8's numbering is aspirational, not a specification.

**Why record it:** a later reader counting nine against fourteen will assume five screens were dropped.
They were never named. If destinations are added, §8 should name them at the same time.

### [devb] 2026-08-15 — Glass is alpha and a hairline; there is no blur

§4 describes glassmorphism as `surfaceVariant` at partial alpha with a 0.5dp border, "blurring the
underlying content softly". The first half is implemented; **the blur is not.**

**Why:** Compose has no backdrop blur. `Modifier.blur` blurs a composable's own content, not what is
behind it. Blurring the background needs either a platform window-blur API, which does not apply to a
Compose surface inside one window, or capturing the background to a layer and blurring it — expensive
per frame and fragile across densities. Alpha plus the hairline reads as frosted because the palette is
low-contrast to begin with. Revisit if a backdrop API lands; do not fake it by screenshotting.

**Left deliberately alone, needing an owner call:** both sidebars use opaque `surface` rather than a
translucent `surfaceVariant`, and the attachment sheet keeps 24dp top corners rather than §4's square
"Sidebars & Modals" rule.

### [devb] 2026-08-15 — The GPU build of the model, not the generic one

The artefact is `gemma-4-E2B-it-gpu.litertlm` (2,008,432,640 bytes), not the generic build
(2,588,147,712 bytes). ~580MB smaller, and it matches the `Backend.GPU()` the harness requests.

**Why record it:** the two files sit in the same repository and look interchangeable; choosing the
generic one adds 580MB to every download for no gain. Vendor-specific builds exist and are not used —
the test device is a MediaTek mt6855 with no matching build, and a per-SoC matrix is a Phase 8 problem.

Pinned by name, size, and SHA-256 in `GemmaModel`, so a silent upstream re-push fails loudly rather
than loading a different file. Whether this build actually accepts image and audio input is still a
device test, not an assumption.

### [devb] 2026-08-15 — The model lives in `filesDir`, verified by size and digest, not existence

Three connected choices, recorded together because they are the no-redownload guarantee and each is
only meaningful with the other two.

1. **`filesDir`, not `cacheDir`, not external storage.** `cacheDir` is what Android deletes first under
   storage pressure, which would silently trigger a 1.9GB re-download. External storage invites the user
   to delete the weights from a file manager. `filesDir` also survives `installDebug` over the same
   signing key — which is why the dev loop must never `adb uninstall`.
2. **`isReady` is size plus a recorded SHA-256, not `exists()`.** v1 checked existence only, its biggest
   correctness hole: a file truncated by a crash mid-rename passes `exists()` and then fails inside the
   native loader, reading to the user as "the app is broken" rather than "retry the download". The digest
   is verified once when the bytes land and recorded, so a cold start never re-hashes 1.9GB.
3. **The partial download survives a retry.** v1 wiped resume state each time, so a flaky connection
   meant restarting 1.9GB. v2 resumes from the `.part` file with a `Range` request; if the server ignores
   `Range` and replies 200, it restarts rather than concatenating.

**Reverse only together and only for cause.** Dropping the digest alone reintroduces v1's hole; moving
to `cacheDir` alone reintroduces silent re-downloads.

### [devb] 2026-08-15 — The first model download is consent-gated; a present model is not

The setup screen waits for consent when the model is absent and starts immediately when it is on disk.

**Why:** 1.9GB is minutes of waiting and real money on a metered plan, so it is never started without
asking. But prompting a user to "download" something they already have is the worse bug — a returning
user should see a load bar, not a download prompt.

**The gate lives in `MainActivity`, above `LumiApp` and its NavHost.** Putting it in the nav graph would
let the drawer and routes appear before the model exists, making "did the model load" a navigation
question rather than a lifecycle one.

**`Unavailable` is split into recoverable and not,** decided in the downloader from HTTP status and
preconditions rather than guessed in the UI. A dropped connection offers resume; a device that cannot
run the model is told so honestly, with the rest of the app still usable.





---

# Part 4 — After the devb merge

### [deva] 2026-08-15 — `material-icons-extended` stays, superseding the Phase 0 entry that dropped it

The Phase 0 entry above dropped `material-icons-extended` because it put 40MB of generated classes into
the debug APK — 63MB total, 42MB in one dex file — against `material-icons-core` at 30.5MB. **The owner
has ruled that it stays.** Lumi's UI needs the icons.

**Why the reversal is right:** `LumiApp` and `HomeScreen` between them use `NoteAlt`, `Event`, `Book`,
`Tune`, `FindInPage`, `History`, `GraphicEq`, `CameraAlt`, `PhotoLibrary`, `UploadFile`, and `Public`.
None are in `material-icons-core`, so the alternative was hand-drawing eleven glyphs or shipping a
navigation drawer with wrong icons. R8 strips the unused ones from release, so the cost is a debug-build
and build-time cost rather than a shipped one. Final APK size is not the constraint for this build;
runtime performance is.

**Superseded on the decision, not on the measurements** — those numbers were real, and anyone reopening
this on size grounds should know the cost is debug-only.

**Now pinned.** It was declared as a bare string with no version, resolving through the Compose BOM — the
only unpinned dependency in the project, against an explicit rule that nothing floats. It is a
version-catalogue entry like its siblings as of this entry.

### [deva] 2026-08-15 — The GPU failed because of the audio backend and the cache directory

`Engine.initialize()` failed on GPU with `NOT_FOUND: TF_LITE_PREFILL_DECODE not found in the model`, and
then — after the model artefact was corrected — kept failing on GPU while succeeding on CPU. Two causes,
both differences from v1's working configuration:

1. **A GPU audio backend fails engine creation.** v1's `EngineConfig` carries the comment "must be CPU"
   beside its audio backend. v2 was requesting `Backend.GPU()` for text, vision, *and* audio.
2. **A `cacheDir` for an app-internal model path fails it too.** v1 passes a cache directory only for
   models loaded from `/data/local/tmp` and null otherwise. v2 was always passing one.

**Both vision and audio backends are now null** until the multimodal path actually exists. Configuring
backends for capabilities nothing in the app exercises bought nothing but this failure. Phase 4 sets
them when image input lands — vision on GPU, audio on CPU, following v1.

**Verified:** GPU with speculative decoding loads in 14-21s on a Samsung SM-M356B (Exynos 1380, Mali).

**Also adopted from v1:** `Capabilities(modelPath).hasSpeculativeDecodingSupport()`, so the flag is only
set when the model file reports supporting it rather than set blind. This build reports true.

**And the swallowed exception is gone.** `loadEngine` caught `Throwable` and discarded it, which made
this the one failure in the app nobody could diagnose — the user saw "could not start the model" and the
log said nothing. Every attempt now logs its cause. That, not the config, is why this took a device
session to find.

### [deva] 2026-08-15 — CPU stays the default backend even though GPU is faster

GPU is several times faster where it works, and the owner's device runs it well. CPU remains the default.

**Why:** a GPU backend that fails takes tens of seconds to fail, on every launch, and lands the user on
an error screen. CPU is slower but starts everywhere. The people this product is for — the ones on
mid-range hardware with no support to call — are exactly the ones a GPU-by-default choice would strand.

**Auto exists for the middle ground** and tries GPU first, so a user who never opens settings on capable
hardware still gets the fast path once they choose it. An explicit GPU choice that fails says so and
points at the setting, rather than silently falling back — a user who asked for GPU deserves to know it
did not happen.

### [deva] 2026-08-15 — Replies are rendered as markdown, using the library v1 shipped

`richtext-commonmark` and `richtext-ui-material3`, pinned at v1's `1.0.0-alpha02`.

**Why a renderer at all:** a 2B instruction-tuned model emits markdown whether or not it is asked to.
A plain `Text` showed users `**bold**`, backticks, and `1.` markers as literal characters — which reads
as broken output rather than as plain text, and is worse than either extreme.

**Why an alpha dependency:** it is what v1 shipped and it works. The alternative is writing a markdown
parser during a seven-day build.

**Code is the one place the type scale is broken.** The design language puts prose in serif and reserves
sans for labels; neither can render code, because column alignment and telling `l` from `1` from `I` is
the entire point of a monospace face. So code blocks and inline code go monospace, and nothing else does.

### [deva] 2026-08-15 — Sampling changes apply to the next conversation, not the open one

The runtime fixes sampling when a `Conversation` is created, so the settings sliders cannot affect a chat
already in progress. The screen says so plainly.

**Why not rebuild the session on change:** that would discard the model's memory of the conversation the
user is in the middle of. A setting that takes effect on the next conversation is a mild surprise; a
setting that silently wipes your chat context is a bad one. Changing the *backend* does reload — it has
to — and that row warns before it does.

### [deva] 2026-08-15 — Token counts are approximate, and labelled as such

Reply metrics show `~14 tok/s`, with the tilde carrying real meaning: there is no tokenizer on this side
of the JNI boundary, so the count is derived from character length at four characters per token.

**Why show it at all:** the decode rate is the single most useful number for judging whether a backend
or a parameter change helped, and an approximate rate answers that question. **Why the tilde:** the
alternative is a precise-looking number that is quietly wrong, which is worse than an honest estimate.

**Measured from the first token, not from the send.** Prefill on a 2B model is a second or more, and
folding it into the rate would report a figure unrelated to how fast text actually appears.
Time-to-first-token is reported separately, and only when it is slow enough to be the story.

### [deva] 2026-08-15 — The app is renamed Lumi

Everything user-facing and everything in code: `com.lumi` namespace and applicationId, `LumiApp`,
`LumiBlob`, `LumiDatabase`, `LumiPersona` and the rest, `lumi.db`, `lumi_settings`, the Gradle project
name, the app label, both spec documents, and all four project logs.

**Three things were deliberately not renamed:**

1. **References to Trace v1 and Trace-beta.** That is the name of a real checkout on disk and of the
   project this one descends from. Renaming it would make the reuse inventory in `todo.md` point at a
   directory nobody has, and would rewrite history that is still being read.
2. **The word "trace" where it is not the product** — "stack traces" in the audit-log documentation, and
   the debugging workflow in the agent operating procedure. A blanket search-and-replace turned
   "Reproduce → Trace → Identify root cause" into "Reproduce → Lumi", which is how a rename quietly
   corrupts prose.
3. **`com.trace.app`**, v1's installed package on the test device. Left alone; it is a different app.

**The rename forced a re-download.** A new `applicationId` means a new app-private data directory, so the
2.6GB model does not carry over. It was already gone — the instrumented test run had reinstalled over the
app and wiped its data, which is the hazard `decisions.md` records about never using `adb uninstall` in
the dev loop. Worth stating plainly: **any change to `applicationId` costs every user their model.**

### [deva] 2026-08-15 — Space required for the model is ~3.5GB, not 2.6GB

`ModelDownloader` demanded 2.6GB plus 256MB of slack. The real figure is larger, and the gap was a real
bug: a phone with 3GB free would pass the check, spend 2.6GB of the user's data, and then have no room to
load what it fetched.

**Why:** the harness passes `cacheDir = null` — it has to, since a cache directory stopped the model
loading on GPU at all — so the runtime writes its compilation caches beside the model instead. Measured
on the test device: `xnnpack_cache_*` at ~788MB, an MTP drafter cache at ~44MB, a program cache at ~13MB,
and some small `.bin` files. Roughly 850MB on top of 2.59GB of weights.

Headroom is now 1.1GB, and the out-of-space message states the real total rather than the download size.

**Two related fixes came out of the same measurement.** `ModelStore.clear()` deleted three named files
and left ~600MB of generated caches orphaned with nothing that would ever remove them — it now deletes
the directory. And `bytesOnDisk()` reports weights plus caches together, shown in Settings, because a
user auditing why the app holds 3.4GB deserves the real number rather than the one they agreed to.

---

# Part 5 — The strategy brief

`Lumi_Strategy_Brief.docx.PDF` (Aug 2026) repositions the product and reverses three decisions recorded
above. Where it disagrees with an earlier entry, it wins, and the entry below says so explicitly rather
than leaving two contradictory records in one file.

The reframing itself: Lumi is a **voice-first, privacy-first automation layer for working professionals
who cannot legally or practically send data to cloud AI** — doctors, lawyers, business professionals —
with students secondary. Judging weights are Gemma Integration 30%, Innovation & Impact 30%,
Functionality 20%, Presentation 20%. Two thirds of the score is *why this needs Gemma on-device*, not
how many features exist.

### [deva] 2026-08-15 — Reversed: Sherpa-ONNX moves into the critical path

The entry above defers Sherpa to Phase 8 and ships native `SpeechRecognizer` for the hackathon, on the
grounds that v1 built the offline stack and reverted it for latency and glitching. **The brief reverses
this, and its reasoning is better than mine.**

Sherpa performs 100% of audio→text as streaming ASR during a meeting, which means **Gemma never touches
raw audio**. That turns Gemma's 30-second audio-clip cap from a blocking constraint into a non-issue —
it is designed around rather than fought. My deferral optimised for avoiding v1's latency bug and missed
that the architecture removes the constraint entirely.

**What I got right and keep:** native ASR was the correct call for *voice chat*, where a 2-second turn is
the whole interaction. It is the wrong call for a 45-60 minute continuous session, which is what call
mode needs — native recognition is not built for that duration and would have been throwaway scaffolding.

**Sequencing, on the owner's call:** Sherpa directly, tested on the real device on day one rather than at
the end. v1's revert is a real warning, and the mitigation is measuring early, not deferring.

**The three things that must differ from v1's attempt** still stand and are now urgent rather than
theoretical: streaming partial results instead of batch-after-stop; a TTS queue that appends instead of
cancelling per chunk; model size and thread count tuned to the device before integration.

### [deva] 2026-08-15 — Call mode captures the microphone, not phone-call audio

The brief says Lumi "joins/listens during a call". Confirmed with the owner: **device microphone** — in
person, or a call on speakerphone.

**Why this had to be asked rather than assumed.** Android has blocked third-party apps from capturing
remote-party phone audio since Android 10; only the default dialer holding a system-signature permission
can do it, and the Accessibility-service loophole was closed as well. Had the answer been "both sides of
a real phone call", §4.1 would not have been buildable as written and the feature would have needed
reframing before any code was written. Microphone capture needs only `RECORD_AUDIO` and works.

`MediaProjection` audio capture remains available if device-playback capture (Meet, Zoom, Teams) is
wanted later. Its persistent system recording notification would arguably *help* the consent story the
brief raises against the recording wearables in §3.1.

### [deva] 2026-08-15 — Native function-calling is now required, not evaluated

An entry above says to test LiteRT-LM's built-in tool calling against Gemma 4 and fall back to a
hand-rolled JSON registry as v1 did, treating the two as comparable options. **The brief makes the native
path the requirement**, and supplies the number I did not have: Gemma 3 scored 6.6% on the τ²-bench
agentic tool-use benchmark; Gemma 4 scores 86.4%, through a dedicated architecture of six special tokens
rather than prompt-engineered JSON extraction.

That gap is the concrete, demoable answer to "deeper Gemma integration" — 30% of the score. A
prompt-hacked JSON parser would work and would be worth nothing to a judge.

The hand-rolled registry survives only as a genuine last resort if the native path proves broken on
device, and choosing it would need a new entry here explaining what failed.

### [deva] 2026-08-15 — SOS is cut

Per brief §7. It does not fit the working-professional privacy/automation positioning and would split the
pitch's identity in a three-minute demo.

**Consequences, executed rather than noted:** the Phase 5 SOS section leaves `todo.md`; the
`EmergencyContact` entity, DAO, and table leave the Room schema; and the SOS carve-out is removed from the
carried-forward v1 decision on confirmation-gating, because that exception no longer applies to anything
that exists. The rule it excepted — no autonomous action without confirmation — stands and is now
universal, which the brief independently reinforces in §4.5 and §4.6.

Nothing is lost but plan: SOS was never built in v2.

### [deva] 2026-08-15 — Do not fine-tune Gemma 4 multimodal before the demo

Brief §5.3, adopted as a hard constraint. The official Gemma 4 vision-tower export path
(`litert_torch/model_ext/gemma4/vision_exportable.py`) is a documented stub raising
`NotImplementedError`, and a July 2026 Qualcomm × Google LiteRT team found that even a cleanly fine-tuned,
cleanly exported Gemma 4 E2B failed at *inference* time with an undocumented Jinja chat-template error.

**Why that specific failure mode matters:** it does not appear when the model loads. It appears when
inference runs — mid-demo. Use Gemma 4 as shipped, including its built-in vision and audio.

This is also why the multimodality instrumented test is worth keeping rather than deleting now that the
brief asserts §5.2's native multimodal architecture: the test checks the *as-shipped* build on the actual
device, which is precisely what this decision depends on.

### [deva] 2026-08-15 — Correction: EmbeddingGemma is gated on Hugging Face, not on Google's CDN

An entry above rules EmbeddingGemma out as gated, alongside FunctionGemma. **Half right, and the half
that was wrong blocked the better embedder for no reason.**

Hugging Face does gate it — `litert-community/embeddinggemma-300m` requires accepting conditions, and
Google's own Cloud documentation says an `HF_TOKEN` is needed. But MediaPipe serves the `.task` file from
`storage.googleapis.com` with no gate at all: verified HTTP 200, no `Authorization` header, 183,816,181
bytes, and HTTP 206 on a range request so it resumes like the main model does. The MD5 in the CDN's own
`x-goog-hash` header matched the downloaded bytes, which is what makes the pinned SHA-256 trustworthy.

The no-token rule is satisfied, so EmbeddingGemma is now the embedder. FunctionGemma remains ruled out —
its gating is not worked around by this, and the native tool-calling decision above means Gemma 4 does
that job anyway.

**Why not `all-MiniLM-L6-v2` through ONNX Runtime**, the obvious 23MB alternative: MediaPipe tokenises
internally, while MiniLM would mean hand-writing a WordPiece tokeniser, mean pooling, and L2
normalisation in Kotlin. A subtly wrong tokeniser does not crash — it produces plausible embeddings that
retrieve slightly badly, which is close to undiagnosable. Not a risk worth 150MB with six days left.

**The cost, stated plainly:** 180MB fetched separately, and Google measures 200ms per embed on an S26
Ultra, so expect two to three times that here. `Embedder` is an interface precisely so Universal Sentence
Encoder — 6MB, 10ms, weaker retrieval — is one class away if that proves unacceptable.

### [deva] 2026-08-16 — Measured: vision and audio both work. My first result was wrong.

PRD §5's top risk, answered on a Samsung SM-M356B by instrumented test. **Both image and audio input are
accepted.** The test draws a solid blue square, asks what colour it is, and Gemma replies "Blue" — so the
image is genuinely being seen, not merely tolerated.

**I recorded the opposite an hour earlier and it was my bug, not the model's.** The first run failed inside
`nativeSendMessage` with `INTERNAL: Failed to invoke the compiled model`, and I wrote it up as a build
limitation matching brief §5.3's warning about inference-time multimodal failures. That was wrong, and the
owner was right to challenge it on the grounds that v1's vision feature worked.

**The actual cause: `maxNumTokens = 1024` in the test.** An image expands into hundreds of tokens on top of
the prompt, so the context overflowed and the runtime reported it as a generic invoke failure. v1 runs
`maxNumTokens = 4096`; raising the test to match fixed it immediately.

**Two things I mis-diagnosed on the way, worth recording so nobody repeats them:**

1. **The backend was never the problem.** I suspected `visionBackend` and switched GPU→CPU based on v1's
   `DEFAULT_VISION_ACCELERATOR = Accelerator.CPU` and its `vision_encoder.xnnpack_cache_*` files. CPU
   failed too, at 1024 tokens. Vision is fine on either; v1's "must be GPU for Gemma 3n" comment refers to
   Gemma 3n and does not apply to Gemma 4.
2. **v1 and Lumi run the byte-identical artefact.** v1's allowlist advertises 3,136,226,711 bytes but the
   file on disk is 2,588,147,712 with SHA-256 `181938105e…a63c` — the same digest Lumi pins. The allowlist
   figure is stale metadata, so the size difference I first treated as a lead was a red herring.

**Consequences:**

- **Image input is demoable**, and §5.2's one-model-for-text-vision-audio claim holds in practice.
- **Any engine that accepts images needs a real context budget.** 1024 tokens is enough for text and not
  for an image. When the vision path is built, size the window for the image plus the prompt plus the reply,
  and treat a bare invoke failure as a possible overflow rather than a capability limit.
- Brief §5.3's warning still stands for *fine-tuned, custom-exported* models. It does not apply to
  as-shipped Gemma 4, which is what §5.3 tells us to use anyway.
- Audio acceptance still does not change the ASR decision: Gemma's 30-second clip cap makes it unusable for
  a 45-60 minute meeting regardless, so Sherpa keeps 100% of call-mode speech-to-text.

**Process note, since this is the second time a swallowed or mis-read error cost real time:** the test was
written to record rather than assert, which is why a wrong finding surfaced as a log line I could re-run
instead of a red build I might have "fixed" by weakening the test. That part worked. What failed was my
willingness to accept a plausible external explanation — the brief predicted this exact failure shape, and
that made me stop looking. A prediction matching the symptom is not a diagnosis.

### [deva] 2026-08-16 — Two test-infrastructure gaps found while proving the migration

Both worth recording because each produced a failure that looked like a different bug.

**Exported schemas were not on the androidTest classpath.** `MigrationTestHelper` reads them from
androidTest *assets*, not from `app/schemas/`, so the migration test failed with
`FileNotFoundException: Missing file: com.lumi.data.local.LumiDatabase/1.json` — which reads as "the schema
was never exported" when in fact it was committed all along. Fixed by pointing the androidTest asset
source set at `$projectDir/schemas`.

**kotlinx-serialization was pinned a minor version too low.** Room 2.8.4's migration bundle is compiled
against 1.8.1; `lifecycle-viewmodel-savedstate` pulls 1.7.3, and AGP's consistent resolution pins the
androidTest classpath to whatever the main classpath resolved. The mismatch is invisible at compile time
and surfaces only when a schema is deserialised, as `AbstractMethodError` on a generated serializer — which
reads as a broken migration. Fixed with a version constraint rather than a dependency, so nothing new is
added to the app.

**Why this matters beyond one test:** the no-destructive-migration rule is only worth stating if migrations
are actually executed against real prior-version data. Until now nothing verified that, so the rule was a
comment. It is now enforced by a test that creates a v1 database, migrates it, and asserts the surviving
tables still hold their rows.
