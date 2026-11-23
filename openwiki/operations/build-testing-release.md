---
type: operations
title: Build, Testing & GitHub Release Pipeline
description: Gradle toolchain setup on AGP 9.2.1 with built-in Kotlin and Compose, ABI filtering, JVM ONNX unit-test classpath swapping, and tag-triggered GitHub Actions APK releases.
tags:
  - gradle
  - build
  - testing
  - release
  - ci-cd
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T10:30:12.505Z
sources:
  - id: openwiki-source-4d1d392666be6dfdd7a91a2e
    resource: repo://.github/workflows/release.yml
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-81d5f1627e19148569f46f81
    resource: repo://gradle/libs.versions.toml
generated: { by: "antigravity", at: "2026-11-23T10:30:12.505Z" }
---

# Build, Testing & GitHub Release Pipeline

Gloss builds a standalone Android APK on Android Gradle Plugin `9.2.1`, supports host-JVM ONNX integration tests without an emulator, and publishes installable release APKs via GitHub Actions on `v*` tags.

## Gradle & Toolchain Configuration

- **Android Gradle Plugin & Kotlin**: `gradle/libs.versions.toml` pins `agp = "9.2.1"`, `kotlin = "2.2.10"`, `composeBom = "2026.02.01"`, and `onnxruntime = "1.32.0"`. Because AGP 9 includes built-in Kotlin support, `app/build.gradle.kts` applies `android.application` and `compose.compiler` (`org.jetbrains.kotlin.plugin.compose`) without applying `org.jetbrains.kotlin.android`.
- **Daemon & Java compatibility**: `gradle/gradle-daemon-jvm.properties` targets JVM toolchain `21` for the Gradle daemon, while `app/build.gradle.kts` sets Java 11 source/target compatibility.
- **SDK & ABI targets**:
  - `compileSdk` `36` (`minorApiLevel = 1`) and `targetSdk = 36`. `composeBom` is intentionally pinned to `2026.02.01` (`compose.ui` `1.10.4`) so AAR metadata remains compatible with `compileSdk 36.1` rather than requiring API 37.
  - `minSdk = 24` (required by `onnxruntime-android:1.32.0`).
  - `ndk.abiFilters` restricts native libraries to `arm64-v8a` and `armeabi-v7a`, keeping release APK size compact (~71–72 MiB) while covering physical ARM phones.

## Release Signing, Versioning & Launcher Assets

`app/build.gradle.kts` reads optional environment variables for CI and local release builds:

- `VERSION_CODE` defaults to `4` and `VERSION_NAME` defaults to `"2.2.0"`.
- If `KEYSTORE_PATH` is set, the `release` build type uses `signingConfigs.getByName("release")` with `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Otherwise it falls back to the `debug` signing config so local and CI APKs remain directly installable.
- R8/ProGuard `optimization.enable` is `false` on `release`.
- Launcher icons package an adaptive foreground (`app/src/main/res/drawable-nodpi/ic_launcher_foreground.webp`) over `#0B0E14` (`ic_launcher_background.xml`) alongside `mdpi`–`xxxhdpi` WebP icons (`ic_launcher.webp` and `ic_launcher_round.webp`).

## Host JVM Unit & ONNX Integration Testing

Because `NllbTokenizer`, `NllbTranslatorEngine`, `WordSegmenter`, and `LanguageCatalog` contain zero Android framework dependencies, the full translation pipeline can be tested directly on the development JVM:

- `app/build.gradle.kts` excludes `com.microsoft.onnxruntime:onnxruntime-android` from all `UnitTestRuntimeClasspath` and `UnitTestCompileClasspath` configurations and substitutes `com.microsoft.onnxruntime:onnxruntime:1.32.0` (`onnxruntime-jvm`).
- All `Test` tasks receive `maxHeapSize = "3g"` and forward `-Dgloss.model.dir` when provided.
- Running `./gradlew testDebugUnitTest` executes `WordSegmenterTest` and `LanguageCatalogTest`; passing `-Dgloss.model.dir=/path/to/model` additionally enables the full `NllbEngineIntegrationTest` and `tokenizer.json` 202-language parity assertion.

## GitHub Actions Release Workflow

`.github/workflows/release.yml` triggers on any pushed tag matching `v*`:

1. Checks out the repository and sets up Temurin Java `21` (matching AGP 9.2.1 and the Gradle daemon toolchain).
2. Configures Gradle caching via `gradle/actions/setup-gradle@v4`.
3. Runs `./gradlew assembleRelease` with `VERSION_CODE=${{ github.run_number }}` and `VERSION_NAME=${{ github.ref_name }}`.
4. Renames the output APK to `app/build/outputs/apk/release/gloss_${{ github.ref_name }}.apk`.
5. Uploads the APK to the corresponding GitHub Release using `softprops/action-gh-release@v2`.

## Related Pages

- [Gloss Quickstart & Navigation Map](../quickstart.md)
- [NLLB-200 ONNX Engine & BPE Tokenizer](../translation/nllb-engine.md)
- [Pinned Model Mirror & Resumable Downloads](../models/pinned-distribution.md)
