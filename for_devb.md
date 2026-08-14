# For Dev B

Read this first. It is your complete brief — you should not need anything outside this repository to
start working.

Trace v2 is a private, on-device automation layer for Android. Two developers are building it: Dev A
and you, Dev B. The app must be complete by the morning of Aug 21 2026, including rebuild
documentation. The hackathon is Aug 22.

## Read these, in this order

1. This file.
2. `todo.md` — the full phase plan. Every task is tagged `[deva]`, `[devb]`, or `[both]`. Yours are
   `[devb]`. Read the whole file anyway so you know what Dev A is building around you.
3. `Trace_PRD_v2.docx` — what the product is and who it is for.
4. `Trace — Design Specification.docx` — the system and UI/UX specification. Sections 18 through 35 are
   yours. Read them properly; most of your work is specified there in detail.
5. `decisions.md` — the rules that already bind, and why. Several of them are hard constraints that
   will look like arbitrary preferences until you read the reasoning.

## What you own

The presentation layer:

- The design system and component library
- Every screen, and navigation between them
- The Glance home-screen widget
- Accessibility
- Loading, empty, and error states
- Motion and animation

## What Dev A owns

Core, data, the AI runtime, the router and dispatcher, the routine engine, device actions, and
background execution. You build against the interfaces Dev A publishes. If you need something that is
not on an interface yet, ask — do not reach past it into the implementation, and do not add a field to
a shared model yourself.

## Phase 0: you are blocked, and that is expected

Nothing in an empty repository can be parallelized before the project scaffold, theme tokens,
dependency injection, and database schema exist. Dev A builds those alone and commits the core
interfaces as soon as they compile, before finishing the rest of Phase 0, to unblock you sooner.

Until then: get Trace v1 building locally, read the documents listed above, and confirm Android Studio
and a physical test device work. Do not start writing v2 UI code against interfaces that do not exist.

## Git rules

**Create the `devb` branch, work on it, and push only it. Never push to `main`.**

```
git fetch origin
git checkout -b devb origin/main
git push -u origin devb
```

Branch layout: `main` is the integration branch. Dev A works on `deva`, you work on `devb`. **Neither
agent pushes to `main`.** Dev A merges both branches into `main` when the project owner says so.

The only exception is when the owner explicitly tells you, in that specific instance, to push to
`main`. A previous instruction to do so does not carry forward to the next time.

Keep the git log clean and readable. It is a record other people read.

- One line per commit message. No body unless a reviewer genuinely cannot understand the change
  without one.
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

Create and maintain two files on the `devb` branch, mirroring the ones Dev A keeps on `main`:

- `changelog_devb.md` — major changes. Newest at the top. Group under Added, Changed, Fixed, Removed.
  One line per change, describing what changed for a user or for another developer, not which files
  moved.
- `decisions_devb.md` — why you chose things. Append at the bottom, never edit an old entry to change
  its meaning. If you reverse a decision, add a new entry referencing the old one.

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
codebase whose entire point is a new one. Several v1 implementations are also deliberate time-boxed
hacks that should not survive into a production build.

Read it, understand why it works, rewrite it better. Carry the lesson, not the code.

## Code quality

Boring and stable, production grade from the first commit. There is no "clean it up later" phase in a
seven-day schedule.

- Match the conventions Dev A establishes in Phase 0. Consistency beats your preferred style.
- Prefer the simplest implementation that satisfies the requirement. No speculative abstractions, no
  configurability nobody asked for, no single-use indirection.
- Clear names over comments explaining unclear ones. Comments explain *why*, not what.
- Never ship placeholder logic, hardcoded fake data, swallowed errors, or fake success states. If
  something cannot be finished, say so plainly.
- Run the build before you commit. Verify behaviour on a device, not by reading your own code.

## Hard UI rules

These come from the design specification and are not negotiable without asking:

- **One accent colour.** Rice-paper white surface, sumi near-black text, one muted olive accent. No
  rainbow semantic colouring.
- **Never convey information by colour alone.** Every status needs a second signal.
- **Serif primary typeface.** Hierarchy comes from size, weight, spacing, and position — not colour.
  A legible sans-serif is allowed for dense metadata and native Android controls.
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
  interaction states. It should read as a piece of Trace on the home screen, not a separate product.

## Working with the owner

The project owner stays in the loop for key decisions. Everything else you drive yourself.

Ask the owner before: changing anything in the design specification, adding a dependency, changing a
shared interface or data model, cutting a task from `todo.md`, or anything that would alter what the
demo shows.

Decide yourself: component structure, naming, file organization, animation timing within the rules
above, and how to implement a screen the specification already describes.

When you ask, ask in a batch, and only about things whose answer changes what you build. Do not ask
what the specification or the code already answers.

## Pace

Move fast until the stable baseline lands at the end of Phase 5, then slow down and polish. The
baseline is defined in `todo.md` — five scenarios that must work on a real device in airplane mode.
Until those five pass, prefer a working plain version of a screen over a beautiful unfinished one.

If a phase slips more than half a day, tell the owner and cut the lowest remaining item rather than
leaving several phases half-finished.

