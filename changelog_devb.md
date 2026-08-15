# Trace v2 — Changelog (Dev B)

Major changes made by Dev B on the `devb` branch. Written for a person catching up — not a mirror of
the git log. Dev A folds these entries into `changelog.md` at merge points, prefixes intact.

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

### Changed

- `[devb]` **`DESIGN_LANGUAGE.md` is now the authority for all UI work**, superseding the Design
  Specification's UI sections, which the owner has deprecated. The palette is cyan Slime Blue `#4DB6AC`,
  not the sumi-olive that `todo.md` and `changelog.md` still describe. See `decisions_devb.md`.
- `[devb]` **Phase 1 is being executed end to end by Dev B**, including the items tagged `[deva]`. The
  tags in `todo.md` are left in place as a record of the original split.
- `[devb]` Light mode's `On Primary` is `#171717` rather than `DESIGN_LANGUAGE.md` §2's `#FAFBF7`. White
  on the cyan accent measures 2.35:1 and fails WCAG AA; the dark value measures 7.35:1. The accent hue
  is unchanged. See `decisions_devb.md`.

### Fixed

- `[devb]` Two Phase 0 items that were recorded as open are in fact settled, and `todo.md` now says so:
  Trace is **Apache 2.0** licensed (owner decision, `LICENSE` is in the repository root), and the
  on-device install is **no longer blocked** — a Motorola moto g54 5G on Android 15, `arm64-v8a`, is
  attached over adb, so the LiteRT-LM native runtime has real hardware to run on.

### Added

- `[devb]` Dev B's own append-only logs, `changelog_devb.md` and `decisions_devb.md`, on the `devb`
  branch, per the split in `decisions.md`.
