package rocks.dearanayji.gloss.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSegmenterTest {

    @Test
    fun `tokenizes English sentence with punctuation and contractions`() {
        val tokens = WordSegmenter.tokenize("Hello, don't worry!")
        val words = tokens.filterIsInstance<WordSegmenter.TextToken.Word>()
        assertEquals(listOf("Hello", "don't", "worry"), words.map { it.lookup })
        assertEquals(listOf(0, 1, 2), words.map { it.index })
    }

    @Test
    fun `tokenizes Spanish inverted punctuation cleanly`() {
        val tokens = WordSegmenter.tokenize("¿Dónde está la biblioteca?")
        val words = tokens.filterIsInstance<WordSegmenter.TextToken.Word>()
        assertEquals(listOf("Dónde", "está", "la", "biblioteca"), words.map { it.lookup })
    }

    @Test
    fun `tokenizes Devanagari words preserving matras`() {
        val tokens = WordSegmenter.tokenize("नमस्ते दुनिया")
        val words = tokens.filterIsInstance<WordSegmenter.TextToken.Word>()
        assertEquals(listOf("नमस्ते", "दुनिया"), words.map { it.lookup })
    }

    @Test
    fun `tokenizes Arabic and Japanese text`() {
        val arabicWords = WordSegmenter.tokenize("مرحبا بالعالم").filterIsInstance<WordSegmenter.TextToken.Word>()
        assertEquals(2, arabicWords.size)

        val jaWords = WordSegmenter.tokenize("東京に行きます").filterIsInstance<WordSegmenter.TextToken.Word>()
        assertTrue(jaWords.isNotEmpty())
    }

    @Test
    fun `preserves newlines in separator tokens`() {
        val tokens = WordSegmenter.tokenize("Line one\nLine two")
        val seps = tokens.filterIsInstance<WordSegmenter.TextToken.Separator>()
        assertTrue(seps.any { '\n' in it.text })
    }
}
