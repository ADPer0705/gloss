---
type: architecture
title: App Architecture & State Lifecycle
description: Ownership boundaries across GlossApplication, EngineRepository, MainViewModel, PreferencesStore, MainActivity, and the Compose UI, including mutex-serialized inference and reactive state flow.
tags:
  - architecture
  - lifecycle
  - stateflow
  - android
  - compose
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T09:50:05.804Z
sources:
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-bf46583a7b4d2b00e6f32a5c
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/data/EngineRepository.kt
  - id: openwiki-source-d2afdbb33421a24f3c0ffd11
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/data/PreferencesStore.kt
  - id: openwiki-source-ab35119acc6764ee4e07e3bf
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/GlossApplication.kt
  - id: openwiki-source-e6cf144781d5221a9bf5d597
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/MainActivity.kt
  - id: openwiki-source-1152058e843ff4dae60b1242
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/MainViewModel.kt
generated: { by: "antigravity", at: "2026-11-23T09:50:05.804Z" }
---

# App Architecture & State Lifecycle

Gloss v2 uses an offline-first, single-activity Android architecture that separates native ONNX model ownership from UI state and local persistence.

## Process & Ownership Boundaries

| Component | Lifetime | Primary Responsibility |
| --- | --- | --- |
| `GlossApplication` | Process | Initializes `PreferencesStore`, `ModelManager`, and `EngineRepository`, and triggers initial model state inspection on startup. |
| `EngineRepository` | Process | Owns the single `NllbTranslatorEngine` instance, serializes inference and loading via `Mutex`, manages downloads, and caches word lookups. |
| `PreferencesStore` | Process | Persists source/target language codes, recent languages (up to 8), theme mode (`DARK`, `LIGHT`, `SYSTEM`), and local phrase history (up to 30 entries) in `SharedPreferences`. |
| `MainViewModel` | Activity / Config | Combines `EngineRepository.modelState` with `DraftState` into `StateFlow<GlossUiState>`, debounces phrase translation, and manages word-tap lookups and language swaps. |
| `MainActivity` | Activity | Hosts edge-to-edge Jetpack Compose `MainScreen` inside `GlossTheme` and manages the Android `TextToSpeech` lifecycle. |

In `AndroidManifest.xml`, the application registers `GlossApplication`, sets `android:largeHeap="true"` to accommodate ONNX Runtime allocations on mid-range Android devices, and declares `INTERNET` and `ACCESS_NETWORK_STATE` solely for the one-time offline model download.

## Application Startup & Engine Repository

When Android creates `GlossApplication`, `onCreate()` constructs `PreferencesStore`, `ModelManager`, and `EngineRepository`, then invokes `engineRepository.refreshInitialState()`.

- If `ModelManager.isModelReady()` is already true on disk, `EngineRepository` starts in `ModelState.Loading` and asynchronously loads `NllbTranslatorEngine` on `Dispatchers.Default` inside `engineMutex`.
- If the model is absent or incomplete, `EngineRepository` enters `ModelState.NotDownloaded` without touching the network until the user initiates a download.
- All `translatePhrase`, `translateWord`, `loadEngineInternal`, and `deleteModel` operations acquire `engineMutex` (`Mutex`) so concurrent UI events (such as rapid typing followed by a word tap or model deletion) never race against an active ONNX Runtime session.
- Single-word lookups check an in-memory `LruCache<String, String>(256)` keyed by `"$sourceCode->$targetCode:${trimmed.lowercase()}"` before acquiring `engineMutex`.

## Reactive UI State & Debounced Translation

`MainViewModel` maintains a private `MutableStateFlow<DraftState>` initialized from `PreferencesStore` and merges it with `repo.modelState` using `combine(...).stateIn(..., SharingStarted.WhileSubscribed(5_000), ...)`.

- **Debounced phrase translation**: `onInputChanged` immediately re-tokenizes `inputText` via `WordSegmenter.tokenize` for instant word-chip rendering, cancels any in-flight `phraseJob`, and waits `380 ms` before dispatching `repo.translatePhrase`.
- **Stale-result protection**: Before writing translated output or saving history, `scheduleTranslation` checks that `current.inputText`, `current.sourceCode`, and `current.targetCode` still match the snapshot that launched the coroutine.
- **Swap semantics**: `swapLanguages()` swaps `sourceCode` and `targetCode` in `PreferencesStore` and promotes non-blank `translatedText` into `inputText` before triggering an immediate re-translation.
- **Auto-translation on model load**: An `init` collector watches `repo.modelState`; when the model transitions to `ModelState.Ready` while `inputText` is already populated and `translatedText` is blank, it triggers immediate translation.

## Local Persistence & Offline Speech

- **Preferences & History**: `PreferencesStore` stores JSON-serialized lists via Gson in `gloss_prefs`. Recent languages are deduplicated and capped at 8 (`MAX_RECENT_LANGS = 8`); phrase history is deduplicated by `(sourceText, sourceCode, targetCode)` and capped at 30 items (`MAX_HISTORY_ITEMS = 30`).
- **Text-to-Speech**: `MainActivity` initializes Android `TextToSpeech` in `onCreate()`, resolves the language's optional `bcp47` tag from `LanguageCatalog.byCode(langCode)`, checks `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` (notifying via `Toast` if no offline voice is installed), and shuts down the engine in `onDestroy()`.

## Related Pages

- [Gloss Quickstart & Navigation Map](../quickstart.md)
- [NLLB-200 ONNX Engine & BPE Tokenizer](../translation/nllb-engine.md)
- [Word Segmentation, Tap-to-Gloss & 202-Language Catalog](../translation/word-gloss-and-languages.md)
- [Pinned Model Mirror & Resumable Downloads](../models/pinned-distribution.md)
