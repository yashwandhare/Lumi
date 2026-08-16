# For Dev B

Read this first. It is your complete brief — you should not need anything outside this repository to
start working.

Lumi v2 is a private, on-device AI automation layer for Android. Two developers are building it: Dev A
and you, Dev B. The app must be complete by the morning of **Aug 21 2026**, including rebuild
documentation. The hackathon is Aug 22. **It is currently Aug 16** — five days and a morning remain.

## What changed since your last brief — read this section even if you have read this file before

The product was repositioned. Five things are materially different, and all of them affect your work:

1. **All documentation moved into `docs/`.** Only `README.md` is left at the repository root. That
   includes `changelog_devb.md` and `decisions_devb.md`, so if your branch appends to the old root
   paths, expect a rename-plus-modify at merge. Git handles it.
2. **The core feature set was fixed at seven features** by the owner on Aug 16. See the table below. The
   phase plan in `docs/todo.md` was rebuilt around them.
3. **SOS is cut entirely.** All of it: the SOS screen, the setup screen, emergency contacts, the siren,
   the Morse flashlight. If you built any of it, it comes out. **The mood journal is demoted** to an
   optional Phase 9 below the cut line.
4. **Airplane mode is no longer an acceptance criterion.** The criteria are now **privacy-first, ease of
   use, and voice-first.** Privacy-first is unchanged and still non-negotiable — every inference is
   on-device — but it is now proved by evidence rather than by absence. Two opt-in network features exist
   (web search, Gmail fetch), both audited. This promotes the **audit log screen from a transparency
   nicety to the primary proof of the privacy claim.** It is one of the most important screens you own.
5. **Voice is the product's identity, not a late-phase feature.** It moved from Phase 6 to Phase 2. The
   voice session UI is now one of the first things you build and the screen a judge is most likely to
   remember.

Also worth knowing: **the palette described in older docs is dead.** Rice-paper, sumi, and the olive
accent are gone. `docs/DESIGN_LANGUAGE.md` is the authority and the accent is cyan Slime Blue `#4DB6AC`.
It supersedes the Design Specification's UI sections entirely — the owner ruled on Aug 15 that those
docx sections are mostly deprecated because the markdown deliberately drifted from them.

## Read these, in this order

1. This file.
2. `docs/todo.md` — the full phase plan. Every task is tagged `[deva]`, `[devb]`, or `[both]`. Yours are
   `[devb]`. Read the whole file anyway so you know what Dev A is building around you.
3. `docs/DESIGN_LANGUAGE.md` — **the UI authority.** Read it before the docx.
4. `docs/STRATEGY_BRIEF.pdf` — why the product is positioned the way it is. Short, and it explains most
   of the changes above.
5. `docs/COMPETITIVE_LANDSCAPE.md` — the £100-700 wearables Lumi is positioned against, and where Lumi is
   genuinely worse than them. Read it before writing any user-facing copy.
6. `Lumi_PRD_v2.docx` — what the product is and who it is for.
7. `Lumi — Design Specification.docx` — the system specification. Sections 18-35 are yours. **Its UI
   sections are superseded by `DESIGN_LANGUAGE.md`** — read it for structure and behaviour, not for
   colour, type, or palette.
8. `docs/decisions.md` — the rules that already bind, and why. Several are hard constraints that look
   like arbitrary preferences until you read the reasoning. The last few entries cover everything in the
   section above.

## The seven core features

These are the product. Everything else is supporting infrastructure or explicitly optional.

| # | Feature | Phase | Your part |
|---|---|---|---|
| 1 | **Voice-first mode** | 2 | Voice session UI: listening, thinking, speaking states. Live transcript |
| 2 | **Widget → reminders, todos, routines** | 3 | The Glance widget. Reminder/todo lists. Routine creation and detail |
| 3 | **File fetch** | 4 | Candidate list for ambiguous matches. Notes and documents screens |
| 4 | **DuckDuckGo search, on demand** | 5 | Result surface with the source visible on every item |
| 5 | **Gmail fetch over MCP** | 5 | Mail result surface. The network-features settings panel |
| 6 | **Call mode** | 6 | Recording state, elapsed time, live transcript, unmissable stop. Summary screen |
| 7 | **Notification reading + suggestions** | 7 | Suggestion surface — quiet, dismissible, never modal. Access settings |

