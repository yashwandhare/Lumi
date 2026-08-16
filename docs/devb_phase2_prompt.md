# Dev B — Phase 2 brief

You are Dev B on Lumi v2, an on-device AI automation layer for Android. You are picking up mid-project
and **you are building all of Phase 2 alone — Dev A's items as well as your own.**

Deadline: the app is complete on the morning of **Aug 21 2026**. Today is **Aug 16**. The hackathon is
Aug 22.

## Your hard constraint, and what it changes

**Your machine cannot afford repeated builds. You build once, at the end of the phase.**

That single fact reorders everything. Normally you lean on the compiler to find your mistakes and on the
device to find your wrong assumptions. You have neither. So:

- **Read before you write.** Every signature you call, read it in the file it is declared in. Do not
  recall an API from memory and do not infer a parameter name from its purpose. This document quotes the
  interfaces you need verbatim so you do not have to guess.
- **Do not add dependencies.** Everything Phase 2 needs is already in `gradle/libs.versions.toml` or is a
  platform API. A new dependency is a new way for the one build to fail, and it needs the owner's
  approval anyway.
- **Prefer the boring construction.** A clever generic signature that does not compile costs you the
  phase. A plain one that does is worth more than elegance you cannot verify.
- **Commit in small coherent pieces as you go,** even though you are not building between them. If the
  build fails at the end, granular commits let you revert one thing instead of the day.
- **Never break what already works.** Chat works end to end on a device right now and it is what demos.
  Build the new path *beside* it and switch over in a single final commit, so that commit is revertible.

### The one cheap checkpoint, if you can afford anything

`./gradlew :app:compileDebugKotlin` is dramatically cheaper than `installDebug` — no KSP re-run in most
cases, no dexing, no packaging, no device transfer. Roughly 8 seconds against 20+ plus install.

**If you can afford exactly one extra invocation, spend it on `compileDebugKotlin` at the halfway
point** — after the router and dispatcher are written and before you start the voice UI. That is where
the type errors cluster, and finding them at the halfway mark is worth far more than finding them all at
the end.

`./gradlew :app:testDebugUnitTest` is also comparatively cheap and needs **no device**. The capability
and router contracts deliberately contain no Android types precisely so their tests are plain JVM tests.
Router tests are the highest-value thing you can run without hardware — use them.

## Read these first, in this order

1. `docs/for_devb.md` — your standing brief. **Read the "Continuation brief — Aug 16, evening" section at
   the bottom first**; it is the most current statement of where things stand.
2. `docs/todo.md` — the phase plan. Phase 2 is your scope. Every item is tagged `[deva]`, `[devb]`, or
   `[both]` — **ignore the tags this time, you have all of them.**
3. `docs/DESIGN_LANGUAGE.md` — the UI authority. The accent is cyan Slime Blue `#4DB6AC`, and it is the
   only accent. Read it before you draw anything.
4. `docs/decisions.md` — the rules that already bind. Several look arbitrary until you read the reasoning.
5. `app/src/main/java/com/lumi/core/` — the contracts. All of them. This is not optional reading given
   your constraint.

## The gate: check it before you start Phase 2 UI

**Phase 1 is not closed.** `docs/todo.md` records exactly which boxes are ticked. Three things remain: a
rotation and process-death pass, a low-memory pass, and exit-run scenarios 3-5 plus the GPU leg of
scenario 1.

Your standing brief says to finish the component library — **cards, dialogs, chips, and styled sliders**
— before Phase 2 screens. That is still right, and it is now more right, because Phases 3-7 all need
chips and dialogs and you cannot afford to discover a missing component in a later phase's single build.
`LumiGlassPanel`, `LumiIconButton`, `LumiInput`, `LumiBlob`, `MarkdownText`, `LoadingState`, `ErrorState`
and `EmptyState` exist. Cards, dialogs, and chips do not.

Do the component library first. It compiles in isolation, it has no dependency on Dev A's work, and
everything after it needs it.

## The ASR decision — read this before you plan the phase

`docs/todo.md` opens Phase 2 with "prove Sherpa-ONNX streaming ASR on the device before anything else,
timeboxed to two hours."

**Do not attempt Sherpa. Use `android.speech.SpeechRecognizer`.** That is the documented fallback in the
plan and it is the correct choice for you specifically:

- A spike is *defined* by iteration. Two hours of Sherpa means many builds and many device runs measuring
  latency and glitching. You have one build. The task is structurally impossible for you, not merely
  hard.
- Sherpa is a new dependency plus native `.so` libraries plus a separate model download. Each is a way
  the single build fails, and the dependency needs the owner's approval you do not have.
