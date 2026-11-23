package rocks.dearanayji.gloss.model

data class ModelFileSpec(
    val name: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Pinned Hugging Face mirror of the memory-lean INT8 mobile ONNX build of
 * Meta AI's NLLB-200-distilled-600M.
 */
object ModelCatalog {
    const val REPO_ID = "ADPer/nllb-200-distilled-600M-mobile"
    const val PINNED_REVISION = "c998583de40ac4331dce8e8d7254d4456696133a"
    const val REPO_URL = "https://huggingface.co/$REPO_ID"
    const val RESOLVE_BASE_URL = "https://huggingface.co/$REPO_ID/resolve/$PINNED_REVISION"
    const val CONFIG_URL = "$RESOLVE_BASE_URL/config.json"

    const val MODEL_DISPLAY_NAME = "NLLB-200 distilled 600M (INT8 Mobile)"
    const val LICENSE_NAME = "CC-BY-NC-4.0"
    const val UPSTREAM_CREDIT =
        "Meta AI (facebook/nllb-200-distilled-600M) · Xenova (ONNX INT8) · boat1603 (mobile MatMulInteger rewrite)"

    val FILES = listOf(
        ModelFileSpec(
            name = "encoder.onnx",
            sizeBytes = 4_590_467L,
            sha256 = "c776129b906fff1a29cddde26d7f6b1980fba3a91da5963ff759e8aba338f192"
        ),
        ModelFileSpec(
            name = "encoder.bin",
            sizeBytes = 155_353_088L,
            sha256 = "17b46e94fb7bf5cce244356d3a45d4d2c99b22791d82d0a9d3bda937406da85b"
        ),
        ModelFileSpec(
            name = "decoder.onnx",
            sizeBytes = 10_115_640L,
            sha256 = "2ff9baa71ae0ff15f848fc71e7e30c2753afffa4c1481cd42e6bca671f76d6bb"
        ),
        ModelFileSpec(
            name = "decoder.bin",
            sizeBytes = 208_437_248L,
            sha256 = "a5a9a6cfcbb7bcccf09bef86ead95ba0e6e6bfb993b005631e4fc57b72e6f053"
        ),
        ModelFileSpec(
            name = "shared.bin",
            sizeBytes = 262_354_944L,
            sha256 = "db4bec614ad38a9aacbb3c5be9e7dffb55630050b9e3994025759c5668bfd60a"
        ),
        ModelFileSpec(
            name = "tokenizer.json",
            sizeBytes = 17_331_224L,
            sha256 = "8ac789ad7dabea44d41537822d48c516ba358374c51813e2cba78c006e150c94"
        )
    )

    val TOTAL_BYTES: Long = FILES.sumOf { it.sizeBytes }
}
