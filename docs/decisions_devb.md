# Lumi v2 — Decisions (Dev B)

Staging area only. Dev B appends here on the `devb` branch; Dev A folds the entries into `decisions.md`
at each merge, prefixes intact, and empties this file.

**`decisions.md` is the complete record.** Read that one — Dev B's folded entries are Part 3. This file
holds only what has not been folded in yet; if it is empty, everything is already in the main log.

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

### [devb] 2026-08-16 — Components wrap Material where behaviour is the point, restyle where looks are

`LumiDialog` wraps `AlertDialog` and `LumiSlider` wraps `Slider` rather than hand-rolling a
dialog surface or a slider track.

**Why:** a hand-rolled dialog re-implements focus handling, dismissal on outside tap, and
system-back behaviour — all things that go wrong silently and cost a device run each to find,
and this build has one device run's worth of budget. Wrapping keeps the behaviour and changes
only the tokens: the app surface, §4's radius, the accent on the moving part.

**The converse holds for cards and chips.** They are pure layout with no platform behaviour to
inherit, so `LumiCard`, `LumiCardRow`, and `LumiChip` are drawn from primitives. A stock
Material `Card` or `AssistChip` would have needed more overrides to reach §2 and §3 than the
wrappers saved.

**Selection is never colour alone, anywhere in the library.** Selected rows keep their fill and
hairline plus heavier text; a selected chip gets the accent fill plus heavier-weight text.
`for_devb.md`'s no-colour-alone rule is enforced in the component so a later screen cannot
forget it.

### [devb] 2026-08-16 — `LumiDialog`'s text is a slot, not a string

Phases 3-5 confirm different shapes of consequence: a routine's interpreted WHEN/DO structure,
a matched file, a network request about to leave the device. A single string parameter would
have forced each screen to flatten its structure into prose and lose the transparency the
confirmation exists for. The slot costs nothing at the simple call sites.

### [devb] 2026-08-16 — ASR is `SpeechRecognizer`, not Sherpa-ONNX; the spike is ruled out, not failed

`docs/todo.md` opens Phase 2 with a two-hour Sherpa spike. This entry records that **the spike
is deliberately not attempted**, and Phase 2 ships the plan's documented fallback:
`android.speech.SpeechRecognizer` and `TextToSpeech`.

**Why the spike is structurally impossible on this machine, not merely unattractive:** a spike
is defined by iteration — many builds, many device runs, latency measured and tweaked. This
workspace builds once, at the end of the phase. A two-hour task that needs ten build cycles
cannot be timeboxed here because it cannot start. Declaring it is a scheduling decision, not a
technical one.

**Why the fallback is also the better engineering choice for this phase:**

- Sherpa is a new dependency plus native `.so` libraries plus a separate model download — three
  new ways for the single build to fail, and the dependency needs the owner's approval, which is
  not granted.
- v1 already built an offline ASR stack on this exact hardware class, hit unacceptable latency
  and glitching, and reverted it. This is not an unknown being skipped; it is a known-bad bet
  being declined.
- `SpeechRecognizer` and `TextToSpeech` are platform APIs. Zero dependencies, nothing to
  download, nothing to version.

**What supersedes this, and when.** The 2026-08-15 strategy-brief entry in `decisions.md`
moved Sherpa back into the call-mode critical path for its streaming property. That ruling
stands for call mode (Phase 6), where a 45-minute session genuinely cannot run on native
recognition. It does not bind Phase 2's voice chat, where a spoken turn is two seconds and the
native path is the right tool — it is the one place the same `decisions.md` entry says native
was correct all along. Dev A may attempt Sherpa later on stronger hardware if the schedule
allows; that needs a new entry here, not a quiet revival.

**Honest caveat, handled rather than hidden:** `SpeechRecognizer` needs Google's recognition
service present, and without an offline language pack it may use the network. That is a real
tension for a privacy-first product. The handling, built into the Phase 2 code:

1. Recognition is treated as a **network-touching feature in the audit log until proven
   offline** — every voice turn is audited, and the UI says when the platform could not offer an
   offline pack.
2. When recognition is unavailable, the honest failure is typed input, not a hang — the same
   degrade path `decisions.md`'s 2026-08-14 voice entry already requires.
3. TTS speaks only turns whose origin is `VOICE`, per the `InteractionOrigin` contract. v1 read
   every reply aloud; the plumbing here carries the origin end to end so it cannot.

### [devb] 2026-08-16 — Reversed: sherpa-ONNX ships Phase 2 voice, on the owner's instruction

Supersedes the entry immediately above, which chose `SpeechRecognizer`. **The owner ruled on
2026-08-16, with an adb device connected, that sherpa-ONNX is the Phase 2 ASR stack**, and that
rebuilds stay minimal — only when needed — with verification compiled directly onto the device.

**What changed since the previous entry.** That entry's strongest point was structural: a spike
needs many build cycles and this workspace was assumed to get one. The owner has since attached
a device (`moto g54 5G`, Android 15 — **not** the SM-M356B named elsewhere in these files; all
on-device numbers from this phase are measured on the g54) and approved building onto it as
needed. The single-build premise no longer holds in its original form, so the reversal is on
the owner's record, not on optimism.