- `SpeechRecognizer` and `TextToSpeech` are **platform APIs**. Zero new dependencies, nothing to
  download, nothing to version.
- v1 already tried an offline stack, hit unacceptable latency on this exact hardware, and reverted it.
  You are not skipping an unknown; you are declining a known bad bet with worse tooling than the attempt
  that already failed.

**Write a `decisions_devb.md` entry recording this**, referencing the todo item you are not doing and
why. Do not quietly skip it — the plan says Sherpa first, and a future reader needs to find the reason.

Note honestly in that entry that `SpeechRecognizer` needs Google's recognition service present and may
use the network unless an offline language pack is installed. That is a real caveat for a privacy-first
product. Handle it: prefer offline recognition where the platform offers it, and **treat recognition as a
network-touching feature in the audit log until proven otherwise.** Dev A can attempt Sherpa later on
stronger hardware if the schedule allows.

## Two things that will bite you at runtime, not at compile time

1. **`RECORD_AUDIO` is not in the manifest.** The app currently declares only `INTERNET` and
   `ACCESS_NETWORK_STATE`. Voice cannot work without it, and its absence is a runtime `SecurityException`
   your one build will happily produce. Add it, and add the runtime permission request with a real
   denial path that falls back to typed input.
2. **`InteractionOrigin` already exists with a `VOICE` value** — the enum is done, the plumbing is not.
   TTS must speak only turns whose origin is `VOICE`. v1 lost track of this and read every reply aloud.

## Phase 2 scope, in the order to build it

Phase 2 is roughly 25 items across two owners in `docs/todo.md` and you have all of them. **That will not
all fit.** Cut from the bottom, as the plan says. This is the order:

### 1. Component library (finish Phase 1's open item)
Cards, dialogs, chips, styled sliders. Shared tokens, no ad-hoc styling per screen.

### 2. Router, tiers 1 and 2
Pure Kotlin, no Android types, JVM-testable — the safest thing you can write blind, and the thing five of
the seven core features route through.

- Tier 1: regex and keyword rules for exact device commands. Always runs first, zero latency, no model.
- Tier 2: cosine similarity over the bundled embedder against labelled example phrases per intent.
  **Keep the phrase lists in one editable place** — when the router is wrong the fix should be adding a
  phrase, not changing code.
- Input normalization so typed, spoken, and widget input converge on one representation.
- Fix the ordering collisions: reminder, todo, and attach verbs all overlap on "add". Test the collisions.
- The ambiguity path: return `RouterOutcome.Ambiguous` and ask, rather than running a guess.

### 3. Router unit tests
JVM, no device, cheap to run. Reminders, todos, routines, RAG requests, file requests, device commands,
web search, mail fetch, and ambiguous input. **This is your only real safety net — write it properly.**

### 4. Dispatcher and capability registry
Keyed by `CapabilityId` so adding a capability does not mean editing a dispatch `when`.

### 5. The network-intent gate
Web search and mail fetch are the only intents that may leave the device. **One chokepoint**, not two
code paths. It checks the per-feature opt-in and writes an audit event *before* the request. Nothing
exists behind it yet in Phase 2 — build the gate anyway, because Phase 5 plugs into it and a gate added
afterwards is a gate something has already bypassed.

### 6. Voice: `SpeechRecognizer` and `TextToSpeech`
Carry v1's two bug fixes exactly:

- **Cancel on send.** If the mic is live and the user types and hits send, the recording loop must be
  cancelled cleanly. In v1 the mic stayed live in the background.
- **TTS queue.** Append streamed chunks to the queue with `QUEUE_ADD`. **Do not cancel the speak job on
  each new chunk** — v1 did, so the engine interrupted its own sentence, skipped words, and restarted
  mid-phrase.

Push-to-talk only. No wake word — battery drain, false triggers, and background-service reliability make
it a live-demo risk. Describe it as next; never claim it works.

### 7. Voice session UI
Distinct listening, thinking, and speaking states, mascot-anchored, plus a live transcript surface so the
user can correct course before Lumi acts. **This is the screen a judge remembers** — it carries the
voice-first claim on its own.

Build it against a fake state holder first if that is simpler. The three states are the same whatever
sits behind them.

### 8. Interpreted-intent surface
Where an action has consequence, show what Lumi understood before it acts. This is the ease-of-use
criterion made visible.

### 9. Chat behind the `Capability` interface — do this LAST, in one commit
One dispatch path, not two. **This is the item most likely to break the thing that currently demos.**
Leave the working direct path in place until everything else is done, then switch in a single revertible
commit.

### Defer these if time runs short
Router tier 3 (Gemma for ambiguity) can be thin or absent — tiers 1 and 2 cover the demo. Persistent
"remember this" memory, widget voice invocation, and the LiteRT-LM tool-calling investigation all defer.
Say plainly in your changelog what you did not build.

