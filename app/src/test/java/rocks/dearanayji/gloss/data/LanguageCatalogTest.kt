package rocks.dearanayji.gloss.data

import com.google.gson.stream.JsonReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class LanguageCatalogTest {

    @Test
    fun `catalog has all 202 languages with unique codes`() {
        assertEquals(202, LanguageCatalog.all.size)
        assertEquals(202, LanguageCatalog.all.map { it.code }.toSet().size)
    }

    @Test
    fun `search matches English name, native name, and code`() {
        assertTrue(LanguageCatalog.search("español").any { it.code == "spa_Latn" })
        assertTrue(LanguageCatalog.search("हिन्दी").any { it.code == "hin_Deva" })
        assertTrue(LanguageCatalog.search("jpn_Jpan").any { it.code == "jpn_Jpan" })
    }

    @Test
    fun `all tokenizer language codes are present in LanguageCatalog`() {
        val modelDirPath = System.getProperty("gloss.model.dir") ?: return
        val tokenizerFile = File(modelDirPath, "tokenizer.json")
        if (!tokenizerFile.isFile) return

        val tokenizerCodes = mutableSetOf<String>()
        JsonReader(BufferedReader(InputStreamReader(FileInputStream(tokenizerFile), Charsets.UTF_8))).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "added_tokens") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var content = ""
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "content") {
                                content = reader.nextString()
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if ('_' in content && !content.startsWith("<")) {
                            tokenizerCodes += content
                        }
                    }
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }

        val catalogCodes = LanguageCatalog.all.map { it.code }.toSet()
        assertEquals(tokenizerCodes, catalogCodes)
    }
}
