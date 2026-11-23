package rocks.dearanayji.gloss.engine

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

data class TokenizerLoadResult(
    val tokenizer: NllbTokenizer,
    val fromCache: Boolean
)

/**
 * Pure-Kotlin streaming BPE tokenizer for NLLB-200 (`tokenizer.json`).
 *
 * Uses a primitive open-addressing hash table for the ~514k BPE merges and persists
 * a versioned binary snapshot (`tokenizer.glossbin`) so subsequent cold starts skip
 * 17 MB JSON parsing and 1.5M+ hash lookups.
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

    private fun writeBinaryCache(cacheFile: File, sourceLength: Long) {
        val tmpFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 65_536)).use { out ->
            out.writeInt(CACHE_MAGIC)
            out.writeInt(CACHE_VERSION)
            out.writeLong(sourceLength)

            out.writeInt(idToToken.size)
            for (tok in idToToken) {
                if (tok.isEmpty()) {
                    out.writeInt(0)
                } else {
                    val bytes = tok.toByteArray(Charsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }

            out.writeInt(languageIds.size)
            for ((code, id) in languageIds) {
                val bytes = code.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
                out.writeInt(id)
            }

            out.writeInt(specialIds.size)
            for (id in specialIds) {
                out.writeInt(id)
            }

            merges.writeTo(out)
            out.flush()
        }
        if (cacheFile.exists()) cacheFile.delete()
        if (!tmpFile.renameTo(cacheFile)) {
            tmpFile.delete()
        }
    }

    private class MergeTable {
        val capacity = 1 shl 20 // 1,048,576 slots (~49% load for 514k merges)
        private val mask = capacity - 1
        val keys = LongArray(capacity)
        val ranks = IntArray(capacity)
        val mergedIds = IntArray(capacity)

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

        fun writeTo(out: DataOutputStream) {
            out.writeInt(capacity)
            val chunk = ByteArray(CHUNK_LONGS * 8)
            val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 0
            while (offset < capacity) {
                val count = minOf(CHUNK_LONGS, capacity - offset)
                bb.clear()
                bb.asLongBuffer().put(keys, offset, count)
                out.write(chunk, 0, count * 8)
                offset += count
            }

            val intChunk = ByteArray(CHUNK_INTS * 4)
            val ibb = ByteBuffer.wrap(intChunk).order(ByteOrder.LITTLE_ENDIAN)
            offset = 0
            while (offset < capacity) {
                val count = minOf(CHUNK_INTS, capacity - offset)
                ibb.clear()
                ibb.asIntBuffer().put(ranks, offset, count)
                out.write(intChunk, 0, count * 4)
                offset += count
            }
            offset = 0
            while (offset < capacity) {
                val count = minOf(CHUNK_INTS, capacity - offset)
                ibb.clear()
                ibb.asIntBuffer().put(mergedIds, offset, count)
                out.write(intChunk, 0, count * 4)
                offset += count
            }
        }

        fun readFrom(input: DataInputStream) {
            val cap = input.readInt()
            require(cap == capacity) { "Unexpected MergeTable capacity: $cap" }

            val chunk = ByteArray(CHUNK_LONGS * 8)
            val bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 0
            while (offset < capacity) {
                val count = minOf(CHUNK_LONGS, capacity - offset)
                input.readFully(chunk, 0, count * 8)
                bb.clear()
                bb.asLongBuffer().get(keys, offset, count)
                offset += count
            }

            val intChunk = ByteArray(CHUNK_INTS * 4)
            val ibb = ByteBuffer.wrap(intChunk).order(ByteOrder.LITTLE_ENDIAN)
            offset = 0
            while (offset < capacity) {
                val count = minOf(CHUNK_INTS, capacity - offset)
                input.readFully(intChunk, 0, count * 4)
                ibb.clear()
                ibb.asIntBuffer().get(ranks, offset, count)
                offset += count
            }
            offset = 0
            while (offset < capacity) {
                val count = minOf(CHUNK_INTS, capacity - offset)
                input.readFully(intChunk, 0, count * 4)
                ibb.clear()
                ibb.asIntBuffer().get(mergedIds, offset, count)
                offset += count
            }
        }

        companion object {
            private const val CHUNK_LONGS = 16_384
            private const val CHUNK_INTS = 32_768
        }
    }

    companion object {
        const val BOS_ID = 0L
        const val PAD_ID = 1L
        const val EOS_ID = 2L
        const val UNK_ID = 3
        private const val METASPACE = '\u2581'
        private const val MAX_SOURCE_TOKENS = 508
        private val MULTI_SPACE = Regex(" {2,}")

        private const val CACHE_FILE_NAME = "tokenizer.glossbin"
        private const val CACHE_MAGIC = 0x474C544B // "GLTK"
        private const val CACHE_VERSION = 1

        fun load(tokenizerFile: File): NllbTokenizer = loadWithInfo(tokenizerFile).tokenizer

        fun loadWithInfo(tokenizerFile: File): TokenizerLoadResult {
            val cacheFile = File(tokenizerFile.parentFile, CACHE_FILE_NAME)
            val expectedLength = tokenizerFile.length()
            if (cacheFile.isFile) {
                val cached = runCatching { readBinaryCache(cacheFile, expectedLength) }.getOrNull()
                if (cached != null) {
                    return TokenizerLoadResult(cached, fromCache = true)
                }
                runCatching { cacheFile.delete() }
            }

            val parsed = loadFromJson(tokenizerFile)
            runCatching { parsed.writeBinaryCache(cacheFile, expectedLength) }
            return TokenizerLoadResult(parsed, fromCache = false)
        }

        private fun readBinaryCache(cacheFile: File, expectedSourceLength: Long): NllbTokenizer? {
            DataInputStream(BufferedInputStream(FileInputStream(cacheFile), 65_536)).use { input ->
                val magic = input.readInt()
                val version = input.readInt()
                val sourceLen = input.readLong()
                if (magic != CACHE_MAGIC || version != CACHE_VERSION || sourceLen != expectedSourceLength) {
                    return null
                }

                val vocabCount = input.readInt()
                if (vocabCount <= 0 || vocabCount > 512_000) return null
                val idToToken = Array(vocabCount) { "" }
                val tokenToId = HashMap<String, Int>(vocabCount + 32_768, 0.75f)
                var scratch = ByteArray(256)
                for (id in 0 until vocabCount) {
                    val len = input.readInt()
                    if (len > 0) {
                        if (len > scratch.size) scratch = ByteArray(len)
                        input.readFully(scratch, 0, len)
                        val tok = String(scratch, 0, len, Charsets.UTF_8)
                        idToToken[id] = tok
                        tokenToId[tok] = id
                    }
                }

                val langCount = input.readInt()
                val languageIds = HashMap<String, Int>(langCount + 16)
                for (i in 0 until langCount) {
                    val len = input.readInt()
                    if (len > scratch.size) scratch = ByteArray(len)
                    input.readFully(scratch, 0, len)
                    val code = String(scratch, 0, len, Charsets.UTF_8)
                    val id = input.readInt()
                    languageIds[code] = id
                    tokenToId[code] = id
                }

                val specialCount = input.readInt()
                val specialIds = HashSet<Int>(specialCount + 16)
                for (i in 0 until specialCount) {
                    specialIds += input.readInt()
                }

                val merges = MergeTable()
                merges.readFrom(input)

                return NllbTokenizer(
                    tokenToId = tokenToId,
                    idToToken = idToToken,
                    merges = merges,
                    languageIds = languageIds,
                    specialIds = specialIds
                )
            }
        }

        internal fun loadFromJson(tokenizerFile: File): NllbTokenizer {
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
