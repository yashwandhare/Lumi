# Trace v2 — Changelog

Major changes only. This is a record of what changed in the product, written for a person catching up —
not a mirror of the git log.

## How to use this file

- Newest entries at the top, under `## Unreleased`.
- Prefix every entry with the author: `[deva]` or `[devb]`. Dev B keeps entries in `changelog_devb.md`
  on the `devb` branch; Dev A folds them into this file at merge, prefixes intact.
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

### Added

- `[deva]` Planning documents for the v2 rebuild: `todo.md` with the full phase plan and Dev A / Dev B
  split, `decisions.md` seeded with the v1 rules that still bind plus this session's calls,
  `changelog.md`, and `for_devb.md` as Dev B's self-contained brief.

### Changed

- `[deva]` No model may require an access token, a gate, or an account to download. Gemma 4 E2B IT is
  ungated and fetched directly. FunctionGemma and EmbeddingGemma are both gated and therefore ruled
  out. See `decisions.md`.
- `[deva]` Router is now three tiers: regex rules, then embedding similarity against labelled example
  phrases, then Gemma 4 only for what the first two cannot settle.
- `[deva]` The small router model is a bundled APK asset shared with RAG retrieval — one embedder,
  two jobs, no download.
- `[deva]` Branch workflow: Dev A works on `deva`, Dev B works on `devb`, and neither agent pushes to
  `main`. `main` advances only when the owner says so.
- `[deva]` Trace v2 builds against the published LiteRT-LM Kotlin API and derives no code from Google
  AI Edge Gallery, so no attribution is inherited from that fork. The Gemma model's own terms still
  apply separately. See `decisions.md`.
- `[deva]` Voice stack targets native Android `SpeechRecognizer` and `TextToSpeech` for the Aug 22
  build. Sherpa-ONNX moves to Phase 8. v1 built the offline stack and reverted it for latency and
  playback glitching; see `decisions.md`.
- `[deva]` All persistence moves to Room. v1's proto DataStore stores are not carried forward.
- `[deva]` Package namespace is `com.trace`, replacing v1's `com.trace.app`.