Plus the **audit log screen** in Phase 4, which is not one of the seven but now carries the privacy claim
on its own.

## What you own

The presentation layer:

- The design system and component library
- Every screen, and navigation between them
- The Glance home-screen widget
- Accessibility
- Loading, empty, and error states
- Motion and animation

## What Dev A owns

Core, data, the AI runtime, the router and dispatcher, the routine engine, device actions, voice
plumbing, MCP, and background execution. You build against the interfaces Dev A publishes. If you need
something that is not on an interface yet, ask — do not reach past it into the implementation, and do not
add a field to a shared model yourself.

## Where the project actually is

Phases 0 and 1 have largely landed. What is working on a real device, verified rather than assumed:

- Gemma 4 E2B loads and stays resident. **14-21s on GPU, 8-12s on CPU** on a Samsung SM-M356B.
- Chat streams, renders markdown, persists to Room, and reopens from the history drawer with per-chat
  context.
- Settings: model parameters, system prompt, backend choice, motion.
- **Vision and audio input both work.** Confirmed by instrumented test — an image is described
  correctly. An earlier report said images were rejected; that was a test bug with too small a context
  window. Phase 4 can rely on image understanding.
- EmbeddingGemma is bundled as the shared embedder, which unblocks the router's similarity tier and RAG.
- The mascot, the sleeping-mascot loading easter egg, and the home screen.

**Phase 1 is a stability gate.** Dev A is finishing it now: the audit log write path, a rotation and
process-death pass, and a five-scenario device run. **Phase 2 does not start until that gate passes.**
Your remaining Phase 1 item is the component library — cards, dialogs, and chips do not exist yet, and
sliders are in use on the settings screen unstyled. Finish those before Phase 2 work, because Phases 3-7
all need chips and dialogs and building them ad-hoc per screen is how a design system dies.

## Git rules

**Work on the `devb` branch and push only it. Never push to `main`.**

```
git fetch origin
git checkout -b devb origin/main    # if you do not have it yet
git push -u origin devb
```

Branch layout: `main` is the integration branch. Dev A works on `deva`, you work on `devb`. **Neither
agent pushes to `main`.** Dev A merges both branches into `main` when the project owner says so.

The only exception is when the owner explicitly tells you, in that specific instance, to push to `main`.
A previous instruction to do so does not carry forward to the next time.

Keep the git log clean and readable. It is a record other people read.

- One line per commit message. No body unless a reviewer genuinely cannot understand the change without
  one.
- Conventional prefixes: `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`, `perf:`, `style:`.
- Describe intent, not mechanics.
- One coherent change per commit. Stage specific files rather than `git add .`, so unrelated work does
  not ride along.
- Never commit secrets, API keys, `.env` files, or large binaries.
- Never force-push, never rewrite shared history, never `reset --hard` on shared work.

Good:

```
feat: add routine creation screen with interpreted trigger preview
fix: mascot breathing animation ignores reduced-motion setting
chore: extract spacing scale into theme tokens
refactor: collapse duplicate card variants into one component
```

Bad:

```
update
fixes
WIP
final
added RoutineScreen.kt, RoutineViewModel.kt, and updated NavGraph.kt and Theme.kt
```

## Your changelog and decisions files

Maintain two files on the `devb` branch, now at `docs/changelog_devb.md` and `docs/decisions_devb.md`,
mirroring the ones Dev A keeps:

- `docs/changelog_devb.md` — major changes. Newest at the top. Group under Added, Changed, Fixed,
  Removed. One line per change, describing what changed for a user or another developer, not which files
  moved.
- `docs/decisions_devb.md` — why you chose things. Append at the bottom, never edit an old entry to
  change its meaning. If you reverse a decision, add a new entry referencing the old one.

**Prefix every entry in both files with `[devb]`.** Dev A prefixes theirs with `[deva]` and folds your
entries into the main files at merge points, prefixes intact. Separate files exist so two append-only
logs on two branches do not conflict on every merge.

