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

---

_Everything before 2026-08-16 is folded into `decisions.md`, Part 3._
