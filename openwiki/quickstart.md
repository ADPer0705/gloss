---
type: quickstart
title: Gloss Quickstart & Navigation Map
description: Repository overview, key entrypoints, common development commands, and navigation map across the Gloss offline Android translator documentation.
tags:
  - quickstart
  - navigation
  - android
  - offline-translation
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T14:57:27.550Z
sources:
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-e6cf144781d5221a9bf5d597
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/MainActivity.kt
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
generated: { by: "antigravity", at: "2026-11-23T14:57:27.550Z" }
---

# Gloss Quickstart & Navigation Map

**Gloss** (`rocks.dearanayji.gloss`, v2.3.0) is an offline-first Android translation and language-learning app powered by **NLLB-200-distilled-600M** (INT8 mobile ONNX via `onnxruntime-android:1.32.0`) across **202 languages**, featuring a Jetpack Compose Material 3 interface, Duolingo-style tap-a-word lookups on both input and translated sentences, Google Translate–style home screen widgets, persistent release signing for in-place updates, and an in-app GitHub Releases updater with download stats.

## Key Entrypoints

- **App startup & singletons**: `GlossApplication` (`app/src/main/java/rocks/dearanayji/gloss/GlossApplication.kt`) initializes `PreferencesStore`, `ModelManager`, and `EngineRepository`.
- **Main UI, widget intents, updater & TTS host**: `MainActivity` (`app/src/main/java/rocks/dearanayji/gloss/MainActivity.kt`) hosts `MainScreen` inside `GlossTheme`, handles `singleTop` widget launch actions, triggers `FileProvider` APK updates, and binds `MainViewModel` plus Android `TextToSpeech`.
- **In-app GitHub updater & stats**: `AppUpdateManager` (`app/src/main/java/rocks/dearanayji/gloss/update/AppUpdateManager.kt`) checks GitHub Releases, sums `.apk` download counts, and downloads update APKs to `cacheDir/updates/`.
- **Home screen widgets**: `GlossQuickWidgetProvider` (4×1) and `GlossPhrasesWidgetProvider` (4×2) in `app/src/main/java/rocks/dearanayji/gloss/widget/GlossWidgets.kt` provide quick translate, clipboard paste, language swap, and recent phrase restoration.
- **Build, persistent signing, launcher icon & Android targets**: `app/build.gradle.kts` (`minSdk = 24`, `targetSdk = 36`, `compileSdk 36.1`, `arm64-v8a` and `armeabi-v7a`, `buildConfig = true`) with custom adaptive/mipmap launcher icons and persistent release signing via `.github/workflows/release.yml`.
- **Pinned model manifest**: `ModelCatalog` (`app/src/main/java/rocks/dearanayji/gloss/model/ModelCatalog.kt`) pins the 6-file INT8 ONNX distribution plus `config.json` to `ADPer/nllb-200-distilled-600M-mobile` (rev `c998583de40ac4331dce8e8d7254d4456696133a`).

## Common Commands

```bash
# Run host-JVM unit tests (WordSegmenterTest, LanguageCatalogTest, AppUpdateManagerTest)
./gradlew testDebugUnitTest

# Run full ONNX integration tests against a local model folder
./gradlew testDebugUnitTest -Dgloss.model.dir=/path/to/nllb-200-distilled-600M-mobile

# Build release APK locally
./gradlew assembleRelease
```

## Wiki Navigation Map

| If you need to understand… | Read |
| --- | --- |
| Process singletons, `Mutex` inference serialization, `StateFlow`, `PreferencesStore`, home screen widgets, `AppUpdateManager`, and TTS lifecycle | [App Architecture & State Lifecycle](architecture/overview.md) |
| Streaming `NllbTokenizer`, primitive `MergeTable`, ORT mmap sessions, and 12-layer KV-cache decoding | [NLLB-200 ONNX Engine & BPE Tokenizer](translation/nllb-engine.md) |
| `WordSegmenter`, bidirectional source/translation word glossing, LRU cache, and the 202-language catalog | [Word Segmentation, Tap-to-Gloss & 202-Language Catalog](translation/word-gloss-and-languages.md) |
| Pinned Hugging Face mirror, `config.json` download tracking, SHA-256 checks, manual cross-host HTTP `Range` resume, and App & Offline Model UI | [Pinned Model Mirror & Resumable Downloads](models/pinned-distribution.md) |
| AGP 9.2.1 + Compose setup, persistent release keystore CI signing, unit-test ORT JVM swapping, launcher assets, and tag-driven GitHub releases | [Build, Testing & GitHub Release Pipeline](operations/build-testing-release.md) |