Log a decision when someone could reasonably reverse your choice later and would need the reasoning to
decide whether to. Do not log routine implementation detail. Date every entry.

## Trace v1 is reference only

You have a v1 checkout. Use it to understand how something was solved and which problems are real. Then
write the v2 version clean.

**Do not copy files across.** Not whole files, not whole classes. v1 is a fork of a Google sample app
with a chat-app structure, and v2 is a different architecture — copied code drags the old shape into a
codebase whose entire point is a new one. Several v1 implementations are also deliberate time-boxed hacks
that should not survive into a production build.

Read it, understand why it works, rewrite it better. Carry the lesson, not the code.

**Also: do not rename v1's references.** v1 is called Trace and stays called Trace. Only the current
implementation is Lumi. If you are reading v1 code or docs, leave the name alone.

## Code quality

Boring and stable, production grade from the first commit. There is no "clean it up later" phase in a
five-day schedule.

- Match the conventions Dev A established in Phase 0. Consistency beats your preferred style.
- Prefer the simplest implementation that satisfies the requirement. No speculative abstractions, no
  configurability nobody asked for, no single-use indirection.
- Clear names over comments explaining unclear ones. Comments explain *why*, not what.
- **Zero compiler warnings.** The build is currently at zero. A warning that survives a day becomes a
  warning nobody reads.
- Never ship placeholder logic, hardcoded fake data, swallowed errors, or fake success states. If
  something cannot be finished, say so plainly. A swallowed exception has already cost this project a day
  — a load failure was caught and discarded, which made it the one bug nobody could diagnose.
- Run the build before you commit. Verify behaviour on a device, not by reading your own code.

## Hard UI rules

Not negotiable without asking. `docs/DESIGN_LANGUAGE.md` is the authority; these are the ones that get
broken most often.

- **One accent colour: cyan Slime Blue `#4DB6AC`.** No rainbow semantic colouring. The rice-paper/sumi/
  olive palette in older documents is dead.
- **Never convey information by colour alone.** Every status needs a second signal.
- **Hierarchy comes from size, weight, spacing, and position — not colour.**
- **Respect reduced motion.** Every animation, including the mascot's breathing, must stop or go static
  when reduced motion is enabled. Also respect lifecycle state and battery saver.
- **Support system text scaling.** Layouts must not break at large font sizes.
- **Adequate touch targets, real content descriptions, visible focus states.**
- **No placeholder screens, no debug UI, no unfinished interactions** in anything you mark done.
- **Loading states name the real operation.** "Finding your college ID…", never "Loading…".
- **Errors explain what happened, what to do, and whether it partly completed.** Never surface a raw
  exception or an Android internal to the user.
- **The home screen must not look like a generic AI chat app.** No feature grid, no dashboard cards, no
  conversation-first layout. Mascot, one input, two affordances, and a lot of empty space.
- **The widget must be indistinguishable from the app.** Same colours, type, spacing, mascot, and
  interaction states. It should read as a piece of Lumi on the home screen, not a separate product.

Two rules that come from the repositioning rather than the design spec:

- **Anything Lumi does on its own must be visible and dismissible.** Notification suggestions are the
  only place Lumi acts unprompted, and they are suggestions — never modal, never a queue, never a badge
  count, never a nag.
- **When the network is used, show it.** Web search and Gmail are the only features that leave the
  device. A request in flight needs a visible indicator. A judge should be able to watch the network
  being used and watch it stop.

## Working with the owner

The project owner stays in the loop for key decisions. Everything else you drive yourself.

Ask the owner before: changing anything in the design language, adding a dependency, changing a shared
interface or data model, cutting a task from `docs/todo.md`, or anything that would alter what the demo
shows.

Decide yourself: component structure, naming, file organization, animation timing within the rules above,
and how to implement a screen the specification already describes.

When you ask, ask in a batch, and only about things whose answer changes what you build. Do not ask what
the specification or the code already answers.

## Pace

**Phase 1 is the stability gate.** Until it passes, prefer finishing the component library over starting
new screens. After it passes, move fast through Phases 2-7 and slow down for Phase 8 polish.

