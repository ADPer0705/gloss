---
type: translation-engine
title: NLLB-200 ONNX Engine & BPE Tokenizer
description: Offline seq2seq inference pipeline combining a streaming low-RAM BPE tokenizer with memory-mapped INT8 ONNX Runtime encoder and 12-layer KV-cached decoder sessions.
tags:
  - nllb-200
  - onnxruntime
  - bpe-tokenizer
  - kv-cache
  - inference
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T09:50:05.804Z
sources:
  - id: openwiki-source-7d7956c8ca8fd0517516fd13
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/engine/NllbTokenizer.kt
  - id: openwiki-source-38d77ad2e88dc8d72a142cfa
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/engine/NllbTranslatorEngine.kt
  - id: openwiki-source-390c26bd4202067d66977546
    resource: repo://app/src/test/java/rocks/dearanayji/gloss/engine/NllbEngineIntegrationTest.kt
generated: { by: "antigravity", at: "2026-11-23T09:50:05.804Z" }
---

# NLLB-200 ONNX Engine & BPE Tokenizer

Gloss runs Meta's `nllb-200-distilled-600M` completely offline in pure Kotlin + ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.32.0`) using a memory-lean INT8 mobile graph where weights stay memory-mapped on disk and the decoder outputs `next_token` directly instead of full vocabulary logits.

## Streaming BPE Tokenizer (`NllbTokenizer`)

Standard Hugging Face `tokenizer.json` files for NLLB-200 contain ~256k vocabulary entries and ~514k BPE merge rules. Loading them into boxed Kotlin objects would risk OOM on 4 GB Android devices. `NllbTokenizer` solves this in two ways:

- **Streaming `JsonReader` load**: `NllbTokenizer.load(tokenizerFile)` streams `added_tokens`, `model.vocab`, and `model.merges` sequentially with a `64 KB` buffer (`BufferedReader`), resolving each merge string (`"left right"`) directly into token IDs without retaining raw merge strings in memory.
- **Primitive open-addressing `MergeTable`**: Stores `(leftId, rightId) -> (rank, mergedId)` inside three primitive arrays (`LongArray(1 shl 20)`, `IntArray`, `IntArray`) using linear probing (~49% load factor, ~16 MB total).

### Encoding & Decoding Behavior

1. **Normalization**: `normalizeText` applies `Normalizer.Form.NFKC`, collapses consecutive spaces (`" {2,}"`), and trims whitespace.
2. **Metaspace pre-tokenization**: Replaces `' '` with `U+2581` (`▁`) and prepends `▁`.
3. **Codepoint lookup & `fuse_unk`**: Walks Unicode codepoints (handling surrogate pairs via `Character.codePointAt`), maps missing pieces to `UNK_ID` (`3`), and collapses consecutive `UNK_ID`s.
4. **Greedy BPE merges**: Repeatedly finds the lowest-rank adjacent pair in `MergeTable` and merges all non-overlapping left-to-right occurrences until no merge matches.
5. **Sequence framing**: Truncates content tokens to `MAX_SOURCE_TOKENS = 508` and emits `[sourceLangId, ...tokens, EOS_ID]`.
6. **Decoding**: Skips special/language tokens (`id <= 3` or `specialIds`), concatenates token strings, converts `▁` back to spaces, and strips a single leading space.

## ONNX Runtime Inference (`NllbTranslatorEngine`)

`NllbTranslatorEngine.load(modelDir)` loads `NllbTokenizer`, then opens `encoder.onnx` and `decoder.onnx` by **file path** (`encoderFile.absolutePath` / `decoderFile.absolutePath`) rather than reading `ByteArray`s into JVM memory. This allows ORT to memory-map the external INT8 weight files (`encoder.bin`, `decoder.bin`, `shared.bin`). Both sessions configure `2..4` intra-op threads (`availableProcessors().coerceIn(2, 4)`), `1` inter-op thread, `OptLevel.ALL_OPT`, and memory pattern optimization.

### Encoder & 12-Layer Merged KV-Cache Decoder Loop

`translate(text, sourceLangCode, targetLangCode)` splits multi-line input on `'\n'` and translates each non-blank line independently (`translateSingleLine`):

1. **Encoder pass**: Feeds `[1, encLen]` `input_ids` and `attention_mask` into `encoderSession` to produce `last_hidden_state` (`[1, encLen, 1024]`).
2. **Decoder Step 0 (`use_cache_branch = false`)**: Passes `input_ids = [[EOS_ID]]` (`2L`), `encoder_hidden_states`, `encoder_attention_mask`, and 48 empty `FLOAT` tensors (`[1, 16, 0, 64]`) for `past_key_values.{0..11}.{decoder,encoder}.{key,value}`. The resulting `present.{0..11}.encoder.{key,value}` tensors are captured once from `step0Result` and reused across every subsequent decode step; `step0Result` stays open until the `finally` block.
3. **Decoder Step 1+ (`use_cache_branch = true`)**: Step 1 forces `input_ids = [[targetLangId]]` to condition generation on the target FLORES-200 language; subsequent steps feed the previous `next_token` (`int64 [1, 1]`) and `prevDecoderResult`'s `present.*.decoder.{key,value}` tensors. Each non-step-0 `prevDecoderResult` is closed immediately after the next step completes.
4. **Stopping & loop guards**: Stops when `next_token == EOS_ID` (`2L`), when `generated.size` reaches `(encLen * 3 + 24).coerceIn(32, 256)`, or when `isDegenerateLoop` detects a single token repeated 8 times consecutively or a 4-token cycle repeated 4 times consecutively.

## Verification

`NllbEngineIntegrationTest` exercises the real tokenizer and ONNX Runtime sessions against the mirrored model files (`-Dgloss.model.dir`), verifying encode/decode roundtrips (English and Devanagari), multi-language phrase translation (`eng_Latn -> spa_Latn`, `spa_Latn -> eng_Latn`, `eng_Latn -> fra_Latn`, `eng_Latn -> hin_Deva`), and single-word gloss lookups (`biblioteca -> library`, `water -> agua`).

## Related Pages

- [App Architecture & State Lifecycle](../architecture/overview.md)
- [Word Segmentation, Tap-to-Gloss & 202-Language Catalog](word-gloss-and-languages.md)
- [Pinned Model Mirror & Resumable Downloads](../models/pinned-distribution.md)
- [Build, Testing & GitHub Release Pipeline](../operations/build-testing-release.md)
