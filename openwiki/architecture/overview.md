---
type: architecture
title: App Architecture & State Lifecycle
description: Ownership boundaries across GlossApplication, EngineRepository, MainViewModel, PreferencesStore, MainActivity, home screen widgets, AppUpdateManager, and the Compose UI, including mutex-serialized inference and reactive state flow.
tags:
  - architecture
  - lifecycle
  - stateflow
  - android
  - compose
  - widgets
  - updates
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T14:57:27.550Z
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
  - id: openwiki-source-03a33906bbb0991c28f3200a
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/update/AppUpdateManager.kt
  - id: openwiki-source-a8376cef6c02b46b86f3bd9a
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/widget/GlossWidgetActions.kt
  - id: openwiki-source-4325bb241889681f08d0155c
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/widget/GlossWidgets.kt
generated: { by: "antigravity", at: "2026-11-23T14:57:27.550Z" }
---

# App Architecture & State Lifecycle

Gloss uses an offline-first, single-activity Android architecture that separates native ONNX model ownership from UI state, home screen widgets, GitHub release update checks, and local persistence.

## Process & Ownership Boundaries

| Component | Lifetime | Primary Responsibility |
| --- | --- | --- |
| `GlossApplication` | Process | Initializes `PreferencesStore`, `ModelManager`, and `EngineRepository`, and triggers initial model state inspection on startup. |
| `EngineRepository` | Process | Owns the single `NllbTranslatorEngine` instance, serializes inference and loading via `Mutex`, manages downloads, and caches word lookups. |
| `PreferencesStore` | Process | Persists source/target language codes, recent languages (up to 8), theme mode (`DARK`, `LIGHT`, `SYSTEM`), local phrase history (up to 30 entries), and cached GitHub release stats/check timestamps in `SharedPreferences`, notifying `GlossWidgetUpdater` on language or history changes. |
| `MainViewModel` | Activity / Config | Combines `EngineRepository.modelState` with `DraftState` into `StateFlow<GlossUiState>`, debounces phrase translation, triggers 24h online + manual GitHub update checks, syncs widget-driven preference changes, and manages word-tap lookups and language swaps. |
| `AppUpdateManager` | Stateless / App | Queries GitHub Releases API, compares semantic versions (`SemVer`), sums `.apk` download counts across releases, downloads update APKs to `cacheDir/updates/`, and creates `FileProvider` install intents. |
| `MainActivity` | Activity | Hosts edge-to-edge Jetpack Compose `MainScreen` inside `GlossTheme`, handles widget launch intents (`singleTop`), triggers `REQUEST_INSTALL_PACKAGES` / `ACTION_VIEW` APK installation, and manages the Android `TextToSpeech` lifecycle. |
| `GlossQuickWidgetProvider` & `GlossPhrasesWidgetProvider` | Broadcast / Home | Render resizable 4×1 Quick Translate and 4×2 Recent Phrases home screen widgets and handle direct language-swap broadcasts. |

In `AndroidManifest.xml`, the application registers `GlossApplication`, sets `android:largeHeap="true"` to accommodate ONNX Runtime allocations on mid-range Android devices, declares `INTERNET`, `ACCESS_NETWORK_STATE`, and `REQUEST_INSTALL_PACKAGES`, and exposes `cacheDir/updates/` through `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`).

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

## In-App GitHub Updater & Download Stats

`AppUpdateManager` powers both version update checks and live GitHub download stats without any external backend:

- **24-hour online + manual checks**: On startup in `MainViewModel.init`, if at least 24 hours have elapsed since `PreferencesStore.lastUpdateCheckMillis` and `ConnectivityManager` reports `NET_CAPABILITY_INTERNET`, `checkForUpdatesInternal(silent = true)` runs silently. Users can also trigger `checkForUpdatesManual()` from the **App & Offline Model** sheet.
- **Release parsing & download counting**: `parseReleasesJson` reads `https://api.github.com/repos/ADPer0705/gloss/releases?per_page=30`, sums `download_count` across all `.apk` assets of non-draft releases (`totalGithubDownloads`), selects the latest non-draft, non-prerelease APK, and compares `SemVer.isNewer(tagName, BuildConfig.VERSION_NAME)`. Stats are cached in `PreferencesStore` (`cachedTotalGithubDownloads`, `cachedLatestTag`, `cachedLatestDownloads`) so they remain visible offline.
- **In-place APK installation**: `MainScreen` displays a dismissible `UpdateBanner` when an update is available. Tapping **Update** downloads `gloss_<tag>.apk` via `AppUpdateManager.downloadUpdateApk` and hands off to `MainActivity.installApk()`, which checks `packageManager.canRequestPackageInstalls()` on API 26+ (prompting `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` if needed) before launching `AppUpdateManager.createInstallIntent()`.

## Local Persistence, Home Screen Widgets & Offline Speech

- **Preferences & History**: `PreferencesStore` stores JSON-serialized lists via Gson in `gloss_prefs`. Recent languages are deduplicated and capped at 8 (`MAX_RECENT_LANGS = 8`); phrase history is deduplicated by `(sourceText, sourceCode, targetCode)` and capped at 30 items (`MAX_HISTORY_ITEMS = 30`). Updating language selections or history automatically invokes `GlossWidgetUpdater.updateAll(appContext)`.
- **Home Screen Widgets**:
  - `GlossQuickWidgetProvider` (4×1) displays the active language pair with a direct **Swap** broadcast (`ACTION_SWAP_LANGUAGES`) plus **Tap to translate offline…**, **Paste & Translate**, and **Recent Phrases** launch actions.
  - `GlossPhrasesWidgetProvider` (4×2) displays up to 3 recent phrases from `PreferencesStore.getHistory()`, launching `MainActivity` with `WIDGET_ACTION_RESTORE` extras so tapping a phrase opens it ready for word-by-word glossing.
  - `MainActivity` runs with `launchMode="singleTop"`, processes widget intents in `onCreate` and `onNewIntent` (reading clipboard text for `WIDGET_ACTION_PASTE`, opening `HistorySheet` for `WIDGET_ACTION_HISTORY`, or restoring a phrase), and calls `viewModel.syncFromPreferences()` in `onResume()` so widget language swaps sync immediately into the app.
- **Text-to-Speech**: `MainActivity` initializes Android `TextToSpeech` in `onCreate()`, resolves the language's optional `bcp47` tag from `LanguageCatalog.byCode(langCode)`, checks `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` (notifying via `Toast` if no offline voice is installed), and shuts down the engine in `onDestroy()`.

## Related Pages

- [Gloss Quickstart & Navigation Map](../quickstart.md)
- [NLLB-200 ONNX Engine & BPE Tokenizer](../translation/nllb-engine.md)
- [Word Segmentation, Tap-to-Gloss & 202-Language Catalog](../translation/word-gloss-and-languages.md)
- [Pinned Model Mirror & Resumable Downloads](../models/pinned-distribution.md)
- [Build, Testing & GitHub Release Pipeline](../operations/build-testing-release.md)