## The interfaces you build against — quoted, so you do not guess

All in `com.lumi.core`. Read the files too; the comments carry reasoning this summary drops.

```kotlin
// core/CapabilityContracts.kt
enum class InteractionOrigin { TEXT, VOICE, WIDGET, QUICK_INVOKE, ROUTINE }

data class CapabilityInput(val intent: StructuredIntent, val origin: InteractionOrigin)

sealed interface CapabilityResult {
    val userMessage: String
    data class Ok(override val userMessage: String) : CapabilityResult
    data class Streaming(override val userMessage: String, val chunks: Flow<String>) : CapabilityResult
    data class Partial(override val userMessage: String, val whatFailed: String) : CapabilityResult
    data class Failed(override val userMessage: String, val recovery: String? = null) : CapabilityResult
    data class NeedsConfirmation(override val userMessage: String, val confirmLabel: String) : CapabilityResult
    data class NeedsPermission(override val userMessage: String, val permission: String) : CapabilityResult
}

interface Capability {
    val id: CapabilityId
    suspend fun execute(input: CapabilityInput): CapabilityResult
}

// core/RouterContracts.kt
data class StructuredIntent(
    val capability: CapabilityId,
    val rawText: String,
    val slots: Map<String, String> = emptyMap(),
) {
    operator fun get(slot: String): String?
    companion object { const val SLOT_QUERY = "query"; const val SLOT_TARGET = "target"; const val SLOT_VALUE = "value" }
}

enum class RouterTier { RULES, SIMILARITY, MODEL }
data class RouterDecision(val intent: StructuredIntent, val confidence: Float, val tier: RouterTier)

sealed interface RouterOutcome {
    data class Routed(val decision: RouterDecision) : RouterOutcome
    data class Ambiguous(val question: String, val candidates: List<CapabilityId>) : RouterOutcome
}

interface Router { suspend fun route(text: String, origin: InteractionOrigin): RouterOutcome }
interface Dispatcher { suspend fun dispatch(input: CapabilityInput): CapabilityResult }
```

```kotlin
// core/ai/ModelHarness.kt — the ONLY entry point to generation. Do not touch the runtime directly.
interface ModelHarness {
    val state: StateFlow<ModelState>
    val lastMetrics: StateFlow<GenerationMetrics?>
    suspend fun prepare()
    suspend fun reload()
    fun activeBackend(): ModelBackend?
    fun generate(request: GenerationRequest): Flow<String>
    suspend fun complete(request: GenerationRequest): String   // use this for router tier 3
    suspend fun resetSession(sessionId: SessionId)
}

data class GenerationRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val attachments: List<ModelAttachment> = emptyList(),
    val sampling: Sampling? = null,      // null = the user's settings
    val sessionId: SessionId? = null,    // null = one-shot, no history, no side effects
)

// Sampling.Structured exists for anything that must parse. Use it for the router, never Default.
val Structured = Sampling(temperature = 0.1f, topP = 0.5f, topK = 8, maxTokens = 384)
```

**Two things there matter enormously for the router.** `complete()` collects a stream into one string —
that is what tier 3 wants. And **`sessionId = null` means a one-shot inference with no history and no
side effects**, which is what keeps a router classification out of the user's chat context. Passing a
real session id from the router would poison the conversation.

```kotlin
// core/ai/Embedder.kt — router tier 2 and all of RAG
interface Embedder {
    val state: StateFlow<EmbedderState>
    suspend fun prepare()
    suspend fun embedDocument(text: String): FloatArray?   // example phrases, note chunks
    suspend fun embedQuery(text: String): FloatArray?      // the utterance being classified
    fun dimensions(): Int?
}
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float   // already written, do not reimplement
```

`embedDocument` and `embedQuery` are **not interchangeable** — EmbeddingGemma is trained with task
prefixes and using one for both measurably degrades retrieval. Example phrases are documents; the user's
utterance is a query. Both return null on failure; the embedder is allowed to be unavailable and the
router must fall through to tier 1 or tier 3 rather than crash.

```kotlin
// core/audit/AuditLog.kt — injectable @Singleton
suspend fun record(
    capability: CapabilityId,
    summary: String,
    outcome: AuditOutcome,
    subject: String? = null,
    detail: String? = null,
    occurredAtMs: Long = System.currentTimeMillis(),
)

enum class AuditOutcome { SUCCESS, FAILURE, PARTIAL, SKIPPED }
```

**Never put prompt text or reply text in an audit entry.** Read `ChatViewModel.recordTurn` for the
reference pattern before you write your first audit call. `summary` and `detail` are read by the phone's
owner: "Silenced the phone", not "SILENT_MODE=on".

