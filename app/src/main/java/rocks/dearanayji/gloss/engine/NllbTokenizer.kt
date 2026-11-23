package rocks.dearanayji.gloss.engine

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * Pure-Kotlin streaming BPE tokenizer for NLLB-200 (`tokenizer.json`).
 *
 * Uses a primitive open-addressing hash table for the ~514k BPE merges so the
 * full 256k-vocabulary tokenizer fits comfortably in ~28 MB of JVM heap
 * without boxed Pair objects.
 */
class NllbTokenizer private constructor(
    private val tokenToId: HashMap<String, Int>,
    private val idToToken: Array<String>,
    private val merges: MergeTable,
    private val languageIds: HashMap<String, Int>,
    private val specialIds: HashSet<Int>
) {

    fun languageTokenId(langCode: String): Long {
        val id = languageIds[langCode] ?: tokenToId[langCode]
            ?: throw IllegalArgumentException("Unsupported NLLB language code: $langCode")
        return id.toLong()
    }

    fun hasLanguage(langCode: String): Boolean =
        languageIds.containsKey(langCode) || tokenToId.containsKey(langCode)

    fun encode(text: String, sourceLangCode: String): LongArray {
        val srcId = languageTokenId(sourceLangCode)
        val normalized = normalizeText(text)
        if (normalized.isEmpty()) {
            return longArrayOf(srcId, EOS_ID)
        }

        // Metaspace pre-tokenizer: replace spaces with U+2581 and prepend U+2581
        val metaspace = buildString(normalized.length + 1) {
            append(METASPACE)
            for (ch in normalized) {
                if (ch == ' ') append(METASPACE) else append(ch)
            }
        }

        // Convert codepoints to initial vocab IDs with fuse_unk = true
        val initial = IntArray(metaspace.length)
        var len = 0
        var i = 0
        while (i < metaspace.length) {
            val cp = Character.codePointAt(metaspace, i)
            val charCount = Character.charCount(cp)
            val piece = metaspace.substring(i, i + charCount)
            val id = tokenToId[piece] ?: UNK_ID
            if (id == UNK_ID && len > 0 && initial[len - 1] == UNK_ID) {
                // fuse consecutive <unk>
            } else {
                initial[len++] = id
            }
            i += charCount
        }

        // BPE merge loop
        var buf = initial
        var curLen = len
        while (curLen >= 2) {
            var bestRank = Int.MAX_VALUE
            var bestLeft = -1
            var bestRight = -1
            var bestMerged = -1

            for (pos in 0 until curLen - 1) {
                val left = buf[pos]
                val right = buf[pos + 1]
                val idx = merges.findSlot(left, right)
                if (idx >= 0) {
                    val rank = merges.rankAt(idx)
                    if (rank < bestRank) {
                        bestRank = rank
                        bestLeft = left
                        bestRight = right
                        bestMerged = merges.mergedAt(idx)
                    }
                }
            }

            if (bestRank == Int.MAX_VALUE) break

            var writePos = 0
            var readPos = 0
            while (readPos < curLen) {
                if (readPos + 1 < curLen && buf[readPos] == bestLeft && buf[readPos + 1] == bestRight) {
                    buf[writePos++] = bestMerged
                    readPos += 2
                } else {
                    buf[writePos++] = buf[readPos++]
                }
            }
            curLen = writePos
        }

        val cappedLen = curLen.coerceAtMost(MAX_SOURCE_TOKENS)
        val out = LongArray(cappedLen + 2)
        out[0] = srcId
        for (idx in 0 until cappedLen) {
            out[idx + 1] = buf[idx].toLong()
        }
        out[cappedLen + 1] = EOS_ID
        return out
    }

    fun decode(tokenIds: List<Long>): String {
        if (tokenIds.isEmpty()) return ""
        val sb = StringBuilder(tokenIds.size * 4)
        for (rawId in tokenIds) {
            val id = rawId.toInt()
            if (id <= 3 || specialIds.contains(id) || id < 0 || id >= idToToken.size) continue
            val tok = idToToken[id]
            if (tok.isNotEmpty()) sb.append(tok)
        }
        if (sb.isEmpty()) return ""
        for (idx in sb.indices) {
            if (sb[idx] == METASPACE) sb.setCharAt(idx, ' ')
        }
        return if (sb[0] == ' ') sb.substring(1) else sb.toString()
    }

    private fun normalizeText(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return MULTI_SPACE.replace(nfkc, " ").trim()
    }

    private class MergeTable {
        private val capacity = 1 shl 20 // 1,048,576 slots (~49% load for 514k merges)
        private val mask = capacity - 1
        private val keys = LongArray(capacity)
        private val ranks = IntArray(capacity)
        private val mergedIds = IntArray(capacity)

        private fun packKey(left: Int, right: Int): Long =
            ((left.toLong() shl 20) or (right.toLong() and 0xFFFFFL)) + 1L

        private fun hash(key: Long): Int {
            var h = key xor (key ushr 33)
            h *= -0xae502812aa7333L
            h = h xor (h ushr 33)
            return h.toInt() and mask
        }

        fun put(left: Int, right: Int, rank: Int, mergedId: Int) {
            val key = packKey(left, right)
            var slot = hash(key)
            while (true) {
                val existing = keys[slot]
                if (existing == 0L) {
                    keys[slot] = key
                    ranks[slot] = rank
                    mergedIds[slot] = mergedId
                    return
                }
                if (existing == key) return
                slot = (slot + 1) and mask
            }
        }

        fun findSlot(left: Int, right: Int): Int {
            val key = packKey(left, right)
            var slot = hash(key)
            while (true) {
                val existing = keys[slot]
                if (existing == 0L) return -1
                if (existing == key) return slot
                slot = (slot + 1) and mask
            }
        }

        fun rankAt(slot: Int): Int = ranks[slot]
        fun mergedAt(slot: Int): Int = mergedIds[slot]
    }

    companion object {
        const val BOS_ID = 0L
        const val PAD_ID = 1L
        const val EOS_ID = 2L
        const val UNK_ID = 3
        private const val METASPACE = '\u2581'
        private const val MAX_SOURCE_TOKENS = 508
        private val MULTI_SPACE = Regex(" {2,}")

        fun load(tokenizerFile: File): NllbTokenizer {
            val tokenToId = HashMap<String, Int>(300_000, 0.75f)
            val idList = ArrayList<Pair<Int, String>>(260_000)
            val languageIds = HashMap<String, Int>(256)
            val specialIds = HashSet<Int>(256)
            val merges = MergeTable()
            var maxId = 256_204

            JsonReader(BufferedReader(InputStreamReader(FileInputStream(tokenizerFile), Charsets.UTF_8), 65_536)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "added_tokens" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                var id = -1
                                var content = ""
                                var special = false
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextInt()
                                        "content" -> content = reader.nextString()
                                        "special" -> special = reader.nextBoolean()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (id >= 0 && content.isNotEmpty()) {
                                    tokenToId[content] = id
                                    idList += id to content
                                    if (id > maxId) maxId = id
                                    if (special) specialIds += id
                                    if ('_' in content && !content.startsWith("<")) {
                                        languageIds[content] = id
                                    }
                                }
                            }
                            reader.endArray()
                        }
                        "model" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "vocab" -> {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            val token = reader.nextName()
                                            val id = reader.nextInt()
                                            tokenToId[token] = id
                                            idList += id to token
                                            if (id > maxId) maxId = id
                                        }
                                        reader.endObject()
                                    }
                                    "merges" -> {
                                        reader.beginArray()
                                        var rank = 0
                                        while (reader.hasNext()) {
                                            val entry = if (reader.peek() == JsonToken.STRING) {
                                                reader.nextString()
                                            } else {
                                                reader.beginArray()
                                                val a = reader.nextString()
                                                val b = reader.nextString()
                                                reader.endArray()
                                                "$a $b"
                                            }
                                            val sp = entry.indexOf(' ')
                                            if (sp > 0) {
                                                val leftStr = entry.substring(0, sp)
                                                val rightStr = entry.substring(sp + 1)
                                                val leftId = tokenToId[leftStr]
                                                val rightId = tokenToId[rightStr]
                                                val mergedId = tokenToId[leftStr + rightStr]
                                                if (leftId != null && rightId != null && mergedId != null) {
                                                    merges.put(leftId, rightId, rank, mergedId)
                                                }
                                            }
                                            rank++
                                        }
                                        reader.endArray()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }

            val idToToken = Array(maxId + 1) { "" }
            for ((id, tok) in idList) {
                if (id in idToToken.indices) idToToken[id] = tok
            }

            return NllbTokenizer(
                tokenToId = tokenToId,
                idToToken = idToToken,
                merges = merges,
                languageIds = languageIds,
                specialIds = specialIds
            )
        }
    }
}
