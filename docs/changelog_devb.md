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
