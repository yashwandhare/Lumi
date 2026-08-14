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

### Added

- `[devb]` Dev B's own append-only logs, `changelog_devb.md` and `decisions_devb.md`, on the `devb`
  branch, per the split in `decisions.md`.
