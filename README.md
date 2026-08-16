# Lumi

The private, on-device automation layer for Android.

Lumi turns your notes, routines, files, and daily logs into things a local model can act on. All
inference, storage, and retrieval happen on the device. Nothing is uploaded.

Built on Gemma 4 E2B via LiteRT-LM, with Kotlin, Jetpack Compose, Room, and WorkManager.

## Status

**Phase 1 — stable baseline.** Gemma 4 E2B loads and stays resident, chat streams and persists,
settings and backend selection work, and image and audio input are both verified on device. Phase 2
adds voice-first interaction and the router. See `docs/todo.md` for the full plan and what is done.

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

Everything lives in `docs/`. This README is the only document at the repository root.

| File | What it is |
|---|---|
| `docs/todo.md` | The phase plan, with every task assigned to Dev A or Dev B |
| `docs/decisions.md` | Why things are the way they are. Read before reversing anything |
| `docs/changelog.md` | What changed |
| `docs/DESIGN_LANGUAGE.md` | The UI authority. Supersedes the design spec's UI sections |
| `docs/COMPETITIVE_LANDSCAPE.md` | The AI wearables Lumi is positioned against, and where it is weaker |
| `docs/STRATEGY_BRIEF.pdf` | Why the product is positioned as it is |
| `docs/for_devb.md` | Dev B's brief |

## Privacy

Every inference is on-device. Two features reach the network — DuckDuckGo search and Gmail fetch over
MCP — and both are opt-in, user-initiated, and audited. Neither sends anything to an inference service:
the network fetches, it never infers. A fetched email is summarised by Gemma on the phone.

The audit log records every capability call and every network request, so the claim can be checked
rather than trusted.

## Tools

`tools/seed-model.sh` pushes the 2.6GB model to a device from a digest-verified host cache. Running
`connectedAndroidTest` uninstalls the app, and an uninstall wipes app-private storage — this restores
the model in about 75 seconds instead of a re-download.

Trace v1 exists as a separate reference checkout. It is read for understanding, never copied from.
