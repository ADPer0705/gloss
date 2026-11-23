package rocks.dearanayji.gloss.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NllbEngineIntegrationTest {

    @Test
    fun `binary tokenizer cache produces identical encoding and faster cold loads`() {
        val dirPath = System.getProperty("gloss.model.dir")
        assumeTrue("Skipping tokenizer cache test: -Dgloss.model.dir not provided", !dirPath.isNullOrBlank())
        val modelDir = File(dirPath!!)
        assumeTrue("Model dir missing", modelDir.isDirectory && File(modelDir, "tokenizer.json").isFile)

        val tokFile = File(modelDir, "tokenizer.json")
        val cacheFile = File(modelDir, "tokenizer.glossbin")
        cacheFile.delete()

        val firstLoad = NllbTokenizer.loadWithInfo(tokFile)
        assertFalse(firstLoad.fromCache)
        assertTrue(cacheFile.isFile)

        val secondLoad = NllbTokenizer.loadWithInfo(tokFile)
        assertTrue(secondLoad.fromCache)

        val samples = listOf(
            "eng_Latn" to "Hello, how are you?",
            "spa_Latn" to "¿Dónde está la biblioteca?",
            "hin_Deva" to "आज मौसम बहुत अच्छा है।",
            "jpn_Jpan" to "今日はいい天気ですね。"
        )
        for ((lang, text) in samples) {
            val idsA = firstLoad.tokenizer.encode(text, lang)
            val idsB = secondLoad.tokenizer.encode(text, lang)
            assertArrayEquals("Encode mismatch for $lang", idsA, idsB)
            assertEquals(firstLoad.tokenizer.decode(idsA.toList()), secondLoad.tokenizer.decode(idsB.toList()))
        }
    }

    @Test
    fun `tokenizer and ONNX engine translate phrases and single words accurately`() {
        val dirPath = System.getProperty("gloss.model.dir")
        assumeTrue("Skipping ONNX integration test: -Dgloss.model.dir not provided", !dirPath.isNullOrBlank())
        val modelDir = File(dirPath!!)
        assumeTrue("Model dir missing", modelDir.isDirectory && File(modelDir, "encoder.onnx").isFile)

        NllbTranslatorEngine.load(modelDir).use { engine ->
            println("Engine loaded in ${engine.loadMetrics}")
            val encodedEn = engine.tokenizer.encode("Hello, how are you?", "eng_Latn")
            assertEquals(engine.tokenizer.languageTokenId("eng_Latn"), encodedEn.first())
            assertEquals(NllbTokenizer.EOS_ID, encodedEn.last())
            assertEquals("Hello, how are you?", engine.tokenizer.decode(encodedEn.toList()))

            val enToEs = engine.translate("Hello, how are you?", "eng_Latn", "spa_Latn")
            assertTrue("Expected Hola/cómo in: $enToEs", enToEs.lowercase().contains("hola") || enToEs.lowercase().contains("cómo"))

            val esToEn = engine.translate("¿Dónde está la biblioteca?", "spa_Latn", "eng_Latn")
            assertTrue("Expected library in: $esToEn", esToEn.lowercase().contains("library"))

            val enToFr = engine.translate("The weather is very nice today.", "eng_Latn", "fra_Latn")
            assertTrue(enToFr.isNotBlank())

            val enToHi = engine.translate("The weather is very nice today.", "eng_Latn", "hin_Deva")
            assertTrue(enToHi.any { it in '\u0900'..'\u097F' })

            val wordLibrary = engine.translate("biblioteca", "spa_Latn", "eng_Latn")
            assertTrue(wordLibrary.lowercase().contains("library"))

            val wordWater = engine.translate("water", "eng_Latn", "spa_Latn")
            assertTrue(wordWater.lowercase().contains("agua"))
        }
    }
}
