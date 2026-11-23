# gloss

[![Latest Release](https://img.shields.io/github/v/release/ADPer0705/gloss?style=flat-square&color=7C73FF)](https://github.com/ADPer0705/gloss/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/ADPer0705/gloss/total?style=flat-square&color=3FD9C9&label=downloads)](https://github.com/ADPer0705/gloss/releases)
[![Latest Release Downloads](https://img.shields.io/github/downloads/ADPer0705/gloss/latest/total?style=flat-square&color=3FD9C9&label=latest%20downloads)](https://github.com/ADPer0705/gloss/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-8B949E?style=flat-square)](LICENSE)

translate a phrase. tap any word. that's it.

**[Project Site](https://gloss.dearanayji.rocks)** &middot; **[Interactive Architecture Wiki](https://gloss.dearanayji.rocks/wiki/)** &middot; **[Latest APK](https://github.com/ADPer0705/gloss/releases/latest)**

Gloss was built for everyday life—so whenever you hear, read, or want to say a phrase, you can look it up quickly and tap any word on either side to see what it means in context. Running all **202 languages** 100% offline (`NLLB-200-distilled-600M` INT8 ONNX after a one-time ~628 MB / ~658 MB download) prioritizes **accessibility and dependability anywhere**—no mobile data, Wi-Fi, roaming plan, account, or subscription required (with on-device privacy as a natural benefit).

---

## Features

- **202 languages offline** — powered by Meta AI's NLLB-200-distilled-600M quantized to INT8 for mobile RAM budgets (~1.4 GB peak RAM).
- **Duolingo-style tap-a-word** — tap any word in **either** your input sentence or the translated output to see that word's individual meaning immediately.
- **Home screen widgets** — Quick Translate (4×1) with instant swap and clipboard paste, plus Recent Phrases (4×2) for 1-tap word study.
- **In-app GitHub updates** — signed with a persistent release key so updates install in-place without losing your 658 MB model or history.
- **Searchable language picker** — search by English name, native script name, script family, or FLORES-200 code, with Recent and Popular chips.
- **Offline pronunciation & history** — listen using Android's on-device Text-to-Speech and revisit recent phrases locally.
- **Pinned model mirror** — downloads SHA-256-verified weights from a pinned Hugging Face revision ([`ADPer/nllb-200-distilled-600M-mobile`](https://huggingface.co/ADPer/nllb-200-distilled-600M-mobile)) so the app never breaks if upstream files move.

---

## Install & Auto-Update

1. Download the latest APK from [**GitHub Releases**](https://github.com/ADPer0705/gloss/releases/latest) (`Android 7.0+`, `arm64-v8a` / `armeabi-v7a`).
2. Future updates can be installed directly inside Gloss (via the update banner or **Status Pill → Check updates**) or tracked automatically with [**Obtainium**](https://github.com/ImranR98/Obtainium) pointed at `https://github.com/ADPer0705/gloss`.

---

## Model Attribution & Licensing

- **App source code**: [MIT License](LICENSE).
- **Translation model**: `CC-BY-NC-4.0` (non-commercial use only, attribution required):
  - **Meta AI**: [`facebook/nllb-200-distilled-600M`](https://huggingface.co/facebook/nllb-200-distilled-600M) (NLLB Team, *"No Language Left Behind: Scaling Human-Centered Machine Translation"*, 2022)
  - **Xenova**: [`Xenova/nllb-200-distilled-600M`](https://huggingface.co/Xenova/nllb-200-distilled-600M) (INT8 ONNX export)
  - **boat1603**: [`boat1603/nllb-200-distilled-600M-mobile`](https://huggingface.co/boat1603/nllb-200-distilled-600M-mobile) (mobile `MatMulInteger` / shared-vocab ONNX rewrite)
  - **Pinned mirror**: [`ADPer/nllb-200-distilled-600M-mobile`](https://huggingface.co/ADPer/nllb-200-distilled-600M-mobile)

---

## Build & Test

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease
```
