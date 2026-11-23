---
type: distribution
title: Pinned Model Mirror & Resumable Downloads
description: How Gloss pins the INT8 mobile NLLB-200 ONNX model to a public Hugging Face mirror with SHA-256 verification, HF download tracking via config.json, manual cross-host HTTP Range resume, and storage lifecycle controls.
tags:
  - huggingface
  - model-distribution
  - sha256
  - downloads
  - storage
verified:
  - by: openwiki/0.9.0
    at: 2026-11-23T17:33:47.960Z
sources:
  - id: openwiki-source-dea163140b114b84409ae2a8
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/model/ModelCatalog.kt
  - id: openwiki-source-07f2d80909a5cb8c320fac54
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/model/ModelManager.kt
  - id: openwiki-source-30395db9b61f4a9485b06bc0
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/DownloadCard.kt
  - id: openwiki-source-fe2874c01d32a5f3a28bf41b
    resource: repo://app/src/main/java/rocks/dearanayji/gloss/ui/components/ModelManagerSheet.kt
generated: { by: "antigravity", at: "2026-11-23T17:33:47.960Z" }
---

# Pinned Model Mirror & Resumable Downloads

To ensure Gloss never breaks if upstream model files are removed or updated incompatibly, the app downloads its offline translation weights from a pinned commit SHA on a dedicated public Hugging Face mirror and verifies every artifact byte-for-byte with SHA-256.

## Pinned Repository & File Manifest

`ModelCatalog` targets `ADPer/nllb-200-distilled-600M-mobile` at commit revision `c998583de40ac4331dce8e8d7254d4456696133a` (`https://huggingface.co/ADPer/nllb-200-distilled-600M-mobile/resolve/c998583de40ac4331dce8e8d7254d4456696133a`). All six required model files total `658,182,611` bytes (~628 MiB / 658 MB), alongside `config.json` (`ModelCatalog.CONFIG_URL`) for Hugging Face download tracking:

| File | Exact Size (Bytes) | Purpose |
| --- | ---: | --- |
| `encoder.onnx` | 4,590,467 | NLLB encoder graph referencing external weights |
| `encoder.bin` | 155,353,088 | Page-aligned INT8 encoder weights |
| `decoder.onnx` | 10,115,640 | Merged NLLB decoder graph (with/without KV cache) |
| `decoder.bin` | 208,437,248 | Page-aligned INT8 decoder weights |
| `shared.bin` | 262,354,944 | Shared transposed INT8 vocabulary table |
| `tokenizer.json` | 17,331,224 | NLLB-200 BPE vocabulary, merges, and 202 language tokens |
| `config.json` | ~1 KB | Hugging Face model metadata used to count Hub downloads |

All model files live in `filesDir/models/nllb-200-distilled-600M-mobile` so ONNX Runtime resolves external `.bin` weight paths relative to the `.onnx` graphs.

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
6. **Hugging Face download tracking & completion marker**: After all six files verify, `fetchConfigForTracking()` fetches `ModelCatalog.CONFIG_URL` (`config.json`) so Hugging Face registers the download, prunes stale `.verified*` markers, and writes `.verified_<revPrefix>`. `isModelReady()` accepts any `.verified*` marker combined with exact byte lengths of the six core model files so existing installations do not re-download 658 MB across revision bumps.

## UI Controls (`DownloadCard` & `ModelManagerSheet`)

- **`DownloadCard`**: Shown inline on the main screen whenever `ModelState` is not `Ready` or `IdleDownloaded`. Displays download CTA, progress bar with percentage and MB/s throughput, **Cancel**, loading spinner during ORT memory mapping, and **Resume Download** / **Retry Load** on failure.
- **`ModelManagerSheet`**: Opened by tapping the top-bar status pill (**App & Offline Model**). Displays an **App & GitHub Releases** card (installed version, update status, total and latest release GitHub download counts, **Check updates** / **Update** / **Install** buttons, and a **Releases** link) above the **Offline Translation Model** card (engine status including `Offline ready (standby)` when idle-evicted, last cold-start breakdown via `lastLoadMetrics`, installed storage footprint, pinned HF mirror details, **View on HF**, and a **Delete Model** button guarded by a confirmation `AlertDialog` that invokes `EngineRepository.deleteModel()`).

## Related Pages

- [App Architecture & State Lifecycle](../architecture/overview.md)
- [NLLB-200 ONNX Engine & BPE Tokenizer](../translation/nllb-engine.md)
- [Gloss Quickstart & Navigation Map](../quickstart.md)
