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

_Empty. Everything through the 2026-08-15 merge is folded into `changelog.md`._
