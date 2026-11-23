---
type: translation-engine
title: NLLB-200 ONNX Engine & BPE Tokenizer
description: Offline seq2seq inference pipeline combining a streaming low-RAM BPE tokenizer with a binary snapshot cache and tuned memory-mapped INT8 ONNX Runtime encoder and 12-layer KV-cached decoder sessions.
tags:
  - nllb-200
  - onnxruntime
  - bpe-tokenizer
  - kv-cache
  - inference
  - cold-start
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T17:33:47.960Z
sources:
  - id: openwiki-source-7d7956c8ca8fd0517516fd13
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/engine/NllbTokenizer.kt
  - id: openwiki-source-38d77ad2e88dc8d72a142cfa
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/engine/NllbTranslatorEngine.kt
  - id: openwiki-source-390c26bd4202067d66977546
    resource: repo://app/src/test/java/rocks/dearanayji/gloss/engine/NllbEngineIntegrationTest.kt
generated: { by: "antigravity", at: "2026-11-23T17:33:47.960Z" }
---

# NLLB-200 ONNX Engine & BPE Tokenizer

Gloss runs Meta's `nllb-200-distilled-600M` completely offline in pure Kotlin + ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.32.0`) using a memory-lean INT8 mobile graph where weights stay memory-mapped on disk, the tokenizer persists a fast binary snapshot (`tokenizer.glossbin`), and ORT session options are tuned for minimal mobile cold-start latency and RAM overhead.

## Streaming BPE Tokenizer & Binary Cache (`NllbTokenizer`)

Standard Hugging Face `tokenizer.json` files for NLLB-200 contain ~256k vocabulary entries and ~514k BPE merge rules. Loading them into boxed Kotlin objects or re-parsing 17.3 MB of JSON on every launch wastes cold-start CPU and heap. `NllbTokenizer` eliminates both bottlenecks:

- **Versioned binary snapshot (`tokenizer.glossbin`)**: `NllbTokenizer.loadWithInfo(tokenizerFile)` checks for `tokenizer.glossbin` (`CACHE_MAGIC = 0x474C544B`, `CACHE_VERSION = 1`, matching `tokenizerFile.length()`). On cache hit, it reads `idToToken`, `languageIds`, `specialIds`, and bulk-loads the `MergeTable` arrays (`keys`, `ranks`, `mergedIds`) directly via little-endian `ByteBuffer` chunks—avoiding JSON parsing, string splitting, and 1.5M+ hash insertions. On miss or mismatch, `loadFromJson` stream-parses `tokenizer.json` via `JsonReader` (`64 KB` buffer) and writes `tokenizer.glossbin.tmp` atomically before renaming.
- **Primitive open-addressing `MergeTable`**: Stores `(leftId, rightId) -> (rank, mergedId)` inside three primitive arrays (`LongArray(1 shl 20)`, `IntArray`, `IntArray`) using linear probing (~49% load factor, ~16 MB total).

### Encoding & Decoding Behavior

1. **Normalization**: `normalizeText` applies `Normalizer.Form.NFKC`, collapses consecutive spaces (`" {2,}"`), and trims whitespace.
2. **Metaspace pre-tokenization**: Replaces `' '` with `U+2581` (`▁`) and prepends `▁`.
3. **Codepoint lookup & `fuse_unk`**: Walks Unicode codepoints (handling surrogate pairs via `Character.codePointAt`), maps missing pieces to `UNK_ID` (`3`), and collapses consecutive `UNK_ID`s.
4. **Greedy BPE merges**: Repeatedly finds the lowest-rank adjacent pair in `MergeTable` and merges all non-overlapping left-to-right occurrences until no merge matches.
5. **Sequence framing**: Truncates content tokens to `MAX_SOURCE_TOKENS = 508` and emits `[sourceLangId, ...tokens, EOS_ID]`.
6. **Decoding**: Skips special/language tokens (`id <= 3` or `specialIds`), concatenates token strings, converts `▁` back to spaces, and strips a single leading space.

