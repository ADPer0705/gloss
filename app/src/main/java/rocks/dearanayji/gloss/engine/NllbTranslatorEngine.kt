package rocks.dearanayji.gloss.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class EngineLoadMetrics(
    val tokenizerMs: Long,
    val encoderMs: Long,
    val decoderMs: Long,
    val totalMs: Long,
    val tokenizerFromCache: Boolean
)

data class OrtTuningConfig(
    val optLevel: OrtSession.SessionOptions.OptLevel = OrtSession.SessionOptions.OptLevel.NO_OPT,
    val memoryPatternOptimization: Boolean = false,
    val cpuArenaAllocator: Boolean = false,
    val disablePrepacking: Boolean = true,
    val intraOpThreads: Int = 2
)

/**
 * On-device offline NLLB-200 translator powered by ONNX Runtime (`encoder.onnx` + `decoder.onnx`).
 *
 * Uses file-path session initialization so ORT memory-maps the external INT8 weights
 * (`encoder.bin`, `decoder.bin`, `shared.bin`) rather than copying them onto the JVM heap.
 */
class NllbTranslatorEngine private constructor(
    private val env: OrtEnvironment,
    private val encoderSession: OrtSession,
    private val decoderSession: OrtSession,
    val tokenizer: NllbTokenizer,
    val loadMetrics: EngineLoadMetrics
) : AutoCloseable {

    fun translate(text: String, sourceLangCode: String, targetLangCode: String): String {
        if (text.isBlank()) return ""
        if (sourceLangCode == targetLangCode) return text

        val lines = text.split('\n')
        return lines.joinToString("\n") { line ->
            if (line.isBlank()) line else translateSingleLine(line, sourceLangCode, targetLangCode)
        }
    }

    private fun translateSingleLine(text: String, sourceLangCode: String, targetLangCode: String): String {
        val inputIds = tokenizer.encode(text, sourceLangCode)
        val targetLangId = tokenizer.languageTokenId(targetLangCode)
        val encLen = inputIds.size
        val encShape = longArrayOf(1L, encLen.toLong())
        val attentionMask = LongArray(encLen) { 1L }

        val toClose = mutableListOf<AutoCloseable>()
        var encoderResult: OrtSession.Result? = null
        var step0Result: OrtSession.Result? = null
        var prevDecoderResult: OrtSession.Result? = null

        try {
            val encInputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), encShape).also { toClose += it }
            val encMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), encShape).also { toClose += it }

            encoderResult = encoderSession.run(
                mapOf(
                    "input_ids" to encInputIdsTensor,
                    "attention_mask" to encMaskTensor
                )
            )
            val encoderHiddenStates = encoderResult.get("last_hidden_state").get() as OnnxTensor

            // Pre-build reusable empty KV tensors & boolean branch flags
            val emptyKvShape = longArrayOf(1L, NUM_HEADS, 0L, HEAD_DIM)
            val emptyKvTensor = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyKvShape).also { toClose += it }
            val useCacheFalse = createBoolTensor(false).also { toClose += it }
            val useCacheTrue = createBoolTensor(true).also { toClose += it }

            val eosInput = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(NllbTokenizer.EOS_ID)), longArrayOf(1L, 1L)).also { toClose += it }

            // Step 0: initialize encoder KV cache + first decoder KV cache for </s>
            val step0Inputs = HashMap<String, OnnxTensor>(54)
            step0Inputs["encoder_attention_mask"] = encMaskTensor
            step0Inputs["input_ids"] = eosInput
            step0Inputs["encoder_hidden_states"] = encoderHiddenStates
            step0Inputs["use_cache_branch"] = useCacheFalse
            for (layer in 0 until NUM_LAYERS) {
                step0Inputs["past_key_values.$layer.decoder.key"] = emptyKvTensor
                step0Inputs["past_key_values.$layer.decoder.value"] = emptyKvTensor
                step0Inputs["past_key_values.$layer.encoder.key"] = emptyKvTensor
                step0Inputs["past_key_values.$layer.encoder.value"] = emptyKvTensor
            }

            step0Result = decoderSession.run(step0Inputs)

            // Encoder KV cache is computed once on step 0 and reused unchanged for every step
            val encoderKvCache = HashMap<String, OnnxTensor>(NUM_LAYERS * 2)
            for (layer in 0 until NUM_LAYERS) {
                encoderKvCache["past_key_values.$layer.encoder.key"] =
                    step0Result.get("present.$layer.encoder.key").get() as OnnxTensor
                encoderKvCache["past_key_values.$layer.encoder.value"] =
                    step0Result.get("present.$layer.encoder.value").get() as OnnxTensor
            }

            prevDecoderResult = step0Result

            val maxNewTokens = (encLen * 3 + 24).coerceIn(32, 256)
            val generated = ArrayList<Long>(32)

            // Step 1 forces targetLangId; subsequent steps feed the previously predicted next_token
            var currentInputId = targetLangId
            for (step in 0 until maxNewTokens) {
                val stepInputTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(currentInputId)),
                    longArrayOf(1L, 1L)
                )
                val stepInputs = HashMap<String, OnnxTensor>(54)
                stepInputs["encoder_attention_mask"] = encMaskTensor
                stepInputs["input_ids"] = stepInputTensor
                stepInputs["encoder_hidden_states"] = encoderHiddenStates
                stepInputs["use_cache_branch"] = useCacheTrue
                stepInputs.putAll(encoderKvCache)
                for (layer in 0 until NUM_LAYERS) {
                    stepInputs["past_key_values.$layer.decoder.key"] =
                        prevDecoderResult!!.get("present.$layer.decoder.key").get() as OnnxTensor
                    stepInputs["past_key_values.$layer.decoder.value"] =
                        prevDecoderResult!!.get("present.$layer.decoder.value").get() as OnnxTensor
                }

                val currentResult = try {
                    decoderSession.run(stepInputs)
                } finally {
                    stepInputTensor.close()
                }

                if (prevDecoderResult != null && prevDecoderResult !== step0Result) {
                    prevDecoderResult.close()
                }
                prevDecoderResult = currentResult

                val nextTokenTensor = currentResult.get("next_token").get() as OnnxTensor
                val nextToken = nextTokenTensor.longBuffer.get(0)

                if (nextToken == NllbTokenizer.EOS_ID) break
                generated += nextToken
                if (isDegenerateLoop(generated)) break
                currentInputId = nextToken
            }

            return tokenizer.decode(generated)
        } finally {
            if (prevDecoderResult != null && prevDecoderResult !== step0Result) {
                runCatching { prevDecoderResult.close() }
            }
            runCatching { step0Result?.close() }
            runCatching { encoderResult?.close() }
            for (res in toClose.asReversed()) {
                runCatching { res.close() }
            }
        }
    }

    private fun createBoolTensor(value: Boolean): OnnxTensor {
        val buf = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder())
        buf.put(if (value) 1.toByte() else 0.toByte())
        buf.rewind()
        return OnnxTensor.createTensor(env, buf, longArrayOf(1L), ai.onnxruntime.OnnxJavaType.BOOL)
    }

    private fun isDegenerateLoop(tokens: List<Long>): Boolean {
        val n = tokens.size
        // Same single token repeated 8 times in a row
        if (n >= 8) {
            val last = tokens[n - 1]
            if ((2..8).all { tokens[n - it] == last }) return true
        }
        // 4-token cycle repeated 4 times consecutively
        if (n >= 16) {
            var match = true
            for (k in 0 until 4) {
                val t = tokens[n - 1 - k]
                if (tokens[n - 5 - k] != t || tokens[n - 9 - k] != t || tokens[n - 13 - k] != t) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    override fun close() {
        runCatching { decoderSession.close() }
        runCatching { encoderSession.close() }
    }

    companion object {
        private const val NUM_LAYERS = 12
        private const val NUM_HEADS = 16L
        private const val HEAD_DIM = 64L

        fun load(
            modelDir: File,
            cachedTokenizer: NllbTokenizer? = null,
            tuning: OrtTuningConfig = OrtTuningConfig()
        ): NllbTranslatorEngine {
            val totalStart = System.nanoTime()
            val encoderFile = File(modelDir, "encoder.onnx")
            val decoderFile = File(modelDir, "decoder.onnx")
            val tokenizerFile = File(modelDir, "tokenizer.json")
            require(encoderFile.isFile) { "Missing ${encoderFile.name}" }
            require(decoderFile.isFile) { "Missing ${decoderFile.name}" }
            require(tokenizerFile.isFile) { "Missing ${tokenizerFile.name}" }

            val env = OrtEnvironment.getEnvironment()
            val tokExecutor = if (cachedTokenizer == null) Executors.newSingleThreadExecutor() else null
            var tokMs = 0L
            var tokFromCache = cachedTokenizer != null

            val tokFuture = tokExecutor?.submit(
                Callable {
                    val t0 = System.nanoTime()
                    val res = NllbTokenizer.loadWithInfo(tokenizerFile)
                    tokMs = (System.nanoTime() - t0) / 1_000_000L
                    tokFromCache = res.fromCache
                    res.tokenizer
                }
            )

            val encOpts = buildSessionOptions(tuning)
            val decOpts = buildSessionOptions(tuning)

            val encStart = System.nanoTime()
            val encoderSession = try {
                env.createSession(encoderFile.absolutePath, encOpts)
            } catch (t: Throwable) {
                tokExecutor?.shutdownNow()
                encOpts.close()
                decOpts.close()
                throw t
            }
            val encoderMs = (System.nanoTime() - encStart) / 1_000_000L

            val tokenizer = try {
                cachedTokenizer ?: tokFuture!!.get()
            } catch (t: Throwable) {
                runCatching { encoderSession.close() }
                encOpts.close()
                decOpts.close()
                throw t
            } finally {
                tokExecutor?.shutdown()
            }

            val decStart = System.nanoTime()
            val decoderSession = try {
                env.createSession(decoderFile.absolutePath, decOpts)
            } catch (t: Throwable) {
                runCatching { encoderSession.close() }
                throw t
            } finally {
                encOpts.close()
                decOpts.close()
            }
            val decoderMs = (System.nanoTime() - decStart) / 1_000_000L
            val totalMs = (System.nanoTime() - totalStart) / 1_000_000L

            return NllbTranslatorEngine(
                env = env,
                encoderSession = encoderSession,
                decoderSession = decoderSession,
                tokenizer = tokenizer,
                loadMetrics = EngineLoadMetrics(
                    tokenizerMs = tokMs,
                    encoderMs = encoderMs,
                    decoderMs = decoderMs,
                    totalMs = totalMs,
                    tokenizerFromCache = tokFromCache
                )
            )
        }

        private fun buildSessionOptions(tuning: OrtTuningConfig): OrtSession.SessionOptions {
            val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(tuning.intraOpThreads).coerceAtLeast(1)
            return OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setInterOpNumThreads(1)
                setOptimizationLevel(tuning.optLevel)
                setMemoryPatternOptimization(tuning.memoryPatternOptimization)
                setCPUArenaAllocator(tuning.cpuArenaAllocator)
                if (tuning.disablePrepacking) {
                    addConfigEntry("session.disable_prepacking", "1")
                }
            }
        }
    }
}
