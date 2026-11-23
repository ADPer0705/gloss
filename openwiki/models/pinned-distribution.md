---
type: distribution
title: Pinned Model Mirror & Resumable Downloads
description: How Gloss pins the INT8 mobile NLLB-200 ONNX model to a public Hugging Face mirror with SHA-256 verification, manual cross-host HTTP Range resume, and storage lifecycle controls.
tags:
  - huggingface
  - model-distribution
  - sha256
  - downloads
  - storage
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T09:50:05.804Z
sources:
  - id: openwiki-source-dea163140b114b84409ae2a8
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/model/ModelCatalog.kt
  - id: openwiki-source-07f2d80909a5cb8c320fac54
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/model/ModelManager.kt
  - id: openwiki-source-30395db9b61f4a9485b06bc0
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/DownloadCard.kt
  - id: openwiki-source-fe2874c01d32a5f3a28bf41b
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/ModelManagerSheet.kt
generated: { by: "antigravity", at: "2026-11-23T09:50:05.804Z" }
---

# Pinned Model Mirror & Resumable Downloads

To ensure Gloss never breaks if upstream model files are removed or updated incompatibly, the app downloads its offline translation weights from a pinned commit SHA on a dedicated public Hugging Face mirror and verifies every artifact byte-for-byte with SHA-256.

## Pinned Repository & File Manifest

`ModelCatalog` targets `ADPer/nllb-200-distilled-600M-mobile` at commit revision `7339939c0b80d2228236f9111c0de1bd22d03b11` (`https://huggingface.co/ADPer/nllb-200-distilled-600M-mobile/resolve/7339939c0b80d2228236f9111c0de1bd22d03b11`). All six files total `658,182,611` bytes (~628 MiB / 658 MB):

| File | Exact Size (Bytes) | Purpose |
| --- | ---: | --- |
| `encoder.onnx` | 4,590,467 | NLLB encoder graph referencing external weights |
| `encoder.bin` | 155,353,088 | Page-aligned INT8 encoder weights |
| `decoder.onnx` | 10,115,640 | Merged NLLB decoder graph (with/without KV cache) |
| `decoder.bin` | 208,437,248 | Page-aligned INT8 decoder weights |
| `shared.bin` | 262,354,944 | Shared transposed INT8 vocabulary table |
| `tokenizer.json` | 17,331,224 | NLLB-200 BPE vocabulary, merges, and 202 language tokens |

All six files must live in the same directory (`filesDir/models/nllb-200-distilled-600M-mobile`) so ONNX Runtime resolves external `.bin` weight paths relative to the `.onnx` graphs.

## Attribution & Licensing

`ModelCatalog` records upstream credit to Meta AI (`facebook/nllb-200-distilled-600M`), Xenova (INT8 ONNX export), and `boat1603` (mobile `MatMulInteger` / shared-vocabulary rewrite), under the **CC-BY-NC-4.0** license. `ModelManagerSheet` exposes these credits, the pinned repository ID and revision, and a direct **View on HF** link alongside the app's MIT-licensed code.

## Resumable Download & Integrity Pipeline

`ModelManager.downloadModel` executes on `Dispatchers.IO` and provides resilience on mobile networks:

1. **Pre-flight storage check**: Computes required remaining bytes plus a `100 MB` safety buffer (minimum `150 MB`) against `StatFs.availableBytes` and fails fast with a clear storage error if space is insufficient.
2. **Manual redirect & Range resume**: Hugging Face LFS resolve URLs redirect across domains to external CDN hosts where Android `HttpURLConnection` does not reliably preserve `Range` headers under automatic redirect handling. `openRedirectedConnection` disables `instanceFollowRedirects`, follows `301/302/303/307/308` up to `10` hops manually, and attaches `Range: bytes=<existingLen>-` when a `.part` file exists.
3. **HTTP status handling**:
   - `206 Partial Content`: appends to `<name>.part`.
   - `200 OK`: restarts from byte `0` cleanly.
   - `416 Range Not Satisfiable`: deletes `<name>.part` and retries from byte `0`.
4. **Throttled progress & cancellation**: Emits `DownloadProgress` updates roughly every `200 ms` with byte counts, file index (`1/6..6/6`), and instantaneous throughput (`bytesPerSecond`), checking `coroutineContext.ensureActive()` so user cancellation stops immediately while keeping `.part` progress for later resumption.
5. **SHA-256 verification & atomic promotion**: After each file matches its expected `sizeBytes`, `ModelManager` computes its SHA-256 digest (`128 KB` buffer). On mismatch, `.part` is deleted and an `IOException` is thrown; on match, `.part` is renamed to the final filename.
6. **Completion marker**: Once all six files verify, stale `.verified*` markers are pruned and `.verified_<revPrefix>` is written. `isModelReady()` checks both the marker and exact byte lengths of all six files.

## UI Controls (`DownloadCard` & `ModelManagerSheet`)

- **`DownloadCard`**: Shown inline on the main screen whenever `ModelState` is not `Ready`. Displays download CTA, progress bar with percentage and MB/s throughput, **Cancel**, loading spinner during ORT memory mapping, and **Resume Download** / **Retry Load** on failure.
- **`ModelManagerSheet`**: Opened by tapping the top-bar status pill. Shows current engine state, installed storage footprint, pinned HF mirror details, and a **Delete Model** button guarded by a confirmation `AlertDialog` that invokes `EngineRepository.deleteModel()` to close active ONNX sessions before deleting files.

## Related Pages

- [App Architecture & State Lifecycle](../architecture/overview.md)
- [NLLB-200 ONNX Engine & BPE Tokenizer](../translation/nllb-engine.md)
- [Gloss Quickstart & Navigation Map](../quickstart.md)
