---
type: feature
title: Word Segmentation, Tap-to-Gloss & 202-Language Catalog
description: How Gloss implements daily-life phrase and tap-to-gloss word lookups across all 202 NLLB-200 languages using Unicode BreakIterator segmentation and LRU-cached lookups.
tags:
  - word-gloss
  - segmentation
  - languages
  - flores-200
  - ui
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T18:48:00.244Z
sources:
  - id: openwiki-source-9f93d70275f6dbd609d041f7
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/data/LanguageCatalog.kt
  - id: openwiki-source-d83645852f2724b64eed2687
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/engine/WordSegmenter.kt
  - id: openwiki-source-ad6514f560c602e910721732
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/InteractiveWordText.kt
  - id: openwiki-source-f5d737a91cf473f221d99c99
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/LanguagePickerSheet.kt
  - id: openwiki-source-82a1ba94b8de47141f61417e
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/WordGlossCard.kt
  - id: openwiki-source-1152058e843ff4dae60b1242
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/MainViewModel.kt
  - id: openwiki-source-16346b1c9cd480c29fa72e24
    resource: repo://app/src/test/java/rocks/dearanayji/gloss/data/LanguageCatalogTest.kt
  - id: openwiki-source-1ba86d72147ba2632fda826b
    resource: repo://app/src/test/java/rocks/dearanayji/gloss/engine/WordSegmenterTest.kt
generated: { by: "antigravity", at: "2026-11-23T18:48:00.244Z" }
---

# Word Segmentation, Tap-to-Gloss & 202-Language Catalog

Being able to quickly look up everyday phrases and see what individual words actually mean in daily life is the primary reason Gloss was developed. Rather than forcing learners or travelers to re-type words one by one into a separate dictionary—or rely on live cellular connectivity—Gloss embeds 202-language offline translation on-device for broad accessibility and lets you tap any word in either the phrase you entered or the translated result without losing sentence context.

## Unicode Word Segmentation (`WordSegmenter`)

`WordSegmenter.tokenize(text)` segments arbitrary scripts into a sequence of `TextToken.Word(display, lookup, index)` and `TextToken.Separator(text)` items:

- Uses `java.text.BreakIterator.getWordInstance()` so contractions (`don't`), accented Latin characters, Devanagari matras, Arabic, Cyrillic, and CJK characters stay intact.
- Classifies slices containing at least one letter or digit (`Character.isLetterOrDigit`) as words, while stripping leading/trailing non-token punctuation into `lookup` (`cleanLookupToken` preserves letters, digits, and Unicode combining/enclosing marks).
- Merges adjacent non-newline separators (`appendSeparator`) and preserves `'\n'` boundaries so multi-line sentences render accurately.

`WordSegmenterTest` covers English contractions, Spanish inverted punctuation (`¿...?`), Devanagari matras, Arabic/Japanese scripts, and newline preservation.

## Bidirectional Tap-to-Gloss (`InteractiveWordText` & `WordGlossCard`)

- **Interactive sentence rendering**: `InteractiveWordText` splits `tokens` by newline (`splitTokensIntoLines`) and renders each line inside a Compose `FlowRow`. Tappable words display a subtle dashed underline (`PathEffect.dashPathEffect`) and highlight with `wordSelectedBg` and an accent border when selected.
- **Bidirectional lookup**: `MainViewModel.onWordTapped(token, origin)` accepts taps across `Ready`, `IdleDownloaded`, and `Loading` model states (auto-reloading evicted ORT sessions if needed), routing `WordOrigin.SOURCE` taps to `sourceCode -> targetCode` and `WordOrigin.TRANSLATION` taps to `targetCode -> sourceCode`. Tapping the currently selected word again dismisses the card.
- **Instant repeat taps**: Lookups call `EngineRepository.translateWord`, which checks a 256-entry `LruCache` before running ONNX inference.
- **Inline `WordGlossCard`**: Animates open between the source and output cards, showing the direction badge (`From → To`), the selected word and translated meaning (or loading indicator), and quick **Speak** (TTS), **Copy**, and **Dismiss** actions.

## 202-Language FLORES-200 Catalog (`LanguageCatalog`)

`LanguageCatalog` defines all `202` FLORES-200 language codes supported by `nllb-200-distilled-600M`, sorted alphabetically by English name with:

- `code` (exact NLLB token such as `eng_Latn`, `spa_Latn`, `hin_Deva`, `zho_Hans`)
- `name` (English display name)
- `nativeName` (endonym in native script)
- `script` (script family label)
- `bcp47` (optional BCP-47 tag used by `MainActivity` to select Android `TextToSpeech` voices)

`LanguageCatalog.search(query)` matches case-insensitively across `name`, `nativeName`, `script`, and `code`, while `LanguagePickerSheet` surfaces **Recent** (from `PreferencesStore`) and **Popular** filter chips above the full 202-language list. `LanguageCatalogTest` asserts uniqueness and exact 1:1 parity against every language token in `tokenizer.json` when `-Dgloss.model.dir` is present.

## Related Pages

- [App Architecture & State Lifecycle](../architecture/overview.md)
- [NLLB-200 ONNX Engine & BPE Tokenizer](nllb-engine.md)
- [Gloss Quickstart & Navigation Map](../quickstart.md)
