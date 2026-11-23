# Files

- [NLLB-200 ONNX Engine & BPE Tokenizer](nllb-engine.md) - Offline seq2seq inference pipeline combining a streaming low-RAM BPE tokenizer with memory-mapped INT8 ONNX Runtime encoder and 12-layer KV-cached decoder sessions.
- [Word Segmentation, Tap-to-Gloss & 202-Language Catalog](word-gloss-and-languages.md) - How Gloss implements Duolingo-style tappable words in both source and translated sentences across all 202 NLLB-200 languages using Unicode BreakIterator segmentation and LRU-cached lookups.
