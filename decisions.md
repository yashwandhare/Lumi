# Trace v2 — Decisions

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

Ingestion happens when the user attaches a file in chat or selects one directly. Trace never performs a
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

### [deva] Carried — SOS bare-keyword tier skips confirmation, deliberately

A bare "sos" typed or spoken fires the flashlight signal, siren, and emergency SMS with no confirmation
dialog. This is an explicit, knowing exception to the confirmation rule above.

**Why:** the bare keyword *is* the user's deliberate confirmation. A dialog in a flood or on an unsafe
street adds friction exactly when seconds matter.

**Guard rails that replace the dialog:** the entire normalized input must be an SOS phrase, so ordinary
chat can never match; word-boundary matching means "society" and "so sorry" are impossible matches; the
keyword embedded in longer text gets a cancellable countdown instead of instant fire; a model-initiated
activation also gets a countdown; and a full-screen stop control, a notification stop action, and a
spoken cancel all end it immediately.

### [deva] Carried — Journaling is pattern surfacing, not companionship

Trace logs entries locally and may surface patterns that suggest a routine. It must never present
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

### [deva] 2026-08-14 — Package namespace is `com.trace`, not v1's `com.trace.app`

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