**The implementation honours "minimal rebuilds" the same way the single-build plan did:**

- The dependency is one pinned artefact: `sherpa-onnx-1.13.5.aar` from the upstream GitHub
  release, fetched into a local `libs/sherpa/` cache by `tools/fetch-sherpa.sh` and pinned by
  name, size, and SHA-256 in code. It is *not* committed — it is a 49MB binary, and the
  repository already keeps the 2.6GB model out of git for the same reason.
- Nothing about the integration is guessed. The Kotlin API written against here is read from the
  AAR's own `classes.jar` sources at version 1.13.5 (`OnlineRecognizer`, `OnlineStream`,
  `EndpointConfig`), not recalled from memory.
- The streaming model is `csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17`,
  int8, ~41MB across four files, fetched on first use exactly like the embedder — ungated on
  Hugging Face, so the no-token rule holds.

**What stays carried from the superseded entry**, because the problems it named are real:

1. Streaming partial results, never batch-after-stop — this is the v1 bug the strategy-brief
   entry in `decisions.md` exists to fix.
2. The TTS queue appends with `QUEUE_ADD`; the speak job is never cancelled per chunk.
3. Recognition is audited as on-device in the audit log, and honestly: sherpa is local by
   construction, so the audit entry can say so — the privacy tension the last entry flagged is
   gone, which is the real win of this reversal.

**TTS remains platform `TextToSpeech`.** sherpa's TTS models are large, English voice quality is
the demo's weakest suit there, and no brief item asks to replace the platform engine — only the
recognition half was in contention.

**Fallback preserved, not pre-built.** `SpeechRecognizer` is not wired as a second path. If
sherpa fails on the device, the fix loop is on-device and the fallback is the honest one the
docs already require: typed input, stated plainly. Two half-working ASR paths would cost more
builds than the one path the owner chose.

### [devb] 2026-08-16 — The network gate is enforced inside the dispatcher, not in the network capabilities

`CapabilityDispatcher` runs the opt-in check and the pre-request audit write for SEARCH and
MAIL before either capability executes; the capabilities themselves contain no network
permission logic.

**Why here and not in each capability:** the brief requires one chokepoint, not two code
paths, and a gate that lives inside a capability is only as strong as that capability author's
discipline — the third network feature (if one ever gets approved) would simply be written
without one. Enforcing it in the dispatcher makes bypassing the gate a structural change to
the dispatch path, which is reviewable, instead of an omission, which is not. `NetworkFeature`
is a closed enum exactly so the privacy audit remains enumerable.

**Refusals are audited as SKIPPED, in plain language.** The log must read as a true history —
"the user asked, Lumi declined, nothing went out" — not only as a record of requests that
succeeded. Pre-request entries are written before the fetch regardless of how the fetch later
ends, because the audit proves the fact of leaving the device, not the outcome.

**Reversal condition:** if a capability needs to make several gated requests, or needs to
decide per-request (e.g. a fetch that fans out), the gate moves into the capability and this
entry gets superseded, not quietly worked around.

### [devb] 2026-08-16 — Reversed: Whisper base.en replaces the 20M streaming zipformer for recognition

Supersedes the sherpa-ONNX entry's model choice (the runtime ruling stands; only the model
inside it changes). **On-device testing on the g54 showed the 20M zipformer could not reliably
recognise everyday sentences — transcripts came back garbled — and the owner ruled 2026-08-16
that speech recognition is the app's front door and accuracy cannot be compromised.** The owner
also made latency a first-class requirement: transcription delay must be kept as low as possible.

**The model is sherpa-onnx-whisper-base.en (int8)**, ~160MB across three files (encoder 28MB,
decoder 125MB, tokens), plus Silero VAD (~0.6MB), hosted on the same ungated Hugging Face repos
and fetched/verified by the same `ModelStore` machinery as before. Base was chosen over Small:
small.en is 375MB and roughly four times slower per second of audio — on this mid-range device
that cost buys accuracy the short spoken commands of this product do not need. If real-world
accuracy still disappoints, the ladder is small.en, not a network service.

**Latency is restored without streaming, by design:**

1. Silero VAD splits mic audio into utterances — a 450ms pause closes a segment, and an
   utterance is force-split at 8s so no single decode grows large.
2. Each closed segment is decoded on a dedicated thread off the recording loop, so the next
   utterance is captured while the previous one is recognised; the transcript grows live, and
   the final text arrives one short decode after the last pause.
3. The session closes ~900ms after the last utterance ends (not after a long silence), so the
   reply starts promptly.

**What changes for the user:** first voice use downloads ~160MB instead of ~41MB (still
progress-reported, still digest-pinned), partial transcripts arrive per utterance rather than
per word, and recognition accuracy improves substantially. **What does not change:** recognition
stays fully on-device and is audited as such; TTS unchanged; the `AsrEngine` interface unchanged
(it promised live progress, never specifically per-word progress).