Seven core features in five days will not all reach production quality — that is arithmetic, and
`docs/todo.md` says so openly. **Cut from the bottom, never from the middle.** If a phase slips more than
half a day, tell the owner and cut the lowest remaining item rather than leaving several phases
half-finished. Four complete features demo. Seven partial features do not demo at all.

Prefer a working plain version of a screen over a beautiful unfinished one.

## Continuation brief — Aug 16, evening

Appended by Dev A at the end of the Aug 16 session. **This is the most current statement of where things
stand; where it disagrees with a section above, this wins.**

### What landed today

- **Every chat turn is audited.** Success, stopped, and failed alike. The entry carries the capability, the
  backend, and the timing, and **never the prompt or the reply text.** Four instrumented tests cover it.
  This is the pattern every capability from Phase 2 on copies — read `ChatViewModel.recordTurn` before you
  build anything that needs to write an audit event, and do not add content fields to it.
- **A Stop control on the composer.** The send button becomes a filled stop square in the accent while a
  reply is decoding. Found by the exit run: there was no way to interrupt a reply at all. Partial text is
  kept, persisted, and audited as `PARTIAL`.
- **The home screen mascot's eyes are bigger and oval.** `LumiBlob` now takes an `eyeSize: DpSize`,
  defaulting to the original near-circular pair. **Only the home screen passes the larger value.** If you
  add a mascot instance, leave the default alone unless it is a hero-sized one.
- Vision is confirmed working at a 4096-token context. Phase 4 can rely on image understanding.
- Docs moved into `docs/`, the plan was rebuilt around the seven core features, and SOS was cut.

### The gate is still open — do not start Phase 2 screens yet

Three stabilisation items remain, all Dev A's, none known-broken and none checked:

1. Rotation and process-death pass on the chat screen.
2. One low-memory pass with the model resident.
3. Exit-run scenarios 3, 4, and 5 — new-chat/reopen context isolation, backgrounding mid-reply, and
   kill-and-relaunch — plus re-verifying the GPU leg of scenario 1 on the current build.

Scenarios 1 (CPU) and 2 pass. Cold start on CPU is **3.0s** with warm compilation caches. `docs/todo.md`
records exactly which boxes are ticked and which are not; trust that file over anyone's summary.

**Your unblocked work right now is the component library** — cards, dialogs, chips, and styled sliders.
`docs/todo.md` still has it at `[~]`. Phases 3-7 all need chips and dialogs, and it is the one thing you
can finish without waiting on the gate or on a Dev A interface. Do that before Phase 2 screens.

### When the gate closes, start here

Your first Phase 2 items, in order:

1. **Voice session UI** — listening, thinking, speaking, mascot-anchored. The screen a judge remembers.
2. **Live transcript surface** — the user sees words as they are recognised and can correct course.
3. **Interpreted-intent surface** — where an action has consequence, show what Lumi understood first.

Build 1 and 2 against a fake state holder if Dev A's ASR spike is still running. The spike is timeboxed to
two hours and may land on `SpeechRecognizer` rather than Sherpa; **either way the UI states are the same
three**, so nothing you build there is wasted. Do not wait on it.

### Two things to know before you touch the composer or the transcript

- **`LumiInput` now has `generating` and `onStop` parameters.** If you reuse the composer in a voice or
  call-mode screen, wire them — a composer that cannot interrupt is the bug that was just fixed.
- **Stopped replies are persisted; failed replies are not.** That asymmetry is deliberate. A truncated
  answer stored without its failure notice reads as a complete answer on reopen, and a reply that
  misrepresents itself is worse than one that is missing. Keep it if you touch persistence.

### Still true, and worth repeating

- Work on `devb`. Push only `devb`. Dev A merges to `main`.
- Stage entries in `docs/changelog_devb.md` and `docs/decisions_devb.md`, prefixed `[devb]`.
- Zero compiler warnings. The build is at zero right now — `assembleDebug` and `installDebug` both clean.
- Verify on the device, not by reading your own code. The Stop control existed in the plan for a day
  before anyone noticed there was no button for it, and only a device run found that.
