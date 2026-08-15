# Lumi

The private, on-device automation layer for Android.

Lumi turns your notes, routines, files, and daily logs into things a local model can act on. All
inference, storage, and retrieval happen on the device. Nothing is uploaded.

Built on Gemma 4 E2B via LiteRT-LM, with Kotlin, Jetpack Compose, Room, and WorkManager.

## Status

Phase 0 of the v2 rebuild. The project scaffold, theme tokens, database schema, and core contracts are
in place. See `todo.md` for the full plan and what is done.

## Building

Requires JDK 17 or newer and the Android SDK with API 37.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`local.properties` is generated locally and gitignored; point `sdk.dir` at your Android SDK.

**A physical arm64 device is required from Phase 1 onward.** The LiteRT-LM native runtime ships arm64
only, so an x86_64 emulator cannot run anything that touches the model.

## Layout

```
app/src/main/java/com/lumi/
├── core/          contracts every layer binds to — Capability, Router, ModelHarness, AuditLog
├── data/local/    Room entities, DAOs, database
├── di/            Hilt modules
└── ui/            theme, components, navigation, screens
```

`app/schemas/` holds the exported Room schema. It is committed on purpose: it is the record every
future migration is validated against. There is no destructive-migration fallback.

## Project documents

| File | What it is |
|---|---|
| `todo.md` | The phase plan, with every task assigned to Dev A or Dev B |
| `decisions.md` | Why things are the way they are. Read before reversing anything |
| `changelog.md` | What changed |
| `for_devb.md` | Dev B's brief |

Trace v1 exists as a separate reference checkout. It is read for understanding, never copied from.