**Reversal condition:** if on-device decode of an 8-second segment proves too slow on mid-range
hardware (target: well under the ~20s the generative model takes anyway), tune `maxSpeechDuration`
down before touching the model again — shorter segments are the latency lever, not a model swap.

## 2026-08-17 — Phase 1 stabilisation gate closed, verified on the g54

**Ruling: the gate is closed.** Dev B verified the three remaining stabilisation items on a
moto g54 5G (Dimensity 7020, Android 15) — the same hardware Whisper was proven on.

- **Rotation / process death:** a configuration change with messages on screen (simulated via
  `wm density`, which destroys and recreates the activity the same way a rotation does) left the chat
  transcript intact, same process, no crash.
- **Low memory:** `am kill` with the 2.6GB model resident killed the process; relaunch cold-started the
  model and the full history was recovered from Room and reopenable. No orphaned notification, no ANR.
- **GPU leg:** GPU loads on this device but took **48.5s** — the Mali-G57 cannot serve it. Hardware
  finding, not a code regression (Samsung SM-M356B measured 14-21s in Phase 1). CPU remains the default;
  GPU stays available as an explicit choice for capable devices.
- Two honest residuals, neither a crash: a reply force-killed mid-generation shows "could not finish
  that reply" and restarts clean; and Google's EmbeddingGemma `.task` artefact is currently invalid —
  see the entry below.

## 2026-08-17 — EmbeddingGemma CDN artefact is corrupt; router degraded to lexical scoring

**Discovery, not a code change.** On the g54, MediaPipe rejects `embedding_gemma.task` with
"not a valid Flatbuffer buffer". Investigation: the on-device file's SHA-256 matches the pinned constant
`913b7a1e…`, and a fresh download from Google's CDN hashes identically — but the bytes start `00 00 504b`
(ZIP magic preceded by two stray nulls), and the CDN's `last-modified` is 23 Jun 2026. Conclusion: the
pinned digest and the artefact are self-consistent with each other, and the artefact is broken at the
source. The digest was pinned from a file that was already bad, so the receipt gives false confidence.

**Why this is not an app regression:** the embedder's failure mode is designed to degrade.
`MediaPipeEmbedder.load` reports `EmbedderState.Unavailable`; the embed call returns null;
`SimilarityTier` falls back to token-overlap lexical scoring; chat and rules-tier routing work as before.
The router's accuracy for paraphrased intents is reduced, but nothing breaks and nothing claims to work
when it does not.

**What needs attention (separate, urgent):** a known-good `.task` file. Options: pin a digest from a
copy fetched before 23 Jun 2026 if one survives on either developer's machine or device cache; check
whether a versioned (non-`latest`) MediaPipe CDN path serves an intact build; or rebuild the same
EmbeddingGemma 300m through the Token-free MediaPipe converter. Whatever is chosen, the new pin must be
verified by actually loading it in `TextEmbedder`, not by digest alone — a digest cannot detect a bad
artefact, as this one proved. Until then the similarity tier stays lexical. This is recorded here rather
than swallowed, because `for_devb.md` forbids silent degradation being mistaken for failure.


## 2026-08-17 — Kokoro TTS source analysis: silence_scale and max_num_sentences

**What the source says (sherpa-onnx v1.13.5, offline-tts-kokoro-impl.h).** Read the native
implementation to settle two open questions before tuning further:

1. **`max_num_sentences` is ignored for Kokoro.** The impl hard-codes `batch_size = 1` and logs
   a warning if any other value is set. Each sentence gets its own `Process()` call internally
   regardless of the config. Batching multiple sentences into one `generate()` call therefore
   does not gain parallelism — but it does reduce per-call overhead, so larger chunks per
   request are still preferable to one-sentence-per-request.

2. **`silence_scale` semantics confirmed.** Any silence interval longer than 200 ms is
   shortened to `interval_length * silence_scale`. Range is [0.01, 10]. The Kotlin
   `GenerationConfig.silence_scale` sentinel value of 0.2 falls back to `OfflineTtsConfig`'s
   `silence_scale`, so setting it on `OfflineTtsConfig` is the correct lever for the whole
   engine. It is applied after each sentence's `Process()` call.

**What changed.**
- `KokoroReplySpeaker.createEngine()`: `silenceScale = 0.15f` on `OfflineTtsConfig` (was
  default 0.2). The owner's spec: "shorten natural pauses" without making speech run together.
  0.15 at 24 kHz means a 300 ms generated pause becomes 45 ms — a natural breath beat, not a
  gap. Lower than 0.01 is rejected by sherpa and was never considered.
- `ChatViewModel.MAX_SPEECH_CHUNK`: 160 → 280 chars. Fewer, larger `generate()` calls mean
  fewer inter-call boundaries where the pipeline can stall. The first fragment still fires
  early via the `anySpokenThisTurn` fast path.

**What remains open.** RTF on Dimensity 7020 is still unknown — no device timing data has been
captured for the current build. If RTF > 1 (synthesis slower than playback), the pipeline
stalls and no chunk-size or silence tuning fixes that; `NUM_THREADS` or model size would need
revisiting. Owner needs to run `adb logcat -s LumiTts` during a test turn to unblock this.
