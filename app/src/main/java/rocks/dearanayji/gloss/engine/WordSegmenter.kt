package rocks.dearanayji.gloss.engine

import java.text.BreakIterator

/**
 * Splits a sentence in any script into tappable words and non-word separators
 * (spaces, punctuation, newlines) using Unicode word boundary rules.
 */
object WordSegmenter {

    sealed interface TextToken {
        data class Word(
            val display: String,
            val lookup: String,
            val index: Int
        ) : TextToken

        data class Separator(val text: String) : TextToken
    }

    fun tokenize(text: String): List<TextToken> {
        if (text.isEmpty()) return emptyList()

        val result = mutableListOf<TextToken>()
        val iterator = BreakIterator.getWordInstance()
        iterator.setText(text)

        var wordIndex = 0
        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val slice = text.substring(start, end)
            if (isWordSlice(slice)) {
                val cleaned = cleanLookupToken(slice)
                if (cleaned.isNotEmpty()) {
                    result += TextToken.Word(
                        display = slice,
                        lookup = cleaned,
                        index = wordIndex++
                    )
                } else {
                    appendSeparator(result, slice)
                }
            } else {
                appendSeparator(result, slice)
            }
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun isWordSlice(slice: String): Boolean {
        for (i in slice.indices) {
            val ch = slice[i]
            if (Character.isLetterOrDigit(ch)) return true
        }
        return false
    }

    private fun cleanLookupToken(slice: String): String {
        var left = 0
        var right = slice.length
        while (left < right && !isTokenChar(slice[left])) left++
        while (right > left && !isTokenChar(slice[right - 1])) right--
        return if (left < right) slice.substring(left, right) else ""
    }

    private fun isTokenChar(ch: Char): Boolean =
        Character.isLetterOrDigit(ch) || Character.getType(ch) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(ch) == Character.COMBINING_SPACING_MARK.toInt() ||
            Character.getType(ch) == Character.ENCLOSING_MARK.toInt()

    private fun appendSeparator(list: MutableList<TextToken>, text: String) {
        if (text.isEmpty()) return
        val last = list.lastOrNull()
        if (last is TextToken.Separator && '\n' !in text && '\n' !in last.text) {
            list[list.lastIndex] = TextToken.Separator(last.text + text)
        } else {
            list += TextToken.Separator(text)
        }
    }
}
