# Lumi v2 — Changelog (Dev B)

Staging area only. Dev B appends here on the `devb` branch; Dev A folds the entries into `changelog.md`
at each merge, prefixes intact, and empties this file.

**`changelog.md` is the complete record.** Read that one. This file holds only what has not been folded
in yet — if it is empty, everything is already in the main log.

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
- [devb] 2026-08-18 — Phase 3 data model: the `reminders` table (schema v3), one table for
  reminders and todos alike, split only by a kind column. `dueAtMs` is the sole trigger for a
  reminder — "remind me at 6" stays a plain row and never becomes a routine graph. Every
  status change is a conditional `UPDATE` returning the rows touched, so a retried worker can
  never notify twice and a dismissal wins any race with firing. Migration 2→3 is additive and
  covered, along with the DAO invariants, by new instrumented tests.
- [devb] 2026-08-16 — Whisper base.en (int8) replaces the 20M streaming zipformer as the
  recognition model — on-device testing showed the old model could not reliably transcribe
  everyday sentences, and the owner ruled recognition quality cannot be compromised. Download is
  ~160MB on first voice use (was ~41MB). Latency is preserved by Silero-VAD segmentation: each
  utterance is recognised the moment a pause closes it, off the recording thread, so the live
  transcript grows while you speak and the final text lands within roughly one short decode of
  you stopping.
- [devb] 2026-08-16 — Chat now dispatches through the capability registry like every other
  capability — one dispatch path for typed, spoken, and widget input alike. Conversation
  persistence and the per-turn audit entry stay with the chat view model because they need the
  conversation id the capability contract does not carry; the split is flagged for Phase 3.
- [devb] 2026-08-16 — Interpreted-intent confirmation: consequence-bearing routes (device
  settings, file, routine, reminder, to-do, search, mail) pause at a dialog that names the
  understood action and quotes the sentence that produced it, then acts only on an explicit
  yes. Ambiguous input asks a clarifying question instead of guessing — guessing is the worse
  behaviour for a product that can flip settings and fetch mail.
- [devb] 2026-08-16 — Voice session surface: push-to-talk from the composer mic button, with
  distinct Listening / Thinking / Speaking states anchored on the mascot, a live transcript
  that updates as words settle, and one stop button per phase. First mic tap requests the
  microphone permission inline; a denial shows one plain dialog offering typed input, not a
  re-ask on every tap. Model download progress shows in the ASR state, so the button is never
  live-looking and inert.
- [devb] 2026-08-16 — Sherpa-ONNX streaming recognition engine wired in (`SherpaAsrEngine`,
  committed `cd23b68`): partial transcripts while speaking, endpoint-silence detection,
  one-session lock, and the recognition model downloaded and digest-verified through the same
  store as the generative model. Recognition is audited as on-device.
- [devb] 2026-08-16 — Network-intent gate (committed `cd7dbe0`): one chokepoint for web search
  and mail fetch. The gate checks each feature's opt-in — both default off — and writes the
  audit event *before* any request leaves the device; a refusal is recorded as SKIPPED with
  plain-language recovery. A third network feature cannot exist without changing the closed
  `NetworkFeature` enum, so the privacy audit stays enumerable.
- [devb] 2026-08-16 — Capability dispatcher and registry (committed `cd7dbe0`): every routed
  intent dispatches through one map keyed by `CapabilityId`, built from DI-bound
  `Capability` implementations. Unknown routes return an honest failure, duplicate ids fail
  fast at construction. JVM tests pin the registry dispatch, pre-request gate enforcement,
  and the unknown-route path (committed `97579a3`).
- [devb] 2026-08-16 — Network feature settings (committed `cd7dbe0`): per-feature opt-in
  persisted in the existing `SettingsStore`, plus a kill switch that turns both features off
  at once.
- [devb] 2026-08-16 — Router unit tests: normalizer, rules, similarity, and composed-router suites
  (32 tests, plain JVM) covering the "add" collisions, device slots, marker tie-breaks, the
  ambiguity path, the chat fallback, and both degraded-embedder fallbacks. All pass;
  `compileDebugKotlin` clean at zero warnings.
- [devb] 2026-08-16 — Component library completed for the Phase 1 gate: `LumiCard` and
  `LumiCardRow` (shared card surface with hairline edge), `LumiChip` (pill chip with icon and
  remove slots, selection carried by fill plus weight rather than colour alone), `LumiDialog`
  (one restyled `AlertDialog` for every Phase 3-5 confirmation), and `LumiSlider` (single
  accent-tracked slider used by settings).

### Fixed
- [devb] 2026-08-16 — Voice replies to spoken turns are read without skipping: streamed reply
  fragments append to the TTS queue (`QUEUE_ADD`) and buffer on sentence boundaries; the speak
  job is never cancelled by the next chunk (v1 bug 2).
- [devb] 2026-08-16 — Cancel-on-send (v1 bug 1): a typed send while the mic is live cancels the
  recording loop cleanly instead of leaving it running in the background.
- [devb] 2026-08-16 — TTS speaks only turns whose origin is `VOICE`; a typed turn's reply is
  text-only. Both v1 bugs tracked in `decisions_devb.md`.
- [devb] 2026-08-16 — Tier-1 rule patterns used `Regex.matches`, which anchors at both ends;
  sentences longer than the pattern never matched, so reminders, files, search, mail, and
  device commands silently fell through to chat. Rules now use `containsMatchIn` against their
  `^`-anchored patterns.

### Changed
- [devb] 2026-08-16 — Settings sliders now draw through the shared `LumiSlider` instead of a
  stock Material `Slider`, so the parameter screen matches the rest of the design system.
- [devb] 2026-08-16 — Similarity-tier marker now resolves a near-tie in its group's favour
  instead of overriding the best match outright: the marker group wins only when it clears the
  threshold and sits within the ambiguity gap of the top score.
