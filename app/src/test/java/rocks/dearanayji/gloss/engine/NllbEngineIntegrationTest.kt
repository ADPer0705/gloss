package rocks.dearanayji.gloss.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NllbEngineIntegrationTest {

    @Test
    fun `tokenizer and ONNX engine translate phrases and single words accurately`() {
        val dirPath = System.getProperty("gloss.model.dir")
        assumeTrue("Skipping ONNX integration test: -Dgloss.model.dir not provided", !dirPath.isNullOrBlank())
        val modelDir = File(dirPath!!)
        assumeTrue("Model dir missing", modelDir.isDirectory && File(modelDir, "encoder.onnx").isFile)

        NllbTranslatorEngine.load(modelDir).use { engine ->
            // 1. Tokenizer encode -> decode roundtrip check
            val encodedEn = engine.tokenizer.encode("Hello, how are you?", "eng_Latn")
            assertEquals(engine.tokenizer.languageTokenId("eng_Latn"), encodedEn.first())
            assertEquals(NllbTokenizer.EOS_ID, encodedEn.last())
            val roundtrip = engine.tokenizer.decode(encodedEn.toList())
            assertEquals("Hello, how are you?", roundtrip)

            val encodedHi = engine.tokenizer.encode("नमस्ते", "hin_Deva")
            assertEquals("नमस्ते", engine.tokenizer.decode(encodedHi.toList()))

            // 2. English -> Spanish
            val enToEs = engine.translate("Hello, how are you?", "eng_Latn", "spa_Latn")
            println("EN->ES: $enToEs")
            assertTrue("Expected Spanish greeting, got: $enToEs", enToEs.isNotBlank() && enToEs != "Hello, how are you?")
            assertTrue("Expected Hola/cómo in: $enToEs", enToEs.lowercase().contains("hola") || enToEs.lowercase().contains("cómo"))

            // 3. Spanish -> English
            val esToEn = engine.translate("¿Dónde está la biblioteca?", "spa_Latn", "eng_Latn")
            println("ES->EN: $esToEn")
            assertTrue("Expected library in: $esToEn", esToEn.lowercase().contains("library"))

            // 4. English -> French
            val enToFr = engine.translate("The weather is very nice today.", "eng_Latn", "fra_Latn")
            println("EN->FR: $enToFr")
            assertTrue("Expected French output, got: $enToFr", enToFr.isNotBlank())

            // 5. English -> Hindi
            val enToHi = engine.translate("The weather is very nice today.", "eng_Latn", "hin_Deva")
            println("EN->HI: $enToHi")
            assertTrue("Expected Devanagari output, got: $enToHi", enToHi.any { it in '\u0900'..'\u097F' })

            // 6. Single-word Duolingo-style tap lookups
            val wordLibrary = engine.translate("biblioteca", "spa_Latn", "eng_Latn")
            println("WORD biblioteca->EN: $wordLibrary")
            assertTrue("Expected library in: $wordLibrary", wordLibrary.lowercase().contains("library"))

            val wordWater = engine.translate("water", "eng_Latn", "spa_Latn")
            println("WORD water->ES: $wordWater")
            assertTrue("Expected agua in: $wordWater", wordWater.lowercase().contains("agua"))
        }
    }
}
