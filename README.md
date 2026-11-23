# gloss

translate a phrase. tap any word. that's it.

no account. no cloud. no tracking — everything runs 100% offline on your phone using **NLLB-200-distilled-600M** (INT8 mobile ONNX) after a one-time ~628 MB (~658 MB) model download.

---

## Features

- **202 languages offline** — powered by Meta AI's NLLB-200-distilled-600M quantized to INT8 for mobile RAM budgets (~1.4 GB peak RAM).
- **Duolingo-style tap-a-word** — tap any word in **either** your input sentence or the translated output to see that word's individual meaning immediately.
- **Instant language swap** — flip source and target languages in one tap, carrying text across cleanly.
- **Searchable language picker** — search by English name, native script name, script family, or FLORES-200 code, with Recent and Popular chips.
- **Offline pronunciation & history** — listen using Android's on-device Text-to-Speech and revisit recent phrases locally.
- **Pinned model mirror** — downloads SHA-256-verified weights from a pinned Hugging Face revision ([`ADPer/nllb-200-distilled-600M-mobile`](https://huggingface.co/ADPer/nllb-200-distilled-600M-mobile)) so the app never breaks if upstream files move.

---

## Model Attribution & Licensing

- **App source code**: [MIT License](LICENSE).
- **Translation model**: `CC-BY-NC-4.0` (non-commercial use only, attribution required):
  - **Meta AI**: [`facebook/nllb-200-distilled-600M`](https://huggingface.co/facebook/nllb-200-distilled-600M) (NLLB Team, *"No Language Left Behind: Scaling Human-Centered Machine Translation"*, 2022)
  - **Xenova**: [`Xenova/nllb-200-distilled-600M`](https://huggingface.co/Xenova/nllb-200-distilled-600M) (INT8 ONNX export)
  - **boat1603**: [`boat1603/nllb-200-distilled-600M-mobile`](https://huggingface.co/boat1603/nllb-200-distilled-600M-mobile) (mobile `MatMulInteger` / shared-vocab ONNX rewrite)
  - **Pinned mirror**: [`ADPer/nllb-200-distilled-600M-mobile`](https://huggingface.co/ADPer/nllb-200-distilled-600M-mobile) (rev `7339939c0b80d2228236f9111c0de1bd22d03b11`)

---

## Build & Test

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease
```