`CapabilityId` values available: `CHAT, RAG, ROUTINE, FILES, DEVICE, JOURNAL, MEMORY, SEARCH, MAIL,
CALL, NOTIFICATIONS, TOOLS`. **There is no `SOS` — it was cut.** Do not add a value without a decision
entry.

## Codebase conventions that will cost you a build if you get them wrong

These are the specific traps in *this* project:

- **Hilt view model injection in Compose** is imported from
  `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`, **not** the older
  `androidx.hilt.navigation.compose` path. Match `LumiApp.kt`.
- **Constructor-injected qualifiers use annotation-use-site syntax:**
  `@param:ApplicationScope private val applicationScope: CoroutineScope`. Available qualifiers are
  `@IoDispatcher`, `@InferenceDispatcher`, `@ApplicationScope` from `com.lumi.di`.
- **A subjectless `when` needs `||`, not commas.** `isAngry || isWinking ->`, never `isAngry, isWinking ->`.
  This is a hard compile error and it is easy to type.
- **State exposure pattern:** `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
  Collect in Compose with `collectAsStateWithLifecycle()`.
- **Icons:** `Icons.Rounded.X`, and `Icons.AutoMirrored.Rounded.X` for anything directional. The extended
  icon set is available and BOM-managed — never write an explicit version for a Compose artifact.
- **Theme tokens, use them and do not hardcode:** `MaterialTheme.spacing.{none,xs,sm,md,lg,xl,xxl}`,
  `LumiShape.{default,input,tile,panel,control}`, `LumiSize.{iconButton,icon,avatar,drawer,mascot,tile,hairline}`,
  and `LocalMotionEnabled` for every animation.
- **`LoadingState(operation = ...)` takes the real operation as a required argument** so "Loading…"
  cannot be written by accident. `ErrorState(problem, next, partial)` takes what happened, what to do,
  and what partly completed as three separate parameters so none can be skipped. Use them as designed.
- **A cancelled coroutine cannot suspend.** If you need to write to Room or the audit log from a `catch`
  that handles cancellation, launch it on `@ApplicationScope`. Catch `CancellationException` before
  `Throwable` and **rethrow it**. This has already cost this project a bug; see `decisions.md`.
- **Room changes mean a version bump, a migration, and a committed exported schema.** There is no
  destructive-migration fallback, deliberately. Avoid schema changes in Phase 2 if you can.

## When the single build fails

It probably will, at least once. Triage in this order:

1. **Read the first error only.** Kotlin cascades — errors two onward are usually the first one's
   consequence. Fix one thing, then re-run.
2. **Compile errors are cheap to re-attempt** relative to a device test. `compileDebugKotlin` beats
   `installDebug` for the fix loop; only install once it compiles.
3. **If something is structurally wrong and you cannot see it, revert that commit.** This is why the
   commits are small. A Phase 2 that ships the router and voice UI without the chat-behind-dispatcher
   switchover is a good Phase 2. A Phase 2 that does not build is not a Phase 2.
4. **Do not "fix" it by deleting the failing feature and reporting success.** Say what does not build and
   what you left out. The project rule is explicit: never ship placeholder logic, swallowed errors, or
   fake success states. A swallowed exception has already cost this project a day.

## Report back with

- What builds and what does not, plainly.
- What you verified on the device and what you did not. Distinguish the two — do not describe something
  you reasoned about as something you tested.
- Which `docs/todo.md` items you completed, which you deferred, and why.
- Your `docs/decisions_devb.md` entries, especially the Sherpa one.

## Standing rules that do not change

- **Work on `devb`. Push only `devb`. Never push to `main`** — Dev A merges when the owner says so.
- One-line conventional commits: `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`, `perf:`,
  `style:`. Describe intent, not mechanics. Stage specific files, never `git add .`.
- Stage log entries in `docs/changelog_devb.md` and `docs/decisions_devb.md`, every entry prefixed
  `[devb]` and dated. Dev A folds them into the main files at merge.
- **Zero compiler warnings.** The build is at zero right now. Keep it there.
- **One accent colour.** Never convey information by colour alone. Respect reduced motion, battery saver,
  and system text scaling. Real content descriptions and visible focus states.
- **Anything Lumi does on its own must be visible and dismissible. When the network is used, show it.**
- v1 is reference only. Read it, understand why it works, rewrite clean. **Never copy a file across**, and
  never rename v1's references — v1 is Trace and stays Trace.
- Ask the owner before changing the design language, adding a dependency, changing a shared interface or
  data model, cutting a `docs/todo.md` task, or altering what the demo shows. Batch your questions.