## ONNX Runtime Cold-Start Tuning & Inference (`NllbTranslatorEngine`)

`NllbTranslatorEngine.load(modelDir, cachedTokenizer, tuning)` minimizes cold-start time and memory pressure without duplicating weights or pruning vocabulary:

- **Parallel tokenizer & encoder initialization**: When `cachedTokenizer` is null (cold launch), `NllbTokenizer.loadWithInfo(tokenizerFile)` runs on a background executor concurrently with `encoderSession` creation; `decoderSession` loads immediately afterward so encoder and decoder peak init allocations do not overlap. When reloading after background idle eviction, `cachedTokenizer` is reused in `0 ms`.
- **File-path mmap & `OrtTuningConfig`**: Passes `encoderFile.absolutePath` and `decoderFile.absolutePath` so ORT memory-maps `encoder.bin`, `decoder.bin`, and `shared.bin`. Default `OrtTuningConfig` configures `OptLevel.NO_OPT` (since `boat1603`'s mobile graphs are already pre-optimized), `session.disable_prepacking = "1"` (avoiding redundant cold-start weight copies), `setCPUArenaAllocator(false)`, `setMemoryPatternOptimization(false)`, `intraOpThreads = 2` (matching big performance cores), and `interOpThreads = 1`.
- **Load telemetry (`EngineLoadMetrics`)**: Records `tokenizerMs`, `encoderMs`, `decoderMs`, `totalMs`, and `tokenizerFromCache` for display in `ModelManagerSheet`.

### Encoder & 12-Layer Merged KV-Cache Decoder Loop

`translate(text, sourceLangCode, targetLangCode)` splits multi-line input on `'\n'` and translates each non-blank line independently (`translateSingleLine`):

1. **Encoder pass**: Feeds `[1, encLen]` `input_ids` and `attention_mask` into `encoderSession` to produce `last_hidden_state` (`[1, encLen, 1024]`).
2. **Decoder Step 0 (`use_cache_branch = false`)**: Passes `input_ids = [[EOS_ID]]` (`2L`), `encoder_hidden_states`, `encoder_attention_mask`, and 48 empty `FLOAT` tensors (`[1, 16, 0, 64]`) for `past_key_values.{0..11}.{decoder,encoder}.{key,value}`. The resulting `present.{0..11}.encoder.{key,value}` tensors are captured once from `step0Result` and reused across every subsequent decode step; `step0Result` stays open until the `finally` block.
3. **Decoder Step 1+ (`use_cache_branch = true`)**: Step 1 forces `input_ids = [[targetLangId]]` to condition generation on the target FLORES-200 language; subsequent steps feed the previous `next_token` (`int64 [1, 1]`) and `prevDecoderResult`'s `present.*.decoder.{key,value}` tensors. Each non-step-0 `prevDecoderResult` is closed immediately after the next step completes.
4. **Stopping & loop guards**: Stops when `next_token == EOS_ID` (`2L`), when `generated.size` reaches `(encLen * 3 + 24).coerceIn(32, 256)`, or when `isDegenerateLoop` detects a single token repeated 8 times consecutively or a 4-token cycle repeated 4 times consecutively.

## Verification

`NllbEngineIntegrationTest` runs against real model files (`-Dgloss.model.dir`) to verify both `tokenizer.glossbin` cache generation/reuse with exact encode/decode parity across English, Spanish, Hindi, and Japanese, and accurate multi-language phrase and single-word translations (`eng_Latn -> spa_Latn`, `spa_Latn -> eng_Latn`, `eng_Latn -> fra_Latn`, `eng_Latn -> hin_Deva`, `biblioteca -> library`, `water -> agua`).

## Related Pages

- [App Architecture & State Lifecycle](../architecture/overview.md)
- [Word Segmentation, Tap-to-Gloss & 202-Language Catalog](word-gloss-and-languages.md)
- [Pinned Model Mirror & Resumable Downloads](../models/pinned-distribution.md)
- [Build, Testing & GitHub Release Pipeline](../operations/build-testing-release.md)
